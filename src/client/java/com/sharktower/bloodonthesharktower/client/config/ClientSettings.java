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
    public static boolean pronounTags = true;
    /** One-time migration: the vanilla Friends List key (O) was unbound in favour of Sharktower Settings. */
    public static boolean friendsKeyReleased = false;
    public static boolean electionHud = true;
    public static boolean handHud = true;
    public static boolean timerHud = true;
    /** Role names under Grimoire tokens, so roles can be read without hovering. */
    public static boolean grimoireLabels = true;
    /** "G" / "E" letters beside alignment colours, so alignment never relies on colour alone. */
    public static boolean alignmentLetters = true;
    /** "candlelit" (dark) or "parchment" (light); see UiTheme. */
    public static String theme = "candlelit";
    /** Smaller role, nomination and hand cards, so players can play with them open. */
    public static boolean compactCards = true;
    /** Set once the voice group list has been moved to the row above the role card (see VoiceGroupHud). */
    public static boolean voiceIconsMoved = false;
    /** Size of the whole Sharktower HUD; one of HudScale.STEPS. */
    public static float hudScale = 1.0F;

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
        pronounTags = read(p, "pronounTags", true);
        friendsKeyReleased = read(p, "friendsKeyReleased", false);
        electionHud = read(p, "electionHud", true);
        handHud = read(p, "handHud", true);
        timerHud = read(p, "timerHud", true);
        grimoireLabels = read(p, "grimoireLabels", true);
        alignmentLetters = read(p, "alignmentLetters", true);
        theme = p.getProperty("theme", "candlelit");
        compactCards = read(p, "compactCards", true);
        voiceIconsMoved = read(p, "voiceIconsMoved", false);
        try {
            hudScale = Float.parseFloat(p.getProperty("hudScale", "1.0"));
        } catch (NumberFormatException ex) {
            hudScale = 1.0F;
        }
        com.sharktower.bloodonthesharktower.client.gui.UiTheme.apply();
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("voiceHud", Boolean.toString(voiceHud));
        p.setProperty("roleHud", Boolean.toString(roleHud));
        p.setProperty("setupHud", Boolean.toString(setupHud));
        p.setProperty("playerListHud", Boolean.toString(playerListHud));
        p.setProperty("roleCountsHud", Boolean.toString(roleCountsHud));
        p.setProperty("worldRoleIcons", Boolean.toString(worldRoleIcons));
        p.setProperty("pronounTags", Boolean.toString(pronounTags));
        p.setProperty("friendsKeyReleased", Boolean.toString(friendsKeyReleased));
        p.setProperty("electionHud", Boolean.toString(electionHud));
        p.setProperty("handHud", Boolean.toString(handHud));
        p.setProperty("timerHud", Boolean.toString(timerHud));
        p.setProperty("grimoireLabels", Boolean.toString(grimoireLabels));
        p.setProperty("alignmentLetters", Boolean.toString(alignmentLetters));
        p.setProperty("theme", theme);
        p.setProperty("compactCards", Boolean.toString(compactCards));
        p.setProperty("voiceIconsMoved", Boolean.toString(voiceIconsMoved));
        p.setProperty("hudScale", Float.toString(hudScale));
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
