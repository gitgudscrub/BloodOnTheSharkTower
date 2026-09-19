package com.sharktower.bloodonthesharktower.nightorder;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative queue for event/death-driven night-order visits.
 *
 * This mirrors the original Blocktower idea: deaths create temporary visits in
 * the next/current night's Storyteller order instead of relying on the ST to
 * remember every triggered character manually.
 *
 * The queue intentionally remains advisory. It does not resolve role abilities
 * automatically; it only inserts the appropriate Storyteller reminder/visit.
 */
public final class TriggeredNightOrderManager {
    private static final List<TriggeredVisit> TRIGGERS = new ArrayList<>();

    private TriggeredNightOrderManager() {}

    public enum DeathCause {
        GENERIC,
        EXECUTION,
        NIGHT,
        DEMON
    }

    public record TriggeredVisit(
            int targetNight,
            UUID playerId,
            Role role,
            UUID sourcePlayerId,
            DeathCause cause
    ) {}

    /**
     * Called after the victim has been marked dead in ServerState.
     */
    public static synchronized void onDeath(UUID deadPlayer, DeathCause cause) {
        if (deadPlayer == null) return;
        if (ServerState.currentNight == 0 && ServerState.currentDay == 0) return;

        int targetNight = targetNight();
        if (targetNight <= 0) return;

        Map<UUID, PendingRoleAssignment> roles = StorytellerState.effectiveGrimoireRoles();
        PendingRoleAssignment deadAssignment = roles.get(deadPlayer);
        Role deadRole = builtInRole(deadAssignment);
        boolean nightDeath = PhaseOperations.isNight() || cause == DeathCause.NIGHT || cause == DeathCause.DEMON;
        boolean demonKill = cause == DeathCause.DEMON;

        if (deadRole != null) {
            // ANY-death triggers. Some of these wake the character that died,
            // while Barber/Hatter/Poppy Grower affect other players. The latter
            // mirrors the original Blocktower triggered-visit target selection:
            // Barber -> Demon(s), Hatter/Poppy Grower -> Minions + Demon(s).
            switch (deadRole) {
                case BARBER -> addTeamHolders(
                        targetNight, Role.BARBER, deadPlayer, cause, roles, RoleType.DEMON);
                case HATTER -> addTeamHolders(
                        targetNight, Role.HATTER, deadPlayer, cause, roles, RoleType.MINION, RoleType.DEMON);
                case POPPY_GROWER -> addTeamHolders(
                        targetNight, Role.POPPY_GROWER, deadPlayer, cause, roles, RoleType.MINION, RoleType.DEMON);
                case SWEETHEART, PLAGUE_DOCTOR ->
                        add(targetNight, deadPlayer, deadRole, deadPlayer, cause);
                default -> { }
            }

            // NIGHT death triggers.
            if (nightDeath) {
                switch (deadRole) {
                    case FARMER, RAVENKEEPER ->
                            add(targetNight, deadPlayer, deadRole, deadPlayer, cause);
                    default -> { }
                }
            }

            // DEMON-kill-only self triggers.
            if (demonKill) {
                switch (deadRole) {
                    case SAGE, BANSHEE ->
                            add(targetNight, deadPlayer, deadRole, deadPlayer, cause);
                    default -> { }
                }
            }
        }

        // Scarlet Woman is triggered by the Demon dying while at least five
        // non-Traveller players remain alive. This is a reminder only; the ST
        // still resolves exceptional/jinxed replacement rules manually.
        if (deadAssignment != null
                && deadAssignment.getRoleType() == RoleType.DEMON
                // onDeath() is called after the victim has been marked dead.
                // Scarlet Woman checks the alive count at the moment immediately
                // before the Demon dies, so add the just-dead Demon back in here.
                && aliveNonTravelerCount(roles) + 1 >= 5) {
            addLivingRoleHolders(targetNight, Role.SCARLET_WOMAN, deadPlayer, cause, roles);
        }

        // Choirboy: Demon kills the King.
        if (demonKill && deadRole == Role.KING) {
            addLivingRoleHolders(targetNight, Role.CHOIRBOY, deadPlayer, cause, roles);
        }

        // Grandmother: Demon kills the player marked Grandchild.
        if (demonKill && hasReminder(deadPlayer, "Grandchild")) {
            addLivingRoleHolders(targetNight, Role.GRANDMOTHER, deadPlayer, cause, roles);
        }
    }

    /** Remove visits caused by this death if the source is revived/corrected. */
    public static synchronized void onRevived(UUID playerId) {
        if (playerId == null) return;
        TRIGGERS.removeIf(trigger -> playerId.equals(trigger.sourcePlayerId()));
    }

