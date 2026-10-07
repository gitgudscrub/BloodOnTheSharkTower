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
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.networking.SocialStateManager;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

import java.util.UUID;

/**
 * Simple Voice Chat 2.6.x plugin entrypoint for Blood on the Sharktower.
 *
 * Physical houses and manual private rooms stay isolated across phases and
 * voice reconnects; outside routing follows the daytime/proximity policy.
 */
public final class SharktowerVoicechatPlugin implements VoicechatPlugin {
    private static final java.util.Map<UUID,Boolean> WHISPERING = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean whisperAllowed(UUID sender, UUID receiver) {
        return StorytellerState.isStoryteller(sender) || StorytellerState.isStoryteller(receiver)
                || VoicePolicy.neighbours(sender, receiver, ServerState.PLAYER_SEAT_NUMBERS);
    }
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

        // SVC starts each microphone packet with the configured normal/whisper
        // distance. Scale that once here; never rewrite the persisted config.
        registration.registerEvent(VoiceDistanceEvent.class, event -> {
            if (event.getSenderConnection().getGroup() != null) return;
            UUID sender = event.getSenderConnection().getPlayer().getUuid();
            event.setDistance(StorytellerState.isStoryteller(sender) ? Float.MAX_VALUE : event.getDistance() * (2.0F / 3.0F));
        });

        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            if (event.getSenderConnection() == null) return;
            UUID sender = event.getSenderConnection().getPlayer().getUuid();
            WHISPERING.put(sender, event.getPacket().isWhispering());
            SocialStateManager.speaking(sender, event.getPacket().getOpusEncodedData().length > 0);
            // Ordinary proximity packets reach the ST independently of distance,
            // while respecting private rooms. Group routing already has no attenuation.
            if (event.getSenderConnection().getGroup() == null) {
                for (UUID id : StorytellerState.STORYTELLERS) {
                    var receiver = event.getVoicechat().getConnectionOf(id);
                    if (receiver == null || receiver.getGroup() != null || id.equals(sender) || !NightChatManager.sameNightRoom(sender,id)) continue;
                    var packet = event.getPacket().staticSoundPacketBuilder().channelId(sender).build();
                    event.getVoicechat().sendStaticSoundPacketTo(receiver, packet);
                }
            }
        });
        registration.registerEvent(EntitySoundPacketEvent.class, event -> {
            if (event.getSenderConnection() == null || event.getReceiverConnection() == null) return;
            UUID sender = event.getSenderConnection().getPlayer().getUuid();
            UUID receiver = event.getReceiverConnection().getPlayer().getUuid();
            if (!NightChatManager.sameNightRoom(sender,receiver)
                    || (event.getPacket().isWhispering() && !whisperAllowed(sender,receiver))
                    || (SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource()) && StorytellerState.isStoryteller(receiver))) event.cancel();
        });

        // House and manual room isolation applies in every phase. The group
        // filter also protects against packets crossing during a route change.
        registration.registerEvent(StaticSoundPacketEvent.class, event -> {
            if (!SoundPacketEvent.SOURCE_GROUP.equals(event.getSource())) return;
            if (event.getSenderConnection() == null || event.getReceiverConnection() == null) return;

            UUID senderId = event.getSenderConnection().getPlayer().getUuid();
            UUID receiverId = event.getReceiverConnection().getPlayer().getUuid();
            if (!NightChatManager.sameNightRoom(senderId, receiverId)
                    || (WHISPERING.getOrDefault(senderId, false) && !whisperAllowed(senderId, receiverId))) {
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
