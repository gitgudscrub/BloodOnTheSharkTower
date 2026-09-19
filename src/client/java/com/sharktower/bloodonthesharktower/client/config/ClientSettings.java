package com.sharktower.bloodonthesharktower.client.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local-only presentation preferences. Never changes authoritative game rules. */
public final class ClientSettings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("blood-on-the-sharktower-client.properties");

    public static boolean voiceHud = true;
    public static boolean roleHud = true;
    public static boolean setupHud = true;
    public static boolean playerListHud = true;
    public static boolean roleCountsHud = true;
    public static boolean worldRoleIcons = true;
    public static boolean electionHud = true;
    public static boolean handHud = true;
    public static boolean timerHud = true;

    private ClientSettings() {}

    public static void load() {
        Properties p = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            } catch (IOException ignored) {}
        }
        voiceHud = read(p, "voiceHud", true);
        roleHud = read(p, "roleHud", true);
        setupHud = read(p, "setupHud", true);
        playerListHud = read(p, "playerListHud", true);
        roleCountsHud = read(p, "roleCountsHud", true);
        worldRoleIcons = read(p, "worldRoleIcons", true);
        electionHud = read(p, "electionHud", true);
        handHud = read(p, "handHud", true);
        timerHud = read(p, "timerHud", true);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("voiceHud", Boolean.toString(voiceHud));
        p.setProperty("roleHud", Boolean.toString(roleHud));
        p.setProperty("setupHud", Boolean.toString(setupHud));
        p.setProperty("playerListHud", Boolean.toString(playerListHud));
        p.setProperty("roleCountsHud", Boolean.toString(roleCountsHud));
        p.setProperty("worldRoleIcons", Boolean.toString(worldRoleIcons));
        p.setProperty("electionHud", Boolean.toString(electionHud));
        p.setProperty("handHud", Boolean.toString(handHud));
        p.setProperty("timerHud", Boolean.toString(timerHud));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Blood on the Sharktower client settings");
            }
        } catch (IOException ignored) {}
    }

    private static boolean read(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
