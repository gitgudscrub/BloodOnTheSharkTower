package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Storyteller-only snapshot of event-driven night-order visits. */
public record TriggeredNightOrderS2CPayload(String encoded) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "triggered_night_order"
    );

    public static final Type<TriggeredNightOrderS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, TriggeredNightOrderS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            TriggeredNightOrderS2CPayload::encoded,
            TriggeredNightOrderS2CPayload::new
    );

    public TriggeredNightOrderS2CPayload {
        encoded = encoded == null ? "" : encoded;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
