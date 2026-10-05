package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;

/** Storyteller-only snapshot of the Lunatic's separate fake bluff set. */
public record LunaticBluffsS2CPayload(List<String> roleIds) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "lunatic_bluffs"
    );
    public static final Type<LunaticBluffsS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, LunaticBluffsS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public LunaticBluffsS2CPayload decode(RegistryFriendlyByteBuf buf) {
            String encoded = buf.readUtf();
            if (encoded.isBlank()) return new LunaticBluffsS2CPayload(List.of());
            return new LunaticBluffsS2CPayload(Arrays.stream(encoded.split("\\|", -1))
                    .filter(value -> !value.isBlank())
                    .toList());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LunaticBluffsS2CPayload value) {
            buf.writeUtf(String.join("|", value.roleIds()));
        }
    };

    public LunaticBluffsS2CPayload {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
