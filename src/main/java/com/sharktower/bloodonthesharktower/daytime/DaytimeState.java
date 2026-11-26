package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.states.ServerState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative daytime state: nominations, hands and votes, ghost votes, the
 * player on the block and Traveller exiles. The clockwise vote clock itself
 * lives in {@link ElectionState} and {@link ElectionManager}.
 */
public final class DaytimeState {
    private static final Map<UUID, Boolean> canNominate = new HashMap<>();
    private static final Map<UUID, Boolean> canBeNominated = new HashMap<>();
    private static boolean storytellerCanBeNominated = true;
    private static final Map<UUID, Boolean> hasUsedGhostVote = new HashMap<>();

    private static UUID currentNominator;
    private static UUID currentNominee;
    private static UUID markedForExecution;
    private static int votesForMarkedPlayer;
    private static UUID storytellerMFE;
    private static int storytellerMFEVotes;
    private static boolean nominationsOpen;
    private static boolean voteInProgress;
    private static boolean organGrinderMode;
    private static boolean organGrinderModeActiveToday;
    /** Storyteller-only manual total used for unusual voting rulings. Null means use locked votes. */
    private static Integer manualVoteCountOverride;

    private static final Map<UUID, Boolean> currentVotes = new HashMap<>();
    private static final Map<UUID, Boolean> lockedVotes = new HashMap<>();
    /** Sharktower hand-voting intent. This stays separate from locked/counting vote state. */
    private static final Map<UUID, Boolean> raisedHands = new HashMap<>();
    private static final Map<UUID, Integer> nominationsRemaining = new HashMap<>();

    private static boolean voudonModeActive;
    private static UUID voudonPlayerUuid;

    private static final Map<UUID, Boolean> canBeExiled = new HashMap<>();
    /** Travellers who have been exiled are removed from election participation; they are not dead. */
    private static final Set<UUID> exiledTravelers = new HashSet<>();
    private static UUID currentExileCaller;
    private static UUID currentExileTarget;
    private static boolean exileSupportInProgress;
    private static final Map<UUID, Boolean> exileSupportVotes = new HashMap<>();
    private static final Map<UUID, Boolean> lockedExileSupportVotes = new HashMap<>();

    private DaytimeState() {}

    public static boolean canNominate(UUID id) { return canNominate.getOrDefault(id, false); }
    public static boolean canBeNominated(UUID id) { return canBeNominated.getOrDefault(id, false); }
    public static Map<UUID, Boolean> getCanNominateMap() { return Map.copyOf(canNominate); }
    public static Map<UUID, Boolean> getCanBeNominatedMap() { return Map.copyOf(canBeNominated); }
    public static void setCanNominate(UUID id, boolean value) { canNominate.put(id, value); }
    public static void setCanBeNominated(UUID id, boolean value) { canBeNominated.put(id, value); }
    public static boolean canStorytellerBeNominated() { return storytellerCanBeNominated; }
    public static void setStorytellerCanBeNominated(boolean value) { storytellerCanBeNominated = value; }

    public static boolean hasUsedGhostVote(UUID id) { return hasUsedGhostVote.getOrDefault(id, false); }
    public static void markGhostVoteUsed(UUID id) { hasUsedGhostVote.put(id, true); }
    public static void clearAllGhostVotes() { hasUsedGhostVote.clear(); }
    public static Map<UUID, Boolean> getGhostVoteMap() { return Map.copyOf(hasUsedGhostVote); }

    public static UUID getCurrentNominator() { return currentNominator; }
    public static void setCurrentNominator(UUID id) { currentNominator = id; }
    public static UUID getCurrentNominee() { return currentNominee; }
    public static void setCurrentNominee(UUID id) { currentNominee = id; }
    public static boolean hasActiveNomination() { return currentNominee != null; }

    public static UUID getMarkedForExecution() { return markedForExecution; }
    public static void setMarkedForExecution(UUID id, int votes) {
        markedForExecution = id;
        votesForMarkedPlayer = Math.max(0, votes);
        // Who nominated the player now on the block (the Psychopath's roshambo needs it).
        markedNominator = id != null && id.equals(currentNominee) ? currentNominator : null;
    }
    public static void clearMarkedForExecution() {
        markedForExecution = null;
        votesForMarkedPlayer = 0;
        markedNominator = null;
    }
    private static UUID markedNominator;
    public static UUID getMarkedNominator() { return markedNominator; }
    public static int getVotesForMarkedPlayer() { return votesForMarkedPlayer; }

    public static UUID getStorytellerMFE() { return storytellerMFE; }
    public static int getStorytellerMFEVotes() { return storytellerMFEVotes; }
    public static void setStorytellerMFE(UUID id, int votes) {
        storytellerMFE = id;
        storytellerMFEVotes = Math.max(0, votes);
    }
    public static void clearStorytellerMFE() {
        storytellerMFE = null;
        storytellerMFEVotes = 0;
    }

