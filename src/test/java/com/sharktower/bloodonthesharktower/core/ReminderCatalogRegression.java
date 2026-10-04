package com.sharktower.bloodonthesharktower.core;

import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import java.util.UUID;

public final class ReminderCatalogRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Script script = Script.fromJson("[\"tinker\",\"poisoner\",\"monk\",\"slayer\",\"preacher\"]").orElseThrow();
        var tinker = new PendingRoleAssignment(Role.TINKER, AlignmentOverride.DEFAULT);
        var monk = new PendingRoleAssignment(Role.MONK, AlignmentOverride.DEFAULT);
        var tinkerOptions = ReminderCatalog.forScript(script, tinker);
        var monkOptions = ReminderCatalog.forScript(script, monk);
        check(tinkerOptions.stream().anyMatch(o -> o.text().equals("Tinker Death")), "Tinker has death reminder");
        check(monkOptions.stream().noneMatch(o -> o.text().equals("Tinker Death")), "non-Tinker excludes Tinker death");
        check(monkOptions.stream().anyMatch(o -> o.sourceId().equals(Role.POISONER.getId()) && o.text().equals("Poisoned")), "Poisoner targets other characters");
        check(monkOptions.stream().noneMatch(o -> o.sourceId().equals(Role.SLAYER.getId())), "Slayer spent ability restricted to Slayer");
        check(monkOptions.stream().anyMatch(o -> o.sourceId().equals(Role.PREACHER.getId()) && o.text().equals("No Ability")), "Preacher may affect others");
        check(monkOptions.stream().noneMatch(o -> o.sourceId().equals(Role.FORTUNE_TELLER.getId())), "off-script reminders hidden");
        check(ReminderCatalog.forScript(null, monk).isEmpty(), "no script has no character reminders");
        check(ReminderCatalog.generic().stream().allMatch(o -> o.sourceId().isBlank()), "generic reminders have no source character");
        Script custom = Script.fromJson("[{\"id\":\"customtest\",\"name\":\"Custom Test\",\"team\":\"townsfolk\",\"ability\":\"Test\",\"reminders\":[\"Chosen\",\"Chosen\"],\"remindersGlobal\":[\"Global\"]}]").orElseThrow();
        var customOptions = ReminderCatalog.forScript(custom, monk);
        check(customOptions.stream().filter(o -> o.text().equals("Chosen")).count() == 1, "custom labels deduplicated");
        check(customOptions.stream().anyMatch(o -> o.text().equals("Global")), "custom global reminders supported");
        UUID player = UUID.randomUUID();
        ServerState.currentScript = script;
        ServerState.PLAYER_SEAT_NUMBERS.put(player, 1);
        ServerState.PLAYER_ROLES.put(player, monk);
        check(!SetupOperations.addRoleReminder(1, Role.TINKER.getId(), "Tinker Death").ok(), "server rejects invalid target");
        ServerState.PLAYER_ROLES.put(player, tinker);
        check(SetupOperations.addRoleReminder(1, Role.TINKER.getId(), "Tinker Death").ok(), "server accepts valid target");
        check(!ServerState.PLAYER_DEATH_STATUS.getOrDefault(player, false), "reminder does not kill player");
        StorytellerState.REMINDERS.clear(); ServerState.PLAYER_ROLES.clear(); ServerState.PLAYER_SEAT_NUMBERS.clear();
        System.out.println("PASS: " + checks + " reminder catalog/target checks");
    }
}
