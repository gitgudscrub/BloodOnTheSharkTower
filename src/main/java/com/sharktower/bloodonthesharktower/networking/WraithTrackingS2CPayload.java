package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Targeted, knowledge-scoped Wraith compass / Storyteller house marker update. */
public record WraithTrackingS2CPayload(String state) implements CustomPacketPayload {
    public static final Type<WraithTrackingS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "wraith_tracking"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WraithTrackingS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
                    WraithTrackingS2CPayload::state, WraithTrackingS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
