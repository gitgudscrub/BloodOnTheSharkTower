package com.sharktower.bloodonthesharktower.daytime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Minecraft-independent vote clock: the seat order of an election and the seat the clock is on. */
public final class ElectionState {
    private static List<UUID> electionOrder = new ArrayList<>();
    private static int currentPlayerIndex;
    private static boolean votingPhaseActive;
    private static boolean votingPhaseComplete;
    private static long startTime;

    private ElectionState() {}

    /** A nomination or exile call on {@code target}: the clock will start after their seat and end on it. */
    public static void beginElection(UUID target, Map<UUID, Integer> seatNumbers) {
        electionOrder = buildElectionOrder(target, seatNumbers);
        currentPlayerIndex = 0;
        votingPhaseActive = false;
        votingPhaseComplete = false;
        startTime = System.currentTimeMillis();
    }

    public static void beginVotingPhase() {
        votingPhaseActive = true;
        currentPlayerIndex = 0;
        votingPhaseComplete = electionOrder.isEmpty();
        startTime = System.currentTimeMillis();
    }

    public static void endElection() {
        electionOrder = new ArrayList<>();
        currentPlayerIndex = 0;
        votingPhaseActive = false;
        votingPhaseComplete = false;
        startTime = 0L;
    }

    public static void resetVotingPhase() {
        votingPhaseActive = false;
        votingPhaseComplete = false;
        currentPlayerIndex = 0;
    }

    public static boolean isVotingPhaseActive() { return votingPhaseActive; }
    public static boolean isVotingPhaseComplete() { return votingPhaseActive && votingPhaseComplete; }
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

    /**
     * BOTC clockwise order: the seat immediately after the nominee is counted
     * first, and the nominee is counted last.
     */
    private static List<UUID> buildElectionOrder(UUID target, Map<UUID, Integer> seats) {
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
