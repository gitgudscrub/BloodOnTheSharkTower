package com.sharktower.bloodonthesharktower.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharktower.bloodonthesharktower.core.CustomRole;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Script-sheet reference data, independent of which characters are in play.
 *
 * The official night sheet and jinxes are bundled so this also works offline.
 * Explicit _meta night-order arrays override the bundled order. Homebrew role
 * priorities and homebrew jinxes come from the loaded script itself.
 */
public final class ScriptReferenceData {
    private static final String ROOT = "/data/blood_on_the_sharktower/reference/";
    private static final JsonElement NIGHT_SHEET = load("night_order.json");
    private static final JsonElement OFFICIAL_JINXES = load("jinxes.json");

    public record Jinx(String first, String second, String reason) {}
    private record NightEntry(String id, double priority, int position) {}

    private ScriptReferenceData() {}

    public static List<String> nightOrder(Script script, boolean firstNight) {
        if (script == null) return List.of();
        List<String> override = firstNight ? script.firstNightOrder() : script.otherNightOrder();
        if (override != null && !override.isEmpty()) {
            return formatOrder(script, override);
        }

        List<String> official = strings(array(NIGHT_SHEET, firstNight ? "firstNight" : "otherNight"));
        List<NightEntry> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < official.size(); i++) {
            String id = official.get(i);
            if (shouldDisplay(script, id) && seen.add(normalize(id))) {
                ordered.add(new NightEntry(id, i, i));
            }
        }

        // Official positions are from the published night sheet; user-authored
        // roles supply numeric firstNight/otherNight positions. Do not invent
        // wake-ups for custom characters with a priority of zero.
        int position = official.size();
        for (CustomRole custom : script.allCustomRoles()) {
            String id = custom.id();
            double priority = firstNight ? custom.firstNight() : custom.otherNight();
            if (!Double.isFinite(priority) || priority <= 0 || !seen.add(normalize(id))) continue;
            // Keep custom actions inside the night, not beyond the Dawn marker.
            int dawn = official.indexOf("dawn");
            double maxPosition = dawn > 1 ? dawn - 0.01 : official.size();
            ordered.add(new NightEntry(id, Math.min(priority, maxPosition), position++));
        }
        ordered.sort(Comparator.comparingDouble(NightEntry::priority)
                .thenComparingInt(NightEntry::position));
        List<String> ids = new ArrayList<>(ordered.size());
        for (NightEntry entry : ordered) ids.add(entry.id());
        return formatOrder(script, ids);
    }

    private static List<String> formatOrder(Script script, List<String> ids) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (id == null || !seen.add(normalize(id))) continue;
            String key = normalize(id);
            switch (key) {
                case "dusk" -> result.add("Dusk");
                case "dawn" -> result.add("Dawn");
                case "minioninfo" -> result.add("Minion Information");
                case "demoninfo" -> result.add("Demon Information");
                default -> script.getScriptRole(id).ifPresent(role ->
                        result.add(role.getDisplayName()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean shouldDisplay(Script script, String id) {
        String key = normalize(id);
        return key.equals("dusk") || key.equals("dawn")
                || key.equals("minioninfo") || key.equals("demoninfo")
                || script.getScriptRole(id).isPresent();
    }

    public static List<Jinx> jinxes(Script script) {
        if (script == null) return List.of();
        Map<String, Jinx> pairs = new LinkedHashMap<>();

        // Script-defined jinxes take precedence over the default rules when
        // both sources describe the same character pair.
        for (CustomRole role : script.allCustomRoles()) {
            for (CustomRole.Jinx jinx : role.jinxes()) {
                addJinx(script, pairs, role.id(), jinx.roleId(), jinx.reason());
            }
        }
        JsonArray source = OFFICIAL_JINXES.isJsonArray() ? OFFICIAL_JINXES.getAsJsonArray() : new JsonArray();
        for (JsonElement item : source) {
            if (!item.isJsonObject()) continue;
            JsonObject character = item.getAsJsonObject();
            String id = string(character, "id");
            JsonArray jinxes = array(character, "jinx");
            for (JsonElement pair : jinxes) {
                if (!pair.isJsonObject()) continue;
                JsonObject jinx = pair.getAsJsonObject();
                addJinx(script, pairs, id, string(jinx, "id"), string(jinx, "reason"));
            }
        }
        List<Jinx> result = new ArrayList<>(pairs.values());
        result.sort(Comparator.comparing(Jinx::first, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Jinx::second, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static void addJinx(Script script, Map<String, Jinx> pairs,
                                String firstId, String secondId, String reason) {
        if (firstId == null || secondId == null || reason == null || reason.isBlank()) return;
        ScriptRole first = script.getScriptRole(firstId).orElse(null);
        ScriptRole second = script.getScriptRole(secondId).orElse(null);
        if (first == null || second == null) return;
        String a = normalize(first.getId());
        String b = normalize(second.getId());
        if (a.equals(b)) return;
        String key = a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
        pairs.putIfAbsent(key, new Jinx(first.getDisplayName(), second.getDisplayName(), reason.trim()));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<String> strings(JsonArray array) {
        List<String> items = new ArrayList<>();
        for (JsonElement item : array) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                items.add(item.getAsString());
            }
        }
        return items;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static JsonArray array(JsonElement root, String key) {
        if (!root.isJsonObject()) return new JsonArray();
        JsonElement value = root.getAsJsonObject().get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonElement load(String filename) {
        try (InputStream stream = ScriptReferenceData.class.getResourceAsStream(ROOT + filename)) {
            if (stream == null) return new JsonObject();
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        } catch (IOException | RuntimeException exception) {
            return new JsonObject();
        }
    }
}
