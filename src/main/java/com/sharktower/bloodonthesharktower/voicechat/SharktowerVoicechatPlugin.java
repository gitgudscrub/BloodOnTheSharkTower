package com.sharktower.bloodonthesharktower.voicechat;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import java.util.UUID;

/**
 * Simple Voice Chat 2.6.x plugin entrypoint for Blood on the Sharktower.
 *
 * 1.0.2-dev uses server-side voice connection lifecycle events to keep shared
 * Night Chat authoritative and to tear down temporary private conversations
 * safely when either participant disconnects.
 */
public final class SharktowerVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return BloodOnTheSharktower.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatIntegrationState.onInitialized(api);
        BloodOnTheSharktower.LOGGER.info("Simple Voice Chat API initialized for Blood on the Sharktower.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            VoicechatIntegrationState.onServerStarted(event.getVoicechat());
            NightChatManager.Result sync = NightChatManager.syncToGamePhase();
            BloodOnTheSharktower.LOGGER.info(
                    "Simple Voice Chat server started; BOTS voice integration is ONLINE. {}",
                    sync.message()
            );
        });

        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            NightChatManager.onVoiceServerStopped();
            VoicechatIntegrationState.onServerStopped();
            BloodOnTheSharktower.LOGGER.info("Simple Voice Chat server stopped; BOTS voice integration is offline.");
        });

        registration.registerEvent(PlayerConnectedEvent.class, event -> {
            UUID playerId = event.getConnection().getPlayer().getUuid();
            NightChatManager.onVoicePlayerConnected(playerId);
        });

        registration.registerEvent(PlayerDisconnectedEvent.class, event ->
                NightChatManager.onVoicePlayerDisconnected(event.getPlayerUuid()));

        // During Night, private Storyteller chats keep both participants in the
        // shared SVC group so the visible group-member heads never change. Filter
        // the actual group audio per receiver instead of changing membership.
        registration.registerEvent(StaticSoundPacketEvent.class, event -> {
            if (!SoundPacketEvent.SOURCE_GROUP.equals(event.getSource())) return;
            if (event.getSenderConnection() == null || event.getReceiverConnection() == null) return;

            UUID senderId = event.getSenderConnection().getPlayer().getUuid();
            UUID receiverId = event.getReceiverConnection().getPlayer().getUuid();
            if (NightChatManager.shouldCancelSharedNightAudio(senderId, receiverId)) {
                event.cancel();
            }
        });

        registration.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            VoicechatIntegrationState.onClientConnectionChanged(event.isConnected(), event.getVoicechat());
            BloodOnTheSharktower.LOGGER.info(
                    "Simple Voice Chat local client connection: {}.",
                    event.isConnected() ? "CONNECTED" : "DISCONNECTED"
            );
        });
    }
}
