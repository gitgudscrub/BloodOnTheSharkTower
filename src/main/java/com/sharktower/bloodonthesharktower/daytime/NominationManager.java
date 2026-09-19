package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Logical 26.2 port of BOTB NominationManager. */
public final class NominationManager {
    private NominationManager() {}

    public static boolean validateNomination(UUID nominator, UUID nominee, boolean override) {
        if (nominator == null || nominee == null) return false;
        if (override) return true;
        if (!DaytimeState.areNominationsOpen()) return false;
        if (DaytimeState.hasActiveExile()) return false;
        if (!DaytimeState.canNominate(nominator) || !DaytimeState.hasNominationsRemaining(nominator)) return false;
        return DaytimeState.canBeNominated(nominee);
    }

    public static boolean executeNomination(MinecraftServer server, UUID nominator, UUID nominee, int alivePlayerCount) {
        if (!validateNomination(nominator, nominee, false)) return false;
        if (DaytimeState.hasActiveNomination()) resetNomination(server);
        VotingManager.clearLastResult();

        DaytimeState.setCurrentNominator(nominator);
        DaytimeState.setCurrentNominee(nominee);
        ElectionState.beginElection(
                ElectionType.VOTE,
                ElectionConfig.forVote(false),
                nominee,
                nominator,
                DaytimeState.getActiveElectionSeats()
        );
        DaytimeState.useNomination(nominator);
        if (!DaytimeState.hasNominationsRemaining(nominator)) DaytimeState.setCanNominate(nominator, false);
        if (ServerState.PLAYER_SEAT_NUMBERS.containsKey(nominee)) DaytimeState.setCanBeNominated(nominee, false);

        BloodOnTheSharktower.LOGGER.info(
                "Nomination accepted: {} -> {}, alive={}, threshold={}",
                nominator, nominee, alivePlayerCount, VotingManager.calculateThreshold(alivePlayerCount)
        );
        DayPublicInfoBookManager.recordNomination(server, nominator);
        recordMinionNomination(server, nominator);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }


    /** Atheist-style nomination of an active Storyteller. */
    public static boolean executeStorytellerNomination(MinecraftServer server, UUID nominator, UUID storyteller, int alivePlayerCount) {
        if (nominator == null || storyteller == null) return false;
        if (!DaytimeState.areNominationsOpen()) return false;
        if (DaytimeState.hasActiveExile()) return false;
        if (!DaytimeState.canNominate(nominator) || !DaytimeState.hasNominationsRemaining(nominator)) return false;
        if (!DaytimeState.canStorytellerBeNominated()) return false;

        if (DaytimeState.hasActiveNomination()) resetNomination(server);
        VotingManager.clearLastResult();
        DaytimeState.setCurrentNominator(nominator);
        DaytimeState.setCurrentNominee(storyteller);
        ElectionState.beginElection(
                ElectionType.VOTE,
                ElectionConfig.forVote(false),
                storyteller,
                nominator,
                DaytimeState.getActiveElectionSeats()
        );
        DaytimeState.useNomination(nominator);
        if (!DaytimeState.hasNominationsRemaining(nominator)) DaytimeState.setCanNominate(nominator, false);
        DaytimeState.setStorytellerCanBeNominated(false);

        BloodOnTheSharktower.LOGGER.info(
                "Storyteller nomination accepted: {} -> {}, alive={}, threshold={}",
                nominator, storyteller, alivePlayerCount, VotingManager.calculateThreshold(alivePlayerCount)
        );
        DayPublicInfoBookManager.recordNomination(server, nominator);
        recordMinionNomination(server, nominator);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static boolean executeNomination(MinecraftServer server, UUID nominator, UUID nominee, int alivePlayerCount, boolean override) {
        if (!override) return executeNomination(server, nominator, nominee, alivePlayerCount);
        DaytimeState.setCurrentNominator(nominator);
        DaytimeState.setCurrentNominee(nominee);
        ElectionState.beginElection(ElectionType.VOTE, ElectionConfig.forVote(false), nominee, nominator, DaytimeState.getActiveElectionSeats());
        DayPublicInfoBookManager.recordNomination(server, nominator);
        recordMinionNomination(server, nominator);
        StateBroadcaster.broadcastDaytimeState(server);
        return true;
    }

    private static void recordMinionNomination(MinecraftServer server, UUID nominator) {
        if (nominator == null || StorytellerState.minionNominatedToday) return;
        PendingRoleAssignment assignment = StorytellerState.effectiveGrimoireRoles().get(nominator);
        if (assignment == null || assignment.getRoleType() != RoleType.MINION) return;
        StorytellerState.minionNominatedToday = true;
        StateBroadcaster.broadcastStorytellerNightInfo(server);
    }

    public static void resetNomination(MinecraftServer server) {
        DaytimeState.resetNomination();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

    /** Presentation hook retained for original API compatibility; glow returns with the UI/world batch. */
    public static void updateMarkedGlow(MinecraftServer server, UUID player, boolean marked) {
        BloodOnTheSharktower.LOGGER.debug("Deferred marked glow update: player={}, marked={}", player, marked);
    }
}
