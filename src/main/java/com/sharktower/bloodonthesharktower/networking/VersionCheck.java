package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;

import java.util.function.Consumer;

/**
 * Login-time version check. During the configuration phase (before the player
 * is in the world, so no seat is taken) the server sends its Sharktower
 * version, the client answers with its own, and the server either lets the
 * login continue or disconnects with {@link VersionGate#refusal}.
 *
 * <p>Clients without the Sharktower mod, or older than this check, cannot
 * receive the request at all and are refused with the "missing" message.
 * Start the server with {@code -Dsharktower.versionCheck=false} to turn it off.
 */
public final class VersionCheck {
    private static final int MAX_VERSION_LENGTH = 64;

    public record RequestS2C(String serverVersion) implements CustomPacketPayload {
        public static final Type<RequestS2C> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                BloodOnTheSharktower.MOD_ID, "version_request"));
        public static final StreamCodec<FriendlyByteBuf, RequestS2C> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), RequestS2C::serverVersion,
                RequestS2C::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ReplyC2S(String clientVersion) implements CustomPacketPayload {
        public static final Type<ReplyC2S> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                BloodOnTheSharktower.MOD_ID, "version_reply"));
        public static final StreamCodec<FriendlyByteBuf, ReplyC2S> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), ReplyC2S::clientVersion,
                ReplyC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Task(String serverVersion) implements ConfigurationTask {
        static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(
                BloodOnTheSharktower.MOD_ID + ":version_check");

        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new RequestS2C(serverVersion)));
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    private VersionCheck() {}

    public static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(BloodOnTheSharktower.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /** Payload types are registered on both sides; call from the common initializer. */
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundConfiguration().register(RequestS2C.TYPE, RequestS2C.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ReplyC2S.TYPE, ReplyC2S.CODEC);
    }

    public static void registerServer() {
        if (!Boolean.parseBoolean(System.getProperty("sharktower.versionCheck", "true"))) {
            BloodOnTheSharktower.LOGGER.warn("Sharktower version check is OFF (-Dsharktower.versionCheck=false).");
            return;
        }
        String serverVersion = modVersion();

        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, RequestS2C.TYPE)) {
                handler.addTask(new Task(serverVersion));
            } else {
                refuse(handler, serverVersion, null);
            }
        });

        ServerConfigurationNetworking.registerGlobalReceiver(ReplyC2S.TYPE, (payload, context) -> {
            var handler = context.packetListener();
            if (VersionGate.refusal(serverVersion, payload.clientVersion()) == null) {
                handler.completeTask(Task.TYPE);
            } else {
                refuse(handler, serverVersion, payload.clientVersion());
            }
        });
    }

    private static void refuse(net.minecraft.server.network.ServerConfigurationPacketListenerImpl handler,
                               String serverVersion, String clientVersion) {
        String message = VersionGate.refusal(serverVersion, clientVersion);
        BloodOnTheSharktower.LOGGER.info("Refused {}: Sharktower version {} (server {}).",
                handler.getOwner().name(), clientVersion == null ? "missing" : clientVersion, serverVersion);
        handler.disconnect(Component.literal(message));
    }
}
