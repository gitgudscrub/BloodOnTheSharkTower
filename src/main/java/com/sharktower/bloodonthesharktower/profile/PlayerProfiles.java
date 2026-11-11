package com.sharktower.bloodonthesharktower.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, cosmetic per-player profile (currently pronouns only). Keyed by
 * UUID and stored in the server config directory rather than the world, so a
 * player's choice survives world resets and server restarts. Everything here is
 * public to all players and must never encode role, alignment or any other
 * hidden Clocktower information.
 */
public final class PlayerProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final java.lang.reflect.Type FILE_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static final Map<UUID, Entry> PROFILES = new LinkedHashMap<>();
    private static Path path;

    private PlayerProfiles() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerProfiles::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            save();
            PROFILES.clear();
            path = null;
        });
    }

    public static String pronouns(UUID playerId) {
        Entry entry = PROFILES.get(playerId);
        return entry == null || entry.pronouns == null ? "" : entry.pronouns;
    }

    /** Pronouns for every stored player; callers filter to the players they care about. */
    public static Map<UUID, String> allPronouns() {
        Map<UUID, String> result = new LinkedHashMap<>();
        PROFILES.forEach((id, entry) -> {
            if (entry != null && entry.pronouns != null && !entry.pronouns.isEmpty()) result.put(id, entry.pronouns);
        });
        return result;
    }

    /** Stores already-validated pronouns (normalised here). Returns false if nothing changed. */
    public static boolean setPronouns(UUID playerId, String pronouns) {
        String value = Pronouns.normalise(pronouns);
        if (value.isEmpty()) return clearPronouns(playerId);
        Entry entry = PROFILES.computeIfAbsent(playerId, id -> new Entry());
        if (value.equals(entry.pronouns)) return false;
        entry.pronouns = value;
        save();
        return true;
    }

    public static boolean clearPronouns(UUID playerId) {
        Entry entry = PROFILES.get(playerId);
        if (entry == null || entry.pronouns == null || entry.pronouns.isEmpty()) return false;
        entry.pronouns = null;
        if (entry.isEmpty()) PROFILES.remove(playerId);
        save();
        return true;
    }

    private static void load(MinecraftServer server) {
        PROFILES.clear();
        path = FabricLoader.getInstance().getConfigDir()
                .resolve("blood_on_the_sharktower/player-profiles.json").toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) return;
            Map<String, Entry> read = GSON.fromJson(Files.readString(path), FILE_TYPE);
            if (read == null) return;
            read.forEach((id, entry) -> {
                if (entry == null) return;
                try {
                    UUID playerId = UUID.fromString(id);
                    if (entry.pronouns != null && !Pronouns.isValid(entry.pronouns)) entry.pronouns = null;
                    if (!entry.isEmpty()) PROFILES.put(playerId, entry);
                } catch (IllegalArgumentException ignored) {}
            });
        } catch (Exception e) {
            BloodOnTheSharktower.LOGGER.warn("Could not load player profiles", e);
        }
    }

    private static void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Map<String, Entry> out = new LinkedHashMap<>();
            PROFILES.forEach((id, entry) -> out.put(id.toString(), entry));
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(out, FILE_TYPE));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            BloodOnTheSharktower.LOGGER.warn("Could not save player profiles", e);
        }
    }

    /** One stored profile. Fields are nullable so future cosmetic options can be added. */
    private static final class Entry {
        String pronouns;

        boolean isEmpty() {
            return pronouns == null || pronouns.isEmpty();
        }
    }
}
