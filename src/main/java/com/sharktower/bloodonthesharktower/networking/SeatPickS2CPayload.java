package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A night choice the player can make by clicking a seat in their Grimoire:
 * {@code title} is the banner ("Monk: click a player to protect tonight"),
 * {@code command} the command the seat number is appended to ("/protect "),
 * {@code excludeSeat} a seat that cannot be chosen (0 for none). An empty
 * command clears the choice.
 */
public record SeatPickS2CPayload(String title, String command, int excludeSeat) implements CustomPacketPayload {
    public static final Type<SeatPickS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "seat_pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeatPickS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SeatPickS2CPayload::title,
            ByteBufCodecs.STRING_UTF8, SeatPickS2CPayload::command,
            ByteBufCodecs.VAR_INT, SeatPickS2CPayload::excludeSeat,
            SeatPickS2CPayload::new);

    public static SeatPickS2CPayload none() {
        return new SeatPickS2CPayload("", "", 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
