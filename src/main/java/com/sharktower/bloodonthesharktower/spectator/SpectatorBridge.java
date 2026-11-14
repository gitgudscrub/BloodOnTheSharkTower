package com.sharktower.bloodonthesharktower.spectator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.serverupdate.SiteLink;
import com.sharktower.bloodonthesharktower.states.DeathVisibility;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feeds the website's "Watch live" page: the public table (seats, names,
 * deaths as players see them, phase, who is in which voice room) and, while
 * someone is listening and the owner has voice switched on, every voice room's
 * audio as raw Opus frames. Roles, alignments, reminders and anything else
 * from the Grimoire are never sent.
 *
 * <p>Uses the same {@code site-link.json} as server updates; without it this
 * does nothing. Spectators on the site can only listen and talk among
 * themselves: nothing comes back into the game except the "audio wanted" flag.
 */
public final class SpectatorBridge {
    private static final Gson GSON = new Gson();
    private static final int SNAPSHOT_EVERY_TICKS = 10;
    private static final long HEARTBEAT_MS = 2_000;
    private static final int MAX_QUEUED_FRAMES = 600;
    private static final int MAX_BATCH = 150;
    private static final int MAX_OPUS_BYTES = 1_200;

    private static final Deque<Frame> FRAMES = new ArrayDeque<>();
    private static final AtomicBoolean LIVE_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean AUDIO_IN_FLIGHT = new AtomicBoolean();
    private static volatile boolean audioWanted;
    private static volatile String lastBody;
    private static volatile long lastSentMs;
    private static int ticks;
    private static ScheduledExecutorService audioClock;

    private SpectatorBridge() {}

