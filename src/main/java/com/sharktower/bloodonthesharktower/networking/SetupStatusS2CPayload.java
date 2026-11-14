package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Storyteller-only setup progress for the Grimoire checklist:
 * {@code rolesSent} is true once SEND ROLES has committed the setup and no
 * edits are waiting to be sent.
 */
public record SetupStatusS2CPayload(boolean rolesSent) implements CustomPacketPayload {
    public static final Type<SetupStatusS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "setup_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetupStatusS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SetupStatusS2CPayload::rolesSent,
            SetupStatusS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
