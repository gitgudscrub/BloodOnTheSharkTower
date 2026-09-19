package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Temporary 0.3.2 diagnostic acknowledgement for the restored role packet. */
public record RoleSyncAckC2SPayload(String roleId, boolean good) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "role_sync_ack"
    );

    public static final Type<RoleSyncAckC2SPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleSyncAckC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RoleSyncAckC2SPayload::roleId,
            ByteBufCodecs.BOOL,
            RoleSyncAckC2SPayload::good,
            RoleSyncAckC2SPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
