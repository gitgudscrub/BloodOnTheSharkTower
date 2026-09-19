package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.voicechat.DayChatZoneManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Persistent installation-level configuration for the Sharktower map.
 *
 * Match state is deliberately not stored here. This file contains only physical
 * map setup that should survive server restarts and Hard Reset:
 *  - seat-home positions
 *  - town-square seat positions
 *  - voting-clock centre/scale
 *  - daytime private-chat entrance/exit markers
 */
public final class MapConfigurationStore {
    private static final int FORMAT_VERSION = 1;
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir()
            .resolve("blood-on-the-sharktower");
    private static final Path FILE = DIRECTORY.resolve("map-setup.properties");
    private static final Path TEMP_FILE = DIRECTORY.resolve("map-setup.properties.tmp");

    private static boolean loading;

    private MapConfigurationStore() {}

    public static synchronized Path filePath() {
        return FILE;
    }

    public static synchronized void load() {
        if (!Files.isRegularFile(FILE)) {
            BloodOnTheSharktower.LOGGER.info(
                    "No saved Sharktower map setup found yet at {}. It will be created when map positions are configured.",
                    FILE.toAbsolutePath());
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
        } catch (IOException ex) {
            BloodOnTheSharktower.LOGGER.error("Could not read persistent Sharktower map setup from {}.", FILE, ex);
            return;
        }

        loading = true;
        try {
            Map<Integer, SeatPositionManager.Position> homes = new HashMap<>();
            Map<Integer, SeatPositionManager.Position> squareSeats = new HashMap<>();

            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("seat.home.")) {
                    Integer seat = parseSeat(key.substring("seat.home.".length()));
                    SeatPositionManager.Position position = parsePosition(properties.getProperty(key));
                    if (seat != null && position != null) homes.put(seat, position);
                } else if (key.startsWith("seat.square.")) {
                    Integer seat = parseSeat(key.substring("seat.square.".length()));
                    SeatPositionManager.Position position = parsePosition(properties.getProperty(key));
                    if (seat != null && position != null) squareSeats.put(seat, position);
                }
            }

            SeatPositionManager.Position clockCenter = parsePosition(properties.getProperty("clock.center"));
            double clockScale = parseDouble(properties.getProperty("clock.scale"), 4.0D);
            clockScale = Math.max(0.5D, Math.min(12.0D, clockScale));

            SeatPositionManager.restorePersistentConfiguration(new SeatPositionManager.Configuration(
                    homes, squareSeats, clockCenter, clockScale));

            Map<String, DayChatZoneManager.PersistentZone> zones = readZones(properties);
            DayChatZoneManager.restorePersistentZones(zones);

            BloodOnTheSharktower.LOGGER.info(
                    "Loaded persistent Sharktower map setup: {} home(s), {} town-square seat(s), {} private-chat area(s) from {}.",
                    homes.size(), squareSeats.size(), zones.size(), FILE.toAbsolutePath());
        } catch (RuntimeException ex) {
            BloodOnTheSharktower.LOGGER.error("Saved Sharktower map setup is malformed; valid entries were not loaded.", ex);
        } finally {
            loading = false;
        }
    }

    public static synchronized void saveQuietly() {
        if (loading) return;
        try {
            save();
        } catch (IOException ex) {
            BloodOnTheSharktower.LOGGER.error("Could not save persistent Sharktower map setup to {}.", FILE, ex);
        }
    }

    private static void save() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format.version", Integer.toString(FORMAT_VERSION));

        SeatPositionManager.Configuration seats = SeatPositionManager.snapshotConfiguration();
        for (Map.Entry<Integer, SeatPositionManager.Position> entry : new TreeMap<>(seats.seatHomes()).entrySet()) {
            properties.setProperty("seat.home." + entry.getKey(), formatPosition(entry.getValue()));
        }
        for (Map.Entry<Integer, SeatPositionManager.Position> entry : new TreeMap<>(seats.townSquareSeats()).entrySet()) {
            properties.setProperty("seat.square." + entry.getKey(), formatPosition(entry.getValue()));
        }
        if (seats.clockCenter() != null) {
            properties.setProperty("clock.center", formatPosition(seats.clockCenter()));
        }
        properties.setProperty("clock.scale", Double.toString(seats.clockHandScale()));

        Map<String, DayChatZoneManager.PersistentZone> zones = DayChatZoneManager.snapshotPersistentZones();
        for (Map.Entry<String, DayChatZoneManager.PersistentZone> entry : zones.entrySet()) {
            String prefix = "zone." + entry.getKey() + ".";
            properties.setProperty(prefix + "entrances", formatPoints(entry.getValue().entrances()));
            properties.setProperty(prefix + "exits", formatPoints(entry.getValue().exits()));
        }

        Files.createDirectories(DIRECTORY);
        try (OutputStream output = Files.newOutputStream(TEMP_FILE)) {
            properties.store(output, "Blood on the Sharktower persistent map setup - do not edit while the server is running");
        }

        try {
            Files.move(TEMP_FILE, FILE,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(TEMP_FILE, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, DayChatZoneManager.PersistentZone> readZones(Properties properties) {
        Map<String, List<DayChatZoneManager.TriggerPosition>> entrances = new TreeMap<>();
        Map<String, List<DayChatZoneManager.TriggerPosition>> exits = new TreeMap<>();

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("zone.")) continue;
            String remainder = key.substring("zone.".length());
            if (remainder.endsWith(".entrances")) {
                String name = remainder.substring(0, remainder.length() - ".entrances".length());
                entrances.put(name, parsePoints(properties.getProperty(key)));
            } else if (remainder.endsWith(".exits")) {
                String name = remainder.substring(0, remainder.length() - ".exits".length());
                exits.put(name, parsePoints(properties.getProperty(key)));
            }
        }

        Map<String, DayChatZoneManager.PersistentZone> zones = new TreeMap<>();
        java.util.Set<String> names = new java.util.TreeSet<>();
        names.addAll(entrances.keySet());
        names.addAll(exits.keySet());
        for (String name : names) {
            zones.put(name, new DayChatZoneManager.PersistentZone(
                    List.copyOf(entrances.getOrDefault(name, List.of())),
                    List.copyOf(exits.getOrDefault(name, List.of()))));
        }
        return zones;
    }

    private static String formatPoints(List<DayChatZoneManager.TriggerPosition> points) {
        if (points == null || points.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (DayChatZoneManager.TriggerPosition point : points) {
            if (point == null) continue;
            if (builder.length() > 0) builder.append(';');
            builder.append(point.x()).append(',').append(point.y()).append(',').append(point.z());
        }
        return builder.toString();
    }

    private static List<DayChatZoneManager.TriggerPosition> parsePoints(String raw) {
        List<DayChatZoneManager.TriggerPosition> points = new ArrayList<>();
        if (raw == null || raw.isBlank()) return points;
        for (String token : raw.split(";")) {
            SeatPositionManager.Position parsed = parsePosition(token);
            if (parsed != null) {
                points.add(new DayChatZoneManager.TriggerPosition(parsed.x(), parsed.y(), parsed.z()));
            }
        }
        return points;
    }

    private static String formatPosition(SeatPositionManager.Position position) {
        return position.x() + "," + position.y() + "," + position.z();
    }

    private static SeatPositionManager.Position parsePosition(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.trim().split(",");
        if (parts.length != 3) return null;
        try {
            return new SeatPositionManager.Position(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseSeat(String raw) {
        try {
            int seat = Integer.parseInt(raw);
            return seat > 0 ? seat : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
