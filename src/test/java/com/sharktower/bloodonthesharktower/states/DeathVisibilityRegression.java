package com.sharktower.bloodonthesharktower.states;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Plain Java regression executable: no client bootstrap or test framework required. */
public final class DeathVisibilityRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Map<UUID, Boolean> actual = new HashMap<>(Map.of(first, true, second, false));
        DeathVisibility.clear();
        DeathVisibility.stage(first);
        check(actual.get(first), "staging must preserve authoritative death");
        check(!DeathVisibility.visible(actual, false).get(first), "ordinary/reconnect sync masks death");
        check(DeathVisibility.visible(actual, true).get(first), "Storyteller sees real death");
        check(!DeathVisibility.visible(actual, false).get(second), "living players remain living");
        check(DeathVisibility.pending().size() == 1, "pending indicators");
        DeathVisibility.stage(first);
        check(DeathVisibility.pending().size() == 1, "staging is idempotent");
        actual.put(second, true); DeathVisibility.stage(second);
        check(DeathVisibility.visible(actual, false).values().stream().noneMatch(Boolean.TRUE::equals), "multiple deaths hidden");
        DeathVisibility.remove(first); actual.put(first, false);
        check(!DeathVisibility.visible(actual, true).get(first), "correction revives immediately");
        check(!DeathVisibility.pending().contains(first), "correction clears pending indicator");
        check(DeathVisibility.pending().contains(second), "correction preserves other pending death");
        DeathVisibility.clear();
        check(DeathVisibility.visible(actual, false).get(second), "manual reveal publishes death");
        check(DeathVisibility.pending().isEmpty(), "reveal/reset clears pending");
        Map<UUID, Boolean> snapshot = DeathVisibility.visible(actual, false);
        actual.clear();
        check(snapshot.size() == 2, "payload snapshot remains immutable after reset");
        check(DeathVisibility.visible(actual, false).isEmpty(), "reset cannot repopulate roles/deaths");
        System.out.println("PASS: " + checks + " death visibility regression checks");
    }
}
