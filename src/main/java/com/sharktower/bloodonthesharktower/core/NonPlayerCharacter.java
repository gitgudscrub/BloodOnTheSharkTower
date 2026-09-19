package com.sharktower.bloodonthesharktower.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of BOTB's non-player-character model used for custom Fabled and Loric.
 */
public record NonPlayerCharacter(
        String id,
        String name,
        FabledType type,
        String ability,
        String flavor,
        String imageUrl,
        List<String> reminders,
        List<String> remindersGlobal
) {
    public NonPlayerCharacter {
        id = id == null ? "unknown" : id;
        name = name == null ? id : name;
        type = type == null ? FabledType.FABLED : type;
        ability = ability == null ? "" : ability;
        flavor = flavor == null ? "" : flavor;
        imageUrl = imageUrl == null ? "" : imageUrl;
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
        remindersGlobal = remindersGlobal == null ? List.of() : List.copyOf(remindersGlobal);
    }

    public static NonPlayerCharacter fromJsonMap(Map<String, Object> map, FabledType type) {
        String id = asString(map.getOrDefault("id", "unknown"), "unknown");
        String name = asString(map.getOrDefault("name", id), id);
        String ability = asString(map.getOrDefault("ability", ""), "");
        String flavor = asString(map.getOrDefault("flavor", ""), "");

        String imageUrl = "";
        Object image = map.get("image");
        if (image instanceof String string) {
            imageUrl = string;
        } else if (image instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof String string) {
            imageUrl = string;
        }

        return new NonPlayerCharacter(
                id,
                name,
                type,
                ability,
                flavor,
                imageUrl,
                stringList(map.get("reminders")),
                stringList(map.get("remindersGlobal"))
        );
    }

    public String getDisplayName() {
        return name.toUpperCase(Locale.ROOT);
    }

    public boolean isFabled() {
        return type == FabledType.FABLED;
    }

    public boolean isLoric() {
        return type == FabledType.LORIC;
    }

    public RoleType getRoleType() {
        return isFabled() ? RoleType.FABLED : RoleType.LORIC;
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String string) result.add(string);
            }
        }
        return result;
    }

    public enum FabledType {
        FABLED,
        LORIC
    }
}
