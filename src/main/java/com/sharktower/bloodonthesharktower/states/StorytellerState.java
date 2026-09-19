package com.sharktower.bloodonthesharktower.states;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
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

    public static void claimStoryteller(UUID playerId) {
        STORYTELLERS.add(playerId);
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
        MARKED_PLAYERS.clear();
        nextSeatNumber = 1;
        setupOutsiderCount = 0;
        resetDailyNightInfo();
    }
}
