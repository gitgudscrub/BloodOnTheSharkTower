package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.CustomRole;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.snapshot.MatchSnapshotManager;
import com.sharktower.bloodonthesharktower.sound.ModSounds;
import com.sharktower.bloodonthesharktower.timer.TimerManager;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 0.7.0 functional-parity backend for the original BOTB Storyteller workflow.
 *
 * These operations intentionally live outside the screen classes. The original
 * circular Grimoire can therefore be reattached in 0.8.0 without changing the
 * authoritative setup/role/reminder/bluff logic again.
 */
public final class SetupOperations {
    public record Result(boolean ok, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }

    private record Counts(int townsfolk, int outsiders, int minions, int demons) {}

    private SetupOperations() {}

    public static Map<UUID, Integer> workingSeats() {
        return StorytellerState.PENDING_SEAT_NUMBERS.isEmpty()
                ? ServerState.PLAYER_SEAT_NUMBERS
                : StorytellerState.PENDING_SEAT_NUMBERS;
    }

    public static Map<UUID, PendingRoleAssignment> workingRoles() {
        return StorytellerState.PENDING_ROLES.isEmpty()
                ? ServerState.PLAYER_ROLES
                : StorytellerState.PENDING_ROLES;
    }

    public static Map<UUID, PendingRoleAssignment> workingPerceivedRoles() {
        boolean editing = !StorytellerState.PENDING_ROLES.isEmpty()
                || !StorytellerState.PENDING_SEAT_NUMBERS.isEmpty()
                || !StorytellerState.PENDING_PERCEIVED_ROLES.isEmpty();
        return editing ? StorytellerState.PENDING_PERCEIVED_ROLES : ServerState.PLAYER_PERCEIVED_ROLES;
    }

    public static void beginEditingFromLiveState() {
        if (StorytellerState.PENDING_SEAT_NUMBERS.isEmpty()) {
            StorytellerState.PENDING_SEAT_NUMBERS.putAll(ServerState.PLAYER_SEAT_NUMBERS);
        }
        if (StorytellerState.PENDING_ROLES.isEmpty()) {
            StorytellerState.PENDING_ROLES.putAll(ServerState.PLAYER_ROLES);
        }
        if (StorytellerState.PENDING_PERCEIVED_ROLES.isEmpty()) {
            StorytellerState.PENDING_PERCEIVED_ROLES.putAll(ServerState.PLAYER_PERCEIVED_ROLES);
        }
    }

    public static Result assignSeat(UUID player, int seat) {
        if (player == null) return Result.fail("Player is not available.");
        if (seat < 1) return Result.fail("Seat numbers start at 1.");
        beginEditingFromLiveState();
        UUID occupying = playerBySeat(seat);
        if (occupying != null && !occupying.equals(player)) {
            return Result.fail("Seat " + seat + " is already occupied.");
        }
        StorytellerState.PENDING_SEAT_NUMBERS.put(player, seat);
        StorytellerState.nextSeatNumber = Math.max(StorytellerState.nextSeatNumber, seat + 1);
        return Result.ok("Assigned pending seat " + seat + ".");
    }

    public static Result seatNext(UUID player) {
        if (player == null) return Result.fail("Player is not available.");
        beginEditingFromLiveState();
        int seat = 1;
        java.util.Set<Integer> used = new java.util.HashSet<>(workingSeats().values());
        while (used.contains(seat)) seat++;
        return assignSeat(player, seat);
    }

    public static Result unseat(UUID player) {
        if (player == null) return Result.fail("Player is not available.");
        beginEditingFromLiveState();
        Integer seat = StorytellerState.PENDING_SEAT_NUMBERS.remove(player);
        StorytellerState.PENDING_ROLES.remove(player);
        StorytellerState.PENDING_PERCEIVED_ROLES.remove(player);
        StorytellerState.REMINDERS.remove(player);
        return seat == null
                ? Result.fail("Player was not seated.")
                : Result.ok("Removed player from pending seat " + seat + ".");
    }

    public static Result seatAll(List<UUID> players) {
        if (players == null || players.isEmpty()) return Result.fail("No players are available to seat.");
        StorytellerState.PENDING_SEAT_NUMBERS.clear();
        int seat = 1;
        for (UUID player : players) {
            StorytellerState.PENDING_SEAT_NUMBERS.put(player, seat++);
        }
        StorytellerState.nextSeatNumber = seat;
        return Result.ok("Assigned pending seats 1-" + players.size() + " to " + players.size() + " player(s).");
    }

