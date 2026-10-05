package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import com.sharktower.bloodonthesharktower.setup.AutoSeatManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** First 26.2 slice of the original BOTB ModPackets server receiver setup. */
public final class ModPackets {
    private static final Map<UUID, ServerPlayer> JOINED_PLAYERS = new LinkedHashMap<>();
    private static final Set<UUID> DISCONNECTED_PLAYERS = new LinkedHashSet<>();
    private ModPackets() {}

    public static void registerC2SReceivers() {
        NetworkDiagnostics.registerServerReceiver();
        StorytellerActionHandler.register();
        LunaticBluffNetworking.register();
        PlayerActionHandler.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Setup QoL: new players automatically take the lowest free seat.
            // Reconnecting players keep their existing seat, while a player who
            // later claims Storyteller control is removed from the circle.
            AutoSeatManager.seatJoiningPlayer(server, handler.player);

            int sequence = StateBroadcaster.sendCurrentStateTo(handler.player);
            BloodOnTheSharktower.LOGGER.info(
                    "Sent initial Sharktower core state sync {} to {}",
                    sequence,
                    handler.player.getUUID()
            );
            // execute() may run inline on the server thread. An end-of-tick
            // refresh really waits until JOIN has finished updating the live list.
            DISCONNECTED_PLAYERS.remove(handler.player.getUUID());
            JOINED_PLAYERS.put(handler.player.getUUID(), handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            java.util.UUID playerId = handler.player.getUUID();
            StorytellerMovement.restore(handler.player);
            if (com.sharktower.bloodonthesharktower.states.ServerState.currentNight == 0
                    && com.sharktower.bloodonthesharktower.states.ServerState.currentDay == 0
                    && !com.sharktower.bloodonthesharktower.states.ServerState.gameEnded
                    && com.sharktower.bloodonthesharktower.states.StorytellerState.isStoryteller(playerId)) {
                com.sharktower.bloodonthesharktower.states.StorytellerState.releaseStoryteller(playerId);
                com.sharktower.bloodonthesharktower.snapshot.MatchSnapshotManager.refreshCurrentSetupState();
            }
            NightChatManager.onMinecraftPlayerDisconnected(playerId);
            JOINED_PLAYERS.remove(playerId);
            DISCONNECTED_PLAYERS.add(playerId);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (JOINED_PLAYERS.isEmpty() && DISCONNECTED_PLAYERS.isEmpty()) return;
            Map<UUID, ServerPlayer> joined = new LinkedHashMap<>(JOINED_PLAYERS);
            Set<UUID> disconnected = new LinkedHashSet<>(DISCONNECTED_PLAYERS);
            JOINED_PLAYERS.clear();
            DISCONNECTED_PLAYERS.clear();

            for (UUID playerId : disconnected) {
                // Ignore a stale disconnect if this identity already reconnected.
                if (server.getPlayerList().getPlayer(playerId) == null) {
                    AutoSeatManager.releaseDisconnectedSetupSeat(server, playerId);
                }
            }
            if (!joined.isEmpty()) {
                // Repair the public seat/sidebar view for existing clients too.
                StateBroadcaster.broadcastSeats(server);
                StateBroadcaster.broadcastGrimoire(server);
                for (ServerPlayer player : joined.values()) {
                    if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                        StateBroadcaster.sendCurrentStateTo(player);
                    }
                }
            }
            StateBroadcaster.broadcastPlayerDirectory(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            JOINED_PLAYERS.clear();
            DISCONNECTED_PLAYERS.clear();
        });
    }
}
