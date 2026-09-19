package com.sharktower.bloodonthesharktower.setup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Bundled official Base 3 scripts.
 *
 * They intentionally live under data/blood_on_the_sharktower/scripts/base3 so
 * they stay separate from Storyteller-built/custom scripts. The client sends
 * only a short key; the server loads the bundled resource authoritatively.
 */
public final class BaseThreeScripts {
    private static final String ROOT = "data/blood_on_the_sharktower/scripts/base3/";

    private BaseThreeScripts() {}

    public static SetupOperations.Result load(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .replace("&", "and");

        String file = switch (normalized) {
            case "tb", "troublebrewing" -> "trouble_brewing.json";
            case "bmr", "badmoonrising" -> "bad_moon_rising.json";
            case "snv", "sav", "sectsandviolets", "sectsandviolet" -> "sects_and_violets.json";
            default -> null;
        };

        if (file == null) {
            return SetupOperations.Result.fail("Unknown Base 3 script: " + key);
        }

        String path = ROOT + file;
        try (InputStream stream = BaseThreeScripts.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return SetupOperations.Result.fail("Bundled Base 3 script is missing: " + file);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return SetupOperations.loadScriptJson(json);
        } catch (IOException exception) {
            return SetupOperations.Result.fail("Could not read bundled Base 3 script: " + exception.getMessage());
        }
    }
}
