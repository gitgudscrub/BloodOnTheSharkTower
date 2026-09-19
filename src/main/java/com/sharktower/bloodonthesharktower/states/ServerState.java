package com.sharktower.bloodonthesharktower.states;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Script;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 26.2 port of the original BOTB ServerState layout, now using the real
 * PendingRoleAssignment and Script model rather than temporary bridge types.
 */
public final class ServerState {
    public static final Map<UUID, PendingRoleAssignment> PLAYER_ROLES = new HashMap<>();
    /** Actual Drunk/Marionette-facing role shown to that player instead of their true role. */
    public static final Map<UUID, PendingRoleAssignment> PLAYER_PERCEIVED_ROLES = new HashMap<>();
    public static final Map<UUID, Integer> PLAYER_SEAT_NUMBERS = new HashMap<>();
    public static final Map<UUID, Boolean> PLAYER_DEATH_STATUS = new HashMap<>();

    public static int currentNight = 0;
    public static int currentDay = 0;
    public static Script currentScript = null;
    public static boolean executionToday = false;
    public static boolean gameEnded = false;
    /** A.10 end-game reveal state. Values: NONE, GOOD, EVIL. */
    public static String winningTeam = "NONE";
    public static boolean rolesRevealed = false;

    private ServerState() {}

    public static Set<UUID> deadPlayers() {
        Set<UUID> deadPlayers = new HashSet<>();
        for (Map.Entry<UUID, Boolean> entry : PLAYER_DEATH_STATUS.entrySet()) {
            if (entry.getValue()) deadPlayers.add(entry.getKey());
        }
        return deadPlayers;
    }

    public static void updateRoles(Map<UUID, PendingRoleAssignment> roles) {
        PLAYER_ROLES.clear();
        PLAYER_ROLES.putAll(roles);
    }

    public static void updatePerceivedRoles(Map<UUID, PendingRoleAssignment> roles) {
        PLAYER_PERCEIVED_ROLES.clear();
        PLAYER_PERCEIVED_ROLES.putAll(roles);
    }

    public static void updateSeats(Map<UUID, Integer> seats) {
        PLAYER_SEAT_NUMBERS.clear();
        PLAYER_SEAT_NUMBERS.putAll(seats);
    }

    public static void updateDeathStatus(Map<UUID, Boolean> deathStatus) {
        PLAYER_DEATH_STATUS.clear();
        PLAYER_DEATH_STATUS.putAll(deathStatus);
    }
}
