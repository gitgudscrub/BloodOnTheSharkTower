package com.sharktower.bloodonthesharktower.states;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.Script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk 26.2 port of the core/gameplay-facing BOTB ClientState.
 *
 * UI-only fields are still deferred, but the authoritative player, seat,
 * death, script, day/night and grimoire state now live here in their original
 * architectural home.
 */
public final class ClientState {
    public static Role myRole = Role.NO_ROLE;
    public static PendingRoleAssignment myAssignment =
            new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT);
    public static Boolean myAlignment = null;

    public static int activePlayerCount = 0;
    public static int travelerCount = 0;
    public static int lobbyPlayerCount = 0;
    public static int lobbyStorytellerCount = 0;

    public static Script currentScript = null;
    public static Map<UUID, Integer> playerSeatNumbers = new HashMap<>();
    public static Map<UUID, Boolean> playerDeathStatus = new HashMap<>();

    public static int currentNight = 0;
    public static int currentDay = 0;
    public static boolean executionToday = false;
    public static boolean gameEnding = false;
    public static boolean rolesRevealed = false;
    public static String winningTeam = "NONE";

    /** Client presentation toggles restored with the 0.6.0 UI bulk port. */
    public static boolean isHudEnabled = true;
    public static boolean isRoleHudVisible = true;
    public static boolean isNightHudVisible = true;


    /** Daytime / election state restored in 0.5.0. */
    public static Map<UUID, Boolean> canNominate = new HashMap<>();
    public static Map<UUID, Boolean> canBeNominated = new HashMap<>();
    public static Map<UUID, Boolean> hasUsedGhostVote = new HashMap<>();
    public static Map<UUID, Integer> nominationsRemaining = new HashMap<>();
    public static UUID currentNominator = null;
    public static UUID currentNominee = null;
    public static UUID markedForExecution = null;
    public static int votesForMarkedPlayer = 0;
    public static boolean nominationsOpen = false;
    public static boolean organGrinderModeActiveToday = false;
    public static UUID storytellerMFE = null;
    public static int storytellerMFEVotes = 0;
    public static boolean storytellerCanBeNominated = true;
    public static Map<UUID, Boolean> canBeExiled = new HashMap<>();
    public static Map<UUID, Boolean> exiledTravelers = new HashMap<>();
    public static UUID currentExileCaller = null;
    public static UUID currentExileTarget = null;
    public static boolean exileSupportInProgress = false;
    public static int exileSupportCount = 0;
    public static boolean voudonModeActive = false;
    public static UUID voudonPlayerUuid = null;

    public static boolean voteInProgress = false;
    public static int effectiveVoteCount = 0;
    public static int voteThreshold = 0;
    public static int handsRaised = 0;
    public static Map<UUID, Boolean> raisedHands = new HashMap<>();
    public static Map<UUID, Boolean> currentVotes = new HashMap<>();
    public static Map<UUID, Boolean> lockedVotes = new HashMap<>();
    public static Map<UUID, Boolean> leverStates = new HashMap<>();
    public static boolean organGrinderMode = false;
    public static boolean exileSupportVote = false;
    public static UUID currentVoteClockPlayer = null;
    public static int voteClockIndex = 0;
    public static int voteClockTotal = 0;
    public static boolean voteClockComplete = false;
    public static boolean voteCountOverrideActive = false;
    public static int voteCountOverride = 0;
    public static String lastVoteResult = "NONE";
    public static UUID lastVoteNominee = null;
    public static int lastVoteCount = 0;
    public static int lastVoteThreshold = 0;
    public static boolean clockCenterAvailable = false;
    public static double clockCenterX = 0.0D;
    public static double clockCenterY = 0.0D;
    public static double clockCenterZ = 0.0D;
    public static double clockHandScale = 4.0D;
    public static int voteStepTicks = 20;
    public static boolean clockTargetAvailable = false;
    public static double clockTargetX = 0.0D;
    public static double clockTargetY = 0.0D;
    public static double clockTargetZ = 0.0D;
    public static boolean clockReferenceAvailable = false;
    public static double clockReferenceX = 0.0D;
    public static double clockReferenceY = 0.0D;
    public static double clockReferenceZ = 0.0D;
    public static long voteClockStepClientNanos = System.nanoTime();

    /** Storyteller/grimoire snapshot state. */
    public static Map<UUID, PendingRoleAssignment> grimoireRoles = new HashMap<>();
    /** Storyteller-only fake identities for the Drunk/Marionette. */
    public static Map<UUID, PendingRoleAssignment> grimoirePerceivedRoles = new HashMap<>();
    public static Map<UUID, Integer> grimoireSeatNumbers = new HashMap<>();
    public static Map<UUID, List<Reminder>> grimoireReminders = new HashMap<>();
    public static List<String> demonBluffs = List.of();
    public static boolean lastGrimoireSendTargeted = false;

    /** Connected-player directory used by the Grimoire for names/heads/unseated UI. */
    public static Map<UUID, String> playerNames = new HashMap<>();
    public static List<UUID> connectedPlayers = List.of();
    public static List<UUID> storytellerPlayers = List.of();

    /** Local-only server-synchronized Simple Voice Chat route. */
    public static String voiceRoute = "PROXIMITY";

    /** Storyteller-only daily facts used by the night-visit helper HUD. */
    public static String lastExecutedRoleName = "";
    public static boolean demonVotedToday = false;
    public static boolean minionNominatedToday = false;

    private ClientState() {}

    public static void updatePlayerState(
            PendingRoleAssignment assignment,
            int activePlayers,
            int travelers,
            boolean silent
    ) {
        PendingRoleAssignment resolved = resolveAssignment(assignment);

        myAssignment = resolved;
        myRole = resolved.role();
        myAlignment = resolved.isFinalGood();
        activePlayerCount = activePlayers;
        travelerCount = travelers;

        // The original client also triggers role HUD/audio/animation here when
        // silent=false. Presentation is deferred to the UI/HUD bulk milestone.
    }

    public static void updateEndGame(boolean ended, boolean revealed, String winner) {
        gameEnding = ended;
        rolesRevealed = revealed;
        winningTeam = winner == null || winner.isBlank()
                ? "NONE"
                : winner.toUpperCase(java.util.Locale.ROOT);
    }

    public static void updateDayNight(int night, int day, boolean execution) {
        currentNight = night;
        currentDay = day;
        executionToday = execution;
    }

    public static void updateScript(Script script) {
        currentScript = script;
        myAssignment = resolveAssignment(myAssignment);
        myRole = myAssignment.role();
        myAlignment = myAssignment.isFinalGood();

        Map<UUID, PendingRoleAssignment> resolved = new HashMap<>();
        grimoireRoles.forEach((uuid, assignment) -> resolved.put(uuid, resolveAssignment(assignment)));
        grimoireRoles = resolved;
        Map<UUID, PendingRoleAssignment> resolvedPerceived = new HashMap<>();
        grimoirePerceivedRoles.forEach((uuid, assignment) -> resolvedPerceived.put(uuid, resolveAssignment(assignment)));
        grimoirePerceivedRoles = resolvedPerceived;
    }

    public static void updateSeats(Map<UUID, Integer> seats) {
        playerSeatNumbers = new HashMap<>(seats);
    }

    public static void updateDeathStatus(Map<UUID, Boolean> deaths) {
        playerDeathStatus = new HashMap<>(deaths);
    }

    public static boolean isExiledTraveler(UUID id) {
        return id != null && exiledTravelers.getOrDefault(id, false);
    }


    public static void updateDaytimeState(
            Map<UUID, Boolean> canNominateState,
            Map<UUID, Boolean> canBeNominatedState,
            Map<UUID, Boolean> ghostVotes,
            Map<UUID, Integer> remaining,
            UUID nominator,
            UUID nominee,
            UUID marked,
            int markedVotes,
            boolean open,
            boolean organGrinderToday,
            UUID storytellerMarked,
            int storytellerVotes,
            boolean storytellerEligible,
            Map<UUID, Boolean> exileEligibility,
            Map<UUID, Boolean> exiledTravelerState,
            UUID exileCaller,
            UUID exileTarget,
            boolean exileSupportActive,
            int supportCount,
            boolean voudonActive,
            UUID voudonPlayer
    ) {
        canNominate = new HashMap<>(canNominateState);
        canBeNominated = new HashMap<>(canBeNominatedState);
        hasUsedGhostVote = new HashMap<>(ghostVotes);
        nominationsRemaining = new HashMap<>(remaining);
        currentNominator = nominator;
        currentNominee = nominee;
        markedForExecution = marked;
        votesForMarkedPlayer = markedVotes;
        nominationsOpen = open;
        organGrinderModeActiveToday = organGrinderToday;
        storytellerMFE = storytellerMarked;
        storytellerMFEVotes = storytellerVotes;
        storytellerCanBeNominated = storytellerEligible;
        canBeExiled = new HashMap<>(exileEligibility);
        exiledTravelers = new HashMap<>(exiledTravelerState);
        currentExileCaller = exileCaller;
        currentExileTarget = exileTarget;
        exileSupportInProgress = exileSupportActive;
        exileSupportCount = supportCount;
        voudonModeActive = voudonActive;
        voudonPlayerUuid = voudonPlayer;
    }

    public static void updateVoteState(
            boolean active,
            int effectiveCount,
            int threshold,
            int raisedCount,
            Map<UUID, Boolean> handState,
            Map<UUID, Boolean> votes,
            Map<UUID, Boolean> locks,
            Map<UUID, Boolean> levers,
            boolean organGrinder,
            boolean exileSupport,
            UUID currentVoter,
            int clockIndex,
            int clockTotal,
            boolean clockComplete,
            boolean overrideActive,
            int overrideCount,
            String resolvedResult,
            UUID resolvedNominee,
            int resolvedCount,
            int resolvedThreshold,
            boolean hasClockCenter,
            double centerX,
            double centerY,
            double centerZ,
            double handScale,
            int stepTicks,
            boolean hasClockTarget,
            double targetX,
            double targetY,
            double targetZ,
            boolean hasClockReference,
            double referenceX,
            double referenceY,
            double referenceZ
    ) {
        boolean voteActiveChanged = voteInProgress != active;
        voteInProgress = active;
        effectiveVoteCount = effectiveCount;
        voteThreshold = threshold;
        handsRaised = raisedCount;
        raisedHands = new HashMap<>(handState);
        currentVotes = new HashMap<>(votes);
        lockedVotes = new HashMap<>(locks);
        leverStates = new HashMap<>(levers);
        organGrinderMode = organGrinder;
        exileSupportVote = exileSupport;
        boolean voterChanged = currentVoteClockPlayer == null
                ? currentVoter != null
                : !currentVoteClockPlayer.equals(currentVoter);
        if (voterChanged || voteClockIndex != clockIndex || voteActiveChanged) {
            voteClockStepClientNanos = System.nanoTime();
        }
        currentVoteClockPlayer = currentVoter;
        voteClockIndex = clockIndex;
        voteClockTotal = clockTotal;
        voteClockComplete = clockComplete;
        voteCountOverrideActive = overrideActive;
        voteCountOverride = overrideCount;
        lastVoteResult = resolvedResult == null || resolvedResult.isBlank() ? "NONE" : resolvedResult;
        lastVoteNominee = resolvedNominee;
        lastVoteCount = resolvedCount;
        lastVoteThreshold = resolvedThreshold;
        clockCenterAvailable = hasClockCenter;
        clockCenterX = centerX;
        clockCenterY = centerY;
        clockCenterZ = centerZ;
        clockHandScale = handScale;
        voteStepTicks = Math.max(1, stepTicks);
        clockTargetAvailable = hasClockTarget;
        clockTargetX = targetX;
        clockTargetY = targetY;
        clockTargetZ = targetZ;
        clockReferenceAvailable = hasClockReference;
        clockReferenceX = referenceX;
        clockReferenceY = referenceY;
        clockReferenceZ = referenceZ;
    }

    public static void updatePlayerDirectory(Map<UUID, String> names, List<UUID> connected, List<UUID> storytellers) {
        playerNames = new HashMap<>(names);
        connectedPlayers = List.copyOf(connected);
        storytellerPlayers = List.copyOf(storytellers);
    }

    public static void updateVoiceRoute(String route) {
        voiceRoute = route == null || route.isBlank() ? "PROXIMITY" : route;
    }

    public static void updateStorytellerNightInfo(String executedRoleName, boolean demonVoted, boolean minionNominated) {
        lastExecutedRoleName = executedRoleName == null ? "" : executedRoleName;
        demonVotedToday = demonVoted;
        minionNominatedToday = minionNominated;
    }

    public static boolean isHandRaised(UUID id) {
        return id != null && raisedHands.getOrDefault(id, false);
    }

    public static String playerName(UUID id, int seat) {
        String name = playerNames.get(id);
        if (name != null && !name.isBlank()) return name;
        return seat > 0 ? "Seat " + seat : shortId(id);
    }

    private static String shortId(UUID id) {
        if (id == null) return "Unknown";
        String text = id.toString();
        return text.substring(0, Math.min(8, text.length()));
    }

    public static void updateGrimoire(
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, PendingRoleAssignment> perceivedRoles,
            Map<UUID, Integer> seats,
            Map<UUID, List<Reminder>> reminders,
            List<String> bluffs,
            boolean targeted
    ) {
        Map<UUID, PendingRoleAssignment> resolved = new HashMap<>();
        roles.forEach((uuid, assignment) -> resolved.put(uuid, resolveAssignment(assignment)));
        grimoireRoles = resolved;
        Map<UUID, PendingRoleAssignment> resolvedPerceived = new HashMap<>();
        perceivedRoles.forEach((uuid, assignment) -> resolvedPerceived.put(uuid, resolveAssignment(assignment)));
        grimoirePerceivedRoles = resolvedPerceived;
        grimoireSeatNumbers = new HashMap<>(seats);
        grimoireReminders = new HashMap<>(reminders);
        demonBluffs = List.copyOf(bluffs);
        lastGrimoireSendTargeted = targeted;
    }

    private static PendingRoleAssignment resolveAssignment(PendingRoleAssignment assignment) {
        if (assignment == null) {
            return new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT);
        }
        if (assignment.isCustomRole() && currentScript != null) {
            return assignment.resolveCustomRole(currentScript);
        }
        return assignment;
    }

    public static GamePhase phase() {
        return GamePhase.determine(
                currentNight,
                currentDay,
                nominationsOpen,
                currentNominee,
                markedForExecution,
                currentExileTarget,
                exileSupportInProgress
        );
    }

    public static String displayScriptName() {
        return currentScript == null || currentScript.name().isBlank()
                ? "none"
                : currentScript.name();
    }

    public static int scriptRoleCount() {
        return currentScript == null ? 0 : currentScript.allRoles().size();
    }

    public static String displayRoleId() {
        if (myAssignment == null) return "none";
        if (!myAssignment.isCustomRole() && myAssignment.role() == Role.NO_ROLE) return "none";
        return myAssignment.getRoleId();
    }

    public static String displayRoleName() {
        if (myAssignment == null) return "none";
        if (!myAssignment.isCustomRole() && myAssignment.role() == Role.NO_ROLE) return "none";
        return myAssignment.getDisplayName();
    }

    public static boolean isGood() {
        return Boolean.TRUE.equals(myAlignment);
    }

    public static int seatedPlayerCount() {
        return playerSeatNumbers.size();
    }

    public static int deadPlayerCount() {
        return (int) playerDeathStatus.values().stream().filter(Boolean.TRUE::equals).count();
    }

    public static int grimoirePlayerCount() {
        return grimoireRoles.size();
    }
}
