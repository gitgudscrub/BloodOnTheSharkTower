package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.nightorder.TriggeredNightOrderManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.sound.ModSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
        com.sharktower.bloodonthesharktower.states.DeathVisibility.remove(player);
        if (!wasDead) {
            TriggeredNightOrderManager.onDeath(player, TriggeredNightOrderManager.DeathCause.EXECUTION);
            PendingRoleAssignment executed = StorytellerState.effectiveGrimoireRoles().get(player);
            StorytellerState.lastExecutedRoleName = executed == null ? "" : executed.getDisplayName();
        }
        ServerState.executionToday = true;
        DaytimeState.clearMarkedForExecution();
        DaytimeState.closeNominations();
        VotingManager.clearLastResult();
        BloodOnTheSharktower.LOGGER.info("Executed player {} (forced={}, butcher={})", player, forced, butcherUuid);
        ModSounds.playForAll(server, ModSounds.EXECUTION);
        announceExecution(server, player, true);
        StateBroadcaster.broadcastDeathStatus(server);
        StateBroadcaster.broadcastVoteState(server);
        StateBroadcaster.broadcastDayNightState(server);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastGrimoire(server);
        StateBroadcaster.broadcastStorytellerNightInfo(server);
    }

    public static void executePlayerFail(MinecraftServer server, UUID player, boolean forced, UUID butcherUuid) {
        if (player == null) return;
        ServerState.executionToday = true;
        DaytimeState.clearMarkedForExecution();
        DaytimeState.closeNominations();
        VotingManager.clearLastResult();
        BloodOnTheSharktower.LOGGER.info("Execution failed/survived for {} (forced={}, butcher={})", player, forced, butcherUuid);
        ModSounds.playForAll(server, ModSounds.EXECUTION_SURVIVED);
        announceExecution(server, player, false);
        StateBroadcaster.broadcastDayNightState(server);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }
    private static void announceExecution(MinecraftServer server, UUID player, boolean died) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(player);
        String name = online != null ? online.getName().getString()
                : (seat == null ? "Player" : "Seat " + seat);
        Component message = Component.literal(name + (died
                ? " was executed and died" : " was executed and survived"))
                .withStyle(died ? ChatFormatting.RED : ChatFormatting.GOLD);
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            target.sendSystemMessage(message);
        }
    }

    public static void noExecution(MinecraftServer server) {
        DaytimeState.clearMarkedForExecution();
        DaytimeState.clearStorytellerMFE();
        DaytimeState.closeNominations();
        VotingManager.clearLastResult();
        ServerState.executionToday = false;
        BloodOnTheSharktower.LOGGER.info("Day closed with no execution.");
        StateBroadcaster.broadcastDayNightState(server);
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

}
