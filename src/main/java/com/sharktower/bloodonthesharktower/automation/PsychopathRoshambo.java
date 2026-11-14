package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.ExecutionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Psychopath's second clause: "If executed, you only die if you lose
 * roshambo."
 *
 * When the Storyteller executes a sober Psychopath (or an Alchemist with the
 * Psychopath ability) who was nominated by another player, the execution
 * waits. Both players get Rock / Paper / Scissors buttons in chat
 * ({@code /roshambo <pick>}); the picks stay secret until both are in, then
 * everyone sees them. The Storyteller rules in the Resolve panel: <b>Dies</b>
 * runs the execution, <b>Lives</b> is an execution the Psychopath survives.
 * Either button works at any time, so an offline player never blocks the day.
 * A Psychopath babysitting Lil' Monsta dies when executed (jinx), and a drunk
 * or poisoned one simply dies.
 */
public final class PsychopathRoshambo {
    private static final String PROMPT = "roshambo";

    public enum Pick {
        ROCK, PAPER, SCISSORS;

        boolean beats(Pick other) {
            return (this == ROCK && other == SCISSORS) || (this == PAPER && other == ROCK)
                    || (this == SCISSORS && other == PAPER);
        }

        String pretty() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }

        public static Pick parse(String text) {
            try {
                return Pick.valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private static UUID psychopath;
    private static UUID nominator;
    private static Pick psychopathPick;
    private static Pick nominatorPick;
    private static int day = -1;
    /** The day a roshambo was ruled on: Execute after that runs normally. */
    private static int ruledDay = -1;

    private PsychopathRoshambo() {}

    public static void reset() {
        clear();
        ruledDay = -1;
    }

    static void onDusk() {
        clear();
    }

    private static void clear() {
        psychopath = null;
        nominator = null;
        psychopathPick = null;
        nominatorPick = null;
        day = -1;
        RoleAutomation.resolve(PROMPT);
    }

    /**
     * Called before an execution that kills. Starts (or reports) a roshambo
     * and returns the message for the Storyteller, or null when the execution
     * should go ahead.
     */
    public static String intercept(MinecraftServer server, UUID target) {
        if (server == null || target == null) return null;
        if (ruledDay == ServerState.currentDay) return null;
        if (psychopath != null && day == ServerState.currentDay) {
            return target.equals(psychopath)
                    ? "Rock-paper-scissors is still being played. Use Dies or Lives in Resolve."
                    : null;
        }
        if (!PsychopathAxe.hasAbility(target) || RoleAutomation.droisoned(target)) return null;
        if (RoleAutomation.hasReminder(target, "Is The Demon")) return null; // babysitting Lil' Monsta: jinx
        UUID by = DaytimeState.getMarkedNominator();
        if (by == null || by.equals(target) || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(by)) return null;

        psychopath = target;
        nominator = by;
        psychopathPick = null;
        nominatorPick = null;
        day = ServerState.currentDay;
        String who = RoleAutomation.label(server, target);
        String whom = RoleAutomation.label(server, by);
        RoleAutomation.announce(server, who + " plays rock-paper-scissors with " + whom
                + ": " + who + " only dies if " + whom + " wins.", ChatFormatting.GOLD);
        sendButtons(server, target, whom);
        sendButtons(server, by, who);
        offerPrompt(server, who + " is the " + (RoleAutomation.is(target, Role.PSYCHOPATH) ? "Psychopath"
                : "Alchemist with the Psychopath ability") + ": they only die if " + whom
                + " wins rock-paper-scissors. Waiting for both picks.");
        return "Rock-paper-scissors started between " + who + " and " + whom + ". Rule in Resolve when it is done.";
    }

    private static void sendButtons(MinecraftServer server, UUID player, String opponent) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) return;
        MutableComponent line = Component.literal("Rock-paper-scissors against " + opponent + ": ")
                .withStyle(ChatFormatting.GOLD);
        for (Pick pick : Pick.values()) {
            line.append(RoleAutomation.button("[" + pick.pretty() + "]", "/roshambo " + pick.name().toLowerCase(Locale.ROOT),
                    "Play " + pick.pretty())).append(Component.literal(" "));
        }
        online.sendSystemMessage(line);
    }

    private static void offerPrompt(MinecraftServer server, String body) {
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Rock-paper-scissors", body, List.of(
                StorytellerPrompt.Action.danger("Dies", "/bots automation psychopath roshambo dies"),
                StorytellerPrompt.Action.ok("Lives", "/bots automation psychopath roshambo lives"))));
    }

    /** A player's pick. Returns why not, or null. */
    public static String pick(MinecraftServer server, UUID player, Pick pick) {
        if (psychopath == null || day != ServerState.currentDay) return "Nobody is playing rock-paper-scissors right now.";
        if (pick == null) return "Pick rock, paper or scissors.";
        boolean isPsychopath = psychopath.equals(player);
        if (!isPsychopath && !nominator.equals(player)) return "You are not playing this rock-paper-scissors.";
        if ((isPsychopath ? psychopathPick : nominatorPick) != null) return "You have already picked.";
        if (isPsychopath) psychopathPick = pick;
        else nominatorPick = pick;
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(Component.literal("You picked " + pick.pretty() + ". Waiting for the other player.")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (psychopathPick != null && nominatorPick != null) reveal(server);
        return null;
    }

    private static void reveal(MinecraftServer server) {
        String who = RoleAutomation.label(server, psychopath);
        String whom = RoleAutomation.label(server, nominator);
        String outcome = nominatorPick == psychopathPick ? "A draw."
                : (nominatorPick.beats(psychopathPick) ? whom : who) + " wins.";
        RoleAutomation.announce(server, whom + " plays " + nominatorPick.pretty() + ", " + who + " plays "
                + psychopathPick.pretty() + ". " + outcome, ChatFormatting.GOLD);
        String verdict;
        if (nominatorPick.beats(psychopathPick)) verdict = whom + " wins: " + who + " should die.";
        else if (nominatorPick == psychopathPick) verdict = "A draw: " + who + " should live.";
        else verdict = who + " wins: " + who + " should live.";
        offerPrompt(server, whom + " played " + nominatorPick.pretty() + ", " + who + " played "
                + psychopathPick.pretty() + ". " + verdict);
    }

    /** The Storyteller's ruling: run the execution ({@code dies}) or let them survive it. */
    public static String rule(MinecraftServer server, boolean dies) {
        if (psychopath == null || day != ServerState.currentDay) return "No rock-paper-scissors is waiting.";
        UUID target = psychopath;
        if (!target.equals(DaytimeState.getMarkedForExecution())) {
            clear();
            return "That player is no longer on the block.";
        }
        ruledDay = ServerState.currentDay;
        clear();
        if (dies) ExecutionManager.executePlayer(server, target, false, null);
        else ExecutionManager.executePlayerFail(server, target, false, null);
        return null;
    }
}
