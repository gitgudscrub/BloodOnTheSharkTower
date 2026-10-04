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
        DeathVisibility.remove(first);
        check(DeathVisibility.visible(actual, false).get(first), "individual reveal publishes selected death");
        check(!DeathVisibility.visible(actual, false).get(second), "individual reveal keeps other death private");
        check(actual.get(first) && actual.get(second), "individual reveal preserves authoritative deaths");
        check(!DeathVisibility.pending().contains(first) && DeathVisibility.pending().contains(second), "individual reveal clears only selected indicator");
        DeathVisibility.stage(first);
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
        // Exercise the same clear helper used after both reset paths/rollback.
        var role = new com.sharktower.bloodonthesharktower.core.PendingRoleAssignment(
                com.sharktower.bloodonthesharktower.core.Role.DRUNK,
                com.sharktower.bloodonthesharktower.core.AlignmentOverride.DEFAULT);
        ServerState.PLAYER_ROLES.put(first, role);
        ServerState.PLAYER_PERCEIVED_ROLES.put(first, role);
        ServerState.PLAYER_DEATH_STATUS.put(first, true);
        StorytellerState.PENDING_ROLES.put(first, role);
        StorytellerState.PENDING_PERCEIVED_ROLES.put(first, role);
        StorytellerState.REMINDERS.put(first, java.util.List.of(new com.sharktower.bloodonthesharktower.core.Reminder("Good", java.util.Optional.empty())));
        StorytellerState.DEMON_BLUFFS.add(new com.sharktower.bloodonthesharktower.core.ScriptRole.Official(com.sharktower.bloodonthesharktower.core.Role.EMPATH));
        ServerState.PLAYER_SEAT_NUMBERS.put(first, 1);
        StorytellerState.claimStoryteller(second);
        DeathVisibility.stage(first);
        long generation = ServerState.resetGeneration;
        com.sharktower.bloodonthesharktower.setup.SetupOperations.clearRolesForFreshSetup();
        check(ServerState.PLAYER_ROLES.isEmpty(), "reset clears actual roles");
        check(ServerState.PLAYER_PERCEIVED_ROLES.isEmpty(), "reset clears perceived roles");
        check(StorytellerState.PENDING_ROLES.isEmpty(), "reset clears pending roles");
        check(StorytellerState.PENDING_PERCEIVED_ROLES.isEmpty(), "reset clears pending perceived roles");
        check(StorytellerState.REMINDERS.isEmpty() && StorytellerState.DEMON_BLUFFS.isEmpty(), "reset clears reminders and bluffs");
        check(ServerState.PLAYER_DEATH_STATUS.isEmpty() && DeathVisibility.pending().isEmpty(), "reset clears staged deaths");
        check(ServerState.resetGeneration == generation + 1, "reset invalidates client notebook generation");
        check(ServerState.PLAYER_SEAT_NUMBERS.get(first) == 1, "role clearing retains seats");
        check(StorytellerState.isStoryteller(second), "role clearing retains current Storyteller");
        System.out.println("PASS: " + checks + " death visibility/reset regression checks");
    }
}
