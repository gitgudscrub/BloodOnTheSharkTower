package com.sharktower.bloodonthesharktower.serverupdate;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * After packwiz-installer has run on the server, move aside any mod JAR that
 * packwiz does not track but that has the same Fabric mod id as one it does:
 * an old Sharktower JAR or an old Fabric API copied in by hand would otherwise
 * stop Fabric from starting (duplicate mod ids). Untracked mods with their own
 * id (server-only extras) are left alone. Moved files go to {@code backupDir}.
 */
public final class ModDedupe {
    private static final Gson GSON = new Gson();

    private ModDedupe() {}

    /** @return the file names moved to {@code backupDir}. */
    public static List<String> run(Path gameDir, Path backupDir) throws IOException {
        Path mods = gameDir.resolve("mods");
        Set<Path> tracked = trackedJars(gameDir);
        Set<String> trackedIds = new HashSet<>();
        Map<Path, String> untracked = new HashMap<>();
        if (!Files.isDirectory(mods)) return List.of();
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : jars) {
                String id = modId(jar);
                if (id == null) continue;
                if (tracked.contains(jar.toAbsolutePath().normalize())) {
                    trackedIds.add(id);
                } else {
                    untracked.put(jar, id);
                }
            }
        }
        List<String> moved = new ArrayList<>();
        List<String> stuck = new ArrayList<>();
        for (Map.Entry<Path, String> entry : untracked.entrySet()) {
            if (!trackedIds.contains(entry.getValue())) continue;
            Files.createDirectories(backupDir);
            Path jar = entry.getKey();
            try {
                Files.move(jar, backupDir.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                moved.add(jar.getFileName().toString());
            } catch (IOException e) {
                stuck.add(jar.getFileName().toString());
            }
        }
        if (!stuck.isEmpty()) {
            // Linux moves files the server has open; Windows refuses, so a
            // Windows-hosted server cannot replace its own JAR while running.
            throw new IOException("Could not move the old " + String.join(", ", stuck)
                    + " out of mods (the file is in use; this happens on Windows hosts). Stop the server, delete it, then start again.");
        }
        moved.sort(String::compareTo);
        return moved;
    }

    /** JARs packwiz installed, from its {@code packwiz.json} cache. */
    static Set<Path> trackedJars(Path gameDir) throws IOException {
        Set<Path> out = new HashSet<>();
        Path meta = gameDir.resolve("packwiz.json");
        if (!Files.isRegularFile(meta)) return out;
        JsonObject root = GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), JsonObject.class);
        JsonObject cached = root != null && root.has("cachedFiles") ? root.getAsJsonObject("cachedFiles") : null;
        if (cached == null) return out;
        for (Map.Entry<String, JsonElement> entry : cached.entrySet()) {
            JsonObject file = entry.getValue().getAsJsonObject();
            if (file.has("cachedLocation")) {
                out.add(gameDir.resolve(file.get("cachedLocation").getAsString()).toAbsolutePath().normalize());
            }
        }
        return out;
    }

    static String modId(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return null;
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                return json != null && json.has("id") ? json.get("id").getAsString() : null;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
