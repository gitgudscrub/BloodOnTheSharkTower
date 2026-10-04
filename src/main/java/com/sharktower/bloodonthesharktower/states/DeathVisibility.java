package com.sharktower.bloodonthesharktower.states;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Public presentation is independent of the authoritative death map used by rules. */
public final class DeathVisibility {
    private static final Set<UUID> PENDING = new HashSet<>();
    private DeathVisibility() {}
    public static void stage(UUID player) { PENDING.add(player); }
    public static void remove(UUID player) { PENDING.remove(player); }
    public static void clear() { PENDING.clear(); }
    public static Set<UUID> pending() { return Set.copyOf(PENDING); }
    public static Map<UUID, Boolean> visible(Map<UUID, Boolean> actual, boolean privileged) {
        Map<UUID, Boolean> visible = new HashMap<>(actual);
        if (!privileged) for (UUID player : PENDING) {
            if (visible.containsKey(player)) visible.put(player, false);
        }
        return Map.copyOf(visible);
    }
}
