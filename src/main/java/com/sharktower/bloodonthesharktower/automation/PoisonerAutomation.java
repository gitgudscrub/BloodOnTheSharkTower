package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Poisoner: "Each night, choose a player: they are poisoned tonight and
 * tomorrow day."
 *
 * At dusk the living Poisoner gets a private, clickable list of seats
 * ({@code /poison <seat>}); they may change their mind until the Storyteller
 * acts. The choice goes to the Resolve panel with the target's character, and
 * <b>Poison</b> places the Poisoner's "Poisoned" reminder, which the existing
 * dusk expiry clears the next evening. Every rule check that asks "drunk or
 * poisoned" then sees it. Nothing is placed without the Storyteller.
 */
public final class PoisonerAutomation {
    private static final String PROMPT = "poisoner";

    private static UUID poisoner;
    private static UUID chosen;
    private static int night = -1;

    private PoisonerAutomation() {}

    public static void reset() {
        poisoner = null;
        chosen = null;
        night = -1;
    }

    /** Dusk (after expiry cleared yesterday's poison): wake the living Poisoner to choose. */
    static void onDusk(MinecraftServer server) {
        RoleAutomation.resolve(PROMPT);
        reset();
        if (server == null || ServerState.gameEnded) return;
        UUID found = RoleAutomation.findAlive(Role.POISONER);
        if (found == null) return;
        poisoner = found;
        night = ServerState.currentDay;
        String who = RoleAutomation.label(server, found);
        ServerPlayer player = server.getPlayerList().getPlayer(found);
        if (player != null) {
            player.sendSystemMessage(Component.literal("Poisoner: choose a player to poison tonight:")
                    .withStyle(ChatFormatting.DARK_GREEN));
            player.sendSystemMessage(RavenkeeperAutomation.seatButtons(server, found, "/poison "));
        }
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Poisoner",
                who + " is choosing a player to poison"
                        + (player == null ? " but is offline. Ask them, then use /bots automation poisoner choose <seat>." : "."),
                List.of(StorytellerPrompt.Action.ok("I'll handle it", "/bots automation poisoner dismiss"))));
    }

    /** The Poisoner (or the Storyteller for them) chose {@code target}. Returns why not, or null. */
    public static String choose(MinecraftServer server, UUID chooser, UUID target, boolean byStoryteller) {
        if (poisoner == null) return "The Poisoner is not choosing right now.";
        if (!byStoryteller && !poisoner.equals(chooser)) return "Only the Poisoner can choose.";
        if (!byStoryteller && (!PhaseOperations.isNight() || ServerState.currentDay != night)) {
            return "The Poisoner chooses at night.";
        }
        if (target == null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(target)) return "That player is not seated.";
        chosen = target;
        String who = RoleAutomation.label(server, poisoner);
        String whom = RoleAutomation.label(server, target);
        ServerPlayer player = server.getPlayerList().getPlayer(poisoner);
        if (player != null && !byStoryteller) {
            player.sendSystemMessage(Component.literal("You chose " + whom + ". You can change your mind until the Storyteller acts.")
                    .withStyle(ChatFormatting.GRAY));
        }

        PendingRoleAssignment real = RoleAutomation.actualRole(target);
        String body = who + " chose " + whom
                + (real == null ? "." : ", the " + RoleAutomation.pretty(real.getDisplayName()) + ".");
        if (RoleAutomation.droisoned(poisoner)) body += " " + who + " is drunk or poisoned, so the poison should not land.";
        else body += " They are poisoned tonight and tomorrow day.";
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Poisoner", body, List.of(
                StorytellerPrompt.Action.ok("Poison", "/bots automation poisoner poison"),
                StorytellerPrompt.Action.neutral("No poison", "/bots automation poisoner dismiss"))));
        RoleAutomation.tellStorytellers(server, Component.literal("Poisoner: " + body + " ").withStyle(ChatFormatting.DARK_GREEN)
                .append(RoleAutomation.button("[Poison]", "/bots automation poisoner poison",
                        "Place the Poisoner's Poisoned reminder on " + whom)));
        return null;
    }

    /** The Storyteller confirms: place the Poisoned reminder on the chosen player. */
    public static String poison(MinecraftServer server) {
        if (poisoner == null || chosen == null) return "No Poisoner choice is waiting.";
        UUID target = chosen;
        List<Reminder> list = StorytellerState.REMINDERS.computeIfAbsent(target, ignored -> new ArrayList<>());
        list.removeIf(reminder -> reminder.role().orElse(null) == Role.POISONER
                && reminder.text().equalsIgnoreCase("Poisoned"));
        list.add(new Reminder("Poisoned", Optional.of(Role.POISONER)));
        StateBroadcaster.broadcastGrimoire(server);
        RoleAutomation.tellStorytellers(server, Component.literal("Poisoner: " + RoleAutomation.label(server, target)
                + " is poisoned until dusk.").withStyle(ChatFormatting.GRAY));
        finish();
        return null;
    }

    public static String dismiss() {
        if (poisoner == null) return "The Poisoner is not choosing right now.";
        finish();
        return null;
    }

    private static void finish() {
        reset();
        RoleAutomation.resolve(PROMPT);
    }
}
