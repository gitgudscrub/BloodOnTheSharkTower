package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import com.sharktower.bloodonthesharktower.setup.AutoSeatManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** First 26.2 slice of the original BOTB ModPackets server receiver setup. */
public final class ModPackets {
    private ModPackets() {}

    public static void registerC2SReceivers() {
        NetworkDiagnostics.registerServerReceiver();
        StorytellerActionHandler.register();
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
            // JOIN can fire before the new player is visible in MinecraftServer's
            // live player list. If we build the directory immediately, existing
            // Storyteller Grims can receive the new seat UUID but not the player's
            // name/connected status, leaving a temporary "Seat N" placeholder and
            // missing head that never corrects itself. Defer one server task so the
            // joining player is fully present, then rebuild the directory for every
            // client. This also covers reconnects after Setup seat compaction.
            server.execute(() -> {
                StateBroadcaster.broadcastPlayerDirectory(server);
                // Re-send the joining player's own directory explicitly as a belt-
                // and-braces sync; broadcastPlayerDirectory normally includes them
                // now, but this keeps reconnect recovery deterministic.
                StateBroadcaster.sendPlayerDirectoryTo(handler.player);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            java.util.UUID playerId = handler.player.getUUID();
            NightChatManager.onMinecraftPlayerDisconnected(playerId);
            // Defer one server task so the disconnected player has been removed
            // from the live player list before rebuilding setup seating/directory.
            server.execute(() -> {
                boolean setupSeatReleased = AutoSeatManager.releaseDisconnectedSetupSeat(server, playerId);
                if (!setupSeatReleased) {
                    StateBroadcaster.broadcastPlayerDirectory(server);
                }
            });
        });
    }
}
