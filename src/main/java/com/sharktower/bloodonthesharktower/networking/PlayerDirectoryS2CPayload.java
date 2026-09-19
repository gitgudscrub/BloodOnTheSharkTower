package com.sharktower.bloodonthesharktower.networking;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small 0.8.1 directory packet used by the original-style Grimoire.
 * Carries connected player names plus the connected/storyteller UUID sets so
 * the client can render real names, player faces, and the unseated list.
 */
public record PlayerDirectoryS2CPayload(
        Map<UUID, String> names,
        List<UUID> connectedPlayers,
        List<UUID> storytellers
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "player_directory"
    );
    public static final Type<PlayerDirectoryS2CPayload> TYPE = new Type<>(ID_VALUE);

    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type WIRE_TYPE = new TypeToken<Wire>() {}.getType();

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDirectoryS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public PlayerDirectoryS2CPayload decode(RegistryFriendlyByteBuf buf) {
            Wire wire = GSON.fromJson(buf.readUtf(), WIRE_TYPE);
            if (wire == null) return new PlayerDirectoryS2CPayload(Map.of(), List.of(), List.of());
            Map<UUID, String> names = new LinkedHashMap<>();
            if (wire.names != null) {
                wire.names.forEach((id, name) -> {
                    try { names.put(UUID.fromString(id), name == null ? "" : name); }
                    catch (IllegalArgumentException ignored) {}
                });
            }
            return new PlayerDirectoryS2CPayload(names, parseIds(wire.connected), parseIds(wire.storytellers));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerDirectoryS2CPayload value) {
            Map<String, String> names = new LinkedHashMap<>();
            value.names().forEach((id, name) -> names.put(id.toString(), name));
            Wire wire = new Wire(
                    names,
                    value.connectedPlayers().stream().map(UUID::toString).toList(),
                    value.storytellers().stream().map(UUID::toString).toList()
            );
            buf.writeUtf(GSON.toJson(wire));
        }
    };

    public PlayerDirectoryS2CPayload {
        names = Map.copyOf(names);
        connectedPlayers = List.copyOf(connectedPlayers);
        storytellers = List.copyOf(storytellers);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static List<UUID> parseIds(List<String> values) {
        if (values == null) return List.of();
        List<UUID> result = new ArrayList<>();
        for (String value : values) {
            try { result.add(UUID.fromString(value)); }
            catch (IllegalArgumentException ignored) {}
        }
        return List.copyOf(result);
    }

    private record Wire(Map<String, String> names, List<String> connected, List<String> storytellers) {}
}
