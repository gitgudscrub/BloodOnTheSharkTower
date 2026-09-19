package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.snapshot.MatchSnapshotManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Setup-phase automatic player seating.
 *
 * Players who join while the match is still in SETUP are assigned the lowest
 * free seat. Existing seat assignments are preserved on reconnect. If an
 * auto-seated player subsequently claims Storyteller control, their temporary
 * player seat is removed and the remaining setup seats are compacted. The same
 * compaction now applies when an ordinary player disconnects during Setup.
 */
public final class AutoSeatManager {
    private AutoSeatManager() {}

    public static SetupOperations.Result seatJoiningPlayer(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return SetupOperations.Result.fail("Player is not available.");
        if (!isSetupPhase()) return SetupOperations.Result.fail("Automatic seating only runs during Setup.");

        UUID playerId = player.getUUID();
        if (StorytellerState.isStoryteller(playerId)) {
            return SetupOperations.Result.fail("Storytellers are not assigned player seats.");
        }

        Integer existing = SetupOperations.workingSeats().get(playerId);
        if (existing != null) {
            return SetupOperations.Result.ok("Kept existing seat " + existing + ".");
        }

        SetupOperations.Result result = SetupOperations.seatNext(playerId);
        if (result.ok()) {
            Integer seat = SetupOperations.workingSeats().get(playerId);
            if (seat != null) {
                player.sendSystemMessage(Component.literal(
                        "Blood on the Sharktower: automatically assigned Seat " + seat + "."
                ).withStyle(ChatFormatting.GRAY));
            }
            StateBroadcaster.broadcastCurrentState(server);
        }
        return result;
    }

    /**
     * Removes a Storyteller from setup seating if they joined as an ordinary
     * player first. Seat numbers are compacted so Player 1/2/etc. still begin
     * at seats 1/2/etc. in the dev harness and ordinary setup flow.
     */
    public static boolean removeStorytellerSeat(MinecraftServer server, UUID storytellerId) {
        if (storytellerId == null || !isSetupPhase()) return false;

        boolean changed = false;
        if (StorytellerState.PENDING_SEAT_NUMBERS.remove(storytellerId) != null) changed = true;
        StorytellerState.PENDING_ROLES.remove(storytellerId);
        StorytellerState.PENDING_PERCEIVED_ROLES.remove(storytellerId);
        StorytellerState.REMINDERS.remove(storytellerId);

        if (ServerState.PLAYER_SEAT_NUMBERS.remove(storytellerId) != null) changed = true;
        ServerState.PLAYER_ROLES.remove(storytellerId);
        ServerState.PLAYER_PERCEIVED_ROLES.remove(storytellerId);
        ServerState.PLAYER_DEATH_STATUS.remove(storytellerId);

        if (!changed) return false;

        compactSeats(StorytellerState.PENDING_SEAT_NUMBERS);
        compactSeats(ServerState.PLAYER_SEAT_NUMBERS);
        StorytellerState.nextSeatNumber = nextSeatNumber();
        if (server != null) StateBroadcaster.broadcastCurrentState(server);
        return true;
    }

    /**
     * A.11 setup cleanup: when an ordinary player disconnects before a match is
     * active, release their seat and close any numbering gap immediately.
     *
     * Active matches deliberately do not use this path: a player who drops
     * during Day/Night keeps their seat so reconnecting restores the same game
     * identity. End-game reveal also keeps the completed Grim intact until the
     * Storyteller resets back to Setup.
     */
    public static boolean releaseDisconnectedSetupSeat(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null || !isSetupPhase() || ServerState.gameEnded) return false;
        if (StorytellerState.isStoryteller(playerId)) return false;

        Integer pendingSeat = StorytellerState.PENDING_SEAT_NUMBERS.remove(playerId);
        Integer liveSeat = ServerState.PLAYER_SEAT_NUMBERS.remove(playerId);
        boolean changed = pendingSeat != null || liveSeat != null;
        if (!changed) return false;

        // A player leaving Setup is no longer part of the pending/committed bag.
        // Remaining roles stay attached to their players while only seat numbers
        // are compacted, so Seat 3 naturally becomes Seat 2, Seat 4 becomes 3, etc.
        StorytellerState.PENDING_ROLES.remove(playerId);
        StorytellerState.PENDING_PERCEIVED_ROLES.remove(playerId);
        StorytellerState.REMINDERS.remove(playerId);
        StorytellerState.MARKED_PLAYERS.remove(playerId);
        ServerState.PLAYER_ROLES.remove(playerId);
        ServerState.PLAYER_PERCEIVED_ROLES.remove(playerId);
        ServerState.PLAYER_DEATH_STATUS.remove(playerId);

        compactSeats(StorytellerState.PENDING_SEAT_NUMBERS);
        compactSeats(ServerState.PLAYER_SEAT_NUMBERS);
        StorytellerState.nextSeatNumber = nextSeatNumber();

        // If roles had already been sent and the start checkpoint existed, keep
        // that checkpoint's setup roster in sync without rotating or recapturing
        // the (potentially large) world snapshot. Otherwise a later Reset for
        // Next Game could resurrect the player who left.
        MatchSnapshotManager.refreshCurrentSetupState();

        StateBroadcaster.broadcastCurrentState(server);
        return true;
    }

    /** If Storyteller control is released during Setup, put that player back in the circle. */
    public static void seatReleasedStoryteller(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || !isSetupPhase()) return;
        if (SetupOperations.workingSeats().containsKey(player.getUUID())) return;
        seatJoiningPlayer(server, player);
    }

    private static boolean isSetupPhase() {
        return ServerState.currentNight == 0 && ServerState.currentDay == 0;
    }

    private static int nextSeatNumber() {
        int max = 0;
        for (Integer seat : SetupOperations.workingSeats().values()) {
            if (seat != null) max = Math.max(max, seat);
        }
        return max + 1;
    }

    private static void compactSeats(Map<UUID, Integer> seats) {
        if (seats.isEmpty()) return;
        var ordered = new ArrayList<>(seats.entrySet());
        ordered.sort(Comparator
                .comparingInt((Map.Entry<UUID, Integer> e) -> e.getValue() == null ? Integer.MAX_VALUE : e.getValue())
                .thenComparing(e -> e.getKey().toString()));

        Map<UUID, Integer> compacted = new LinkedHashMap<>();
        int seat = 1;
        for (Map.Entry<UUID, Integer> entry : ordered) {
            compacted.put(entry.getKey(), seat++);
        }
        seats.clear();
        seats.putAll(compacted);
    }
}