    /** Complete reset for a fresh setup/match. */
    public static synchronized void clear() {
        TRIGGERS.clear();
    }

    /**
     * Encoded Storyteller-only snapshot used by the small S2C payload.
     */
    public static synchronized String encodedSnapshot() {
        prune();
        if (TRIGGERS.isEmpty()) return "";

        StringBuilder out = new StringBuilder();
        for (TriggeredVisit trigger : TRIGGERS) {
            if (out.length() > 0) out.append('\n');
            out.append(trigger.targetNight()).append('|')
                    .append(trigger.playerId()).append('|')
                    .append(trigger.role().getId()).append('|')
                    .append(trigger.sourcePlayerId()).append('|')
                    .append(trigger.cause().name());
        }
        return out.toString();
    }

    private static int targetNight() {
        if (PhaseOperations.isNight()) return ServerState.currentNight;
        if (PhaseOperations.isDay()) return ServerState.currentDay + 1;
        return 0;
    }

    private static void addLivingRoleHolders(
            int targetNight,
            Role role,
            UUID sourcePlayer,
            DeathCause cause,
            Map<UUID, PendingRoleAssignment> roles
    ) {
        for (Map.Entry<UUID, PendingRoleAssignment> entry : roles.entrySet()) {
            UUID holder = entry.getKey();
            if (holder == null || holder.equals(sourcePlayer)) continue;
            if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(holder))) continue;
            if (builtInRole(entry.getValue()) != role) continue;
            add(targetNight, holder, role, sourcePlayer, cause);
        }
    }


    private static void addTeamHolders(
            int targetNight,
            Role triggerRole,
            UUID sourcePlayer,
            DeathCause cause,
            Map<UUID, PendingRoleAssignment> roles,
            RoleType... teams
    ) {
        if (teams == null || teams.length == 0) return;
        for (Map.Entry<UUID, PendingRoleAssignment> entry : roles.entrySet()) {
            UUID holder = entry.getKey();
            PendingRoleAssignment assignment = entry.getValue();
            if (holder == null || holder.equals(sourcePlayer) || assignment == null) continue;

            // Match the original triggered visit builder: Barber/Hatter/Poppy
            // Grower target character holders by team, even if one of those
            // players is already dead. Dead evil players can still need the
            // Hatter/Poppy/Barber information during the night.
            RoleType type = assignment.getRoleType();
            boolean matches = false;
            for (RoleType team : teams) {
                if (type == team) {
                    matches = true;
                    break;
                }
            }
            if (matches) add(targetNight, holder, triggerRole, sourcePlayer, cause);
        }
    }

    private static void add(int targetNight, UUID playerId, Role role, UUID sourcePlayerId, DeathCause cause) {
        if (targetNight <= 0 || playerId == null || role == null || sourcePlayerId == null) return;
        TriggeredVisit visit = new TriggeredVisit(targetNight, playerId, role, sourcePlayerId,
                cause == null ? DeathCause.GENERIC : cause);
        if (!TRIGGERS.contains(visit)) TRIGGERS.add(visit);
    }

    private static Role builtInRole(PendingRoleAssignment assignment) {
        if (assignment == null || assignment.isCustomRole()) return null;
        Role role = assignment.role();
        return role == null || role == Role.NO_ROLE ? null : role;
    }

    private static int aliveNonTravelerCount(Map<UUID, PendingRoleAssignment> roles) {
        int count = 0;
        for (Map.Entry<UUID, PendingRoleAssignment> entry : roles.entrySet()) {
            PendingRoleAssignment assignment = entry.getValue();
            if (assignment == null || assignment.getRoleType() == RoleType.TRAVELER) continue;
            if (assignment.getRoleType() == RoleType.NONE) continue;
            if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(entry.getKey()))) continue;
            count++;
        }
        return count;
    }

    private static boolean hasReminder(UUID playerId, String expectedText) {
        String wanted = normalize(expectedText);
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(playerId, List.of())) {
            if (normalize(reminder.text()).equals(wanted)) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void prune() {
        // Any trip through Setup is a hard boundary between matches. Because
        // sendCurrentStateTo() is called during reset/setup, this prevents stale
        // Game 1 triggers reappearing when the next game reaches Night 1.
        if (ServerState.currentNight == 0 && ServerState.currentDay == 0) {
            TRIGGERS.clear();
            return;
        }

        if (PhaseOperations.isDay()) {
            int completedNight = ServerState.currentDay;
            TRIGGERS.removeIf(trigger -> trigger.targetNight() <= completedNight);
        } else if (PhaseOperations.isNight()) {
            int currentNight = ServerState.currentNight;
            TRIGGERS.removeIf(trigger -> trigger.targetNight() < currentNight);
        }
    }
}
