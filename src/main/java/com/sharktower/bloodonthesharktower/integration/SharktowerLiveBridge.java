package com.sharktower.bloodonthesharktower.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.states.DeathVisibility;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional server-only, outbound publisher for the external Sharktower Live website.
 * Sends an explicit allowlist of PUBLIC state; never serializes roles, alignments,
 * Storyteller reminders, secret night actions, or Grimoire data.
 *
 * Configure with config/sharktower-live-bridge.properties in the server instance:
 * enabled=true
 * url=https://spectator.example.com/api/bridge/state
 * token=<same random BRIDGE_TOKEN as the website>
 *
 * An AMP/Docker Minecraft instance cannot normally reach the Linux host using
 * 127.0.0.1. The HTTPS Cloudflare hostname also works as an outbound URL.
 */
public final class SharktowerLiveBridge {
    private static final int SNAPSHOT_INTERVAL_TICKS = 10; // Detect public-state changes about twice per second
    private static final long UNCHANGED_HEARTBEAT_MS = 5_000L;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean CONNECTED = new AtomicBoolean();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static volatile Settings settings;
    // Voice callbacks run on SVC's processing thread, not the Minecraft tick thread.
    // Allowlist is built on the tick thread from public seat/voice routing only.
    private static volatile Map<UUID, String> ALLOWED_AUDIO_ROUTES = Map.of();
    private static final Deque<VoiceFrame> AUDIO_FRAMES = new ArrayDeque<>();
    private static final AtomicBoolean AUDIO_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean AUDIO_TIMER_STARTED = new AtomicBoolean();
    private static final int MAX_QUEUED_FRAMES = 250;
    private static final ScheduledExecutorService AUDIO_TIMER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "SharktowerLiveVoicePublisher");
                thread.setDaemon(true);
                return thread;
            });
    private static volatile long lastWarningMs;
    private static volatile long lastSuccessfulSendMs;
    private static volatile String lastSuccessfulPayload;
    private static long lastAttemptMs;
    private static int ticks;

    private SharktowerLiveBridge() {}

    public static void tick(MinecraftServer server) {
        // Tick callback runs on Minecraft's main thread. HTTP is always asynchronous.
        if (server == null) return;
        if (settings == null) settings = readSettings();
        Settings cfg = settings;
        if (!cfg.enabled() || ++ticks % SNAPSHOT_INTERVAL_TICKS != 0) return;
        if (IN_FLIGHT.get()) return;

        String body;
        try {
            body = makePublicSnapshot(server).toString();
        } catch (Exception e) {
            warnRateLimited("Unable to build spectator snapshot: " + e.getClass().getSimpleName());
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttemptMs < RETRY_DELAY_MS && lastAttemptMs > lastSuccessfulSendMs) return;
        if (body.equals(lastSuccessfulPayload)
                && now - lastSuccessfulSendMs < UNCHANGED_HEARTBEAT_MS) return;
        if (!IN_FLIGHT.compareAndSet(false, true)) return;
        lastAttemptMs = now;

        HttpRequest request = HttpRequest.newBuilder(cfg.uri())
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.token())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        CONNECTED.set(false);
                        warnRateLimited("Spectator bridge connection failed (" + error.getClass().getSimpleName() + ").");
                    } else if (response.statusCode() != 200) {
                        CONNECTED.set(false);
                        warnRateLimited("Spectator bridge returned HTTP " + response.statusCode() + ".");
                    } else {
                        lastSuccessfulPayload = body;
                        lastSuccessfulSendMs = System.currentTimeMillis();
                        if (CONNECTED.compareAndSet(false, true)) {
                            BloodOnTheSharktower.LOGGER.info("Sharktower Live bridge connected successfully (HTTP 200).");
                        }
                    }
                    IN_FLIGHT.set(false);
                });
    }

    /**
     * Pass through Opus frames from microphone events without decoding on Minecraft.
     * Only allow non-whispered daytime audio from PUBLICLY assigned players whose
     * actual SVC group matches their published Town Square / private zone.
     * Storyteller-only, house, proximity, night and manual private sessions fail closed.
     */
    public static void acceptVoiceFrame(UUID sender, UUID groupId, byte[] opus, boolean whispering) {
        Settings cfg = settings;
        if (cfg == null || !cfg.enabled() || !cfg.audioEnabled() || whispering
                || sender == null || groupId == null || opus == null
                || opus.length == 0 || opus.length > 2400) return;
        String room = ALLOWED_AUDIO_ROUTES.get(sender);
        if (room == null) return;
        String groupKey = "town-square".equals(room)
                ? "blood-on-the-sharktower:day:shared"
                : room.startsWith("zone-")
                    ? "blood-on-the-sharktower:day:zone:" + room.substring("zone-".length())
                    : null;
        if (groupKey == null || !groupId.equals(UUID.nameUUIDFromBytes(groupKey.getBytes(StandardCharsets.UTF_8)))) return;
        synchronized (AUDIO_FRAMES) {
            if (AUDIO_FRAMES.size() >= MAX_QUEUED_FRAMES) AUDIO_FRAMES.pollFirst();
            AUDIO_FRAMES.addLast(new VoiceFrame(sender.toString(), room,
                    Base64.getEncoder().encodeToString(opus)));
        }
    }

    private static void publishVoiceBatch() {
        Settings cfg = settings;
        if (cfg == null || !cfg.enabled() || !cfg.audioEnabled()
                || !AUDIO_IN_FLIGHT.compareAndSet(false, true)) return;

        JsonArray frames = new JsonArray();
        synchronized (AUDIO_FRAMES) {
            while (!AUDIO_FRAMES.isEmpty() && frames.size() < 60) {
                VoiceFrame voice = AUDIO_FRAMES.pollFirst();
                JsonObject frame = new JsonObject();
                frame.addProperty("sender", voice.sender());
                frame.addProperty("room", voice.room());
                frame.addProperty("opus", voice.opus());
                frames.add(frame);
            }
        }
        if (frames.isEmpty()) { AUDIO_IN_FLIGHT.set(false); return; }
        JsonObject data = new JsonObject();
        data.add("frames", frames);
        HttpRequest request = HttpRequest.newBuilder(cfg.uri().resolve("audio"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.token())
                .POST(HttpRequest.BodyPublishers.ofString(data.toString()))
                .build();
        try {
            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete((result, error) -> {
                        AUDIO_IN_FLIGHT.set(false);
                        if (error != null) {
                            warnRateLimited("Spectator voice delivery failed (" + error.getClass().getSimpleName() + ").");
                        } else if (result.statusCode() != 200) {
                            warnRateLimited("Spectator voice endpoint returned HTTP " + result.statusCode() + ".");
                        }
                    });
        } catch (RuntimeException e) {
            AUDIO_IN_FLIGHT.set(false);
            warnRateLimited("Spectator voice publisher failed (" + e.getClass().getSimpleName() + ").");
        }
    }

    private record VoiceFrame(String sender, String room, String opus) {}

    private static JsonObject makePublicSnapshot(MinecraftServer server) {
        JsonObject snapshot = new JsonObject();
        // During SETUP, seating changes are held in the Storyteller's pending
        // seat map until roles are committed. Match the normal client state sync.
        boolean setup = ServerState.currentNight == 0
                && ServerState.currentDay == 0 && !ServerState.gameEnded;
        Map<UUID, Integer> seats = setup
                ? StorytellerState.effectiveGrimoireSeats()
                : ServerState.PLAYER_SEAT_NUMBERS;
        boolean active = !ServerState.gameEnded
                && (!seats.isEmpty() || ServerState.currentNight > 0 || ServerState.currentDay > 0);
        String phase = ServerState.gameEnded ? "ended"
                : ServerState.currentDay <= 0 && ServerState.currentNight <= 0 ? "setup"
                : ServerState.currentNight != ServerState.currentDay ? "night" : "day";
        snapshot.addProperty("live", active);
        snapshot.addProperty("gameId", "sharktower-" + ServerState.resetGeneration);
        snapshot.addProperty("phase", phase);
        snapshot.addProperty("day", Math.max(0, ServerState.currentDay));
        snapshot.addProperty("night", Math.max(0, ServerState.currentNight));

        JsonArray players = new JsonArray();
        JsonArray conversations = new JsonArray();
        JsonArray storytellers = new JsonArray();
        // Storyteller identity is already public in the in-game player directory.
        // Publish only online assigned Storytellers, never their private activity.
        List<ServerPlayer> activeStorytellers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StorytellerState.isStoryteller(player.getUUID())) activeStorytellers.add(player);
        }
        activeStorytellers.sort(Comparator.comparing(
                player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        for (ServerPlayer storyteller : activeStorytellers) {
            JsonObject identity = new JsonObject();
            identity.addProperty("id", storyteller.getUUID().toString());
            identity.addProperty("name", storyteller.getName().getString());
            storytellers.add(identity);
        }
        snapshot.add("storytellers", storytellers);
        if (!active) {
            ALLOWED_AUDIO_ROUTES = Map.of();
            snapshot.add("players", players);
            snapshot.add("conversations", conversations);
            return snapshot;
        }

        Map<UUID, Boolean> publicDeaths = DeathVisibility.visible(
                ServerState.PLAYER_DEATH_STATUS, false);

        // Only seated, online non-Storytellers. Never expose Storyteller presence
        // in confidential chats, even through the conversation membership list.
        List<ServerPlayer> seated = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (seats.containsKey(id)
                    && !StorytellerState.isStoryteller(id)) seated.add(player);
        }
        seated.sort(Comparator.comparingInt(p ->
                seats.getOrDefault(p.getUUID(), Integer.MAX_VALUE)));

        // A Storyteller entering a private day zone turns it into a
        // protected ST conversation for spectators: do not relay ANY participant.
        // Town Square remains public, but ST microphone audio itself is excluded.
        java.util.Set<String> storytellerPrivateRooms = new java.util.HashSet<>();
        for (UUID id : StorytellerState.STORYTELLERS) {
            String stRoute = NightChatManager.routeCode(id);
            if (stRoute != null && stRoute.startsWith("DAY_ZONE:")) {
                storytellerPrivateRooms.add("zone-" + stRoute.substring("DAY_ZONE:".length()));
            }
        }
        Map<UUID, String> audioRoutes = new HashMap<>();
        Map<String, List<String>> roomMembers = new HashMap<>();
        if ("day".equals(phase)) roomMembers.put("town-square", new ArrayList<>());
        for (ServerPlayer player : seated) {
            UUID id = player.getUUID();
            String route = NightChatManager.routeCode(id);
            String group = null;
            if ("day".equals(phase)) {
                if ("DAY_SHARED".equals(route)) group = "town-square";
                else if (route != null && route.startsWith("DAY_ZONE:")) {
                    group = "zone-" + route.substring("DAY_ZONE:".length());
                }
                // ST private, house and unconnected routes are excluded, not
                // mistakenly classified as Town Square.
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("name", player.getName().getString());
            entry.addProperty("alive", !Boolean.TRUE.equals(publicDeaths.get(id)));
            if (group == null) entry.add("chatGroup", com.google.gson.JsonNull.INSTANCE);
            else entry.addProperty("chatGroup", group);
            players.add(entry);
            if (group != null) {
                roomMembers.computeIfAbsent(group, key -> new ArrayList<>()).add(id.toString());
                if (!storytellerPrivateRooms.contains(group)) audioRoutes.put(id, group);
            }
        }
        roomMembers.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(room -> {
            JsonObject conversation = new JsonObject();
            conversation.addProperty("id", room.getKey());
            conversation.addProperty("name", "town-square".equals(room.getKey())
                    ? "Town Square" : "Private Chat: " + room.getKey().substring(5));
            JsonArray ids = new JsonArray();
            for (String id : room.getValue()) ids.add(id);
            conversation.add("playerIds", ids);
            conversations.add(conversation);
        });

        ALLOWED_AUDIO_ROUTES = Map.copyOf(audioRoutes);
        snapshot.add("players", players);
        snapshot.add("conversations", conversations);
        return snapshot;
    }

    private static Settings readSettings() {
        Properties properties = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("sharktower-live-bridge.properties");
        if (Files.isRegularFile(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException e) {
                BloodOnTheSharktower.LOGGER.warn(
                        "Sharktower Live bridge configuration could not be read ({}). Disabled.", e.getClass().getSimpleName());
                return Settings.disabled();
            }
        }
        String enabled = setting("SHARKTOWER_LIVE_BRIDGE_ENABLED", properties, "enabled");
        if (!Boolean.parseBoolean(enabled)) return Settings.disabled();

        String target = setting("SHARKTOWER_LIVE_BRIDGE_URL", properties, "url");
        String token = setting("SHARKTOWER_LIVE_BRIDGE_TOKEN", properties, "token");
        if (target == null || token == null || token.length() < 32
                || token.contains("\r") || token.contains("\n")) {
            BloodOnTheSharktower.LOGGER.warn("Sharktower Live bridge enabled but URL/token configuration is invalid; disabled.");
            return Settings.disabled();
        }
        try {
            URI uri = URI.create(target.trim());
            boolean isHttps = "https".equalsIgnoreCase(uri.getScheme());
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
            if ((!isHttps && !localHttp) || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getRawQuery() != null || !"/api/bridge/state".equals(uri.getPath())) {
                throw new IllegalArgumentException("Invalid bridge endpoint");
            }
            boolean audioEnabled = Boolean.parseBoolean(
                    setting("SHARKTOWER_LIVE_BRIDGE_AUDIO_ENABLED", properties, "audioEnabled"));
            if (audioEnabled && AUDIO_TIMER_STARTED.compareAndSet(false, true)) {
                AUDIO_TIMER.scheduleWithFixedDelay(
                        SharktowerLiveBridge::publishVoiceBatch, 150, 150, TimeUnit.MILLISECONDS);
            }
            BloodOnTheSharktower.LOGGER.info("Sharktower Live public-state bridge enabled. Daytime spectator voice: {} (no Grimoire data).",
                    audioEnabled ? "ON" : "OFF");
            return new Settings(true, uri, token, audioEnabled);
        } catch (IllegalArgumentException e) {
            BloodOnTheSharktower.LOGGER.warn("Sharktower Live bridge URL invalid; use HTTPS or local loopback HTTP and /api/bridge/state. Disabled.");
            return Settings.disabled();
        }
    }

    private static String setting(String env, Properties properties, String key) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = properties.getProperty(key);
        return value == null ? null : value.trim();
    }

    private static void warnRateLimited(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarningMs < 60_000L) return;
        lastWarningMs = now;
        BloodOnTheSharktower.LOGGER.warn(message);
    }

    private record Settings(boolean enabled, URI uri, String token, boolean audioEnabled) {
        static Settings disabled() { return new Settings(false, null, null, false); }
    }
}
