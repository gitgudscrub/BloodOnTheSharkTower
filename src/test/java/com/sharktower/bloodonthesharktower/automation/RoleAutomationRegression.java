package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain Java regression executable for the rule checks behind the
 * Storyteller automation: who counts as alive, drunk or poisoned, spent,
 * and which Demon kills a protection refuses. The hooks that need a live
 * server (Virgin execution, Slayer shot, Scarlet Woman swap, win prompts)
 * are exercised on the dev harness instead.
 */
public final class RoleAutomationRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static UUID seat(int number, Role role) {
        UUID id = UUID.randomUUID();
        ServerState.PLAYER_SEAT_NUMBERS.put(id, number);
        ServerState.PLAYER_ROLES.put(id, new PendingRoleAssignment(role, AlignmentOverride.DEFAULT));
        ServerState.PLAYER_DEATH_STATUS.put(id, false);
        return id;
    }

    private static void remind(UUID player, String text, Role source) {
        StorytellerState.REMINDERS.computeIfAbsent(player, ignored -> new ArrayList<>())
                .add(new Reminder(text, Optional.ofNullable(source)));
    }

    private static void fresh() {
        ServerState.PLAYER_SEAT_NUMBERS.clear();
        ServerState.PLAYER_ROLES.clear();
        ServerState.PLAYER_DEATH_STATUS.clear();
        StorytellerState.REMINDERS.clear();
        ServerState.currentNight = 1;
        ServerState.currentDay = 0;
        ServerState.gameEnded = false;
        RoleAutomation.reset();
    }

    public static void main(String[] args) {
        // Alive counts ignore Travellers and the dead.
        fresh();
        UUID imp = seat(1, Role.IMP);
        UUID monk = seat(2, Role.MONK);
        UUID soldier = seat(3, Role.SOLDIER);
        UUID fool = seat(4, Role.FOOL);
        UUID drunk = seat(5, Role.DRUNK);
        UUID thief = seat(6, Role.THIEF);
        UUID scarlet = seat(7, Role.SCARLET_WOMAN);
        check(RoleAutomation.aliveNonTravellerCount() == 6, "Traveller is not counted");
        ServerState.PLAYER_DEATH_STATUS.put(monk, true);
        check(RoleAutomation.aliveNonTravellerCount() == 5, "dead players are not counted");
        check(!RoleAutomation.isAlive(monk), "dead Monk is not alive");
        check(RoleAutomation.isAlive(imp), "Imp is alive");
        check(RoleAutomation.isTraveller(thief), "Thief is a Traveller");
        ServerState.PLAYER_DEATH_STATUS.put(monk, false);

        // Role identity reads the real Grimoire; the Drunk is never a Townsfolk here.
        check(RoleAutomation.is(imp, Role.IMP), "is() matches the real role");
        check(!RoleAutomation.is(drunk, Role.SOLDIER), "the Drunk is not whoever they think they are");
        check(RoleAutomation.findAlive(Role.SCARLET_WOMAN) == scarlet, "findAlive finds the Scarlet Woman");
        check(RoleAutomation.findAlive(Role.MAYOR) == null, "findAlive returns null when nobody holds the role");

        // Drunk or poisoned.
        check(RoleAutomation.droisoned(drunk), "the Drunk is drunk");
        check(!RoleAutomation.droisoned(soldier), "sober Soldier");
        remind(soldier, "Poisoned", Role.POISONER);
        check(RoleAutomation.droisoned(soldier), "Poisoned reminder makes the Soldier poisoned");
        StorytellerState.REMINDERS.remove(soldier);
        remind(soldier, "drunk", null);
        check(RoleAutomation.droisoned(soldier), "Drunk reminder is matched case-insensitively");
        StorytellerState.REMINDERS.remove(soldier);

        // Ability spent.
        check(!RoleAutomation.abilitySpent(fool), "fresh Fool has their ability");
        RoleAutomation.spendAbility(fool, Role.FOOL);
        check(RoleAutomation.abilitySpent(fool), "spendAbility adds No Ability");
        check(RoleAutomation.hasReminder(fool, "no ability"), "No Ability reminder is on the Fool");
        StorytellerState.REMINDERS.remove(fool);

        // Protection: Soldier is safe, poisoned Soldier is not.
        check(RoleAutomation.demonKillRefusal(null, soldier) != null, "Soldier refuses the Demon kill");
        remind(soldier, "Poisoned", Role.POISONER);
        check(RoleAutomation.demonKillRefusal(null, soldier) == null, "poisoned Soldier can die");
        StorytellerState.REMINDERS.remove(soldier);

        // Protection: Monk's Safe reminder, unless the Monk is poisoned.
        remind(imp, "Safe", Role.MONK);
        check(RoleAutomation.demonKillRefusal(null, imp) != null, "Safe from the Monk protects");
        remind(monk, "Poisoned", Role.POISONER);
        check(RoleAutomation.demonKillRefusal(null, imp) == null, "poisoned Monk's Safe does not protect");
        StorytellerState.REMINDERS.remove(monk);
        ServerState.PLAYER_DEATH_STATUS.put(monk, true);
        check(RoleAutomation.demonKillRefusal(null, imp) != null, "a dead Monk's leftover Safe still counts (Storyteller cleans it up)");
        ServerState.PLAYER_DEATH_STATUS.put(monk, false);
        StorytellerState.REMINDERS.remove(imp);

        // Protection: a Safe reminder with no source is trusted as written.
        remind(imp, "Protected", null);
        check(RoleAutomation.demonKillRefusal(null, imp) != null, "unsourced Protected reminder protects");
        StorytellerState.REMINDERS.remove(imp);

        // Protection: the Fool survives once and the ability is spent by the refusal.
        check(RoleAutomation.demonKillRefusal(null, fool) != null, "Fool survives the first death");
        check(RoleAutomation.abilitySpent(fool), "Fool's ability is spent by surviving");
        check(RoleAutomation.demonKillRefusal(null, fool) == null, "Fool can die the second time");
        StorytellerState.REMINDERS.remove(fool);
        remind(fool, "Drunk", null);
        check(RoleAutomation.demonKillRefusal(null, fool) == null, "drunk Fool does not survive");
        StorytellerState.REMINDERS.remove(fool);

        // Protection: Tea Lady's Cannot Die, Sailor's sobriety.
        remind(imp, "Cannot Die", Role.TEA_LADY);
        check(RoleAutomation.demonKillRefusal(null, imp) != null, "Cannot Die protects when no Tea Lady is poisoned");
        UUID teaLady = seat(8, Role.TEA_LADY);
        remind(teaLady, "Poisoned", Role.POISONER);
        check(RoleAutomation.demonKillRefusal(null, imp) == null, "poisoned Tea Lady's Cannot Die fails");
        StorytellerState.REMINDERS.remove(imp);
        UUID sailor = seat(9, Role.SAILOR);
        check(RoleAutomation.demonKillRefusal(null, sailor) != null, "sober Sailor cannot die");
        remind(sailor, "Drunk", Role.SAILOR);
        check(RoleAutomation.demonKillRefusal(null, sailor) == null, "drunk Sailor can die");

        // Nothing protects an ordinary player.
        check(RoleAutomation.demonKillRefusal(null, scarlet) == null, "unprotected player can be killed");
        check(RoleAutomation.demonKillRefusal(null, null) == null, "null target is not refused");

        // Mayor: the first Demon Kill is held back so the Storyteller can bounce it.
        UUID mayor = seat(10, Role.MAYOR);
        check(RoleAutomation.demonKillRefusal(null, mayor) != null, "first Demon Kill on the Mayor is held back");
        check(RoleAutomation.demonKillRefusal(null, mayor) == null, "second Demon Kill on the Mayor goes through");
        check(RoleAutomation.demonKillRefusal(null, mayor) != null, "the warning resets after the kill goes through");
        ServerState.currentNight = 2;
        check(RoleAutomation.demonKillRefusal(null, mayor) != null, "a new night warns again");
        remind(mayor, "Poisoned", Role.POISONER);
        check(RoleAutomation.demonKillRefusal(null, mayor) == null, "a poisoned Mayor cannot bounce");
        StorytellerState.REMINDERS.remove(mayor);

        // Reminder expiry: dusk clears Poisoner/Innkeeper/Sailor marks, dawn clears the Monk's.
        StorytellerState.REMINDERS.clear();
        remind(soldier, "Poisoned", Role.POISONER);
        remind(soldier, "Safe", Role.MONK);
        remind(imp, "Safe", Role.INNKEEPER);
        remind(imp, "Drunk", Role.SAILOR);
        remind(fool, "Cannot Die", Role.TEA_LADY);
        remind(fool, "Poisoned", null);
        var dusk = RoleAutomation.expireReminders(null, java.util.Map.of(
                Role.POISONER, java.util.Set.of("poisoned"),
                Role.INNKEEPER, java.util.Set.of("safe", "drunk"),
                Role.SAILOR, java.util.Set.of("drunk")), "Dusk");
        check(dusk.size() == 3, "dusk clears the Poisoner, Innkeeper and Sailor reminders");
        check(RoleAutomation.hasReminder(soldier, "Safe"), "the Monk's Safe survives dusk");
        check(!RoleAutomation.hasReminder(imp, "Safe"), "the Innkeeper's Safe is gone at dusk");
        check(!StorytellerState.REMINDERS.containsKey(imp), "an emptied reminder list is removed");
        check(RoleAutomation.hasReminder(fool, "Cannot Die"), "the Tea Lady's mark is not touched");
        check(RoleAutomation.hasReminder(fool, "Poisoned"), "a reminder with no source role is never cleared");
        var dawn = RoleAutomation.expireReminders(null, java.util.Map.of(Role.MONK, java.util.Set.of("safe")), "Dawn");
        check(dawn.size() == 1 && !RoleAutomation.hasReminder(soldier, "Safe"), "dawn clears the Monk's Safe");

        System.out.println("PASS: " + checks + " role automation rule checks");
    }
}
