package com.sharktower.bloodonthesharktower.daytime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Minecraft-independent core of BOTS's election state machine. */
public final class ElectionState {
    private static ElectionType type;
    private static ElectionConfig config;
    private static UUID target;
    private static UUID initiator;
    private static List<UUID> electionOrder = new ArrayList<>();
    private static int currentPlayerIndex;
    private static boolean votingPhaseActive;
    private static boolean votingPhaseComplete;
    private static long startTime;
    private static final Map<UUID, Boolean> leverStates = new HashMap<>();
    private static final Map<UUID, Boolean> lockedVotes = new HashMap<>();

    private ElectionState() {}

    public static void beginElection(ElectionType electionType, ElectionConfig electionConfig,
                                     UUID electionTarget, UUID electionInitiator,
                                     Map<UUID, Integer> seatNumbers) {
        type = electionType;
        config = electionConfig;
        target = electionTarget;
        initiator = electionInitiator;
        electionOrder = buildElectionOrder(electionTarget, seatNumbers);
        currentPlayerIndex = 0;
        votingPhaseActive = false;
        votingPhaseComplete = false;
        startTime = System.currentTimeMillis();
        leverStates.clear();
        lockedVotes.clear();
    }

    public static void beginVotingPhase() {
        votingPhaseActive = true;
        currentPlayerIndex = 0;
        votingPhaseComplete = electionOrder.isEmpty();
        startTime = System.currentTimeMillis();
    }

    public static void endElection() {
        type = null;
        config = null;
        target = null;
        initiator = null;
        electionOrder = new ArrayList<>();
        currentPlayerIndex = 0;
        votingPhaseActive = false;
        votingPhaseComplete = false;
        startTime = 0L;
        leverStates.clear();
        lockedVotes.clear();
    }

    public static void resetVotingPhase() {
        votingPhaseActive = false;
        votingPhaseComplete = false;
        currentPlayerIndex = 0;
        leverStates.clear();
        lockedVotes.clear();
    }

    public static void updateConfig(ElectionConfig value) { config = value; }
    public static boolean hasActiveElection() { return type != null && target != null; }
    public static boolean isVotingPhaseActive() { return votingPhaseActive; }
    public static boolean isVotingPhaseComplete() { return votingPhaseActive && votingPhaseComplete; }
    public static boolean isVoteType() { return type == ElectionType.VOTE; }
    public static boolean isExileSupportType() { return type == ElectionType.EXILE_SUPPORT; }
    public static ElectionType getType() { return type; }
    public static ElectionConfig getConfig() { return config; }
    public static UUID getTarget() { return target; }
    public static UUID getInitiator() { return initiator; }
    public static List<UUID> getElectionOrder() { return List.copyOf(electionOrder); }
    public static long getStartTime() { return startTime; }
    public static int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public static int getElectionSize() { return electionOrder.size(); }

    /** The seat whose hand is currently live on the rotating vote clock. */
    public static UUID getCurrentVoter() {
        if (!votingPhaseActive || votingPhaseComplete) return null;
        if (currentPlayerIndex < 0 || currentPlayerIndex >= electionOrder.size()) return null;
        return electionOrder.get(currentPlayerIndex);
    }

    /** Locks the current seat and moves the clock to the next seat. */
    public static void advanceVotingClock() {
        if (!votingPhaseActive || votingPhaseComplete) return;
        currentPlayerIndex++;
        if (currentPlayerIndex >= electionOrder.size()) {
            currentPlayerIndex = electionOrder.size();
            votingPhaseComplete = true;
        }
    }

    public static void setLeverState(UUID id, boolean value) { leverStates.put(id, value); }
    public static Map<UUID, Boolean> getLeverStates() { return Map.copyOf(leverStates); }
    public static void lockVote(UUID id, boolean value) { lockedVotes.put(id, value); }
    public static Map<UUID, Boolean> getLockedVotes() { return Map.copyOf(lockedVotes); }
    public static int getLockedVoteCount() {
        return (int) lockedVotes.values().stream().filter(Boolean.TRUE::equals).count();
    }
    public static void syncLeverStatesFromWorld(Map<UUID, Boolean> states) {
        leverStates.clear();
        if (states != null) leverStates.putAll(states);
    }

    /**
     * BOTC clockwise order: the seat immediately after the nominee is counted
     * first, and the nominee is counted last.
     */
    public static List<UUID> buildElectionOrder(UUID target, Map<UUID, Integer> seats) {
        if (seats == null || seats.isEmpty()) return List.of();
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(seats.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        List<UUID> ids = entries.stream().map(Map.Entry::getKey).toList();
        if (target == null || !ids.contains(target)) return ids;
        int targetIndex = ids.indexOf(target);
        List<UUID> result = new ArrayList<>(ids.size());
        for (int i = 1; i <= ids.size(); i++) result.add(ids.get((targetIndex + i) % ids.size()));
        return result;
    }
}
