package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.states.ServerState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk 26.2 port of BOTB's authoritative daytime/election state.
 *
 * The physical redstone/lever presentation is intentionally kept outside this
 * class. This is the same logical home for nominations, ghost votes, marked
 * players, exile state and role-specific vote modifiers used by the original.
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
    private static final Map<UUID, Boolean> currentLeverStates = new HashMap<>();
    /** Sharktower hand-voting intent. This stays separate from locked/counting vote state. */
    private static final Map<UUID, Boolean> raisedHands = new HashMap<>();
    private static final Set<UUID> pendingGhostVoteUpdates = new HashSet<>();
    private static final Set<UUID> secretlyUsedGhostVotes = new HashSet<>();
    private static final Set<UUID> mayNotNominatePlayers = new HashSet<>();

    private static final Set<UUID> bansheeDoubleVotePlayers = new HashSet<>();
    private static final Map<UUID, Boolean> bansheeDoubleVoteActive = new HashMap<>();
    private static final Set<UUID> bansheeHasVotedOnce = new HashSet<>();
    private static final Map<UUID, Integer> nominationsRemaining = new HashMap<>();
    private static final Map<UUID, Boolean> bansheeUnderlyingGhostVoteUsed = new HashMap<>();

    private static boolean voudonModeActive;
    private static final Set<UUID> voudonBlockedPlayers = new HashSet<>();
    private static UUID voudonPlayerUuid;
    private static final Set<UUID> ugHatPlayers = new HashSet<>();
    private static final Map<UUID, Integer> bureaucratMultipliers = new HashMap<>();
    private static final Map<UUID, Integer> thiefMultipliers = new HashMap<>();

    private static final Map<UUID, Boolean> canBeExiled = new HashMap<>();
    /** Travellers who have been exiled are removed from election participation; they are not dead. */
    private static final Set<UUID> exiledTravelers = new HashSet<>();
    private static UUID currentExileCaller;
    private static UUID currentExileTarget;
    private static boolean exileSupportInProgress;
    private static final Set<Integer> seatsWithGhostUsedBlocks = new HashSet<>();
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
    public static void resetGhostVote(UUID id) { hasUsedGhostVote.put(id, false); }
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
    }
    public static void clearMarkedForExecution() {
        markedForExecution = null;
        votesForMarkedPlayer = 0;
    }
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

    public static void openNominations(
            Set<UUID> players,
            Set<UUID> deadPlayers,
            List<UUID> bansheePlayers,
            List<UUID> mayNotNominate
    ) {
        nominationsOpen = true;
        resetNomination();
        canNominate.clear();
        canBeNominated.clear();
        nominationsRemaining.clear();
        raisedHands.clear();
        setMayNotNominatePlayers(mayNotNominate);

        for (UUID id : players) {
            boolean mayNominate = !mayNotNominatePlayers.contains(id) && !isExiledTraveler(id);
            canNominate.put(id, mayNominate);
            boolean traveler = isTraveler(id);
            canBeNominated.put(id, !traveler);
            nominationsRemaining.put(id, bansheePlayers != null && bansheePlayers.contains(id) ? 2 : 1);
            hasUsedGhostVote.putIfAbsent(id, false);
        }

        if (bansheePlayers != null) {
            for (UUID id : bansheePlayers) enableBansheeDoubleVote(id);
        }

        initializeExileEligibility(players);
    }

    public static void closeNominations() {
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
        currentLeverStates.clear();
        // A raised hand means "I intend to vote". Seed the logical vote map
        // so the existing vote backend can consume the hand state even before
        // the rotating/counting presentation lands in 1.1.0-dev B.
        currentVotes.putAll(raisedHands);
        bansheeHasVotedOnce.clear();
    }
    public static void endVote() { voteInProgress = false; }

    public static boolean hasManualVoteCountOverride() { return manualVoteCountOverride != null; }
    public static int getManualVoteCountOverride() { return manualVoteCountOverride == null ? 0 : manualVoteCountOverride; }
    public static void setManualVoteCountOverride(Integer value) {
        manualVoteCountOverride = value == null ? null : Math.max(0, Math.min(99, value));
    }
    public static void clearManualVoteCountOverride() { manualVoteCountOverride = null; }

    public static void addPendingGhostVoteUpdate(UUID id) { pendingGhostVoteUpdates.add(id); }
    public static Set<UUID> getPendingGhostVoteUpdates() { return Set.copyOf(pendingGhostVoteUpdates); }
    public static void clearPendingGhostVoteUpdates() { pendingGhostVoteUpdates.clear(); }
    public static void markGhostVoteSecretlyUsed(UUID id) { secretlyUsedGhostVotes.add(id); }
    public static boolean hasSecretlyUsedGhostVote(UUID id) { return secretlyUsedGhostVotes.contains(id); }
    public static void finalizeSecretGhostVotes() {
        for (UUID id : secretlyUsedGhostVotes) markGhostVoteUsed(id);
        secretlyUsedGhostVotes.clear();
    }

    public static void setCurrentVote(UUID id, boolean value) { currentVotes.put(id, value); }
    public static Map<UUID, Boolean> getCurrentVotes() { return Map.copyOf(currentVotes); }
    public static void lockVote(UUID id, boolean value) {
        currentVotes.put(id, value);
        lockedVotes.put(id, value);
    }
    public static Map<UUID, Boolean> getLockedVotes() { return Map.copyOf(lockedVotes); }
    public static void setLeverState(UUID id, boolean value) { currentLeverStates.put(id, value); }
    public static Map<UUID, Boolean> getLeverStates() { return Map.copyOf(currentLeverStates); }
    public static void clearLeverStates() { currentLeverStates.clear(); }

    public static void setRaisedHand(UUID id, boolean raised) {
        if (id == null) return;
        raisedHands.put(id, raised);
        // During an active vote, changing your hand immediately updates your
        // un-locked vote intent. 1.1.0-dev B will add the clockwise lock-in.
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
        resetNomination();
        VotingManager.clearLastResult();
        nominationsOpen = false;
        canNominate.clear();
        canBeNominated.clear();
        nominationsRemaining.clear();
        mayNotNominatePlayers.clear();
        clearRaisedHands();
        organGrinderMode = false;
        organGrinderModeActiveToday = false;
        clearMarkedForExecution();
        clearStorytellerMFE();
        clearBansheeDoubleVotes();
        deactivateVoudonMode();
        clearTravelerMultipliers();
        resetExile();
        resetExileEligibilityDaily();
    }

    public static void resetVote() {
        voteInProgress = false;
        organGrinderMode = false;
        currentVotes.clear();
        lockedVotes.clear();
        currentLeverStates.clear();
        manualVoteCountOverride = null;
        pendingGhostVoteUpdates.clear();
        secretlyUsedGhostVotes.clear();
        bansheeHasVotedOnce.clear();
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

    public static void enableBansheeDoubleVote(UUID id) {
        bansheeDoubleVotePlayers.add(id);
        bansheeDoubleVoteActive.putIfAbsent(id, false);
    }
    public static void disableBansheeDoubleVote(UUID id) {
        bansheeDoubleVotePlayers.remove(id);
        bansheeDoubleVoteActive.remove(id);
        bansheeHasVotedOnce.remove(id);
        bansheeUnderlyingGhostVoteUsed.remove(id);
    }
    public static boolean hasBansheeDoubleVote(UUID id) { return bansheeDoubleVotePlayers.contains(id); }
    public static boolean toggleBansheeDoubleVoteActive(UUID id) {
        boolean next = !bansheeDoubleVoteActive.getOrDefault(id, false);
        bansheeDoubleVoteActive.put(id, next);
        return next;
    }
    public static boolean isBansheeDoubleVoteActive(UUID id) { return bansheeDoubleVoteActive.getOrDefault(id, false); }

    public static int getVoteMultiplier(UUID id) {
        int multiplier = 1;
        if (isBansheeDoubleVoteActive(id)) multiplier *= 2;
        if (ugHatPlayers.contains(id)) multiplier *= 2;
        multiplier *= bureaucratMultipliers.getOrDefault(id, 1);
        multiplier *= thiefMultipliers.getOrDefault(id, 1);
        return multiplier;
    }

    public static void clearBansheeDoubleVotes() {
        bansheeDoubleVotePlayers.clear();
        bansheeDoubleVoteActive.clear();
        bansheeHasVotedOnce.clear();
        bansheeUnderlyingGhostVoteUsed.clear();
    }

    public static void setMayNotNominatePlayers(List<UUID> players) {
        mayNotNominatePlayers.clear();
        if (players != null) mayNotNominatePlayers.addAll(players);
    }
    public static boolean isMayNotNominate(UUID id) { return mayNotNominatePlayers.contains(id); }
    public static boolean isBansheeUnderlyingGhostVoteUsed(UUID id) {
        return bansheeUnderlyingGhostVoteUsed.getOrDefault(id, false);
    }
    public static Set<UUID> getBansheeDoubleVotePlayers() { return Set.copyOf(bansheeDoubleVotePlayers); }
    public static Set<UUID> getBansheeDoubleVoteActivePlayers() {
        Set<UUID> active = new HashSet<>();
        bansheeDoubleVoteActive.forEach((id, value) -> { if (Boolean.TRUE.equals(value)) active.add(id); });
        return active;
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
        voudonBlockedPlayers.clear();
    }
    public static void addVoudonBlockedPlayer(UUID id) { voudonBlockedPlayers.add(id); }
    public static void removeVoudonBlockedPlayer(UUID id) { voudonBlockedPlayers.remove(id); }
    public static Set<UUID> getVoudonBlockedPlayers() { return Set.copyOf(voudonBlockedPlayers); }
    public static void clearVoudonBlockedPlayers() { voudonBlockedPlayers.clear(); }

    public static void setUgHatPlayers(Collection<UUID> ids) {
        ugHatPlayers.clear();
        if (ids != null) ugHatPlayers.addAll(ids);
    }
    public static void clearUgHatPlayers() { ugHatPlayers.clear(); }
    public static void setBureaucratMultiplier(UUID id, boolean active) {
        if (active) bureaucratMultipliers.put(id, 3); else bureaucratMultipliers.remove(id);
    }
    public static void setThiefMultiplier(UUID id, boolean active) {
        if (active) thiefMultipliers.put(id, -1); else thiefMultipliers.remove(id);
    }
    public static void clearTravelerMultipliers() {
        ugHatPlayers.clear();
        bureaucratMultipliers.clear();
        thiefMultipliers.clear();
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
    public static void setCanBeExiled(UUID id, boolean value) {
        if (id != null) canBeExiled.put(id, value && !isExiledTraveler(id));
    }
    public static void initializeExileEligibility(Set<UUID> players) {
        canBeExiled.clear();
        if (players == null) return;
        for (UUID id : players) canBeExiled.put(id, isTraveler(id) && !isExiledTraveler(id));
    }
    public static void addTraveler(UUID id) { if (id != null) canBeExiled.put(id, !isExiledTraveler(id)); }
    public static void removeTraveler(UUID id) { canBeExiled.remove(id); }
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
    public static void setExileSupportVote(UUID id, boolean value) { exileSupportVotes.put(id, value); }
    public static Map<UUID, Boolean> getExileSupportVotes() { return Map.copyOf(exileSupportVotes); }
    public static void lockExileSupportVote(UUID id, boolean value) {
        exileSupportVotes.put(id, value);
        lockedExileSupportVotes.put(id, value);
    }
    public static Map<UUID, Boolean> getLockedExileSupportVotes() { return Map.copyOf(lockedExileSupportVotes); }
    public static int getLockedExileSupportCount() {
        return (int) lockedExileSupportVotes.values().stream().filter(Boolean.TRUE::equals).count();
    }
    public static void saveGhostUsedBlockSeats(Set<Integer> seats) {
        seatsWithGhostUsedBlocks.clear();
        if (seats != null) seatsWithGhostUsedBlocks.addAll(seats);
    }
    public static Set<Integer> getAndClearGhostUsedBlockSeats() {
        Set<Integer> result = Set.copyOf(seatsWithGhostUsedBlocks);
        seatsWithGhostUsedBlocks.clear();
        return result;
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
