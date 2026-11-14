package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.NominationManager;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Golem: "You may only nominate once per game. When you do, if the
 * nominee is not the Demon, they die."
 *
 * When the Golem nominates, the "May Not Nominate" reminder goes on them and
 * the Storyteller gets a ruling in Resolve: <b>dies</b> kills the nominee at
 * once and ends the nomination, <b>No effect</b> lets the vote go ahead. The
 * advice covers the Demon, a Recluse who may register as one, and a drunk or
 * poisoned Golem. A second nomination by the Golem is flagged for the
 * Storyteller to cancel.
 */
public final class GolemAutomation {
    private static final String PROMPT = "golem";
    private static final String SPENT = "May Not Nominate";

    private static UUID pendingNominee;
    private static int pendingDay = -1;

    private GolemAutomation() {}

    public static void reset() {
        clearPending();
    }

    static void onDusk() {
        clearPending();
    }

    /** Called for every accepted nomination. */
    static void onNomination(MinecraftServer server, UUID nominator, UUID nominee) {
        if (!RoleAutomation.is(nominator, Role.GOLEM)) return;
        String golem = RoleAutomation.label(server, nominator);
        String whom = RoleAutomation.label(server, nominee);
        if (RoleAutomation.hasReminder(nominator, SPENT)) {
            RoleAutomation.tellStorytellers(server, Component.literal("Golem: " + golem
                    + " has already nominated this game and may not nominate again. Cancel this nomination"
                    + " unless you have a reason to allow it.").withStyle(ChatFormatting.GOLD));
            return;
        }
        StorytellerState.REMINDERS.computeIfAbsent(nominator, ignored -> new ArrayList<>())
                .add(new Reminder(SPENT, Optional.of(Role.GOLEM)));
        StateBroadcaster.broadcastGrimoire(server);

        PendingRoleAssignment nomineeRole = RoleAutomation.actualRole(nominee);
        String advice;
        boolean shouldDie = false;
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(nominee)) {
            advice = whom + " is not playing, so nothing happens.";
        } else if (RoleAutomation.droisoned(nominator)) {
            advice = "The Golem is drunk or poisoned, so nothing should happen.";
        } else if (nomineeRole != null && nomineeRole.getRoleType() == RoleType.DEMON) {
            advice = whom + " is the Demon, so nothing should happen.";
        } else if (RoleAutomation.is(nominee, Role.RECLUSE)) {
            advice = whom + " is the Recluse and may register as the Demon: your choice.";
            shouldDie = true;
        } else if (nomineeRole != null && nomineeRole.isCustomRole()) {
            advice = whom + " has a custom role: check whether it counts as the Demon.";
            shouldDie = true;
        } else {
            String survives = PsychopathAxe.survivalReason(nominee);
            advice = survives == null ? whom + " is not the Demon, so they should die."
                    : whom + " is not the Demon, but should survive: " + survives + ".";
            shouldDie = true;
        }

        pendingNominee = nominee;
        pendingDay = ServerState.currentDay;
        String body = golem + " (the Golem) nominated " + whom + ". The Golem may not nominate again. " + advice;
        RoleAutomation.tellStorytellers(server, Component.literal("Golem: " + body + " Decide before the vote: ")
                .withStyle(ChatFormatting.GOLD)
                .append(RoleAutomation.button("[" + whom + " dies]", "/bots automation golem kill",
                        whom + " dies now and the nomination ends"))
                .append(Component.literal(" "))
                .append(RoleAutomation.button("[No effect]", "/bots automation golem dismiss",
                        "Nothing happens; the vote can go ahead")));
        List<StorytellerPrompt.Action> actions = List.of(
                StorytellerPrompt.Action.danger(whom + " dies", "/bots automation golem kill"),
                StorytellerPrompt.Action.ok("No effect", "/bots automation golem dismiss"));
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Golem", body
                + (shouldDie ? "" : " Press No effect to carry on with the vote."), actions));
    }

    /** The Storyteller ruled that the nominee dies. */
    public static String kill(MinecraftServer server) {
        if (pendingNominee == null || pendingDay != ServerState.currentDay) return "There is no Golem nomination waiting.";
        UUID target = pendingNominee;
        clearPending();
        if (!RoleAutomation.isAlive(target)) return "That player is already dead.";
        if (target.equals(DaytimeState.getCurrentNominee()) && !DaytimeState.isVoteInProgress()) {
            NominationManager.resetNomination(server);
        }
        RoleAutomation.killPublicly(server, target);
        return null;
    }

    /** The Storyteller ruled that nothing happens. */
    public static String dismiss(MinecraftServer server) {
        if (pendingNominee == null) return "There is no Golem nomination waiting.";
        clearPending();
        return null;
    }

    private static void clearPending() {
        pendingNominee = null;
        pendingDay = -1;
        RoleAutomation.resolve(PROMPT);
    }
}
