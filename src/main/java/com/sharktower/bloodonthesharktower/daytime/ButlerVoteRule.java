package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative Butler voting restriction.
 *
 * The Butler's Master is the player carrying the Butler-sourced "Master"
 * reminder token in the Storyteller Grimoire. A living Butler may only raise
 * their hand while that Master's hand is raised. Dead Butlers are unrestricted.
 */
public final class ButlerVoteRule {
    private ButlerVoteRule() {}

    public static boolean isLivingButler(UUID playerId) {
        if (playerId == null) return false;
        PendingRoleAssignment assignment = ServerState.PLAYER_ROLES.get(playerId);
        return assignment != null
                && !assignment.isCustomRole()
                && assignment.role() == Role.BUTLER
                && !Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(playerId));
    }

    /** Returns the current Butler Master from the reminder token, if configured. */
    public static UUID master() {
        return StorytellerState.REMINDERS.entrySet().stream()
                .filter(entry -> hasMasterReminder(entry.getValue()))
                .map(Map.Entry::getKey)
                .min(Comparator.comparingInt(id ->
                        ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(id, Integer.MAX_VALUE)))
                .orElse(null);
    }

    /** True when this player is unrestricted, or is a living Butler whose Master currently has a raised hand. */
    public static boolean mayRaiseHand(UUID playerId) {
        if (!isLivingButler(playerId)) return true;
        UUID master = master();
        return master != null && DaytimeState.isHandRaised(master);
    }

    /**
     * Called whenever a hand is lowered. Any living Butler following that player
     * must lower too unless their own vote is already locked.
     */
    public static void onHandLowered(MinecraftServer server, UUID loweredPlayer) {
        if (server == null || loweredPlayer == null) return;
        UUID master = master();
        if (!loweredPlayer.equals(master)) return;

        boolean changed = false;
        for (Map.Entry<UUID, PendingRoleAssignment> entry : ServerState.PLAYER_ROLES.entrySet()) {
            UUID butler = entry.getKey();
            if (!isLivingButler(butler) || isLocked(butler) || !DaytimeState.isHandRaised(butler)) continue;

            DaytimeState.setRaisedHand(butler, false);
            DaytimeState.setLeverState(butler, false);
            changed = true;

            ServerPlayer player = server.getPlayerList().getPlayer(butler);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                                "Your Master lowered their hand, so your Butler vote was lowered too.")
                        .withStyle(ChatFormatting.GOLD));
            }
        }

        if (changed) StateBroadcaster.broadcastVoteState(server);
    }

    /**
     * Defensive check at the exact lock-in moment. This prevents stale or
     * malicious client state from ever counting an illegal Butler YES.
     */
    public static boolean allowYesAtLock(MinecraftServer server, UUID playerId, boolean requestedYes) {
        if (!requestedYes || !isLivingButler(playerId)) return requestedYes;
        if (mayRaiseHand(playerId)) return true;

        DaytimeState.setRaisedHand(playerId, false);
        DaytimeState.setLeverState(playerId, false);

        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                            "Your Butler vote could not be counted because your Master is not voting.")
                    .withStyle(ChatFormatting.GOLD));
        }
        return false;
    }

    private static boolean isLocked(UUID playerId) {
        if (DaytimeState.isExileSupportInProgress()) {
            return DaytimeState.getLockedExileSupportVotes().containsKey(playerId);
        }
        return DaytimeState.getLockedVotes().containsKey(playerId);
    }

    private static boolean hasMasterReminder(List<Reminder> reminders) {
        if (reminders == null) return false;
        for (Reminder reminder : reminders) {
            if (reminder == null) continue;
            if (reminder.role().orElse(null) != Role.BUTLER) continue;
            if ("master".equals(normalize(reminder.text()))) return true;
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
