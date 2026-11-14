package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.networking.StorytellerActionHandler;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Night choices made in game, each confirmed by the Storyteller, like the
 * Poisoner's ({@link PoisonerAutomation}).
 *
 * <ul>
 *   <li><b>Monk</b>: "Each night*, choose a player (not yourself): they are
 *   safe from the Demon tonight." {@code /protect <seat>}; <b>Protect</b>
 *   places the Monk's "Safe" reminder, which clears at dawn.</li>
 *   <li><b>Imp</b>: "Each night*, choose a player: they die. If you kill
 *   yourself this way, a Minion becomes the Imp." {@code /attack <seat>};
 *   <b>Kill</b> runs the Grimoire's Demon kill, which checks the Monk,
 *   Soldier, Mayor and so on, and offers the star pass on a self-kill.</li>
 * </ul>
 *
 * Both start at dusk, so never on the first night. The player can change
 * their mind until the Storyteller acts, and nothing happens without the
 * Storyteller.
 */
public final class NightChoices {
    public enum Kind {
        MONK(Role.MONK, "monk", "Monk", "/protect ", "choose a player to protect from the Demon tonight (not yourself)",
                ChatFormatting.AQUA),
        IMP(Role.IMP, "imp", "Imp", "/attack ", "choose a player to kill tonight",
                ChatFormatting.DARK_RED);

        final Role role;
        final String id;
        final String title;
        final String command;
        final String ask;
        final ChatFormatting colour;

        Kind(Role role, String id, String title, String command, String ask, ChatFormatting colour) {
            this.role = role;
            this.id = id;
            this.title = title;
            this.command = command;
            this.ask = ask;
            this.colour = colour;
        }
    }

    private record Choice(UUID chooser, UUID target, int night) {}

    private static final Map<Kind, Choice> WAITING = new EnumMap<>(Kind.class);

    private NightChoices() {}

    public static void reset() {
        for (Kind kind : Kind.values()) RoleAutomation.resolve(kind.id);
        WAITING.clear();
    }

    /** Dusk: wake each living chooser. */
    static void onDusk(MinecraftServer server) {
        reset();
        if (server == null || ServerState.gameEnded) return;
        for (Kind kind : Kind.values()) {
            UUID found = RoleAutomation.findAlive(kind.role);
            if (found == null) continue;
            WAITING.put(kind, new Choice(found, null, ServerState.currentDay));
            String who = RoleAutomation.label(server, found);
            ServerPlayer player = server.getPlayerList().getPlayer(found);
            if (player != null) {
                player.sendSystemMessage(Component.literal(kind.title + ": " + kind.ask + ":").withStyle(kind.colour));
                player.sendSystemMessage(RavenkeeperAutomation.seatButtons(server, found, kind.command));
            }
            RoleAutomation.offer(server, new StorytellerPrompt(kind.id, kind.title,
                    who + " is " + kind.ask.replace("choose", "choosing")
                            + (player == null ? " but is offline. Ask them, then use /bots automation "
                            + kind.id + " choose <seat>." : "."),
                    List.of(StorytellerPrompt.Action.ok("I'll handle it", "/bots automation " + kind.id + " dismiss"))));
        }
    }

