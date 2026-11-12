package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ravenkeeper: "If you die at night, you are woken to choose a player:
 * you learn their character."
 *
 * When the Ravenkeeper dies at night they get a private, clickable list of
 * seats ({@code /raven <seat>}). Their choice goes to the Storyteller's
 * Resolve panel with the chosen player's real character and anything that
 * changes the answer (drunk or poisoned, Recluse, Spy). The Storyteller then
 * shows a character privately ({@code /bots automation ravenkeeper show <role>})
 * or tells the Ravenkeeper in person. Nothing is shown without the Storyteller.
 */
public final class RavenkeeperAutomation {
    private static final String PROMPT = "ravenkeeper";

    private static UUID raven;
    private static UUID chosen;

    private RavenkeeperAutomation() {}

    public static void reset() {
        raven = null;
        chosen = null;
    }

    /** The Ravenkeeper waiting to choose or to be shown, if any. */
    public static UUID pending() {
        return raven;
    }

    /** Called for every death; acts only on a Ravenkeeper dying at night. */
    static void onDeath(MinecraftServer server, UUID dead, boolean execution, boolean night) {
        if (execution || !night || !RoleAutomation.is(dead, Role.RAVENKEEPER)) return;
        raven = dead;
        chosen = null;
        String who = RoleAutomation.label(server, dead);
        ServerPlayer player = server.getPlayerList().getPlayer(dead);
        if (player != null) {
            player.sendSystemMessage(Component.literal("Ravenkeeper: you died tonight. Choose a player to learn their character:")
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(seatButtons(server, dead, "/raven "));
        }
        RoleAutomation.tellStorytellers(server, Component.literal("Ravenkeeper: " + who + " died at night and "
                + (player == null ? "is offline. Ask them in person, or choose with /bots automation ravenkeeper choose <seat>."
                        : "is choosing a player.")).withStyle(ChatFormatting.GOLD));
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Ravenkeeper",
                who + " died at night and is choosing a player to learn their character.",
                List.of(StorytellerPrompt.Action.ok("I'll handle it", "/bots automation ravenkeeper dismiss"))));
    }

    /** Every seat as a clickable {@code [N Name]} that runs {@code command + seat}. */
    static Component seatButtons(MinecraftServer server, UUID self, String command) {
        List<Map.Entry<UUID, Integer>> seats = new ArrayList<>(ServerState.PLAYER_SEAT_NUMBERS.entrySet());
        seats.sort(Comparator.comparingInt(Map.Entry::getValue));
        MutableComponent line = Component.empty();
        boolean first = true;
        for (var seat : seats) {
            if (!first) line.append(Component.literal("  "));
            first = false;
            String name = seat.getValue() + " " + RoleAutomation.label(server, seat.getKey())
                    + (seat.getKey().equals(self) ? " (you)" : "");
            line.append(Component.literal("[" + name + "]")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand(command + seat.getValue()))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Choose " + name)))));
        }
        return line;
    }

    /** The Ravenkeeper (or the Storyteller for them) chose {@code target}. Returns why not, or null. */
    public static String choose(MinecraftServer server, UUID chooser, UUID target, boolean byStoryteller) {
        if (raven == null) return "No Ravenkeeper is waiting to choose.";
        if (!byStoryteller && !raven.equals(chooser)) return "Only the Ravenkeeper who died tonight can choose.";
        if (chosen != null) return "The Ravenkeeper has already chosen.";
        if (target == null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(target)) return "That player is not seated.";
        chosen = target;
        String who = RoleAutomation.label(server, raven);
        String whom = RoleAutomation.label(server, target);
        ServerPlayer player = server.getPlayerList().getPlayer(raven);
        if (player != null) {
            player.sendSystemMessage(Component.literal("You chose " + whom + ". The Storyteller will show you their character.")
                    .withStyle(ChatFormatting.GRAY));
        }

        PendingRoleAssignment real = RoleAutomation.actualRole(target);
        String realName = real == null ? "no character" : RoleAutomation.pretty(real.getDisplayName());
        StringBuilder body = new StringBuilder(who + " chose " + whom + ", who is the " + realName + ".");
        if (RoleAutomation.droisoned(raven)) body.append(" " + who + " is drunk or poisoned: you may show any character.");
        if (RoleAutomation.is(target, Role.RECLUSE)) body.append(" The Recluse may register as a Minion or Demon.");
        if (RoleAutomation.is(target, Role.SPY)) body.append(" The Spy may register as a Townsfolk or Outsider.");

        List<StorytellerPrompt.Action> actions = new ArrayList<>();
        if (real != null) {
            String id = real.isCustomRole() ? real.getScriptRole().getId() : real.role().getId();
            actions.add(StorytellerPrompt.Action.neutral("Show " + realName, "/bots automation ravenkeeper show " + id));
        }
        actions.add(StorytellerPrompt.Action.ok("I'll tell them", "/bots automation ravenkeeper dismiss"));
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Ravenkeeper", body.toString(), actions));

        Component chat = Component.literal("Ravenkeeper: " + body + " ").withStyle(ChatFormatting.GOLD)
                .append(real == null ? Component.empty() : RoleAutomation.button("[Show " + realName + "]",
                        actions.get(0).command(), "Privately show " + who + " that " + whom + " is the " + realName))
                .append(Component.literal(" "))
                .append(Component.literal("[Show another...]").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand("/bots automation ravenkeeper show "))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Type the character to show")))));
        RoleAutomation.tellStorytellers(server, chat);
        return null;
    }

    /** The Storyteller shows the Ravenkeeper that the chosen player is {@code roleId}. */
    public static String show(MinecraftServer server, String roleId) {
        if (raven == null || chosen == null) return "No Ravenkeeper choice is waiting.";
        ScriptRole role = SetupOperations.resolveScriptRole(roleId);
        if (role == null) return "Unknown character '" + roleId + "'.";
        String whom = RoleAutomation.label(server, chosen);
        String name = RoleAutomation.pretty(role.getDisplayName());
        ServerPlayer player = server.getPlayerList().getPlayer(raven);
        if (player == null) return "The Ravenkeeper is offline. Tell them in person, then dismiss.";
        player.sendSystemMessage(Component.literal("Ravenkeeper: " + whom + " is the " + name + ".")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        RoleAutomation.tellStorytellers(server, Component.literal("Ravenkeeper: showed "
                + RoleAutomation.label(server, raven) + " that " + whom + " is the " + name + ".").withStyle(ChatFormatting.GRAY));
        finish();
        return null;
    }

    public static String dismiss() {
        if (raven == null) return "No Ravenkeeper is waiting.";
        finish();
        return null;
    }

    private static void finish() {
        reset();
        RoleAutomation.resolve(PROMPT);
    }
}
