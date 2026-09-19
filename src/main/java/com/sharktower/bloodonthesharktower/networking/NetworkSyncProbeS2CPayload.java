package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Diagnostic packet used only to prove that the 26.2 S2C -> C2S round trip works. */
public record NetworkSyncProbeS2CPayload(int sequence) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "network_sync_probe"
    );

    public static final Type<NetworkSyncProbeS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSyncProbeS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            NetworkSyncProbeS2CPayload::sequence,
            NetworkSyncProbeS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
