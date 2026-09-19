package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;

/** Server-authoritative Traveller exile support vote using the same physical clock as nominations. */
public final class ExileSupportManager {
    public record Result(boolean exiled, int support, int threshold) {}

    private ExileSupportManager() {}

    public static boolean startExileSupport(MinecraftServer server) {
        if (!DaytimeState.hasActiveExile() || DaytimeState.isExileSupportInProgress()) return false;
        DaytimeState.startExileSupport();
        DaytimeState.clearManualVoteCountOverride();
        ElectionState.beginVotingPhase();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    /** Direct/manual support setter retained for command testing. */
    public static void setSupport(MinecraftServer server, UUID player, boolean yes) {
        if (!DaytimeState.isExileSupportInProgress() || player == null || DaytimeState.isExiledTraveler(player)) return;
        DaytimeState.setRaisedHand(player, yes);
        DaytimeState.lockExileSupportVote(player, yes);
        ElectionState.lockVote(player, yes);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

    /** Locks the current voting hand exactly when the exile clock reaches this seat. */
    public static void lockSupportFromHand(MinecraftServer server, UUID player) {
        if (!DaytimeState.isExileSupportInProgress() || player == null || DaytimeState.isExiledTraveler(player)) return;
        boolean yes = DaytimeState.isHandRaised(player);
        DaytimeState.lockExileSupportVote(player, yes);
        ElectionState.lockVote(player, yes);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

    public static boolean canResolve() {
        return DaytimeState.isExileSupportInProgress() && ElectionState.isVotingPhaseComplete();
    }

    public static Result resolve(MinecraftServer server) {
        UUID target = DaytimeState.getCurrentExileTarget();
        UUID caller = DaytimeState.getCurrentExileCaller();
        int support = effectiveSupportCount();
        int threshold = currentThreshold();
        boolean exiled = support >= threshold;
        DaytimeState.endExileSupport();
        if (exiled) ExileManager.executeExile(server, target, caller, support);
        else ExileManager.resetExile(server);
        return new Result(exiled, support, threshold);
    }

    public static int lockedSupportCount() {
        int support = 0;
        for (Map.Entry<UUID, Boolean> entry : DaytimeState.getLockedExileSupportVotes().entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue()) && !DaytimeState.isExiledTraveler(entry.getKey())) support++;
        }
        return support;
    }

    public static int effectiveSupportCount() {
        return DaytimeState.hasManualVoteCountOverride()
                ? DaytimeState.getManualVoteCountOverride()
                : lockedSupportCount();
    }

    /** All current players, living or dead, count toward an exile majority; prior ghost-vote use is irrelevant. */
    public static int currentThreshold() {
        int eligible = Math.max(1, DaytimeState.getActiveElectionSeats().size());
        return Math.max(1, (int) Math.ceil(eligible / 2.0D));
    }

    public static boolean adjustSupportOverride(MinecraftServer server, int delta) {
        if (!canResolve()) return false;
        int base = effectiveSupportCount();
        DaytimeState.setManualVoteCountOverride(base + delta);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static boolean setSupportOverride(MinecraftServer server, int count) {
        if (!canResolve()) return false;
        DaytimeState.setManualVoteCountOverride(count);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static void clearSupportOverride(MinecraftServer server) {
        DaytimeState.clearManualVoteCountOverride();
        StateBroadcaster.broadcastVoteState(server);
    }

    /** Stop the physical clock but keep the active exile call so it can be restarted. */
    public static boolean cancelSupportVote(MinecraftServer server) {
        if (!DaytimeState.isExileSupportInProgress()) return false;
        DaytimeState.cancelExileSupportVote();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static void resetExileSupport(MinecraftServer server) {
        ExileManager.resetExile(server);
    }
}
