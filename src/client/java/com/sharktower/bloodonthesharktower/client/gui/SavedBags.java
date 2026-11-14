package com.sharktower.bloodonthesharktower.client.gui;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Role Bags the Storyteller saved, one per script and player count, kept in
 * the client's config folder so a rematch or the next session can reuse them.
 */
final class SavedBags {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("blood_on_the_sharktower_bags.properties");

    private SavedBags() {}

    private static String key(String script, int players) {
        return script.trim().toLowerCase(java.util.Locale.ROOT) + "|" + players;
    }

    private static Properties read() {
        Properties p = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            } catch (IOException ignored) {
            }
        }
        return p;
    }

    /** The saved token ids for this script and player count, or an empty list. */
    static List<String> load(String script, int players) {
        String value = read().getProperty(key(script, players), "");
        if (value.isBlank()) return List.of();
        return new ArrayList<>(Arrays.asList(value.split(",")));
    }

    static boolean has(String script, int players) {
        return !load(script, players).isEmpty();
    }

    static boolean save(String script, int players, List<String> ids) {
        Properties p = read();
        p.setProperty(key(script, players), String.join(",", ids));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Blood on the Sharktower saved Role Bags (script|players = token ids)");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
