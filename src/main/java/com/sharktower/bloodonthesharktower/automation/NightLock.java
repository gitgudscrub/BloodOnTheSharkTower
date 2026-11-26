package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.networking.NightLockS2CPayload;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Night lock: at night a seated player who reaches their house stays inside it
 * until dawn, and their client draws a thick dark fog so they cannot see out.
 * The Storyteller, the Wraith and spectators are never locked.
 *
 * A house is the area within {@link HouseVisits#ARRIVE_RADIUS} blocks (across
 * the ground) of the seat's home spot. Walking out is undone by putting the
 * player back where they last stood inside. A long jump (the Storyteller's
 * teleport) unlocks them instead, so the Storyteller can always move players.
 * The Grimoire's "Night lock" toggle switches it off.
 */
public final class NightLock {
    private static final double RADIUS = HouseVisits.ARRIVE_RADIUS;
    /** A move longer than this in one tick is a teleport, not a walk. */
    private static final double TELEPORT = 16.0D;

    /** Locked players and the last spot each stood inside their house. */
    private static final Map<UUID, Vec3> LOCKED = new HashMap<>();
    private static boolean enabled = true;

    private NightLock() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(NightLock::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LOCKED.remove(handler.player.getUUID()));
    }

    public static boolean enabled() {
        return enabled;
    }

    public static String describe() {
        return enabled
                ? "Night lock ON: at night players stay in their house once they reach it, and see only darkness outside."
                : "Night lock OFF: players can leave their houses at night.";
    }

    public static void setEnabled(MinecraftServer server, boolean on) {
        enabled = on;
        if (!on && server != null) unlockAll(server);
        if (server != null) StateBroadcaster.broadcastPrompts(server);
    }

    public static boolean locked(UUID player) {
        return LOCKED.containsKey(player);
    }

    private static void tick(MinecraftServer server) {
        if (!enabled || ServerState.gameEnded || !PhaseOperations.isNight()) {
            if (!LOCKED.isEmpty()) unlockAll(server);
            return;
        }
        for (Map.Entry<UUID, Integer> seat : Map.copyOf(ServerState.PLAYER_SEAT_NUMBERS).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(seat.getKey());
            if (player == null) continue;
            var home = SeatPositionManager.seatHome(seat.getValue());
            if (home == null || exempt(player)) {
                if (LOCKED.containsKey(player.getUUID())) unlock(player, null);
                continue;
            }
            Vec3 now = player.position();
            double dx = now.x - home.x();
            double dz = now.z - home.z();
            boolean inside = dx * dx + dz * dz <= RADIUS * RADIUS;
            Vec3 last = LOCKED.get(player.getUUID());
            if (last == null) {
                if (inside) lock(player, now);
            } else if (inside) {
                LOCKED.put(player.getUUID(), now);
            } else if (last.distanceToSqr(now) > TELEPORT * TELEPORT) {
                unlock(player, "The Storyteller moved you. You are free to move.");
            } else {
                player.teleportTo(last.x, last.y, last.z);
            }
        }
        LOCKED.keySet().removeIf(id -> !ServerState.PLAYER_SEAT_NUMBERS.containsKey(id));
    }

    private static boolean exempt(ServerPlayer player) {
        if (StorytellerState.isStoryteller(player.getUUID())) return true;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return true;
        PendingRoleAssignment role = ServerState.PLAYER_ROLES.get(player.getUUID());
        return role != null && role.role() == Role.WRAITH;
    }

    private static void lock(ServerPlayer player, Vec3 at) {
        LOCKED.put(player.getUUID(), at);
        ServerPlayNetworking.send(player, new NightLockS2CPayload(true));
        player.sendSystemMessage(Component.literal("You are locked in your house until dawn.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void unlock(ServerPlayer player, String message) {
        LOCKED.remove(player.getUUID());
        ServerPlayNetworking.send(player, new NightLockS2CPayload(false));
        if (message != null) player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void unlockAll(MinecraftServer server) {
        for (UUID id : List.copyOf(LOCKED.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) unlock(player, null);
            else LOCKED.remove(id);
        }
    }
}
