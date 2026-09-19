package com.sharktower.bloodonthesharktower.networking;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.CustomRole;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Small wire-format bridge used while the remaining original BOTB packet
 * codecs are being bulk-ported to Minecraft 26.2.
 *
 * The public payload shapes remain the original maps/collections; only their
 * 26.2 StreamCodec transport is temporarily JSON-backed so we can restore the
 * whole gameplay state family in one batch.
 */
final class StateWire {
    private static final Gson GSON = new Gson();
    private static final Type STRING_INT_MAP = new TypeToken<Map<String, Integer>>() {}.getType();
    private static final Type STRING_BOOL_MAP = new TypeToken<Map<String, Boolean>>() {}.getType();

    private StateWire() {}

    static String seatsToJson(Map<UUID, Integer> seats) {
        Map<String, Integer> wire = new LinkedHashMap<>();
        seats.forEach((id, seat) -> wire.put(id.toString(), seat));
        return GSON.toJson(wire);
    }

    static Map<UUID, Integer> seatsFromJson(String json) {
        Map<String, Integer> wire = GSON.fromJson(json, STRING_INT_MAP);
        Map<UUID, Integer> result = new HashMap<>();
        if (wire != null) {
            wire.forEach((id, seat) -> {
                try {
                    result.put(UUID.fromString(id), seat);
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
        return result;
    }

    static String deathToJson(Map<UUID, Boolean> deaths) {
        Map<String, Boolean> wire = new LinkedHashMap<>();
        deaths.forEach((id, dead) -> wire.put(id.toString(), dead));
        return GSON.toJson(wire);
    }

    static Map<UUID, Boolean> deathFromJson(String json) {
        Map<String, Boolean> wire = GSON.fromJson(json, STRING_BOOL_MAP);
        Map<UUID, Boolean> result = new HashMap<>();
        if (wire != null) {
            wire.forEach((id, dead) -> {
                try {
                    result.put(UUID.fromString(id), Boolean.TRUE.equals(dead));
                } catch (IllegalArgumentException ignored) {
                }
            });
        }
        return result;
    }

    static String grimoireToJson(
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, PendingRoleAssignment> perceivedRoles,
            Map<UUID, Integer> seats,
            Map<UUID, List<Reminder>> reminders,
            List<String> demonBluffs,
            boolean targeted
    ) {
        Map<String, AssignmentWire> roleWire = new LinkedHashMap<>();
        roles.forEach((id, assignment) -> roleWire.put(id.toString(), AssignmentWire.of(assignment)));

        Map<String, AssignmentWire> perceivedRoleWire = new LinkedHashMap<>();
        perceivedRoles.forEach((id, assignment) -> perceivedRoleWire.put(id.toString(), AssignmentWire.of(assignment)));

        Map<String, Integer> seatWire = new LinkedHashMap<>();
        seats.forEach((id, seat) -> seatWire.put(id.toString(), seat));

        Map<String, List<ReminderWire>> reminderWire = new LinkedHashMap<>();
        reminders.forEach((id, list) -> reminderWire.put(
                id.toString(),
                list == null ? List.of() : list.stream().map(ReminderWire::of).toList()
        ));

        List<String> bluffIds = demonBluffs == null ? List.of() : List.copyOf(demonBluffs);

        return GSON.toJson(new GrimoireWire(roleWire, perceivedRoleWire, seatWire, reminderWire, bluffIds, targeted));
    }

    static GrimoireDecoded grimoireFromJson(String json, Script script) {
        GrimoireWire wire = GSON.fromJson(json, GrimoireWire.class);
        if (wire == null) {
            return new GrimoireDecoded(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), false);
        }

        Map<UUID, PendingRoleAssignment> roles = new HashMap<>();
        if (wire.roles != null) {
            wire.roles.forEach((id, assignment) -> {
                try {
                    roles.put(UUID.fromString(id), assignment.toAssignment(script));
                } catch (IllegalArgumentException ignored) {
                }
            });
        }

        Map<UUID, PendingRoleAssignment> perceivedRoles = new HashMap<>();
        if (wire.perceivedRoles != null) {
            wire.perceivedRoles.forEach((id, assignment) -> {
                try {
                    perceivedRoles.put(UUID.fromString(id), assignment.toAssignment(script));
                } catch (IllegalArgumentException ignored) {
                }
            });
        }

        Map<UUID, Integer> seats = new HashMap<>();
        if (wire.seats != null) {
            wire.seats.forEach((id, seat) -> {
                try {
                    seats.put(UUID.fromString(id), seat);
                } catch (IllegalArgumentException ignored) {
                }
            });
        }

        Map<UUID, List<Reminder>> reminders = new HashMap<>();
        if (wire.reminders != null) {
            wire.reminders.forEach((id, list) -> {
                try {
                    reminders.put(UUID.fromString(id), list == null
                            ? List.of()
                            : list.stream().map(ReminderWire::toReminder).toList());
                } catch (IllegalArgumentException ignored) {
                }
            });
        }

        return new GrimoireDecoded(
                roles,
                perceivedRoles,
                seats,
                reminders,
                wire.demonBluffs == null ? List.of() : List.copyOf(wire.demonBluffs),
                wire.targeted
        );
    }

    private static CustomRole placeholderCustomRole(String id) {
        return new CustomRole(
                id,
                id,
                RoleType.TOWNSFOLK,
                "",
                "",
                List.of(),
                0.0,
                0.0,
                "",
                "",
                List.of(),
                List.of(),
                false,
                List.of()
        );
    }

    private record AssignmentWire(String roleId, boolean custom, String alignment) {
        static AssignmentWire of(PendingRoleAssignment assignment) {
            if (assignment == null) {
                return new AssignmentWire("norole", false, AlignmentOverride.DEFAULT.name());
            }
            return new AssignmentWire(
                    assignment.getRoleId(),
                    assignment.isCustomRole(),
                    assignment.override().name()
            );
        }

        PendingRoleAssignment toAssignment(Script script) {
            AlignmentOverride override;
            try {
                override = AlignmentOverride.valueOf(alignment);
            } catch (Exception ignored) {
                override = AlignmentOverride.DEFAULT;
            }

            if (custom) {
                CustomRole customRole = script == null
                        ? placeholderCustomRole(roleId)
                        : script.getCustomRole(roleId).orElseGet(() -> placeholderCustomRole(roleId));
                return new PendingRoleAssignment(customRole, override);
            }

            Role role = Role.findById(roleId);
            if (role == null) role = Role.NO_ROLE;
            return new PendingRoleAssignment(role, override);
        }
    }

    private record ReminderWire(String text, String roleId, String customRoleId, String playerUuid) {
        static ReminderWire of(Reminder reminder) {
            if (reminder == null) return new ReminderWire("", "", "", "");
            return new ReminderWire(
                    reminder.text(),
                    reminder.role().map(Role::getId).orElse(""),
                    reminder.customRoleId().orElse(""),
                    reminder.playerUuid().map(UUID::toString).orElse("")
            );
        }

        Reminder toReminder() {
            Role role = roleId == null || roleId.isBlank() ? null : Role.findById(roleId);
            Optional<Role> roleOptional = Optional.ofNullable(role).filter(value -> value != Role.NO_ROLE);
            Optional<String> custom = customRoleId == null || customRoleId.isBlank()
                    ? Optional.empty()
                    : Optional.of(customRoleId);
            Optional<UUID> player = Optional.empty();
            if (playerUuid != null && !playerUuid.isBlank()) {
                try {
                    player = Optional.of(UUID.fromString(playerUuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new Reminder(text, roleOptional, custom, player);
        }
    }

    private static final class GrimoireWire {
        Map<String, AssignmentWire> roles;
        Map<String, AssignmentWire> perceivedRoles;
        Map<String, Integer> seats;
        Map<String, List<ReminderWire>> reminders;
        List<String> demonBluffs;
        boolean targeted;

        GrimoireWire(
                Map<String, AssignmentWire> roles,
                Map<String, AssignmentWire> perceivedRoles,
                Map<String, Integer> seats,
                Map<String, List<ReminderWire>> reminders,
                List<String> demonBluffs,
                boolean targeted
        ) {
            this.roles = roles;
            this.perceivedRoles = perceivedRoles;
            this.seats = seats;
            this.reminders = reminders;
            this.demonBluffs = demonBluffs;
            this.targeted = targeted;
        }
    }

    record GrimoireDecoded(
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, PendingRoleAssignment> perceivedRoles,
            Map<UUID, Integer> seats,
            Map<UUID, List<Reminder>> reminders,
            List<String> demonBluffs,
            boolean targeted
    ) {}
}
