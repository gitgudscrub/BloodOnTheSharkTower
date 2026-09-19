package com.sharktower.bloodonthesharktower.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Direct data-model port of BOTB's custom/homebrew role record.
 */
public record CustomRole(
        String id,
        String name,
        RoleType team,
        String ability,
        String flavor,
        List<String> imageUrls,
        double firstNight,
        double otherNight,
        String firstNightReminder,
        String otherNightReminder,
        List<String> reminders,
        List<String> remindersGlobal,
        boolean setup,
        List<Jinx> jinxes
) {
    public CustomRole {
        id = id == null ? "unknown" : id;
        name = name == null ? id : name;
        team = team == null ? RoleType.TOWNSFOLK : team;
        ability = ability == null ? "" : ability;
        flavor = flavor == null ? "" : flavor;
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        firstNightReminder = firstNightReminder == null ? "" : firstNightReminder;
        otherNightReminder = otherNightReminder == null ? "" : otherNightReminder;
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
        remindersGlobal = remindersGlobal == null ? List.of() : List.copyOf(remindersGlobal);
        jinxes = jinxes == null ? List.of() : List.copyOf(jinxes);
    }

    public static CustomRole fromJsonMap(Map<String, Object> map) {
        String id = asString(map.getOrDefault("id", "unknown"), "unknown");
        String name = asString(map.getOrDefault("name", id), id);
        RoleType team = parseTeam(asString(map.getOrDefault("team", "townsfolk"), "townsfolk"));
        String ability = asString(map.getOrDefault("ability", ""), "");
        String flavor = asString(map.getOrDefault("flavor", ""), "");

        List<String> imageUrls = stringList(map.get("image"));
        double firstNight = parseDouble(map.get("firstNight"), 0.0);
        double otherNight = parseDouble(map.get("otherNight"), 0.0);
        String firstNightReminder = asString(map.getOrDefault("firstNightReminder", ""), "");
        String otherNightReminder = asString(map.getOrDefault("otherNightReminder", ""), "");
        List<String> reminders = stringList(map.get("reminders"));
        List<String> remindersGlobal = stringList(map.get("remindersGlobal"));
        boolean setup = Boolean.TRUE.equals(map.get("setup"));

        List<Jinx> jinxes = new ArrayList<>();
        Object rawJinxes = map.get("jinxes");
        if (rawJinxes instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> rawMap) {
                    jinxes.add(Jinx.fromMap(castStringObjectMap(rawMap)));
                }
            }
        }

        return new CustomRole(
                id, name, team, ability, flavor, imageUrls,
                firstNight, otherNight, firstNightReminder, otherNightReminder,
                reminders, remindersGlobal, setup, jinxes
        );
    }

    public String getDisplayName() {
        return name.toUpperCase(Locale.ROOT);
    }

    public boolean isDefaultGood() {
        return team.isDefaultGood();
    }

    public String getImageUrl(boolean good) {
        if (imageUrls.isEmpty()) return "";
        if (imageUrls.size() == 1) return imageUrls.getFirst();
        if (imageUrls.size() == 2) return good ? imageUrls.get(0) : imageUrls.get(1);
        return good ? imageUrls.get(1) : imageUrls.get(2);
    }

    public String getNeutralImageUrl() {
        if (imageUrls.isEmpty()) return "";
        return imageUrls.getFirst();
    }

    public boolean wakesFirstNight() {
        return firstNight > 0.0;
    }

    public boolean wakesOtherNights() {
        return otherNight > 0.0;
    }

    private static RoleType parseTeam(String team) {
        return switch (team.toLowerCase(Locale.ROOT)) {
            case "townsfolk" -> RoleType.TOWNSFOLK;
            case "outsider" -> RoleType.OUTSIDER;
            case "minion" -> RoleType.MINION;
            case "demon" -> RoleType.DEMON;
            case "traveller", "traveler" -> RoleType.TRAVELER;
            case "fabled" -> RoleType.FABLED;
            case "loric" -> RoleType.LORIC;
            default -> RoleType.TOWNSFOLK;
        };
    }

    private static double parseDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof String string) {
            result.add(string);
        } else if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String string) result.add(string);
            }
        }
        return result;
    }

    private static Map<String, Object> castStringObjectMap(Map<?, ?> raw) {
        java.util.HashMap<String, Object> result = new java.util.HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }

    public record Jinx(String roleId, String reason) {
        public static Jinx fromMap(Map<String, Object> map) {
            return new Jinx(
                    asString(map.getOrDefault("id", ""), ""),
                    asString(map.getOrDefault("reason", ""), "")
            );
        }
    }
}
