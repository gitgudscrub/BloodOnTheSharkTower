package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Storyteller-only control channel for the Lunatic's fake Demon bluffs. */
public record LunaticBluffActionC2SPayload(String action, String argument) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "lunatic_bluff_action"
    );
    public static final Type<LunaticBluffActionC2SPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, LunaticBluffActionC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            LunaticBluffActionC2SPayload::action,
            ByteBufCodecs.STRING_UTF8,
            LunaticBluffActionC2SPayload::argument,
            LunaticBluffActionC2SPayload::new
    );

    public LunaticBluffActionC2SPayload {
        action = action == null ? "" : action;
        argument = argument == null ? "" : argument;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
