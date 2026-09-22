package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative hand vote + clockwise lock-in logic. */
public final class VotingManager {
    public enum VoteResult { NONE, MARKED, TIE, NOT_ENOUGH }

    public record Result(VoteResult result, UUID nominee, int votes, int threshold, List<UUID> voters) {}

    private static Result lastResult = new Result(VoteResult.NONE, null, 0, 0, List.of());

    private VotingManager() {}

    public static void startVote(MinecraftServer server, Set<UUID> deadPlayers, boolean organGrinderMode,
                                 boolean voudonMode, UUID voudonPlayer, Set<UUID> evilsForLegion) {
        if (!DaytimeState.hasActiveNomination()) return;
        DaytimeState.startVote(organGrinderMode);
        ElectionState.beginVotingPhase();
        lastResult = new Result(VoteResult.NONE, null, 0, currentHandsRequired(), List.of());
        if (voudonMode && voudonPlayer != null) DaytimeState.activateVoudonMode(voudonPlayer);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

    public static void clearLastResult() {
        lastResult = new Result(VoteResult.NONE, null, 0, 0, List.of());
    }

    public static void setVote(MinecraftServer server, UUID player, boolean yes) {
        if (!DaytimeState.isVoteInProgress()) return;
        if (player == null) return;
        if (DaytimeState.getLockedVotes().containsKey(player)) return;
        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(player)) && DaytimeState.hasUsedGhostVote(player)) {
            yes = false;
        }
        if (yes) yes = ButlerVoteRule.mayRaiseHand(player);
        DaytimeState.setRaisedHand(player, yes);
        DaytimeState.setCurrentVote(player, yes);
        DaytimeState.setLeverState(player, yes);
        StateBroadcaster.broadcastVoteState(server);
    }

    /** Locks the hand state exactly when the clockwise clock reaches this seat. */
    public static void lockVote(MinecraftServer server, UUID player) {
        if (player == null || !DaytimeState.isVoteInProgress()) return;
        boolean vote = DaytimeState.getCurrentVotes().getOrDefault(player, false);
        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(player)) && DaytimeState.hasUsedGhostVote(player)) {
            vote = false;
        }
        vote = ButlerVoteRule.allowYesAtLock(server, player, vote);
        DaytimeState.lockVote(player, vote);
        ElectionState.lockVote(player, vote);
        if (vote) DayPublicInfoBookManager.recordVote(server, player);
        if (vote && !StorytellerState.demonVotedToday) {
            PendingRoleAssignment assignment = StorytellerState.effectiveGrimoireRoles().get(player);
            if (assignment != null && assignment.getRoleType() == RoleType.DEMON) {
                StorytellerState.demonVotedToday = true;
                StateBroadcaster.broadcastStorytellerNightInfo(server);
            }
        }
        StateBroadcaster.broadcastVoteState(server);
    }

    public static boolean canResolveVote() {
        return DaytimeState.isVoteInProgress() && ElectionState.isVotingPhaseComplete();
    }

    public static Result resolveVote(MinecraftServer server) {
        UUID nominee = DaytimeState.getCurrentNominee();
        if (nominee == null) {
            lastResult = new Result(VoteResult.NOT_ENOUGH, null, 0, 0, List.of());
            return lastResult;
        }
        if (!ElectionState.isVotingPhaseComplete()) {
            lastResult = new Result(VoteResult.NONE, nominee, lockedEffectiveVoteCount(), currentHandsRequired(), List.of());
            return lastResult;
        }

        Map<UUID, Boolean> votes = DaytimeState.getLockedVotes();
        List<UUID> voters = new ArrayList<>();
        int lockedTotal = 0;
        Set<UUID> deadPlayers = ServerState.deadPlayers();

        for (Map.Entry<UUID, Boolean> entry : votes.entrySet()) {
            UUID player = entry.getKey();
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            if (deadPlayers.contains(player) && DaytimeState.hasUsedGhostVote(player)) continue;
            voters.add(player);
            lockedTotal += DaytimeState.getVoteMultiplier(player);
        }

        int total = DaytimeState.hasManualVoteCountOverride()
                ? DaytimeState.getManualVoteCountOverride()
                : lockedTotal;
        int alive = alivePlayerCount();
        int baseThreshold = calculateThreshold(alive);
        boolean storytellerNominee = com.sharktower.bloodonthesharktower.states.StorytellerState.isStoryteller(nominee)
                && !ServerState.PLAYER_SEAT_NUMBERS.containsKey(nominee);

        int markedVotes;
        boolean somethingMarked;
        if (DaytimeState.getStorytellerMFE() != null) {
            markedVotes = DaytimeState.getStorytellerMFEVotes();
            somethingMarked = true;
        } else {
            markedVotes = DaytimeState.getVotesForMarkedPlayer();
            somethingMarked = DaytimeState.getMarkedForExecution() != null;
        }
        int threshold = !somethingMarked
                ? baseThreshold
                : Math.max(baseThreshold, markedVotes + 1);

        VoteResult result;
        if (total < baseThreshold) {
            result = VoteResult.NOT_ENOUGH;
        } else if (somethingMarked && total == markedVotes) {
            result = VoteResult.TIE;
            DaytimeState.clearMarkedForExecution();
            DaytimeState.clearStorytellerMFE();
        } else if (total >= threshold) {
            result = VoteResult.MARKED;
            if (storytellerNominee) {
                DaytimeState.clearMarkedForExecution();
                DaytimeState.setStorytellerMFE(nominee, total);
            } else {
                DaytimeState.clearStorytellerMFE();
                DaytimeState.setMarkedForExecution(nominee, total);
            }
        } else {
            result = VoteResult.NOT_ENOUGH;
        }

        // Ghost votes are consumed from the actual locked YES hands. A manual
        // total override changes the Storyteller's ruling, not who physically voted.
        for (UUID voter : voters) {
            if (deadPlayers.contains(voter)) DaytimeState.markGhostVoteUsed(voter);
        }
        DaytimeState.finalizeSecretGhostVotes();
        DaytimeState.endVote();
        DaytimeState.clearRaisedHands();
        DaytimeState.clearManualVoteCountOverride();
        DaytimeState.setCurrentNominator(null);
        DaytimeState.setCurrentNominee(null);
        ElectionState.endElection();
        lastResult = new Result(result, nominee, total, threshold, List.copyOf(voters));

        BloodOnTheSharktower.LOGGER.info(
                "Vote resolved: nominee={}, votes={}, threshold={}, result={}", nominee, total, threshold, result
        );
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        return lastResult;
    }

    public static Result getLastResult() { return lastResult; }

    public static int calculateThreshold(int alivePlayers) {
        return Math.max(1, (int) Math.ceil(Math.max(0, alivePlayers) / 2.0));
    }

    public static int alivePlayerCount() {
        int active = 0;
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            if (DaytimeState.isTraveler(id)) continue;
            if (!Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id))) active++;
        }
        return active;
    }

    public static int raisedHandCount() { return DaytimeState.getRaisedHandCount(); }

    public static int currentHandsRequired() {
        int base = calculateThreshold(alivePlayerCount());
        int markedVotes;
        boolean somethingMarked;
        if (DaytimeState.getStorytellerMFE() != null) {
            markedVotes = DaytimeState.getStorytellerMFEVotes();
            somethingMarked = true;
        } else {
            markedVotes = DaytimeState.getVotesForMarkedPlayer();
            somethingMarked = DaytimeState.getMarkedForExecution() != null;
        }
        return somethingMarked ? Math.max(base, markedVotes + 1) : base;
    }

    /** Weighted YES votes whose seats have already been reached by the clock. */
    public static int lockedEffectiveVoteCount() {
        int total = 0;
        Set<UUID> dead = ServerState.deadPlayers();
        for (Map.Entry<UUID, Boolean> entry : DaytimeState.getLockedVotes().entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            if (dead.contains(entry.getKey()) && DaytimeState.hasUsedGhostVote(entry.getKey())) continue;
            total += DaytimeState.getVoteMultiplier(entry.getKey());
        }
        return total;
    }

    /** Display/resolve count, including an explicit Storyteller override when present. */
    public static int effectiveVoteCount() {
        return DaytimeState.hasManualVoteCountOverride()
                ? DaytimeState.getManualVoteCountOverride()
                : lockedEffectiveVoteCount();
    }

    public static boolean adjustVoteCountOverride(MinecraftServer server, int delta) {
        if (!canResolveVote()) return false;
        int base = DaytimeState.hasManualVoteCountOverride()
                ? DaytimeState.getManualVoteCountOverride()
                : lockedEffectiveVoteCount();
        DaytimeState.setManualVoteCountOverride(base + delta);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static boolean setVoteCountOverride(MinecraftServer server, int count) {
        if (!canResolveVote()) return false;
        DaytimeState.setManualVoteCountOverride(count);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static void clearVoteCountOverride(MinecraftServer server) {
        DaytimeState.clearManualVoteCountOverride();
        StateBroadcaster.broadcastVoteState(server);
    }

    public static Set<UUID> currentYesVoters() {
        Set<UUID> result = new HashSet<>();
        DaytimeState.getLockedVotes().forEach((id, yes) -> { if (Boolean.TRUE.equals(yes)) result.add(id); });
        return result;
    }

    public static void resetLeverStates(MinecraftServer server) {
        DaytimeState.clearLeverStates();
        StateBroadcaster.broadcastVoteState(server);
    }

    public static void broadcastLeverStates(MinecraftServer server) {
        StateBroadcaster.broadcastVoteState(server);
    }

    public static void resetVote(MinecraftServer server) {
        DaytimeState.resetVote();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

    public static void updatePlayerDeathIndicator(MinecraftServer server, UUID player) {
        StateBroadcaster.broadcastDeathStatus(server);
        StateBroadcaster.broadcastVoteState(server);
    }
}
