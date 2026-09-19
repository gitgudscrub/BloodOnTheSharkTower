package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.nightorder.TriggeredNightOrderManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/** Logical/core port of BOTB ExecutionManager; presentation animation returns later. */
public final class ExecutionManager {
    private ExecutionManager() {}

    public static boolean executeMarkedPlayer(MinecraftServer server) {
        UUID marked = DaytimeState.getMarkedForExecution();
        if (marked == null) return false;
        executePlayer(server, marked, false, null);
        return true;
    }

    public static void executePlayer(MinecraftServer server, UUID player, boolean forced, UUID butcherUuid) {
        if (player == null) return;
        boolean wasDead = Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(player));
        ServerState.PLAYER_DEATH_STATUS.put(player, true);
        if (!wasDead) {
            TriggeredNightOrderManager.onDeath(player, TriggeredNightOrderManager.DeathCause.EXECUTION);
            PendingRoleAssignment executed = StorytellerState.effectiveGrimoireRoles().get(player);
            StorytellerState.lastExecutedRoleName = executed == null ? "" : executed.getDisplayName();
        }
        ServerState.executionToday = true;
        DaytimeState.clearMarkedForExecution();
        DaytimeState.closeNominations();
        BloodOnTheSharktower.LOGGER.info("Executed player {} (forced={}, butcher={})", player, forced, butcherUuid);
        StateBroadcaster.broadcastDeathStatus(server);
        StateBroadcaster.broadcastDayNightState(server);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastGrimoire(server);
        StateBroadcaster.broadcastStorytellerNightInfo(server);
    }

    public static void executePlayerFail(MinecraftServer server, UUID player, boolean forced, UUID butcherUuid) {
        ServerState.executionToday = true;
        DaytimeState.clearMarkedForExecution();
        DaytimeState.closeNominations();
        BloodOnTheSharktower.LOGGER.info("Execution failed/survived for {} (forced={}, butcher={})", player, forced, butcherUuid);
        StateBroadcaster.broadcastDayNightState(server);
        StateBroadcaster.broadcastDaytimeState(server);
    }
    public static void noExecution(MinecraftServer server) {
        DaytimeState.clearMarkedForExecution();
        DaytimeState.clearStorytellerMFE();
        DaytimeState.closeNominations();
        ServerState.executionToday = false;
        BloodOnTheSharktower.LOGGER.info("Day closed with no execution.");
        StateBroadcaster.broadcastDayNightState(server);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

}
