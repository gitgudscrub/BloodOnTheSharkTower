package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.Script;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 26.2 port of the original BOTB SendScriptS2CPayload.
 *
 * The Script codec carries the original raw script JSON using the same
 * compressed transport strategy as the 1.21.1 mod.
 */
public record SendScriptS2CPayload(Script script) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "send_script"
    );

    public static final Type<SendScriptS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, SendScriptS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public SendScriptS2CPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SendScriptS2CPayload(Script.PACKET_CODEC.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SendScriptS2CPayload payload) {
            Script.PACKET_CODEC.encode(buffer, payload.script());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
