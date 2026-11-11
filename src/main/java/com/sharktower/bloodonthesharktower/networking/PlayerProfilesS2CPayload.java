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
 * Public cosmetic profile data for connected players: pronouns and nicknames
 * keyed by UUID. Sent to everyone on join and whenever a player changes them. It
 * never carries anything a player could not already see.
 */
public record PlayerProfilesS2CPayload(Map<UUID, String> pronouns, Map<UUID, String> nicknames)
        implements CustomPacketPayload {
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
            return new PlayerProfilesS2CPayload(
                    readMap(wire == null ? null : wire.pronouns),
                    readMap(wire == null ? null : wire.nicknames));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerProfilesS2CPayload value) {
            buf.writeUtf(GSON.toJson(new Wire(writeMap(value.pronouns()), writeMap(value.nicknames()))));
        }
    };

    public PlayerProfilesS2CPayload {
        pronouns = Map.copyOf(pronouns);
        nicknames = nicknames == null ? Map.of() : Map.copyOf(nicknames);
    }

    private static Map<UUID, String> readMap(Map<String, String> wire) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (wire == null) return out;
        wire.forEach((id, text) -> {
            if (text == null || text.isBlank()) return;
            try { out.put(UUID.fromString(id), text); }
            catch (IllegalArgumentException ignored) {}
        });
        return out;
    }

    private static Map<String, String> writeMap(Map<UUID, String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((id, text) -> out.put(id.toString(), text));
        return out;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private record Wire(Map<String, String> pronouns, Map<String, String> nicknames) {}
}