    public static Result assignRole(int seat, String roleId) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        ScriptRole role = resolveScriptRole(roleId);
        if (role == null) return Result.fail("Unknown role '" + roleId + "' for the current script.");
        beginEditingFromLiveState();
        PendingRoleAssignment assigned = assignment(role, AlignmentOverride.DEFAULT);
        StorytellerState.PENDING_ROLES.put(player, assigned);
        PendingRoleAssignment perceived = StorytellerState.PENDING_PERCEIVED_ROLES.get(player);
        if (!requiresPerceivedRole(assigned) || (isAssigned(perceived) && !perceivedRoleAllowed(assigned, perceived))) {
            StorytellerState.PENDING_PERCEIVED_ROLES.remove(player);
        }
        return Result.ok("Assigned " + role.getDisplayName() + " to seat " + seat + " (pending).");
    }

    public static Result assignPerceivedRole(int seat, String roleId) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        beginEditingFromLiveState();
        PendingRoleAssignment actual = StorytellerState.PENDING_ROLES.get(player);
        if (!requiresPerceivedRole(actual)) {
            return Result.fail("Seat " + seat + " is not the Drunk or Marionette.");
        }
        ScriptRole perceivedRole = resolveScriptRole(roleId);
        if (perceivedRole == null) return Result.fail("Unknown believed role '" + roleId + "' for the current script.");
        PendingRoleAssignment perceived = assignment(perceivedRole, AlignmentOverride.DEFAULT);
        if (!perceivedRoleAllowed(actual, perceived)) {
            return Result.fail(actual.role() == Role.DRUNK
                    ? "The Drunk must believe they are a Townsfolk character."
                    : "The Marionette must believe they are a good Townsfolk or Outsider character.");
        }
        StorytellerState.PENDING_PERCEIVED_ROLES.put(player, perceived);
        return Result.ok("Seat " + seat + " will be shown " + perceived.getDisplayName() + ".");
    }

    public static Result clearPerceivedRole(int seat) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        beginEditingFromLiveState();
        StorytellerState.PENDING_PERCEIVED_ROLES.remove(player);
        return Result.ok("Cleared the believed role for seat " + seat + ".");
    }

    public static Result clearRole(int seat) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        beginEditingFromLiveState();
        StorytellerState.PENDING_ROLES.put(player,
                new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT));
        StorytellerState.PENDING_PERCEIVED_ROLES.remove(player);
        return Result.ok("Cleared the pending role for seat " + seat + ".");
    }

    public static Result setAlignment(int seat, AlignmentOverride override) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        beginEditingFromLiveState();
        PendingRoleAssignment old = StorytellerState.PENDING_ROLES.get(player);
        if (old == null) return Result.fail("Seat " + seat + " has no pending role to modify.");
        StorytellerState.PENDING_ROLES.put(player,
                new PendingRoleAssignment(old.role(), old.customRole(), override));
        return Result.ok("Seat " + seat + " alignment override set to " + override.name() + ".");
    }

    public static Result swapRoles(int seatA, int seatB) {
        UUID a = playerBySeat(seatA);
        UUID b = playerBySeat(seatB);
        if (a == null || b == null) return Result.fail("Both seats must contain players.");
        beginEditingFromLiveState();
        PendingRoleAssignment roleA = StorytellerState.PENDING_ROLES.get(a);
        PendingRoleAssignment roleB = StorytellerState.PENDING_ROLES.get(b);
        PendingRoleAssignment perceivedA = StorytellerState.PENDING_PERCEIVED_ROLES.get(a);
        PendingRoleAssignment perceivedB = StorytellerState.PENDING_PERCEIVED_ROLES.get(b);
        if (roleA == null) StorytellerState.PENDING_ROLES.remove(b);
        else StorytellerState.PENDING_ROLES.put(b, roleA);
        if (roleB == null) StorytellerState.PENDING_ROLES.remove(a);
        else StorytellerState.PENDING_ROLES.put(a, roleB);
        if (perceivedA == null) StorytellerState.PENDING_PERCEIVED_ROLES.remove(b);
        else StorytellerState.PENDING_PERCEIVED_ROLES.put(b, perceivedA);
        if (perceivedB == null) StorytellerState.PENDING_PERCEIVED_ROLES.remove(a);
        else StorytellerState.PENDING_PERCEIVED_ROLES.put(a, perceivedB);
        return Result.ok("Swapped pending roles for seats " + seatA + " and " + seatB + ".");
    }

    public static Result swapSeats(int seatA, int seatB) {
        UUID a = playerBySeat(seatA);
        UUID b = playerBySeat(seatB);
        if (a == null || b == null) return Result.fail("Both seats must contain players.");
        beginEditingFromLiveState();
        StorytellerState.PENDING_SEAT_NUMBERS.put(a, seatB);
        StorytellerState.PENDING_SEAT_NUMBERS.put(b, seatA);
        return Result.ok("Swapped seats " + seatA + " and " + seatB + " (pending).");
    }

    public static Result shuffleRoles() {
        beginEditingFromLiveState();
        List<UUID> players = seatedPlayersInOrder();
        if (players.size() < 2) return Result.fail("At least two seated players are required.");
        record RoleBundle(PendingRoleAssignment actual, PendingRoleAssignment perceived) {}
        List<RoleBundle> roles = new ArrayList<>();
        for (UUID player : players) {
            PendingRoleAssignment actual = StorytellerState.PENDING_ROLES.get(player);
            if (actual != null) roles.add(new RoleBundle(actual, StorytellerState.PENDING_PERCEIVED_ROLES.get(player)));
        }
        if (roles.size() != players.size()) {
            return Result.fail("Every seated player needs a pending role before roles can be shuffled.");
        }
        Collections.shuffle(roles);
        StorytellerState.PENDING_PERCEIVED_ROLES.clear();
        for (int i = 0; i < players.size(); i++) {
            RoleBundle bundle = roles.get(i);
            UUID player = players.get(i);
            StorytellerState.PENDING_ROLES.put(player, bundle.actual());
            if (bundle.perceived() != null) StorytellerState.PENDING_PERCEIVED_ROLES.put(player, bundle.perceived());
        }
        return Result.ok("Shuffled " + roles.size() + " pending roles.");
    }

    public static Result shuffleSeats() {
        beginEditingFromLiveState();
        List<UUID> players = seatedPlayersInOrder();
        if (players.size() < 2) return Result.fail("At least two seated players are required.");
        List<Integer> seats = players.stream()
                .map(StorytellerState.PENDING_SEAT_NUMBERS::get)
                .toList();
        List<Integer> shuffled = new ArrayList<>(seats);
        Collections.shuffle(shuffled);
        for (int i = 0; i < players.size(); i++) {
            StorytellerState.PENDING_SEAT_NUMBERS.put(players.get(i), shuffled.get(i));
        }
        return Result.ok("Shuffled " + players.size() + " pending seat assignments.");
    }

    public static Result randomizeRoles() {
        Script script = ServerState.currentScript;
        if (script == null) return Result.fail("Load a script before randomizing roles.");
        beginEditingFromLiveState();
        List<UUID> players = seatedPlayersInOrder();
        int playerCount = players.size();
        Counts counts = countsFor(playerCount);
        if (counts == null) {
            return Result.fail("Standard random role setup supports 5-15 seated players; found " + playerCount + ".");
        }

        List<ScriptRole> selected = new ArrayList<>();
        Result townsfolk = takeRandom(script, RoleType.TOWNSFOLK, counts.townsfolk(), selected);
        if (!townsfolk.ok()) return townsfolk;
        Result outsiders = takeRandom(script, RoleType.OUTSIDER, counts.outsiders(), selected);
        if (!outsiders.ok()) return outsiders;
        Result minions = takeRandom(script, RoleType.MINION, counts.minions(), selected);
        if (!minions.ok()) return minions;
        Result demons = takeRandom(script, RoleType.DEMON, counts.demons(), selected);
        if (!demons.ok()) return demons;

        Collections.shuffle(selected);
        StorytellerState.PENDING_ROLES.clear();
        StorytellerState.PENDING_PERCEIVED_ROLES.clear();
        for (int i = 0; i < players.size(); i++) {
            StorytellerState.PENDING_ROLES.put(players.get(i),
                    assignment(selected.get(i), AlignmentOverride.DEFAULT));
        }
        randomizeBluffs();
        return Result.ok("Randomized roles for " + playerCount + " players ("
                + counts.townsfolk() + "T/" + counts.outsiders() + "O/"
                + counts.minions() + "M/" + counts.demons() + "D).");
    }

    /**
     * Sharktower role-bag assignment. The Storyteller explicitly chooses the
     * exact characters that go into the bag; the server only randomises which
     * seated player receives which chosen character. Roles remain pending until
     * SEND ROLES is used, matching the existing Storyteller workflow.
     */
    public static Result distributeRoleBag(List<String> roleIds) {
        Script script = ServerState.currentScript;
        if (script == null) return Result.fail("Load a script before using the role bag.");

        List<UUID> players = seatedPlayersInOrder();
        if (players.isEmpty()) return Result.fail("Seat players before using the role bag.");
        if (roleIds == null || roleIds.size() != players.size()) {
            int selected = roleIds == null ? 0 : roleIds.size();
            return Result.fail("Role bag has " + selected + " role(s), but " + players.size()
                    + " player(s) are seated.");
        }

        List<ScriptRole> bag = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String rawId : roleIds) {
            String roleId = rawId == null ? "" : rawId.trim();
            if (roleId.isEmpty()) return Result.fail("The role bag contains a blank role.");
            if (!seen.add(roleId)) {
                return Result.fail("The role bag contains " + roleId + " more than once.");
            }

            Optional<ScriptRole> scripted = script.getScriptRole(roleId);
            if (scripted.isEmpty()) {
                return Result.fail("Role '" + roleId + "' is not on the current script.");
            }
            ScriptRole role = scripted.get();
            if (role.getTeam() == RoleType.TRAVELER || role.getTeam() == RoleType.FABLED
                    || role.getTeam() == RoleType.LORIC || role.getTeam() == RoleType.NONE) {
                return Result.fail(role.getDisplayName() + " cannot be placed in the role bag.");
            }
            bag.add(role);
        }

        Collections.shuffle(bag);
        beginEditingFromLiveState();
        StorytellerState.PENDING_ROLES.clear();
        StorytellerState.PENDING_PERCEIVED_ROLES.clear();
        for (int i = 0; i < players.size(); i++) {
            StorytellerState.PENDING_ROLES.put(players.get(i),
                    assignment(bag.get(i), AlignmentOverride.DEFAULT));
        }

        long townsfolk = bag.stream().filter(role -> role.getTeam() == RoleType.TOWNSFOLK).count();
        long outsiders = bag.stream().filter(role -> role.getTeam() == RoleType.OUTSIDER).count();
        long minions = bag.stream().filter(role -> role.getTeam() == RoleType.MINION).count();
        long demons = bag.stream().filter(role -> role.getTeam() == RoleType.DEMON).count();
        return Result.ok("Distributed role bag to " + players.size() + " pending seats ("
                + townsfolk + "T/" + outsiders + "O/" + minions + "M/" + demons + "D). "
                + "Use SEND ROLES when ready.");
    }

    public static Result randomizeBluffs() {
        Script script = ServerState.currentScript;
        if (script == null) return Result.fail("Load a script before randomizing Demon bluffs.");

        Set<String> inPlay = workingRoles().values().stream()
                .filter(SetupOperations::isAssigned)
                .map(PendingRoleAssignment::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
        workingPerceivedRoles().values().stream()
                .filter(SetupOperations::isAssigned)
                .map(PendingRoleAssignment::getRoleId)
                .forEach(inPlay::add);

        List<ScriptRole> candidates = script.allRoles().stream()
                .filter(role -> role.getTeam() == RoleType.TOWNSFOLK || role.getTeam() == RoleType.OUTSIDER)
                .filter(role -> !inPlay.contains(role.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);

        StorytellerState.DEMON_BLUFFS.clear();
        StorytellerState.DEMON_BLUFFS.addAll(candidates.subList(0, Math.min(3, candidates.size())));
        if (StorytellerState.DEMON_BLUFFS.size() < 3) {
            return Result.fail("Only " + StorytellerState.DEMON_BLUFFS.size()
                    + " valid out-of-play good bluff(s) were available.");
        }
        return Result.ok("Randomized 3 Demon bluffs.");
    }

    public static Result addBluff(String roleId) {
        ScriptRole role = resolveScriptRole(roleId);
        if (role == null) return Result.fail("Unknown role '" + roleId + "'.");
        if (role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER) {
            return Result.fail("Demon bluffs must be good characters.");
        }
        if (StorytellerState.DEMON_BLUFFS.stream().anyMatch(existing -> existing.getId().equals(role.getId()))) {
            return Result.fail(role.getDisplayName() + " is already a bluff.");
        }
        if (StorytellerState.DEMON_BLUFFS.size() >= 3) {
            return Result.fail("There are already 3 Demon bluffs. Clear or replace them first.");
        }
        StorytellerState.DEMON_BLUFFS.add(role);
        return Result.ok("Added Demon bluff: " + role.getDisplayName() + ".");
    }

    /** Replaces one of the three original BOTB Demon bluff slots. */
    public static Result setBluff(int index, String roleId) {
        if (index < 0 || index > 2) return Result.fail("Demon bluff slot must be 1-3.");
        ScriptRole role = resolveScriptRole(roleId);
        if (role == null) return Result.fail("Unknown role '" + roleId + "'.");
        if (role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER) {
            return Result.fail("Demon bluffs must be good characters.");
        }
        for (int i = 0; i < StorytellerState.DEMON_BLUFFS.size(); i++) {
            if (i != index && StorytellerState.DEMON_BLUFFS.get(i).getId().equals(role.getId())) {
                return Result.fail(role.getDisplayName() + " is already a bluff.");
            }
        }
        if (index > StorytellerState.DEMON_BLUFFS.size()) {
            return Result.fail("Fill earlier Demon bluff slots first.");
        }
        if (index == StorytellerState.DEMON_BLUFFS.size()) StorytellerState.DEMON_BLUFFS.add(role);
        else StorytellerState.DEMON_BLUFFS.set(index, role);
        return Result.ok("Set Demon bluff slot " + (index + 1) + " to " + role.getDisplayName() + ".");
    }

    public static Result clearBluffs() {
        int count = StorytellerState.DEMON_BLUFFS.size();
        StorytellerState.DEMON_BLUFFS.clear();
        return Result.ok("Cleared " + count + " Demon bluff(s).");
    }

    public static Result addReminder(int seat, String text) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty()) return Result.fail("Reminder text cannot be blank.");
        StorytellerState.REMINDERS.computeIfAbsent(player, ignored -> new ArrayList<>())
                .add(new Reminder(cleaned, Optional.empty()));
        return Result.ok("Added reminder to seat " + seat + ": " + cleaned);
    }

    /** Add an original-style source-role reminder marker, e.g. Steward: Know or Imp: Kill. */
    public static Result addRoleReminder(int seat, String roleId, String text) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");

        ScriptRole sourceRole = resolveScriptRole(roleId);
        if (sourceRole == null) return Result.fail("Unknown reminder source role: " + roleId);

        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty()) return Result.fail("Reminder text cannot be blank.");

        Reminder reminder;
        if (sourceRole instanceof ScriptRole.Official official) {
            reminder = new Reminder(cleaned, Optional.of(official.role()));
        } else {
            // Custom/script-only roles keep their role id so the client can use
            // that script role's artwork when rendering the reminder token.
            reminder = Reminder.forCustomRole(cleaned, sourceRole.getId());
        }

        StorytellerState.REMINDERS.computeIfAbsent(player, ignored -> new ArrayList<>())
                .add(reminder);
        return Result.ok("Added " + sourceRole.getDisplayName()
                + " reminder to seat " + seat + ": " + cleaned);
    }

    public static Result removeReminder(int seat, int index) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        List<Reminder> list = StorytellerState.REMINDERS.get(player);
        if (list == null || index < 0 || index >= list.size()) return Result.fail("Reminder index is out of range.");
        Reminder removed = list.remove(index);
        if (list.isEmpty()) StorytellerState.REMINDERS.remove(player);
        return Result.ok("Removed reminder from seat " + seat + ": " + removed.text());
    }

    public static Result loadScriptJson(String json) {
        if (json == null || json.isBlank()) return Result.fail("Script JSON is empty.");
        java.util.Optional<Script> parsed = Script.fromJson(json);
        if (parsed.isEmpty()) return Result.fail("Script JSON could not be parsed.");
        ServerState.currentScript = parsed.get();
        StorytellerState.PENDING_ROLES.clear();
        StorytellerState.PENDING_PERCEIVED_ROLES.clear();
        StorytellerState.DEMON_BLUFFS.clear();
        return Result.ok("Loaded script: " + ServerState.currentScript.name() + " ("
                + ServerState.currentScript.allRoles().size() + " roles).");
    }

    public static Result clearReminders(int seat) {
        UUID player = playerBySeat(seat);
        if (player == null) return Result.fail("No player is assigned to seat " + seat + ".");
        int count = StorytellerState.REMINDERS.getOrDefault(player, List.of()).size();
        StorytellerState.REMINDERS.remove(player);
        return Result.ok("Cleared " + count + " reminder(s) from seat " + seat + ".");
    }

    public static Result clearAllReminders() {
        int count = StorytellerState.REMINDERS.values().stream().mapToInt(List::size).sum();
        StorytellerState.REMINDERS.clear();
        return Result.ok("Cleared " + count + " reminder(s).");
    }

    public static Result commitSetup(MinecraftServer server) {
        Map<UUID, Integer> seats = new HashMap<>(workingSeats());
        Map<UUID, PendingRoleAssignment> roles = new HashMap<>(workingRoles());
        Map<UUID, PendingRoleAssignment> perceivedRoles = new HashMap<>(workingPerceivedRoles());
        if (seats.isEmpty()) return Result.fail("No players are seated.");

        List<Integer> seatNumbers = seats.values().stream().sorted().toList();
        if (seatNumbers.stream().distinct().count() != seatNumbers.size()) {
            return Result.fail("Duplicate seat numbers exist; fix seating before sending roles.");
        }
        for (UUID player : seats.keySet()) {
            PendingRoleAssignment assignment = roles.get(player);
            if (!isAssigned(assignment)) {
                Integer seat = seats.get(player);
                return Result.fail("Seat " + seat + " does not have a role assigned.");
            }
            if (requiresPerceivedRole(assignment)) {
                PendingRoleAssignment perceived = perceivedRoles.get(player);
                if (!isAssigned(perceived)) {
                    return Result.fail("Seat " + seats.get(player) + " is " + assignment.getDisplayName()
                            + " and needs a believed role before SEND ROLES.");
                }
                if (!perceivedRoleAllowed(assignment, perceived)) {
                    return Result.fail("Seat " + seats.get(player) + " has an invalid believed role for "
                            + assignment.getDisplayName() + ".");
                }
            } else {
                perceivedRoles.remove(player);
            }
        }

        perceivedRoles.keySet().retainAll(seats.keySet());
        ServerState.updateSeats(seats);
        ServerState.updateRoles(roles);
        ServerState.updatePerceivedRoles(perceivedRoles);
        ServerState.PLAYER_DEATH_STATUS.keySet().retainAll(ServerState.PLAYER_SEAT_NUMBERS.keySet());
        for (UUID player : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            ServerState.PLAYER_DEATH_STATUS.put(player, false);
        }
        ServerState.gameEnded = false;
        ServerState.winningTeam = "NONE";
        ServerState.rolesRevealed = false;
        if (ServerState.currentNight == 0 && ServerState.currentDay == 0) {
            StorytellerState.resetDailyNightInfo();
        }
        StorytellerState.PENDING_ROLES.clear();
        StorytellerState.PENDING_PERCEIVED_ROLES.clear();
        StorytellerState.PENDING_SEAT_NUMBERS.clear();
        if (NightChatManager.isActive()) {
            NightChatManager.resync();
        }
        MatchSnapshotManager.Result snapshot = MatchSnapshotManager.captureAtGameStart(server);
        StateBroadcaster.broadcastCurrentState(server);
        return Result.ok("Committed " + ServerState.PLAYER_ROLES.size() + " role(s) and "
                + ServerState.PLAYER_SEAT_NUMBERS.size() + " seat(s); roles sent to connected players. "
                + snapshot.message());
    }

    public static Result discardPending(MinecraftServer server) {
        StorytellerState.PENDING_ROLES.clear();
        StorytellerState.PENDING_PERCEIVED_ROLES.clear();
        StorytellerState.PENDING_SEAT_NUMBERS.clear();
        StateBroadcaster.broadcastGrimoire(server);
        return Result.ok("Discarded pending role/seat edits.");
    }

    public static Result resetGame(MinecraftServer server, boolean hard) {
        // Full Reset is now the safe match rollback button. If a clean start
        // snapshot exists, restore it instead of permanently applying anything
        // that happened during the game.
        if (hard && MatchSnapshotManager.hasSnapshot()) {
            return restoreStartSnapshot(server, false);
        }

        NightChatManager.resetAll();
        TimerManager.stopTimer(server);
        ServerState.PLAYER_ROLES.clear();
        ServerState.PLAYER_PERCEIVED_ROLES.clear();
        ServerState.PLAYER_DEATH_STATUS.clear();
        ServerState.currentNight = 0;
        ServerState.currentDay = 0;
        ServerState.executionToday = false;
        ServerState.gameEnded = false;
        ServerState.winningTeam = "NONE";
        ServerState.rolesRevealed = false;
        DaytimeState.hardReset(Set.of(), Set.of());
        StorytellerState.clearSetupState();
        if (hard) {
            ServerState.PLAYER_SEAT_NUMBERS.clear();
            ServerState.currentScript = null;
            // Map setup is installation configuration, not match state. Seat homes,
            // town-square seat positions, clock placement and day-chat gates persist.
        }
        StateBroadcaster.broadcastCurrentState(server);
        return Result.ok(hard
                ? "Hard reset complete: game/script/player seats were cleared; persistent map setup was retained."
                : "Game reset complete: script and seats retained; roles/deaths/setup state cleared.");
    }

    /**
     * A.10 ceremonial game end. This deliberately does not restore the match
     * snapshot yet: players get a reveal/discussion window first.
     */
    public static Result beginEndGame(MinecraftServer server, String winningTeam) {
        if (server == null) return Result.fail("Server is not available.");
        if (ServerState.PLAYER_ROLES.isEmpty()) return Result.fail("No committed game is available to end.");

        String winner = winningTeam == null ? "" : winningTeam.trim().toUpperCase(java.util.Locale.ROOT);
        if (!winner.equals("GOOD") && !winner.equals("EVIL")) {
            return Result.fail("Winning team must be GOOD or EVIL.");
        }

        // End active runtime systems without touching deaths/exiles/roles so the
        // final Grimoire still reflects how the game actually ended.
        NightChatManager.resetAll();
        TimerManager.stopTimer(server);
        DaytimeState.closeNominations();
        DaytimeState.resetExile();

        boolean firstReveal = !ServerState.gameEnded;
        ServerState.gameEnded = true;
        ServerState.winningTeam = winner;
        ServerState.rolesRevealed = true;

        if (firstReveal) ModSounds.playGameEnd(server);
        StateBroadcaster.broadcastCurrentState(server);
        return Result.ok(winner + " wins. Final Grimoire reveal is now active.");
    }

    public static Result cancelEndGame(MinecraftServer server) {
        if (!ServerState.gameEnded) return Result.fail("The game is not currently in end-game reveal mode.");
        ServerState.gameEnded = false;
        ServerState.winningTeam = "NONE";
        ServerState.rolesRevealed = false;
        StateBroadcaster.broadcastCurrentState(server);
        return Result.ok("End-game reveal cancelled. Resume the appropriate phase manually.");
    }

    public static Result completeGame(MinecraftServer server) {
        return restoreStartSnapshot(server, false);
    }

    public static Result restorePreviousGameSnapshot(MinecraftServer server) {
        return restoreStartSnapshot(server, true);
    }

    private static Result restoreStartSnapshot(MinecraftServer server, boolean previous) {
        NightChatManager.resetAll();
        TimerManager.stopTimer(server);
        DaytimeState.hardReset(Set.of(), Set.of());
        StorytellerState.resetDailyNightInfo();
        MatchSnapshotManager.Result restored = previous
                ? MatchSnapshotManager.restorePrevious(server)
                : MatchSnapshotManager.restoreCurrent(server);
        if (!restored.ok()) return Result.fail(restored.message());
        if (NightChatManager.isActive()) NightChatManager.resetAll();
        StateBroadcaster.broadcastCurrentState(server);
        SeatPositionManager.sendAllToTownSquare(server, ServerState.PLAYER_SEAT_NUMBERS);
        return Result.ok(restored.message() + " Runtime deaths, ghost votes, nominations, hands, voice rooms and vote state were discarded.");
    }

    public static UUID playerBySeat(int seat) {
        Map<UUID, Integer> seats = workingSeats();
        for (Map.Entry<UUID, Integer> entry : seats.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == seat) return entry.getKey();
        }
        return null;
    }

    public static String summary() {
        int reminders = StorytellerState.REMINDERS.values().stream().mapToInt(List::size).sum();
        return "pendingRoles=" + StorytellerState.PENDING_ROLES.size()
                + ", pendingPerceivedRoles=" + StorytellerState.PENDING_PERCEIVED_ROLES.size()
                + ", pendingSeats=" + StorytellerState.PENDING_SEAT_NUMBERS.size()
                + ", reminders=" + reminders
                + ", bluffs=" + StorytellerState.DEMON_BLUFFS.size()
                + ", storytellers=" + StorytellerState.STORYTELLERS.size();
    }

    private static List<UUID> seatedPlayersInOrder() {
        Map<UUID, Integer> seats = workingSeats();
        return seats.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Result takeRandom(Script script, RoleType type, int count, List<ScriptRole> selected) {
        if (count == 0) return Result.ok("");
        List<ScriptRole> candidates = script.allRoles().stream()
                .filter(role -> role.getTeam() == type)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (candidates.size() < count) {
            return Result.fail("Script has only " + candidates.size() + " " + type.getDisplayName()
                    + " role(s); " + count + " are required.");
        }
        Collections.shuffle(candidates);
        selected.addAll(candidates.subList(0, count));
        return Result.ok("");
    }

    private static PendingRoleAssignment assignment(ScriptRole role, AlignmentOverride override) {
        if (role instanceof ScriptRole.Official official) {
            return new PendingRoleAssignment(official.role(), override);
        }
        if (role instanceof ScriptRole.Custom custom) {
            return new PendingRoleAssignment(custom.customRole(), override);
        }
        return new PendingRoleAssignment(Role.NO_ROLE, override);
    }

    private static ScriptRole resolveScriptRole(String roleId) {
        if (roleId == null || roleId.isBlank()) return null;
        Script script = ServerState.currentScript;
        if (script != null) {
            Optional<ScriptRole> scripted = script.getScriptRole(roleId);
            if (scripted.isPresent()) return scripted.get();
        }
        Role role = Role.findById(roleId);
        return role == null || role == Role.NO_ROLE ? null : new ScriptRole.Official(role);
    }

    private static boolean isAssigned(PendingRoleAssignment assignment) {
        return assignment != null && (assignment.isCustomRole() || assignment.role() != Role.NO_ROLE);
    }

    private static boolean requiresPerceivedRole(PendingRoleAssignment assignment) {
        return assignment != null && !assignment.isCustomRole()
                && (assignment.role() == Role.DRUNK || assignment.role() == Role.MARIONETTE);
    }

    private static boolean perceivedRoleAllowed(PendingRoleAssignment actual, PendingRoleAssignment perceived) {
        if (!requiresPerceivedRole(actual) || !isAssigned(perceived)) return false;
        RoleType type = perceived.getRoleType();
        if (actual.role() == Role.DRUNK) return type == RoleType.TOWNSFOLK;
        return type == RoleType.TOWNSFOLK || type == RoleType.OUTSIDER;
    }

    private static Counts countsFor(int players) {
        return switch (players) {
            case 5 -> new Counts(3, 0, 1, 1);
            case 6 -> new Counts(3, 1, 1, 1);
            case 7 -> new Counts(5, 0, 1, 1);
            case 8 -> new Counts(5, 1, 1, 1);
            case 9 -> new Counts(5, 2, 1, 1);
            case 10 -> new Counts(7, 0, 2, 1);
            case 11 -> new Counts(7, 1, 2, 1);
            case 12 -> new Counts(7, 2, 2, 1);
            case 13 -> new Counts(9, 0, 3, 1);
            case 14 -> new Counts(9, 1, 3, 1);
            case 15 -> new Counts(9, 2, 3, 1);
            default -> null;
        };
    }
}
