package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.core.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Local Storyteller cache for server-generated triggered night visits. */
public final class ClientTriggeredNightOrder {
    private static List<Trigger> triggers = List.of();

    private ClientTriggeredNightOrder() {}

    public static void update(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            triggers = List.of();
            return;
        }

        List<Trigger> parsed = new ArrayList<>();
        for (String line : encoded.split("\\R")) {
            if (line == null || line.isBlank()) continue;
            String[] fields = line.split("\\|", -1);
            if (fields.length < 5) continue;
            try {
                int night = Integer.parseInt(fields[0]);
                UUID playerId = UUID.fromString(fields[1]);
                Role role = roleById(fields[2]);
                UUID sourceId = UUID.fromString(fields[3]);
                String cause = fields[4];
                if (night > 0 && role != null) {
                    parsed.add(new Trigger(night, playerId, role, sourceId, cause));
                }
            } catch (RuntimeException ignored) {
                // Ignore one malformed row without throwing away the rest.
            }
        }
        triggers = List.copyOf(parsed);
    }

    public static List<Trigger> forNight(int night) {
        if (night <= 0 || triggers.isEmpty()) return List.of();
        return triggers.stream().filter(trigger -> trigger.targetNight() == night).toList();
    }

    private static Role roleById(String id) {
        if (id == null) return null;
        String wanted = normalize(id);
        for (Role role : Role.values()) {
            if (normalize(role.getId()).equals(wanted)) return role;
        }
        return null;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replaceAll("[_\\s-]", "");
    }

    public record Trigger(
            int targetNight,
            UUID playerId,
            Role role,
            UUID sourcePlayerId,
            String cause
    ) {}
}
