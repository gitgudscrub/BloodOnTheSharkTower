package com.sharktower.bloodonthesharktower.setup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Functional port of BOTB's seat-home / town-square teleport configuration.
 *
 * 0.7.0 keeps this in memory; persistent server-config JSON is intentionally
 * left for the config/world integration pass. The behavior mirrors the
 * original controls closely enough to operate a real test game now.
 */
public final class SeatPositionManager {
    public record Position(double x, double y, double z) {}

    /** Immutable copy used by the A.6 match-start snapshot. */
    public record Configuration(
            Map<Integer, Position> seatHomes,
            Map<Integer, Position> townSquareSeats,
            Position clockCenter,
            double clockHandScale
    ) {}

    private static final Map<Integer, Position> SEAT_HOMES = new HashMap<>();
    private static final Map<Integer, Position> TOWN_SQUARE_SEATS = new HashMap<>();
    private static Position CLOCK_CENTER = null;
    private static double CLOCK_HAND_SCALE = 4.0D;

    private SeatPositionManager() {}

    public static void setSeatHome(int seat, ServerPlayer player) {
        SEAT_HOMES.put(seat, currentPosition(player));
        MapConfigurationStore.saveQuietly();
    }

    public static void setTownSquareSeat(int seat, ServerPlayer player) {
        TOWN_SQUARE_SEATS.put(seat, currentPosition(player));
        MapConfigurationStore.saveQuietly();
    }

    public static void setClockCenter(ServerPlayer player) {
        CLOCK_CENTER = currentPosition(player);
        MapConfigurationStore.saveQuietly();
    }

    public static void setClockHandScale(double scale) {
        CLOCK_HAND_SCALE = Math.max(0.5D, Math.min(12.0D, scale));
        MapConfigurationStore.saveQuietly();
    }

    public static double clockHandScale() {
        return CLOCK_HAND_SCALE;
    }

    /** Explicit center if configured, otherwise the average configured town-square seat position. */
    public static Position clockCenter() {
        if (CLOCK_CENTER != null) return CLOCK_CENTER;
        if (TOWN_SQUARE_SEATS.isEmpty()) return null;
        double x = 0.0D, y = 0.0D, z = 0.0D;
        for (Position position : TOWN_SQUARE_SEATS.values()) {
            x += position.x();
            y += position.y();
            z += position.z();
        }
        double count = TOWN_SQUARE_SEATS.size();
        return new Position(x / count, y / count, z / count);
    }

    public static Position seatHome(int seat) {
        return SEAT_HOMES.get(seat);
    }

    public static Position townSquareSeat(int seat) {
        return TOWN_SQUARE_SEATS.get(seat);
    }

    public static int configuredSeatHomes() {
        return SEAT_HOMES.size();
    }

    public static int configuredTownSquareSeats() {
        return TOWN_SQUARE_SEATS.size();
    }

    public static Configuration snapshotConfiguration() {
        return new Configuration(
                new HashMap<>(SEAT_HOMES),
                new HashMap<>(TOWN_SQUARE_SEATS),
                CLOCK_CENTER,
                CLOCK_HAND_SCALE
        );
    }

    public static void restoreConfiguration(Configuration configuration) {
        applyConfiguration(configuration);
        MapConfigurationStore.saveQuietly();
    }

    /** Used only by persistent-map loading; avoids writing the file back while it is being read. */
    static void restorePersistentConfiguration(Configuration configuration) {
        applyConfiguration(configuration);
    }

    private static void applyConfiguration(Configuration configuration) {
        if (configuration == null) return;
        SEAT_HOMES.clear();
        SEAT_HOMES.putAll(configuration.seatHomes());
        TOWN_SQUARE_SEATS.clear();
        TOWN_SQUARE_SEATS.putAll(configuration.townSquareSeats());
        CLOCK_CENTER = configuration.clockCenter();
        CLOCK_HAND_SCALE = configuration.clockHandScale();
    }

    public static int sendAllToTownSquare(MinecraftServer server, Map<UUID, Integer> seats) {
        int moved = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer seat = seats.get(player.getUUID());
            if (seat == null) continue;
            Position position = TOWN_SQUARE_SEATS.get(seat);
            if (position == null) continue;
            teleport(player, position);
            moved++;
        }
        return moved;
    }

    public static int sendAllHome(MinecraftServer server, Map<UUID, Integer> seats) {
        int moved = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer seat = seats.get(player.getUUID());
            if (seat == null) continue;
            Position position = SEAT_HOMES.get(seat);
            if (position == null) continue;
            teleport(player, position);
            moved++;
        }
        return moved;
    }

    public static boolean teleportPlayerToHome(ServerPlayer player, int seat) {
        Position position = SEAT_HOMES.get(seat);
        if (position == null) return false;
        teleport(player, position);
        return true;
    }

    public static boolean teleportPlayerToTownSquare(ServerPlayer player, int seat) {
        Position position = TOWN_SQUARE_SEATS.get(seat);
        if (position == null) return false;
        teleport(player, position);
        return true;
    }

    /** True when the player is still within the configured house area for a seat. */
    public static boolean isPlayerNearHome(ServerPlayer player, int seat, double radius) {
        Position position = SEAT_HOMES.get(seat);
        if (player == null || position == null || radius < 0.0D) return false;
        double dx = player.getX() - position.x();
        double dy = player.getY() - position.y();
        double dz = player.getZ() - position.z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static void clear() {
        SEAT_HOMES.clear();
        TOWN_SQUARE_SEATS.clear();
        CLOCK_CENTER = null;
        CLOCK_HAND_SCALE = 4.0D;
        MapConfigurationStore.saveQuietly();
    }

    private static void teleport(ServerPlayer player, Position position) {
        if (player == null || position == null) return;
        // Cushion/sitting mods normally represent sitting as riding a seat
        // entity. Vanilla refuses or immediately corrects some teleports while
        // the player is still mounted, so detach first.
        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(position.x(), position.y(), position.z());
    }

    private static Position currentPosition(ServerPlayer player) {
        return new Position(player.getX(), player.getY(), player.getZ());
    }
}
