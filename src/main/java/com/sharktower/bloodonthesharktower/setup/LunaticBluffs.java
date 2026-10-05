package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Separate fake bluff set used only for a Lunatic.
 *
 * These never share storage or player sync with the real Demon bluffs. The ST
 * can prepare the three fake bluffs during setup as soon as a Lunatic is in the
 * working Grim, then explicitly send them after roles are committed.
 */
public final class LunaticBluffs {
    private static Script selectedForScript;

    private LunaticBluffs() {}

    public static SetupOperations.Result set(List<String> roleIds) {
        Script script = ServerState.currentScript;
        if (script == null) return SetupOperations.Result.fail("Load a script before choosing Lunatic bluffs.");
        if (!lunaticAssigned()) return SetupOperations.Result.fail("There is no Lunatic in the current setup.");
        if (roleIds == null || roleIds.size() != 3) {
            return SetupOperations.Result.fail("Choose exactly 3 Lunatic bluffs.");
        }

        Set<String> unavailable = unavailableRoleIds();
        Set<String> seen = new HashSet<>();
        List<ScriptRole> chosen = new ArrayList<>();

        for (String raw : roleIds) {
            String id = raw == null ? "" : raw.trim();
            ScriptRole role = script.getScriptRole(id).orElse(null);
            if (role == null) return SetupOperations.Result.fail("Unknown Lunatic bluff role '" + id + "'.");
            if (role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER) {
                return SetupOperations.Result.fail("Lunatic bluffs must be good characters.");
            }

            String key = key(role.getId());
            if (!seen.add(key)) {
                return SetupOperations.Result.fail(role.getDisplayName() + " was selected more than once.");
            }
            if (unavailable.contains(key)) {
                return SetupOperations.Result.fail(role.getDisplayName()
                        + " is in play or shown as a believed role and cannot be a Lunatic bluff.");
            }
            chosen.add(role);
        }

        StorytellerState.LUNATIC_BLUFFS.clear();
        StorytellerState.LUNATIC_BLUFFS.addAll(chosen);
        selectedForScript = script;
        return SetupOperations.Result.ok("Set Lunatic bluffs: "
                + String.join(", ", chosen.stream().map(ScriptRole::getDisplayName).toList()) + ".");
    }

    public static SetupOperations.Result send(MinecraftServer server) {
        List<ScriptRole> bluffs = current();
        if (bluffs.size() != 3) return SetupOperations.Result.fail("Choose 3 Lunatic bluffs before sending them.");

        List<ServerPlayer> recipients = new ArrayList<>();
        for (var entry : ServerState.PLAYER_ROLES.entrySet()) {
            if (!isLunatic(entry.getValue())) continue;
            if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(entry.getKey())) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return SetupOperations.Result.fail("The Lunatic is offline. No bluff information was sent.");
            }
            recipients.add(player);
        }
        if (recipients.isEmpty()) return SetupOperations.Result.fail("There is no Lunatic in the committed game.");

        String names = String.join(", ", bluffs.stream().map(ScriptRole::getDisplayName).toList());
        // Never identify the recipient as the Lunatic in their own message.
        Component message = Component.literal("[Demon Info]\nBluffs: " + names);
        recipients.forEach(player -> player.sendSystemMessage(message));
        return SetupOperations.Result.ok("Sent the 3 fake Demon bluffs privately to "
                + recipients.size() + " Lunatic player(s).");
    }

    public static List<ScriptRole> current() {
        // A reset clears both committed and pending roles. Drop the old fake set
        // once there is no Lunatic in either the working setup or live game.
        if (!lunaticAssigned()) {
            clear();
            return List.of();
        }
        if (selectedForScript != null && selectedForScript != ServerState.currentScript) {
            clear();
        }
        return List.copyOf(StorytellerState.LUNATIC_BLUFFS);
    }

    public static List<String> currentIds() {
        return current().stream().map(ScriptRole::getId).toList();
    }

    public static void clear() {
        StorytellerState.LUNATIC_BLUFFS.clear();
        selectedForScript = null;
    }

    /** True while a Lunatic is in the pending setup or committed live game. */
    public static boolean lunaticAssigned() {
        return SetupOperations.workingRoles().values().stream().anyMatch(LunaticBluffs::isLunatic);
    }

    public static boolean lunaticInPlay() {
        return ServerState.PLAYER_ROLES.values().stream().anyMatch(LunaticBluffs::isLunatic);
    }

    public static boolean lunaticOnScript() {
        Script script = ServerState.currentScript;
        return script != null && script.allRoles().stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getId().equalsIgnoreCase(Role.LUNATIC.getId()));
    }

    private static Set<String> unavailableRoleIds() {
        Set<String> unavailable = new HashSet<>();
        SetupOperations.workingRoles().values().stream()
                .filter(Objects::nonNull)
                .map(PendingRoleAssignment::getRoleId)
                .filter(Objects::nonNull)
                .map(LunaticBluffs::key)
                .forEach(unavailable::add);
        SetupOperations.workingPerceivedRoles().values().stream()
                .filter(Objects::nonNull)
                .map(PendingRoleAssignment::getRoleId)
                .filter(Objects::nonNull)
                .map(LunaticBluffs::key)
                .forEach(unavailable::add);
        return unavailable;
    }

    private static boolean isLunatic(PendingRoleAssignment assignment) {
        return assignment != null && assignment.isOfficialRole() && assignment.role() == Role.LUNATIC;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