    /** {@code chooser} (or the Storyteller for them) picked {@code target}. Returns why not, or null. */
    public static String choose(MinecraftServer server, Kind kind, UUID chooser, UUID target, boolean byStoryteller) {
        Choice waiting = WAITING.get(kind);
        if (waiting == null) return "The " + kind.title + " is not choosing right now.";
        if (!byStoryteller && !waiting.chooser().equals(chooser)) return "Only the " + kind.title + " can choose.";
        if (!byStoryteller && (!PhaseOperations.isNight() || ServerState.currentDay != waiting.night())) {
            return "The " + kind.title + " chooses at night.";
        }
        if (target == null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(target)) return "That player is not seated.";
        if (kind == Kind.MONK && target.equals(waiting.chooser())) return "The Monk cannot protect themselves.";
        WAITING.put(kind, new Choice(waiting.chooser(), target, waiting.night()));

        UUID self = waiting.chooser();
        String who = RoleAutomation.label(server, self);
        String whom = RoleAutomation.label(server, target);
        ServerPlayer player = server.getPlayerList().getPlayer(self);
        if (player != null && !byStoryteller) {
            player.sendSystemMessage(Component.literal("You chose " + whom + ". You can change your mind until the Storyteller acts.")
                    .withStyle(ChatFormatting.GRAY));
        }

        PendingRoleAssignment real = RoleAutomation.actualRole(target);
        String body = who + " chose " + (target.equals(self) ? "themselves" : whom)
                + (real == null || target.equals(self) ? "." : ", the " + RoleAutomation.pretty(real.getDisplayName()) + ".");
        String action;
        if (kind == Kind.MONK) {
            body += RoleAutomation.droisoned(self) ? " " + who + " is drunk or poisoned, so the protection should not land."
                    : " They are safe from the Demon tonight.";
            action = "Protect";
        } else {
            body += " " + impAdvice(server, self, target);
            action = "Kill";
        }
        List<StorytellerPrompt.Action> actions = List.of(
                kind == Kind.IMP ? StorytellerPrompt.Action.danger(action, "/bots automation imp confirm")
                        : StorytellerPrompt.Action.ok(action, "/bots automation monk confirm"),
                StorytellerPrompt.Action.neutral(kind == Kind.IMP ? "No kill" : "No protection",
                        "/bots automation " + kind.id + " dismiss"));
        RoleAutomation.offer(server, new StorytellerPrompt(kind.id, kind.title, body, actions));
        RoleAutomation.tellStorytellers(server, Component.literal(kind.title + ": " + body + " ").withStyle(kind.colour)
                .append(RoleAutomation.button("[" + action + "]", "/bots automation " + kind.id + " confirm",
                        action + " " + whom)));
        return null;
    }

    /** What should happen to the Imp's target, without spending anything (the Fool, the Mayor) yet. */
    private static String impAdvice(MinecraftServer server, UUID imp, UUID target) {
        if (RoleAutomation.droisoned(imp)) return "The Imp is drunk or poisoned, so nobody should die.";
        if (target.equals(imp)) return "A self-kill: a Minion becomes the Imp (you pick who after Kill).";
        if (!RoleAutomation.isAlive(target)) return "They are already dead, so nothing happens.";
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(target, List.of())) {
            String text = reminder.text().toLowerCase(java.util.Locale.ROOT);
            if (text.equals("safe") || text.equals("protected")) {
                String by = reminder.role().map(role -> "the " + RoleAutomation.pretty(role.getDisplayName())).orElse("a reminder");
                return "They are marked Safe by " + by + ": Kill will check whether that protection holds.";
            }
        }
        if (RoleAutomation.is(target, Role.SOLDIER) && !RoleAutomation.droisoned(target)) {
            return "They are the sober Soldier, so they should not die.";
        }
        if (RoleAutomation.is(target, Role.MAYOR) && !RoleAutomation.droisoned(target)) {
            return "They are the Mayor: you may have another player die instead.";
        }
        return "They should die tonight.";
    }

    /** The Storyteller confirms the waiting choice. */
    public static String confirm(MinecraftServer server, Kind kind) {
        Choice waiting = WAITING.get(kind);
        if (waiting == null || waiting.target() == null) return "No " + kind.title + " choice is waiting.";
        UUID target = waiting.target();
        if (kind == Kind.MONK) {
            List<Reminder> list = StorytellerState.REMINDERS.computeIfAbsent(target, ignored -> new ArrayList<>());
            list.removeIf(reminder -> reminder.role().orElse(null) == Role.MONK && reminder.text().equalsIgnoreCase("Safe"));
            list.add(new Reminder("Safe", Optional.of(Role.MONK)));
            StateBroadcaster.broadcastGrimoire(server);
            RoleAutomation.tellStorytellers(server, Component.literal("Monk: " + RoleAutomation.label(server, target)
                    + " is safe from the Demon until dawn.").withStyle(ChatFormatting.GRAY));
            finish(kind);
            return null;
        }
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(target);
        if (seat == null) return "That player is no longer seated.";
        // Keep the prompt until the kill goes through, so a refusal can be overridden or dismissed.
        var result = StorytellerActionHandler.markDead(server, seat, true);
        if (!result.ok()) return result.message() + " Press No kill to close this.";
        RoleAutomation.tellStorytellers(server, Component.literal("Imp: " + result.message()).withStyle(ChatFormatting.GRAY));
        finish(kind);
        return null;
    }

    public static String dismiss(Kind kind) {
        if (!WAITING.containsKey(kind)) return "The " + kind.title + " is not choosing right now.";
        finish(kind);
        return null;
    }

    private static void finish(Kind kind) {
        WAITING.remove(kind);
        RoleAutomation.resolve(kind.id);
    }
}
