package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;

/** 26.2 port of BOTB SendSeatsS2CPayload. */
public record SendSeatsS2CPayload(Map<UUID, Integer> seatNumbers) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "send_seats"
    );
    public static final Type<SendSeatsS2CPayload> TYPE = new Type<>(ID_VALUE);
    public static final StreamCodec<RegistryFriendlyByteBuf, SendSeatsS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public SendSeatsS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return new SendSeatsS2CPayload(StateWire.seatsFromJson(buf.readUtf()));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SendSeatsS2CPayload value) {
            buf.writeUtf(StateWire.seatsToJson(value.seatNumbers()));
        }
    };

    public SendSeatsS2CPayload {
        seatNumbers = Map.copyOf(seatNumbers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
