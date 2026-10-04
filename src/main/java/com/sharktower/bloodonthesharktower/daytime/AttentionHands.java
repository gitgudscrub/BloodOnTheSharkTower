package com.sharktower.bloodonthesharktower.daytime;
import java.util.*;
/** Attention queue independent of election votes and ghost-vote eligibility. */
public final class AttentionHands {
    private static final LinkedHashSet<UUID> QUEUE = new LinkedHashSet<>();
    private AttentionHands() {}
    public static boolean raised(UUID id) { return QUEUE.contains(id); }
    public static void set(UUID id, boolean raised) { if (raised) QUEUE.add(id); else QUEUE.remove(id); }
    public static void clear() { QUEUE.clear(); }
    public static Map<UUID, Integer> positions() {
        Map<UUID, Integer> positions = new LinkedHashMap<>(); int i = 1;
        for (UUID id : QUEUE) positions.put(id, i++);
        return Map.copyOf(positions);
    }
}
