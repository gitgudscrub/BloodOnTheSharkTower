package com.sharktower.bloodonthesharktower.networking;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public cosmetic profile data for connected players: pronouns keyed by UUID.
 * Sent to everyone on join and whenever a player changes their pronouns. It
 * never carries anything a player could not already see.
 */
public record PlayerProfilesS2CPayload(Map<UUID, String> pronouns) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "player_profiles"
    );
    public static final Type<PlayerProfilesS2CPayload> TYPE = new Type<>(ID_VALUE);

    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type WIRE_TYPE = new TypeToken<Wire>() {}.getType();

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerProfilesS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public PlayerProfilesS2CPayload decode(RegistryFriendlyByteBuf buf) {
            Wire wire = GSON.fromJson(buf.readUtf(), WIRE_TYPE);
            Map<UUID, String> pronouns = new LinkedHashMap<>();
            if (wire != null && wire.pronouns != null) {
                wire.pronouns.forEach((id, text) -> {
                    if (text == null || text.isBlank()) return;
                    try { pronouns.put(UUID.fromString(id), text); }
                    catch (IllegalArgumentException ignored) {}
                });
            }
            return new PlayerProfilesS2CPayload(pronouns);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerProfilesS2CPayload value) {
            Map<String, String> pronouns = new LinkedHashMap<>();
            value.pronouns().forEach((id, text) -> pronouns.put(id.toString(), text));
            buf.writeUtf(GSON.toJson(new Wire(pronouns)));
        }
    };

    public PlayerProfilesS2CPayload {
        pronouns = Map.copyOf(pronouns);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private record Wire(Map<String, String> pronouns) {}
}