    public static boolean areNominationsOpen() { return nominationsOpen; }

    /** Opens nominations for {@code players}: one nomination each, Travellers can't be nominated. */
    public static void openNominations(Set<UUID> players) {
        nominationsOpen = true;
        AttentionHands.clear();
        resetNomination();
        canNominate.clear();
        canBeNominated.clear();
        nominationsRemaining.clear();
        raisedHands.clear();

        for (UUID id : players) {
            canNominate.put(id, !isExiledTraveler(id));
            canBeNominated.put(id, !isTraveler(id));
            nominationsRemaining.put(id, 1);
            hasUsedGhostVote.putIfAbsent(id, false);
        }

        initializeExileEligibility(players);
    }

    public static void closeNominations() {
        AttentionHands.clear();
        nominationsOpen = false;
        resetNomination();
        resetVote();
        clearRaisedHands();
    }

    public static boolean isVoteInProgress() { return voteInProgress; }
    public static boolean isOrganGrinderMode() { return organGrinderMode; }
    public static boolean isOrganGrinderModeActiveToday() { return organGrinderModeActiveToday; }
    public static void startVote(boolean organGrinder) {
        voteInProgress = true;
        organGrinderMode = organGrinder;
        organGrinderModeActiveToday |= organGrinder;
        currentVotes.clear();
        lockedVotes.clear();
        manualVoteCountOverride = null;
        // A raised hand means "I intend to vote": seed the vote map so a hand
        // raised before the clock starts counts when the clock reaches the seat.
        currentVotes.putAll(raisedHands);
    }
    public static void endVote() { voteInProgress = false; }

    public static boolean hasManualVoteCountOverride() { return manualVoteCountOverride != null; }
    public static int getManualVoteCountOverride() { return manualVoteCountOverride == null ? 0 : manualVoteCountOverride; }
    public static void setManualVoteCountOverride(Integer value) {
        manualVoteCountOverride = value == null ? null : Math.max(0, Math.min(99, value));
    }
    public static void clearManualVoteCountOverride() { manualVoteCountOverride = null; }

    public static void setCurrentVote(UUID id, boolean value) { currentVotes.put(id, value); }
    public static Map<UUID, Boolean> getCurrentVotes() { return Map.copyOf(currentVotes); }
    public static void lockVote(UUID id, boolean value) {
        currentVotes.put(id, value);
        lockedVotes.put(id, value);
    }
    public static Map<UUID, Boolean> getLockedVotes() { return Map.copyOf(lockedVotes); }

    public static void setRaisedHand(UUID id, boolean raised) {
        if (id == null) return;
        raisedHands.put(id, raised);
        // During a vote, changing your hand changes your vote until the clock
        // reaches your seat and locks it.
        if (voteInProgress && !lockedVotes.containsKey(id)) {
            currentVotes.put(id, raised);
        }
    }
    public static boolean isHandRaised(UUID id) { return raisedHands.getOrDefault(id, false); }
    public static Map<UUID, Boolean> getRaisedHands() { return Map.copyOf(raisedHands); }
    public static int getRaisedHandCount() {
        int count = 0;
        for (Boolean raised : raisedHands.values()) if (Boolean.TRUE.equals(raised)) count++;
        return count;
    }
    public static void clearRaisedHands() { raisedHands.clear(); }

    public static void resetNomination() {
        currentNominator = null;
        currentNominee = null;
        ElectionState.endElection();
        resetVote();
    }

    public static void resetDaily() {
        AttentionHands.clear();
        resetNomination();
        VotingManager.clearLastResult();
        nominationsOpen = false;
        canNominate.clear();
        canBeNominated.clear();
        nominationsRemaining.clear();
        clearRaisedHands();
        organGrinderMode = false;
        organGrinderModeActiveToday = false;
        clearMarkedForExecution();
        clearStorytellerMFE();
        deactivateVoudonMode();
        resetExile();
        resetExileEligibilityDaily();
    }

    public static void resetVote() {
        voteInProgress = false;
        organGrinderMode = false;
        currentVotes.clear();
        lockedVotes.clear();
        manualVoteCountOverride = null;
        ElectionState.resetVotingPhase();
    }

    public static void hardReset(Set<UUID> players, Set<UUID> deadPlayers) {
        exiledTravelers.clear();
        resetDaily();
        clearAllGhostVotes();
        for (UUID id : players) {
            hasUsedGhostVote.put(id, false);
            canNominate.put(id, false);
            canBeNominated.put(id, false);
        }
        initializeExileEligibility(players);
    }

