package com.sharktower.bloodonthesharktower.serverupdate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The story of the current game for the website's game recap: phase changes,
 * deaths, revivals, executions and every vote, in order. Sent with the finished
 * game ({@link SiteGameStats}) in the same shape as Grim Poster's timeline and
 * vote log, so Minecraft games also count for vote and death achievements.
 *
 * <p>Phases are numbered like Grim Poster: 0 setup, night N = 2N-1, day N = 2N.
 * People are kept as UUIDs while the game runs and turned into seat numbers
 * when the game is sent. Deaths are noticed by watching death status, so every
 * way a player can die (night kill, Slayer shot, Storyteller toggle) is caught.
 * A server restart mid-game loses what came before it.
 */
public final class GameRecap {
    static final int MAX_TIMELINE = 400;
    static final int MAX_VOTES = 100;
    private static final int SAMPLE_EVERY_TICKS = 10;

    record Event(String type, long time, int phase, UUID player) {}

    record Vote(int phase, long time, UUID nominator, UUID nominee, boolean exile, int votes, int majority,
                Map<UUID, Integer> voters) {}

    private static final List<Event> EVENTS = new ArrayList<>();
    private static final List<Vote> VOTES = new ArrayList<>();
    private static final Map<UUID, Boolean> DEAD = new HashMap<>();
    private static long generation = Long.MIN_VALUE;
    private static int phase = -1;
    private static int ticks;

    private GameRecap() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % SAMPLE_EVERY_TICKS == 0) sample(ServerState.resetGeneration, phaseNow(), ServerState.gameEnded,
                    ServerState.PLAYER_SEAT_NUMBERS, ServerState.PLAYER_DEATH_STATUS, System.currentTimeMillis());
        });
    }

    static int phaseNow() {
        int night = ServerState.currentNight, day = ServerState.currentDay;
        if (night == 0 && day == 0) return 0;
        return night != day ? 2 * night - 1 : 2 * day;
    }

    /** Server thread, twice a second: phase changes, deaths and revivals. */
    static synchronized void sample(long resetGeneration, int phaseNow, boolean ended, Map<UUID, Integer> seats,
                                    Map<UUID, Boolean> deaths, long now) {
        if (resetGeneration != generation || (phaseNow == 0 && phase > 0)) {
            clear();
            generation = resetGeneration;
        }
        if (ended) return;
        if (phaseNow != phase) {
            phase = phaseNow;
            add(new Event("phase", now, phaseNow, null));
        }
        for (UUID id : seats.keySet()) {
            boolean dead = Boolean.TRUE.equals(deaths.get(id));
            Boolean before = DEAD.put(id, dead);
            // Setup moves people around freely: only changes during play count.
            if (before == null || before == dead || phaseNow == 0) continue;
            add(new Event(dead ? "death" : "revive", now, phaseNow, id));
        }
    }

    /** End Game: pick up deaths from the last half second before the game is sent. */
    static void catchUp() {
        sample(ServerState.resetGeneration, phaseNow(), false, ServerState.PLAYER_SEAT_NUMBERS,
                ServerState.PLAYER_DEATH_STATUS, System.currentTimeMillis());
    }

    /** An execution was carried out (the death itself is noticed by {@link #sample}). */
    public static synchronized void onExecution(UUID player, boolean died) {
        if (player == null) return;
        add(new Event(died ? "execution" : "execution_survived", System.currentTimeMillis(), phaseNow(), player));
    }

    /** A nomination vote was counted. {@code majority} is the base threshold (half the living). */
    public static synchronized void onVote(UUID nominator, UUID nominee, int votes, int majority, List<UUID> voters) {
        if (nominee == null) return;
        Map<UUID, Integer> hands = new HashMap<>();
        for (UUID voter : voters) hands.put(voter, Math.max(1, DaytimeState.getVoteMultiplier(voter)));
        addVote(new Vote(phaseNow(), System.currentTimeMillis(), nominator, nominee, false, votes, majority, hands));
    }

    /** A Traveller exile call was counted. */
    public static synchronized void onExile(UUID caller, UUID traveller, int support, int threshold) {
        if (traveller == null) return;
        addVote(new Vote(phaseNow(), System.currentTimeMillis(), caller, traveller, true, support, threshold, Map.of()));
    }

    private static void add(Event event) {
        if (EVENTS.size() >= MAX_TIMELINE) EVENTS.remove(0);
        EVENTS.add(event);
    }

    private static void addVote(Vote vote) {
        if (VOTES.size() >= MAX_VOTES) VOTES.remove(0);
        VOTES.add(vote);
    }

    static synchronized void clear() {
        EVENTS.clear();
        VOTES.clear();
        DEAD.clear();
        phase = -1;
    }

    /** Timeline entries as {type, time, phase, seat?}; seats are 1-based like the players list. */
    static synchronized JsonArray timeline(Map<UUID, Integer> seats) {
        JsonArray out = new JsonArray();
        for (Event e : EVENTS) {
            JsonObject o = new JsonObject();
            o.addProperty("type", e.type());
            o.addProperty("time", e.time());
            o.addProperty("phase", e.phase());
            if (e.player() != null) {
                Integer seat = seats.get(e.player());
                if (seat == null || seat <= 0) continue;
                o.addProperty("seat", seat);
            }
            out.add(o);
        }
        return out;
    }

    /** Votes as Grim Poster logs them, with 1-based seats: voters are [seat, hands]. */
    static synchronized JsonArray votes(Map<UUID, Integer> seats) {
        JsonArray out = new JsonArray();
        for (Vote v : VOTES) {
            Integer nominee = seats.get(v.nominee());
            if (nominee == null || nominee <= 0) continue;
            JsonObject o = new JsonObject();
            o.addProperty("phase", v.phase());
            o.addProperty("time", v.time());
            Integer nominator = v.nominator() == null ? null : seats.get(v.nominator());
            if (nominator != null && nominator > 0) o.addProperty("nominator", nominator);
            o.addProperty("nominee", nominee);
            o.addProperty("type", v.exile() ? "exile" : "execution");
            o.addProperty("votes", Math.max(0, v.votes()));
            o.addProperty("majority", Math.max(0, v.majority()));
            JsonArray voters = new JsonArray();
            v.voters().entrySet().stream()
                    .filter(e -> seats.get(e.getKey()) != null && seats.get(e.getKey()) > 0)
                    .sorted(Map.Entry.comparingByKey((a, b) -> Integer.compare(seats.get(a), seats.get(b))))
                    .forEach(e -> {
                        JsonArray pair = new JsonArray();
                        pair.add(seats.get(e.getKey()));
                        pair.add(e.getValue());
                        voters.add(pair);
                    });
            o.add("voters", voters);
            out.add(o);
        }
        return out;
    }
}
