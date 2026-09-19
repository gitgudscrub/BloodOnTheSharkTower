package com.sharktower.bloodonthesharktower.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Common/server-safe access to the bundled English role strings.
 *
 * BOTB 1.21.1 resolved these through Minecraft's language singleton. For the
 * 26.2 port foundation we read the bundled language JSON directly so the role
 * model remains safe to use on both dedicated servers and clients.
 */
final class RoleText {
    private static final Map<String, String> EN_US = load();

    private RoleText() {}

    static String get(String key, String fallback) {
        return EN_US.getOrDefault(key, fallback);
    }

    private static Map<String, String> load() {
        String path = "/assets/" + BloodOnTheSharktower.MOD_ID + "/lang/en_us.json";
        try (InputStream stream = RoleText.class.getResourceAsStream(path)) {
            if (stream == null) {
                return Collections.emptyMap();
            }

            JsonElement parsed = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            if (!parsed.isJsonObject()) {
                return Collections.emptyMap();
            }

            Map<String, String> result = new HashMap<>();
            JsonObject object = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
