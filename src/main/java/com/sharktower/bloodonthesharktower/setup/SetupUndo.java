package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Undo for the Storyteller's setup edits (before the first night): seats,
 * dealt tokens, believed roles, alignments, reminders and bluffs. Each
 * undoable action saves the state first; Undo puts the last one back.
 * Committed roles (SEND ROLES) and the loaded script are not part of it.
 */
public final class SetupUndo {
    private static final int LIMIT = 20;
    private static final Set<String> TRACKED = Set.of(
            "shuffle_roles", "shuffle_seats", "randomize_roles", "distribute_role_bag",
            "randomize_bluffs", "clear_bluffs", "add_bluff", "set_bluffs", "set_bluff",
            "assign_role", "assign_perceived_role", "clear_perceived_role", "clear_role",
            "alignment_default", "alignment_good", "alignment_evil",
            "add_reminder", "add_role_reminder", "clear_reminders", "remove_reminder",
            "seat_player", "hide_role");

    private record Snapshot(String op,
                            Map<UUID, PendingRoleAssignment> roles,
                            Map<UUID, PendingRoleAssignment> perceived,
                            Map<UUID, Integer> seats,
                            Map<UUID, List<Reminder>> reminders,
                            List<ScriptRole> bluffs,
                            List<ScriptRole> lunaticBluffs,
                            int nextSeat) {}

    private static final Deque<Snapshot> HISTORY = new ArrayDeque<>();

    private SetupUndo() {}

    static boolean inSetup() {
        return ServerState.currentNight == 0 && ServerState.currentDay == 0 && !ServerState.gameEnded;
    }

    public static boolean tracks(String op) {
        return inSetup() && TRACKED.contains(op);
    }

    public static void save(String op) {
        Map<UUID, List<Reminder>> reminders = new HashMap<>();
        StorytellerState.REMINDERS.forEach((id, list) -> reminders.put(id, new ArrayList<>(list)));
        HISTORY.push(new Snapshot(op,
                new HashMap<>(StorytellerState.PENDING_ROLES),
                new HashMap<>(StorytellerState.PENDING_PERCEIVED_ROLES),
                new HashMap<>(StorytellerState.PENDING_SEAT_NUMBERS),
                reminders,
                new ArrayList<>(StorytellerState.DEMON_BLUFFS),
                new ArrayList<>(StorytellerState.LUNATIC_BLUFFS),
                StorytellerState.nextSeatNumber));
        while (HISTORY.size() > LIMIT) HISTORY.removeLast();
    }

    /** Drops the snapshot saved for an action that then failed. */
    public static void discardLast() {
        HISTORY.poll();
    }

    public static void clear() {
        HISTORY.clear();
    }

    public static int size() {
        return HISTORY.size();
    }

    public static SetupOperations.Result undo() {
        if (!inSetup()) {
            HISTORY.clear();
            return SetupOperations.Result.fail("Undo only works during setup, before the first night.");
        }
        Snapshot last = HISTORY.poll();
        if (last == null) return SetupOperations.Result.fail("Nothing to undo.");
        replace(StorytellerState.PENDING_ROLES, last.roles());
        replace(StorytellerState.PENDING_PERCEIVED_ROLES, last.perceived());
        replace(StorytellerState.PENDING_SEAT_NUMBERS, last.seats());
        StorytellerState.REMINDERS.clear();
        last.reminders().forEach((id, list) -> StorytellerState.REMINDERS.put(id, new ArrayList<>(list)));
        StorytellerState.DEMON_BLUFFS.clear();
        StorytellerState.DEMON_BLUFFS.addAll(last.bluffs());
        StorytellerState.LUNATIC_BLUFFS.clear();
        StorytellerState.LUNATIC_BLUFFS.addAll(last.lunaticBluffs());
        StorytellerState.nextSeatNumber = last.nextSeat();
        return SetupOperations.Result.ok("Undid: " + describe(last.op()) + "."
                + (HISTORY.isEmpty() ? "" : " " + HISTORY.size() + " more to undo."));
    }

    private static <K, V> void replace(Map<K, V> target, Map<K, V> source) {
        target.clear();
        target.putAll(source);
    }

    private static String describe(String op) {
        return switch (op) {
            case "shuffle_roles" -> "shuffle roles";
            case "shuffle_seats" -> "shuffle seats";
            case "randomize_roles" -> "random setup";
            case "distribute_role_bag" -> "deal the bag";
            case "seat_player" -> "seat a player";
            case "hide_role" -> "Drunk/Marionette choice";
            case "assign_role", "clear_role" -> "role change";
            case "assign_perceived_role", "clear_perceived_role" -> "believed role change";
            case "alignment_default", "alignment_good", "alignment_evil" -> "alignment change";
            case "add_reminder", "add_role_reminder", "clear_reminders", "remove_reminder" -> "reminder change";
            default -> "bluff change";
        };
    }
}
