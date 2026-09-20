package com.sharktower.bloodonthesharktower.client;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local-only Grimoire notes for ordinary players.
 *
 * Storyteller edits remain server-authoritative. A non-Storyteller may still
 * use the Grimoire as a personal notebook: role guesses, alignment overrides,
 * and reminders live only on that client and are never sent to the server.
 */
public final class ClientGrimoireEdits {
    private static final Map<UUID, PendingRoleAssignment> ROLE_OVERRIDES = new HashMap<>();
    private static final Map<UUID, PendingRoleAssignment> PERCEIVED_ROLE_OVERRIDES = new HashMap<>();
    private static final Map<UUID, List<Reminder>> REMINDER_OVERRIDES = new HashMap<>();

    // Ability-sourced information is kept separate from the player's own reminder
    // notebook so a Spy/Widow refresh can replace the Storyteller snapshot without
    // deleting reminders the player personally added.
    private static final Map<UUID, List<Reminder>> SHARED_ABILITY_REMINDERS = new HashMap<>();
    private static List<String> SHARED_ABILITY_DEMON_BLUFFS = List.of();

    private ClientGrimoireEdits() {}

    public static boolean isLocalStoryteller() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        return ClientState.storytellerPlayers.contains(minecraft.player.getUUID());
    }

    public static PendingRoleAssignment roleFor(UUID playerId) {
        if (playerId == null) return null;
        if (isLocalStoryteller() || ClientState.rolesRevealed) {
            return ClientState.grimoireRoles.get(playerId);
        }
        if (ROLE_OVERRIDES.containsKey(playerId)) {
            return ROLE_OVERRIDES.get(playerId);
        }
        return ClientState.grimoireRoles.get(playerId);
    }

    /**
     * Secondary "believed role" token for Drunk/Marionette.
     * Storytellers read the authoritative server map; ordinary players keep a
     * completely local notebook value that is never sent to the server.
     */
    public static PendingRoleAssignment perceivedRoleFor(UUID playerId) {
        if (playerId == null || ClientState.rolesRevealed) return null;
        if (isLocalStoryteller()) return ClientState.grimoirePerceivedRoles.get(playerId);
        return PERCEIVED_ROLE_OVERRIDES.get(playerId);
    }

    public static List<Reminder> remindersFor(UUID playerId) {
        if (playerId == null) return List.of();
        if (isLocalStoryteller() || ClientState.rolesRevealed) {
            return ClientState.grimoireReminders.getOrDefault(playerId, List.of());
        }

        List<Reminder> combined = new ArrayList<>();
        for (Reminder reminder : SHARED_ABILITY_REMINDERS.getOrDefault(playerId, List.of())) {
            if (!combined.contains(reminder)) combined.add(reminder);
        }
        for (Reminder reminder : REMINDER_OVERRIDES.getOrDefault(playerId, List.of())) {
            if (!combined.contains(reminder)) combined.add(reminder);
        }
        return List.copyOf(combined);
    }

    public static boolean hasVisibleRoleNotes() {
        return !ClientState.grimoireRoles.isEmpty() || !ROLE_OVERRIDES.isEmpty();
    }

    /**
     * Apply a Spy/Widow share into the ordinary personal Grimoire.
     *
     * True roles replace role guesses, Storyteller reminder tokens replace the
     * previous shared snapshot, but the player's own reminder notes are retained.
     * Demon bluffs are kept in a local ability overlay so later ordinary server
     * Grimoire syncs cannot immediately erase them.
     */
    public static void applyAbilityGrimoireSnapshot(
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, List<Reminder>> reminders,
            List<String> demonBluffs
    ) {
        if (isLocalStoryteller()) return;

        ROLE_OVERRIDES.clear();
        PERCEIVED_ROLE_OVERRIDES.clear();

        if (roles != null) {
            for (Map.Entry<UUID, PendingRoleAssignment> entry : roles.entrySet()) {
                PendingRoleAssignment assignment = entry.getValue();
                if (assignment != null && assignment.isCustomRole() && ClientState.currentScript != null) {
                    assignment = assignment.resolveCustomRole(ClientState.currentScript);
                }
                if (entry.getKey() != null && assignment != null) {
                    ROLE_OVERRIDES.put(entry.getKey(), assignment);
                }
            }
        }

        SHARED_ABILITY_REMINDERS.clear();
        if (reminders != null) {
            for (Map.Entry<UUID, List<Reminder>> entry : reminders.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                SHARED_ABILITY_REMINDERS.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }

        SHARED_ABILITY_DEMON_BLUFFS = demonBluffs == null
                ? List.of()
                : List.copyOf(demonBluffs);
    }

    public static List<String> visibleDemonBluffs() {
        if (isLocalStoryteller() || ClientState.rolesRevealed) {
            return ClientState.demonBluffs;
        }
        return SHARED_ABILITY_DEMON_BLUFFS.isEmpty()
                ? ClientState.demonBluffs
                : SHARED_ABILITY_DEMON_BLUFFS;
    }

    public static void assignRole(UUID playerId, ScriptRole role) {
        if (playerId == null || role == null) return;
        AlignmentOverride alignment = AlignmentOverride.DEFAULT;
        PendingRoleAssignment current = roleFor(playerId);
        if (current != null) alignment = current.override();

        PendingRoleAssignment assignment = role.isCustom()
                ? new PendingRoleAssignment(role.asCustomRole(), alignment)
                : new PendingRoleAssignment(role.asRole(), alignment);
        ROLE_OVERRIDES.put(playerId, assignment);
        if (!isDeceivedCharacter(assignment)) PERCEIVED_ROLE_OVERRIDES.remove(playerId);
    }

    public static void assignPerceivedRole(UUID playerId, ScriptRole role) {
        if (playerId == null || role == null) return;
        PendingRoleAssignment actual = roleFor(playerId);
        if (!isDeceivedCharacter(actual)) return;

        Role actualRole = actual.role();
        if (actualRole == Role.DRUNK && role.getTeam() != com.sharktower.bloodonthesharktower.core.RoleType.TOWNSFOLK) return;
        if (actualRole == Role.MARIONETTE
                && role.getTeam() != com.sharktower.bloodonthesharktower.core.RoleType.TOWNSFOLK
                && role.getTeam() != com.sharktower.bloodonthesharktower.core.RoleType.OUTSIDER) return;

        PendingRoleAssignment perceived = role.isCustom()
                ? new PendingRoleAssignment(role.asCustomRole(), AlignmentOverride.DEFAULT)
                : new PendingRoleAssignment(role.asRole(), AlignmentOverride.DEFAULT);
        PERCEIVED_ROLE_OVERRIDES.put(playerId, perceived);
    }

    public static void clearPerceivedRole(UUID playerId) {
        if (playerId != null) PERCEIVED_ROLE_OVERRIDES.remove(playerId);
    }

    private static boolean isDeceivedCharacter(PendingRoleAssignment assignment) {
        return assignment != null && !assignment.isCustomRole()
                && (assignment.role() == Role.DRUNK || assignment.role() == Role.MARIONETTE);
    }

    public static void clearRole(UUID playerId) {
        if (playerId == null) return;
        // Keep an explicit NO_ROLE override so clearing the player's own known
        // role or a public Traveller really does make the local token blank.
        ROLE_OVERRIDES.put(playerId,
                new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT));
        PERCEIVED_ROLE_OVERRIDES.remove(playerId);
    }

    public static void setAlignment(UUID playerId, AlignmentOverride override) {
        if (playerId == null) return;
        PendingRoleAssignment current = roleFor(playerId);
        if (current == null) {
            current = new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT);
        }

        PendingRoleAssignment updated = current.isCustomRole() && current.customRole().isPresent()
                ? new PendingRoleAssignment(current.customRole().get(), override)
                : new PendingRoleAssignment(current.role(), override);
        ROLE_OVERRIDES.put(playerId, updated);
    }

    public static void addReminder(UUID playerId, String text) {
        if (playerId == null || text == null || text.isBlank()) return;
        List<Reminder> reminders = new ArrayList<>(REMINDER_OVERRIDES.getOrDefault(playerId, List.of()));
        reminders.add(new Reminder(text, java.util.Optional.empty()));
        REMINDER_OVERRIDES.put(playerId, reminders);
    }

    public static void removeReminder(UUID playerId, int index) {
        if (playerId == null) return;
        List<Reminder> reminders = new ArrayList<>(REMINDER_OVERRIDES.getOrDefault(playerId, List.of()));
        if (index < 0 || index >= reminders.size()) return;
        reminders.remove(index);
        if (reminders.isEmpty()) REMINDER_OVERRIDES.remove(playerId);
        else REMINDER_OVERRIDES.put(playerId, reminders);
    }

    public static void clearReminders(UUID playerId) {
        if (playerId != null) REMINDER_OVERRIDES.remove(playerId);
    }

    /** Resolve a seated player from either the Grimoire seat map or live seat map. */
    public static UUID playerAtSeat(int seat) {
        for (Map.Entry<UUID, Integer> entry : ClientState.grimoireSeatNumbers.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == seat) return entry.getKey();
        }
        for (Map.Entry<UUID, Integer> entry : ClientState.playerSeatNumbers.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == seat) return entry.getKey();
        }
        return null;
    }
}
