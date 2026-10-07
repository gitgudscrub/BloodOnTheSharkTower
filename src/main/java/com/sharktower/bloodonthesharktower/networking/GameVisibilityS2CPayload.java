package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Map;
import java.util.UUID;

/** Public reset generation plus Storyteller-only pending death indicators. */
public record GameVisibilityS2CPayload(long generation, Map<UUID, Boolean> pendingDeaths) implements CustomPacketPayload {
    public static final Type<GameVisibilityS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "game_visibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameVisibilityS2CPayload> CODEC = new StreamCodec<>() {
        public GameVisibilityS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return new GameVisibilityS2CPayload(buf.readVarLong(), StateWire.deathFromJson(buf.readUtf()));
        }
        public void encode(RegistryFriendlyByteBuf buf, GameVisibilityS2CPayload value) {
            buf.writeVarLong(value.generation());
            buf.writeUtf(StateWire.deathToJson(value.pendingDeaths()));
        }
    };
    public GameVisibilityS2CPayload { pendingDeaths = Map.copyOf(pendingDeaths); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