    public static void setNominationsRemaining(UUID id, int count) {
        nominationsRemaining.put(id, Math.max(0, count));
    }
    public static int getNominationsRemaining(UUID id) { return nominationsRemaining.getOrDefault(id, 0); }
    public static void useNomination(UUID id) {
        setNominationsRemaining(id, Math.max(0, getNominationsRemaining(id) - 1));
    }
    public static boolean hasNominationsRemaining(UUID id) { return getNominationsRemaining(id) > 0; }
    public static Map<UUID, Integer> getNominationsRemainingMap() { return Map.copyOf(nominationsRemaining); }

    public static boolean isVoudonModeActive() { return voudonModeActive; }
    public static UUID getVoudonPlayerUuid() { return voudonPlayerUuid; }
    public static void activateVoudonMode(UUID id) {
        voudonModeActive = true;
        voudonPlayerUuid = id;
    }
    public static void deactivateVoudonMode() {
        voudonModeActive = false;
        voudonPlayerUuid = null;
    }

    public static boolean canBeExiled(UUID id) {
        if (id == null || !isTraveler(id) || isExiledTraveler(id)) return false;
        return canBeExiled.getOrDefault(id, true);
    }
    public static Map<UUID, Boolean> getCanBeExiledMap() {
        Map<UUID, Boolean> result = new HashMap<>();
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) result.put(id, canBeExiled(id));
        return Map.copyOf(result);
    }
    public static boolean isTraveler(UUID id) {
        var assignment = ServerState.PLAYER_ROLES.get(id);
        return assignment != null && assignment.getRoleType() == RoleType.TRAVELER;
    }
    public static boolean isExiledTraveler(UUID id) { return id != null && exiledTravelers.contains(id); }
    public static Set<UUID> getExiledTravelers() { return Set.copyOf(exiledTravelers); }
    public static Map<UUID, Boolean> getExiledTravelerMap() {
        Map<UUID, Boolean> result = new HashMap<>();
        for (UUID id : exiledTravelers) result.put(id, true);
        return Map.copyOf(result);
    }
    public static void markTravelerExiled(UUID id) {
        if (id == null) return;
        exiledTravelers.add(id);
        canBeExiled.put(id, false);
        canNominate.put(id, false);
        canBeNominated.put(id, false);
        raisedHands.remove(id);
        currentVotes.remove(id);
        lockedVotes.remove(id);
    }
    public static void initializeExileEligibility(Set<UUID> players) {
        canBeExiled.clear();
        if (players == null) return;
        for (UUID id : players) canBeExiled.put(id, isTraveler(id) && !isExiledTraveler(id));
    }
    public static void resetExileEligibilityDaily() {
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            canBeExiled.put(id, isTraveler(id) && !isExiledTraveler(id));
        }
    }
    /** Active players for physical election clocks; exiled Travellers remain social participants but do not vote. */
    public static Map<UUID, Integer> getActiveElectionSeats() {
        Map<UUID, Integer> result = new HashMap<>();
        ServerState.PLAYER_SEAT_NUMBERS.forEach((id, seat) -> {
            if (!isExiledTraveler(id)) result.put(id, seat);
        });
        return Map.copyOf(result);
    }

    public static UUID getCurrentExileCaller() { return currentExileCaller; }
    public static void setCurrentExileCaller(UUID id) { currentExileCaller = id; }
    public static UUID getCurrentExileTarget() { return currentExileTarget; }
    public static void setCurrentExileTarget(UUID id) { currentExileTarget = id; }
    public static boolean hasActiveExile() { return currentExileTarget != null; }
    public static boolean isExileSupportInProgress() { return exileSupportInProgress; }
    public static void startExileSupport() {
        exileSupportInProgress = true;
        exileSupportVotes.clear();
        lockedExileSupportVotes.clear();
    }
    public static void endExileSupport() { exileSupportInProgress = false; }
    public static void cancelExileSupportVote() {
        exileSupportInProgress = false;
        exileSupportVotes.clear();
        lockedExileSupportVotes.clear();
        manualVoteCountOverride = null;
        ElectionState.resetVotingPhase();
    }
    public static Map<UUID, Boolean> getExileSupportVotes() { return Map.copyOf(exileSupportVotes); }
    public static void lockExileSupportVote(UUID id, boolean value) {
        exileSupportVotes.put(id, value);
        lockedExileSupportVotes.put(id, value);
    }
    public static Map<UUID, Boolean> getLockedExileSupportVotes() { return Map.copyOf(lockedExileSupportVotes); }
    public static int getLockedExileSupportCount() {
        return (int) lockedExileSupportVotes.values().stream().filter(Boolean.TRUE::equals).count();
    }
    public static void resetExile() {
        currentExileCaller = null;
        currentExileTarget = null;
        exileSupportInProgress = false;
        exileSupportVotes.clear();
        lockedExileSupportVotes.clear();
        manualVoteCountOverride = null;
        clearRaisedHands();
        ElectionState.endElection();
    }
}
