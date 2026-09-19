package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 0.8.0 UI action bridge. The original BOTB Grimoire is highly interactive;
 * this payload lets the 26.2 client trigger the already-restored 0.7.0
 * authoritative setup backend without routing through chat commands.
 */
public record StorytellerActionC2SPayload(String action, String argument) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "storyteller_action"
    );
    public static final Type<StorytellerActionC2SPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, StorytellerActionC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            StorytellerActionC2SPayload::action,
            ByteBufCodecs.STRING_UTF8,
            StorytellerActionC2SPayload::argument,
            StorytellerActionC2SPayload::new
    );

    public StorytellerActionC2SPayload {
        action = action == null ? "" : action;
        argument = argument == null ? "" : argument;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
