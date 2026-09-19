package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A.10 server-authoritative end-game/reveal state. */
public record EndGameStateS2CPayload(boolean gameEnded, boolean rolesRevealed, String winningTeam)
        implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "end_game_state"
    );

    public static final Type<EndGameStateS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, EndGameStateS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            EndGameStateS2CPayload::gameEnded,
            ByteBufCodecs.BOOL,
            EndGameStateS2CPayload::rolesRevealed,
            ByteBufCodecs.STRING_UTF8,
            EndGameStateS2CPayload::winningTeam,
            EndGameStateS2CPayload::new
    );

    public EndGameStateS2CPayload {
        winningTeam = winningTeam == null || winningTeam.isBlank() ? "NONE" : winningTeam;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
