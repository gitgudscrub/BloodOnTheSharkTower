package com.sharktower.bloodonthesharktower.states;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk-port foundation for BOTB StorytellerState.
 *
 * This intentionally restores the persistent grimoire/setup collections first;
 * night-order UI/runtime fields arrive with the later UI/night-order batch.
 */
public final class StorytellerState {
    public static final Map<UUID, PendingRoleAssignment> PENDING_ROLES = new HashMap<>();
    /** Setup-only fake identities for characters such as the Drunk and Marionette. */
    public static final Map<UUID, PendingRoleAssignment> PENDING_PERCEIVED_ROLES = new HashMap<>();
    public static final Map<UUID, Integer> PENDING_SEAT_NUMBERS = new HashMap<>();
    public static final Map<UUID, List<Reminder>> REMINDERS = new HashMap<>();
    public static final List<ScriptRole> DEMON_BLUFFS = new ArrayList<>();
    /** Fake Demon bluffs shown only to an in-play Lunatic. Never merged with DEMON_BLUFFS. */
    public static final List<ScriptRole> LUNATIC_BLUFFS = new ArrayList<>();
    public static final Set<UUID> MARKED_PLAYERS = new HashSet<>();
    /** Players currently acting as Storytellers for setup/grimoire permissions. */
    public static final Set<UUID> STORYTELLERS = new HashSet<>();

    public static int nextSeatNumber = 1;
    public static boolean showUnseated = true;
    public static boolean showSelf = true;
    public static boolean autoTeleportEnabled = false;
    public static boolean sendTeleportInfo = true;
    public static boolean useDoorknock = true;
    public static boolean showBluffs = true;
    public static int setupOutsiderCount = 0;

    /** Storyteller-only daily facts used by the night-visit information HUD. */
    public static String lastExecutedRoleName = "";
    public static boolean demonVotedToday = false;
    public static boolean minionNominatedToday = false;

    private StorytellerState() {}

    public static Map<UUID, PendingRoleAssignment> effectiveGrimoireRoles() {
        return PENDING_ROLES.isEmpty() ? ServerState.PLAYER_ROLES : PENDING_ROLES;
    }

    public static Map<UUID, Integer> effectiveGrimoireSeats() {
        return PENDING_SEAT_NUMBERS.isEmpty() ? ServerState.PLAYER_SEAT_NUMBERS : PENDING_SEAT_NUMBERS;
    }

    public static Map<UUID, PendingRoleAssignment> effectiveGrimoirePerceivedRoles() {
        boolean editing = !PENDING_ROLES.isEmpty() || !PENDING_SEAT_NUMBERS.isEmpty() || !PENDING_PERCEIVED_ROLES.isEmpty();
        return editing ? PENDING_PERCEIVED_ROLES : ServerState.PLAYER_PERCEIVED_ROLES;
    }

    public static boolean isStoryteller(UUID playerId) {
        return STORYTELLERS.contains(playerId);
    }

    /**
     * Adds a Storyteller without any eligibility check. Callers that act on a
     * player's request must consult {@link #claimRefusal} first; this raw form
     * exists for snapshot restore and tests.
     */
    public static void claimStoryteller(UUID playerId) {
        STORYTELLERS.add(playerId);
    }

    /**
     * Storyteller control is open to everyone during Setup and locked once a game
     * is in progress, because a Storyteller sees every hidden role.
     *
     * @param operator whether the requester has operator (gamemaster) permission;
     *                 operators may override the lock to rescue a stuck game.
     * @return null when the claim is allowed, otherwise the message to show.
     */
    public static String claimRefusal(UUID playerId, boolean operator) {
        if (STORYTELLERS.contains(playerId) || operator) return null;
        if (gameInProgress()) {
            return "A game is in progress, so Storyteller control is locked until Reset for Next Game. "
                    + "If the Storyteller cannot return, ask a server operator to take over.";
        }
        return null;
    }

    /**
     * True from the moment roles are sent until the final Grimoire reveal makes
     * them public. Setup (no committed roles) and the post-reveal discussion are
     * not "in progress": nothing hidden would be exposed by a new Storyteller.
     */
    public static boolean gameInProgress() {
        if (ServerState.rolesRevealed) return false;
        if (ServerState.currentNight > 0 || ServerState.currentDay > 0) return true;
        for (PendingRoleAssignment assignment : ServerState.PLAYER_ROLES.values()) {
            if (assignment != null && (assignment.isCustomRole() || assignment.role() != Role.NO_ROLE)) return true;
        }
        return false;
    }

    public static void releaseStoryteller(UUID playerId) {
        STORYTELLERS.remove(playerId);
    }

    public static boolean hasSeatedPlayers() {
        return !ServerState.PLAYER_SEAT_NUMBERS.isEmpty() || !PENDING_SEAT_NUMBERS.isEmpty();
    }

    public static boolean isTraveler(UUID playerId) {
        PendingRoleAssignment assignment = ServerState.PLAYER_ROLES.get(playerId);
        return assignment != null && assignment.getRoleType() == RoleType.TRAVELER;
    }

    public static boolean hasAnyTravelers() {
        return ServerState.PLAYER_ROLES.entrySet().stream().anyMatch(entry -> isTraveler(entry.getKey()));
    }

    public static void resetDailyNightInfo() {
        lastExecutedRoleName = "";
        demonVotedToday = false;
        minionNominatedToday = false;
    }

    public static void clearSetupState() {
        PENDING_ROLES.clear();
        PENDING_PERCEIVED_ROLES.clear();
        PENDING_SEAT_NUMBERS.clear();
        REMINDERS.clear();
        DEMON_BLUFFS.clear();
        LUNATIC_BLUFFS.clear();
        MARKED_PLAYERS.clear();
        nextSeatNumber = 1;
        setupOutsiderCount = 0;
        resetDailyNightInfo();
    }
}
