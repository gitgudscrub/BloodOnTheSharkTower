package com.sharktower.bloodonthesharktower.spectator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.ElectionState;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The "Watch live" map: the town seen from above. Only what anyone standing in
 * the town could see: seat homes, town-square seats, the clock centre, where
 * each person stands, and the voting clockhand.
 *
 * <p>Players in spectator mode (a roaming Wraith, say) are left off, so the map
 * never gives a character away. The Storyteller is always on it. Positions are
 * rounded to half a block so standing still doesn't change the snapshot.
 * Sent only while someone has Watch live open ({@link SpectatorBridge}).
 */
final class SpectatorMap {
    private static final int MAX_PEOPLE = 100;

    private SpectatorMap() {}

    record Dot(String id, double x, double z) {}

    /** The voting clock: who is nominated, and (during the vote) whose turn it is. */
    record Clock(boolean active, boolean exile, String nominee, String nominator, String voter, int index, int total) {}

    static double round(double value) {
        return Math.round(value * 2.0D) / 2.0D;
    }

    static JsonObject build(Map<Integer, SeatPositionManager.Position> homes, Map<Integer, SeatPositionManager.Position> square,
                            SeatPositionManager.Position center, List<Dot> people, Clock clock) {
        JsonObject map = new JsonObject();
        map.add("homes", seats(homes));
        map.add("square", seats(square));
        if (center != null) map.add("center", point(center.x(), center.z()));
        JsonArray dots = new JsonArray();
        for (Dot dot : people) {
            if (dots.size() >= MAX_PEOPLE) break;
            JsonObject o = point(dot.x(), dot.z());
            o.addProperty("id", dot.id());
            dots.add(o);
        }
        map.add("people", dots);
        if (clock != null && clock.nominee() != null) {
            JsonObject c = new JsonObject();
            c.addProperty("active", clock.active());
            if (clock.exile()) c.addProperty("exile", true);
            c.addProperty("nominee", clock.nominee());
            if (clock.nominator() != null) c.addProperty("nominator", clock.nominator());
            if (clock.active() && clock.voter() != null) {
                c.addProperty("voter", clock.voter());
                c.addProperty("index", clock.index());
                c.addProperty("total", clock.total());
            }
            map.add("clock", c);
        }
        return map;
    }

    private static JsonArray seats(Map<Integer, SeatPositionManager.Position> positions) {
        JsonArray out = new JsonArray();
        for (Map.Entry<Integer, SeatPositionManager.Position> e : new TreeMap<>(positions).entrySet()) {
            if (e.getValue() == null) continue;
            JsonObject o = point(e.getValue().x(), e.getValue().z());
            o.addProperty("seat", e.getKey());
            out.add(o);
        }
        return out;
    }

    private static JsonObject point(double x, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("x", round(x));
        o.addProperty("z", round(z));
        return o;
    }

    /** Server thread. */
    static JsonObject snapshot(MinecraftServer server) {
        SeatPositionManager.Configuration config = SeatPositionManager.snapshotConfiguration();
        List<Dot> people = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != server.overworld()) continue;
            if (p.isSpectator() && !StorytellerState.isStoryteller(p.getUUID())) continue;
            people.add(new Dot(p.getUUID().toString(), p.getX(), p.getZ()));
        }
        boolean exile = DaytimeState.getCurrentExileTarget() != null;
        boolean active = DaytimeState.isVoteInProgress() || DaytimeState.isExileSupportInProgress();
        UUID nominee = exile ? DaytimeState.getCurrentExileTarget() : DaytimeState.getCurrentNominee();
        UUID nominator = exile ? DaytimeState.getCurrentExileCaller() : DaytimeState.getCurrentNominator();
        UUID voter = active ? ElectionState.getCurrentVoter() : null;
        Clock clock = new Clock(active, exile, text(nominee), text(nominator), text(voter),
                ElectionState.getCurrentPlayerIndex(), ElectionState.getElectionSize());
        return build(config.seatHomes(), config.townSquareSeats(), SeatPositionManager.clockCenter(), people, clock);
    }

    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }
}
