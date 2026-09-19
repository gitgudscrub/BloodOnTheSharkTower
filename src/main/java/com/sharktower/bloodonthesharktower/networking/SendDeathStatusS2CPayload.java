package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;

/** 26.2 port of BOTB SendDeathStatusS2CPayload. */
public record SendDeathStatusS2CPayload(Map<UUID, Boolean> deadPlayers) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "send_death_status"
    );
    public static final Type<SendDeathStatusS2CPayload> TYPE = new Type<>(ID_VALUE);
    public static final StreamCodec<RegistryFriendlyByteBuf, SendDeathStatusS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public SendDeathStatusS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return new SendDeathStatusS2CPayload(StateWire.deathFromJson(buf.readUtf()));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SendDeathStatusS2CPayload value) {
            buf.writeUtf(StateWire.deathToJson(value.deadPlayers()));
        }
    };

    public SendDeathStatusS2CPayload {
        deadPlayers = Map.copyOf(deadPlayers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
