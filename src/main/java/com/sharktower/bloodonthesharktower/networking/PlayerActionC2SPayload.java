package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Player-originated Grimoire actions that must never implicitly claim Storyteller control. */
public record PlayerActionC2SPayload(String action, String argument) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "player_action"
    );
    public static final Type<PlayerActionC2SPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerActionC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PlayerActionC2SPayload::action,
            ByteBufCodecs.STRING_UTF8,
            PlayerActionC2SPayload::argument,
            PlayerActionC2SPayload::new
    );

    public PlayerActionC2SPayload {
        action = action == null ? "" : action;
        argument = argument == null ? "" : argument;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
