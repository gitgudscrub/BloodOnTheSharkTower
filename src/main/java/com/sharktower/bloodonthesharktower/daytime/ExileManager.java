package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Logical/core port of BOTB ExileManager. */
public final class ExileManager {
    private ExileManager() {}

    public static boolean validateExile(UUID caller, UUID traveler, boolean override) {
        if (caller == null || traveler == null) return false;
        if (override) return true;
        if (!PhaseOperations.isDay()) return false;
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(caller)) return false;
        if (DaytimeState.isExiledTraveler(caller)) return false;
        if (DaytimeState.isVoteInProgress() || DaytimeState.isExileSupportInProgress()) return false;
        if (DaytimeState.hasActiveNomination() || DaytimeState.hasActiveExile()) return false;
        return DaytimeState.canBeExiled(traveler) && DaytimeState.isTraveler(traveler);
    }

    public static boolean callForExile(MinecraftServer server, UUID caller, UUID traveler, boolean override) {
        if (!validateExile(caller, traveler, override)) return false;
        DaytimeState.clearRaisedHands();
        DaytimeState.clearManualVoteCountOverride();
        DaytimeState.setCurrentExileCaller(caller);
        DaytimeState.setCurrentExileTarget(traveler);
        ElectionState.beginElection(
                ElectionType.EXILE_SUPPORT,
                ElectionConfig.forExileSupport(),
                traveler,
                caller,
                DaytimeState.getActiveElectionSeats()
        );
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        return true;
    }

    public static void executeExile(MinecraftServer server, UUID traveler, UUID caller, int supportCount) {
        if (traveler == null) return;
        // Exile is not death: the Traveller leaves the game without consuming or
        // changing ordinary death/ghost-vote state.
        DaytimeState.markTravelerExiled(traveler);
        BloodOnTheSharktower.LOGGER.info("Exiled traveler {} called by {} with {} support", traveler, caller, supportCount);
        DaytimeState.resetExile();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
        StateBroadcaster.broadcastGrimoire(server);
        StateBroadcaster.broadcastRoles(server, true);
        if (NightChatManager.isActive()) NightChatManager.resync();
    }

    public static void resetExile(MinecraftServer server) {
        DaytimeState.resetExile();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }
}