    private record Frame(String room, String speaker, boolean whisper, String opus) {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            audioClock = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Sharktower spectator voice");
                t.setDaemon(true);
                return t;
            });
            audioClock.scheduleAtFixedRate(SpectatorBridge::flushAudio, 100, 100, TimeUnit.MILLISECONDS);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            audioWanted = false;
            if (audioClock != null) audioClock.shutdownNow();
            synchronized (FRAMES) {
                FRAMES.clear();
            }
            JsonObject off = new JsonObject();
            off.addProperty("live", false);
            off.addProperty("stopping", true);
            SiteLink.post("/internal/minecraft/live", GSON.toJson(off), "spectator updates", null);
        });
        ServerTickEvents.END_SERVER_TICK.register(SpectatorBridge::tick);
    }

    /** Server thread: build the public snapshot; the HTTP call is async. */
    private static void tick(MinecraftServer server) {
        if (!SiteLink.enabled() || ++ticks % SNAPSHOT_EVERY_TICKS != 0 || LIVE_IN_FLIGHT.get()) return;
        String body;
        try {
            JsonObject snap = snapshot(server);
            // Only while an approved spectator is watching; the site never
            // shows this field to anyone else.
            if (GrimoireShare.wanted()) snap.add("grimoire", GrimoireShare.snapshot(server));
            body = GSON.toJson(snap);
        } catch (RuntimeException e) {
            BloodOnTheSharktower.LOGGER.debug("Spectator snapshot skipped", e);
            return;
        }
        long now = System.currentTimeMillis();
        if (body.equals(lastBody) && now - lastSentMs < HEARTBEAT_MS) return;
        if (!LIVE_IN_FLIGHT.compareAndSet(false, true)) return;
        SiteLink.post("/internal/minecraft/live", body, "spectator updates", reply -> {
            lastBody = body;
            lastSentMs = System.currentTimeMillis();
            readReply(reply);
        }).whenComplete((ok, error) -> LIVE_IN_FLIGHT.set(false));
    }

    /** SVC thread: queue one microphone frame if the site wants audio. */
    public static void capture(UUID sender, byte[] opus, boolean whisper) {
        if (!audioWanted || sender == null || opus == null || opus.length == 0 || opus.length > MAX_OPUS_BYTES) return;
        String room = NightChatManager.spectatorRoom(sender);
        if (room == null) return;
        Frame frame = new Frame(room, sender.toString(), whisper, Base64.getEncoder().encodeToString(opus));
        synchronized (FRAMES) {
            if (FRAMES.size() >= MAX_QUEUED_FRAMES) FRAMES.pollFirst();
            FRAMES.addLast(frame);
        }
    }

    private static void flushAudio() {
        try {
            if (!AUDIO_IN_FLIGHT.compareAndSet(false, true)) return;
            JsonArray frames = new JsonArray();
            synchronized (FRAMES) {
                while (!FRAMES.isEmpty() && frames.size() < MAX_BATCH) {
                    Frame f = FRAMES.pollFirst();
                    JsonObject o = new JsonObject();
                    o.addProperty("room", f.room());
                    o.addProperty("speaker", f.speaker());
                    if (f.whisper()) o.addProperty("whisper", true);
                    o.addProperty("opus", f.opus());
                    frames.add(o);
                }
            }
            if (frames.isEmpty() || !audioWanted) {
                AUDIO_IN_FLIGHT.set(false);
                return;
            }
            JsonObject body = new JsonObject();
            body.add("frames", frames);
            SiteLink.post("/internal/minecraft/audio", GSON.toJson(body), "spectator voice", SpectatorBridge::readReply)
                    .whenComplete((ok, error) -> AUDIO_IN_FLIGHT.set(false));
        } catch (RuntimeException e) {
            AUDIO_IN_FLIGHT.set(false);
            BloodOnTheSharktower.LOGGER.debug("Spectator voice batch skipped", e);
        }
    }

    /**
     * The site answers {"audio": true} while it has listeners and voice is on;
     * the live reply also carries Grimoire requests ({@link GrimoireShare}).
     */
    private static void readReply(String reply) {
        JsonObject json = GSON.fromJson(reply, JsonObject.class);
        if (json != null && json.has("requests")) GrimoireShare.readReply(json);
        boolean wanted = json != null && json.has("audio") && json.get("audio").getAsBoolean();
        if (wanted != audioWanted) {
            audioWanted = wanted;
            if (!wanted) {
                synchronized (FRAMES) {
                    FRAMES.clear();
                }
            }
            BloodOnTheSharktower.LOGGER.info("Spectator voice {}.", wanted ? "on (someone is listening)" : "off");
        }
    }

    /** Public table state only. Must stay free of roles and Grimoire data. */
    static JsonObject snapshot(MinecraftServer server) {
        // During setup, seats live in the Storyteller's pending map until the
        // deal, as for the in-game seat list.
        boolean setup = ServerState.currentNight == 0 && ServerState.currentDay == 0 && !ServerState.gameEnded;
        Map<UUID, Integer> seats = setup ? StorytellerState.effectiveGrimoireSeats() : ServerState.PLAYER_SEAT_NUMBERS;
        boolean live = !ServerState.gameEnded
                && (!seats.isEmpty() || ServerState.currentNight > 0 || ServerState.currentDay > 0);
        String phase = ServerState.gameEnded ? "ended"
                : ServerState.currentDay <= 0 && ServerState.currentNight <= 0 ? "setup"
                : ServerState.currentNight != ServerState.currentDay ? "night" : "day";
        Map<UUID, Boolean> dead = DeathVisibility.visible(ServerState.PLAYER_DEATH_STATUS, false);

        List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());
        online.sort(Comparator.comparing(p -> p.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        Map<String, String> names = new HashMap<>();
        for (ServerPlayer p : online) names.put(p.getUUID().toString(), p.getName().getString());

        JsonArray storytellers = new JsonArray();
        JsonArray players = new JsonArray();
        JsonArray others = new JsonArray();
        Map<String, Integer> roomCounts = new LinkedHashMap<>();
        List<ServerPlayer> seated = new ArrayList<>();
        for (ServerPlayer p : online) {
            UUID id = p.getUUID();
            String room = NightChatManager.spectatorRoom(id);
            if (room != null) roomCounts.merge(room, 1, Integer::sum);
            if (StorytellerState.isStoryteller(id)) {
                storytellers.add(person(p, room));
            } else if (live && seats.containsKey(id)) {
                seated.add(p);
            } else {
                others.add(person(p, room));
            }
        }
        seated.sort(Comparator.comparingInt(p -> seats.getOrDefault(p.getUUID(), Integer.MAX_VALUE)));
        for (ServerPlayer p : seated) {
            JsonObject o = person(p, NightChatManager.spectatorRoom(p.getUUID()));
            o.addProperty("seat", seats.get(p.getUUID()));
            o.addProperty("alive", !Boolean.TRUE.equals(dead.get(p.getUUID())));
            players.add(o);
        }

        Map<Integer, String> bySeat = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : seats.entrySet()) {
            String n = names.get(e.getKey().toString());
            if (n != null) bySeat.put(e.getValue(), n);
        }
        JsonArray rooms = new JsonArray();
        for (Map.Entry<String, Integer> e : roomCounts.entrySet()) {
            JsonObject r = new JsonObject();
            r.addProperty("id", e.getKey());
            r.addProperty("name", roomName(e.getKey(), names, bySeat));
            r.addProperty("count", e.getValue());
            rooms.add(r);
        }

        JsonObject s = new JsonObject();
        s.addProperty("live", live);
        s.addProperty("gameId", "sharktower-" + ServerState.resetGeneration);
        s.addProperty("phase", phase);
        s.addProperty("day", Math.max(0, ServerState.currentDay));
        s.addProperty("night", Math.max(0, ServerState.currentNight));
        s.add("storytellers", storytellers);
        s.add("players", players);
        s.add("others", others);
        s.add("rooms", rooms);
        return s;
    }

    private static JsonObject person(ServerPlayer p, String room) {
        JsonObject o = new JsonObject();
        o.addProperty("id", p.getUUID().toString());
        o.addProperty("name", p.getName().getString());
        if (room != null) o.addProperty("room", room);
        return o;
    }

    /** Human name for a room id from {@link NightChatManager#spectatorRoom}. */
    static String roomName(String room, Map<String, String> namesByUuid, Map<Integer, String> namesBySeat) {
        if ("near".equals(room)) return "Open voice (nearby)";
        if ("town".equals(room)) return "Town Square";
        if (room.startsWith("zone:")) return "Private chat: " + room.substring(5).replace('_', ' ');
        if (room.startsWith("house:")) {
            String seat = room.substring(6);
            String owner = null;
            try {
                owner = namesBySeat.get(Integer.parseInt(seat));
            } catch (NumberFormatException ignored) {
                // fall through to the plain name
            }
            return owner == null ? "House " + seat : "House " + seat + " (" + owner + ")";
        }
        if (room.startsWith("st:")) {
            String who = namesByUuid.get(room.substring(3));
            return who == null ? "Storyteller chat" : "Storyteller with " + who;
        }
        return room;
    }
}
