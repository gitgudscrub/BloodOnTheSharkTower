package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Diagnostic acknowledgement for the bulk core-state sync probe. */
public record NetworkSyncAckC2SPayload(
        int sequence,
        String clientPhase,
        String scriptName,
        int scriptRoleCount,
        int seatCount,
        int deadCount,
        int grimoirePlayerCount
) implements CustomPacketPayload {

    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "network_sync_ack"
    );

    public static final Type<NetworkSyncAckC2SPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSyncAckC2SPayload> CODEC = new StreamCodec<>() {
        @Override
        public NetworkSyncAckC2SPayload decode(RegistryFriendlyByteBuf buf) {
            return new NetworkSyncAckC2SPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, NetworkSyncAckC2SPayload value) {
            buf.writeVarInt(value.sequence());
            buf.writeUtf(value.clientPhase());
            buf.writeUtf(value.scriptName());
            buf.writeVarInt(value.scriptRoleCount());
            buf.writeVarInt(value.seatCount());
            buf.writeVarInt(value.deadCount());
            buf.writeVarInt(value.grimoirePlayerCount());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
