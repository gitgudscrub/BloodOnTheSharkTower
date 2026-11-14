package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.networking.SeatPickS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Night choices a player can make by clicking a seat in their own Grimoire,
 * alongside the clickable seat list in chat. The server remembers the open
 * choice so a player who reconnects still sees it.
 */
public final class SeatPicks {
    private static final Map<UUID, SeatPickS2CPayload> OPEN = new HashMap<>();
    private static MinecraftServer server;

    private SeatPicks() {}

    /** Lets {@code player} choose by clicking a seat; their pick runs {@code command + seat}. */
    static void offer(MinecraftServer server, UUID player, String title, String command, UUID exclude) {
        if (server == null || player == null) return;
        SeatPicks.server = server;
        Integer seat = exclude == null ? null : com.sharktower.bloodonthesharktower.states.ServerState.PLAYER_SEAT_NUMBERS.get(exclude);
        SeatPickS2CPayload payload = new SeatPickS2CPayload(title, command, seat == null ? 0 : seat);
        OPEN.put(player, payload);
        send(player, payload);
    }

    /** Closes the choice for {@code player}, if one is open. */
    static void clear(UUID player) {
        if (player == null || OPEN.remove(player) == null) return;
        send(player, SeatPickS2CPayload.none());
    }

    static void clearAll() {
        for (UUID player : Map.copyOf(OPEN).keySet()) clear(player);
    }

    /** On join: re-send the open choice, or clear a stale one the client may still show. */
    public static void sendTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, OPEN.getOrDefault(player.getUUID(), SeatPickS2CPayload.none()));
    }

    private static void send(UUID player, SeatPickS2CPayload payload) {
        if (server == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) ServerPlayNetworking.send(online, payload);
    }
}
