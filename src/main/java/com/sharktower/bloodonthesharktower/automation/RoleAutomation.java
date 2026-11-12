package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.daytime.ExecutionManager;
import com.sharktower.bloodonthesharktower.daytime.NominationManager;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.nightorder.TriggeredNightOrderManager;
import com.sharktower.bloodonthesharktower.states.DeathVisibility;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Rules the server resolves on the Storyteller's behalf.
 *
 * Everything here reads the real Grimoire ({@link ServerState#PLAYER_ROLES}
 * and the Storyteller's reminder tokens) and only ever does one of three
 * things: applies a deterministic rule (Virgin, Slayer, Scarlet Woman),
 * refuses an action that a protection would stop (Monk, Soldier, Fool,
 * Innkeeper, Tea Lady, Sailor), or tells the Storyteller what the rules say
 * should happen next (win conditions, Saint, Mayor). The Storyteller can
 * always override: Mark Dead ignores protections, and a win prompt is a pair
 * of buttons, not an automatic game end, because Scarlet Woman, star-passes,
 * Mastermind and homebrew all bend the plain alive-count rules.
 *
 * "Drunk or poisoned" means a Poisoned or Drunk reminder on the player, or the
 * player actually being the Drunk. "Ability spent" means a No Ability or
 * Ability Used reminder.
 */
public final class RoleAutomation {
    private static final String NO_ABILITY = "No Ability";
    private static String lastPromptKey = "";
    /** The Virgin trigger waiting for the Storyteller's decision, if any. */
    private static UUID pendingVirginNominator;
    private static UUID pendingVirginNominee;
    private static int pendingVirginDay = -1;
    /** Night on which the Imp killed themselves and a Minion must catch the star-pass. */
    private static int pendingStarPassNight = -1;
    /** The Mayor targeted by a Demon Kill this night, so a second Demon Kill goes through. */
    private static UUID mayorWarned;
    private static int mayorWarnedNight = -1;

    /**
     * Reminders that end at dusk, keyed by the role that places them. Each one
     * lasts "tonight and tomorrow day" or "until dusk", so it is stale once the
     * next night begins (before the role chooses again).
     */
    private static final Map<Role, Set<String>> EXPIRE_AT_DUSK = Map.of(
            Role.POISONER, Set.of("poisoned"),
            Role.INNKEEPER, Set.of("safe", "drunk"),
            Role.SAILOR, Set.of("drunk"),
            Role.DEVILS_ADVOCATE, Set.of("survivesexecution"),
            Role.WITCH, Set.of("cursed"));

    /** Reminders that only last the night they were placed. */
    private static final Map<Role, Set<String>> EXPIRE_AT_DAWN = Map.of(
            Role.MONK, Set.of("safe"),
            // The Godfather acts at night on an Outsider's death that day.
            Role.GODFATHER, Set.of("diedtoday"));

    /** Day on which the Minstrel's "Everyone Is Drunk" began; it ends at the dusk after the next day. */
    private static int minstrelDrunkDay = -1;
    /** Day on which an executed Demon handed the game one more day through the Mastermind. */
    private static int mastermindDay = -1;
    /** Execution warnings already shown: target + day, so a second Execute goes through. */
    private static final Set<String> EXECUTION_WARNED = new java.util.HashSet<>();
    /** The Witch's curse waiting for the Storyteller's decision, if any. */
    private static UUID pendingWitchVictim;
    private static int pendingWitchDay = -1;

    private static UUID pendingSlayerShooter;
    private static UUID pendingSlayerTarget;
    private static int pendingSlayerDay = -1;
    /** Players whose Slayer shot has hit a valid target this game; misses do not count. */
    private static final java.util.Set<UUID> SLAYER_SHOT_TAKEN = new java.util.HashSet<>();

    /** Decisions waiting on the Storyteller, by id; shown in the Grimoire's Resolve panel. */
    private static final Map<String, StorytellerPrompt> PROMPTS = new java.util.LinkedHashMap<>();
    private static MinecraftServer promptServer;

    private RoleAutomation() {}

    // ----------------------------------------------------------------- Prompts

    public static List<StorytellerPrompt> prompts() {
        return List.copyOf(PROMPTS.values());
    }

    static void offer(MinecraftServer server, StorytellerPrompt prompt) {
        PROMPTS.put(prompt.id(), prompt);
        if (server != null) promptServer = server;
        if (promptServer != null) StateBroadcaster.broadcastPrompts(promptServer);
    }

    static void resolve(String id) {
        if (PROMPTS.remove(id) != null && promptServer != null) StateBroadcaster.broadcastPrompts(promptServer);
    }

    /** Forget the dedupe key so the next game can prompt again from scratch. */
    public static void reset() {
        lastPromptKey = "";
        clearPendingVirgin();
        pendingStarPassNight = -1;
        mayorWarned = null;
        mayorWarnedNight = -1;
        minstrelDrunkDay = -1;
        mastermindDay = -1;
        EXECUTION_WARNED.clear();
        pendingWitchVictim = null;
        pendingWitchDay = -1;
        clearPendingSlayer();
        SLAYER_SHOT_TAKEN.clear();
        RavenkeeperAutomation.reset();
        PoisonerAutomation.reset();
        PsychopathAxe.reset();
        PROMPTS.clear();
    }

    // ------------------------------------------------------------------ Virgin

    /**
     * First nomination of the Virgin. The ability is spent at once, but the
     * execution is always the Storyteller's call: the Spy may register as a
     * Townsfolk, a custom role may or may not count, and the Storyteller may
     * know something the Grimoire does not. The Storyteller gets a
     * recommendation with [Execute] and [No effect] buttons and decides before
     * starting the vote.
     */
    public static void onNomination(MinecraftServer server, UUID nominator, UUID nominee) {
        if (server == null || nominator == null || nominee == null) return;
        offerWitchCurse(server, nominator);
        if (!is(nominee, Role.VIRGIN) || !isAlive(nominee) || abilitySpent(nominee)) return;
        spendAbility(nominee, Role.VIRGIN);
        StateBroadcaster.broadcastGrimoire(server);

        PendingRoleAssignment nominatorRole = actualRole(nominator);
        String virgin = label(server, nominee);
        String who = label(server, nominator);
        String role = nominatorRole == null ? "no role" : pretty(nominatorRole.getDisplayName());
        String advice;
        if (droisoned(nominee)) {
            advice = "The Virgin is drunk or poisoned, so nothing should happen.";
        } else if (is(nominator, Role.SPY)) {
            advice = "The Spy may register as a Townsfolk: executing them is your choice.";
        } else if (nominatorRole != null && nominatorRole.isCustomRole()) {
            advice = role + " is a custom role: check whether it counts as a Townsfolk.";
        } else if (nominatorRole != null && nominatorRole.getRoleType() == RoleType.TOWNSFOLK) {
            advice = who + " is a Townsfolk, so they should be executed.";
        } else {
            advice = who + " is not a Townsfolk, so nothing should happen.";
        }

        pendingVirginNominator = nominator;
        pendingVirginNominee = nominee;
        pendingVirginDay = ServerState.currentDay;
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(nominator);
        Component message = Component.literal("Virgin: " + who + " (" + role + ") nominated " + virgin
                        + ". The Virgin's ability is now spent. " + advice + " Decide before starting the vote: ")
                .withStyle(ChatFormatting.GOLD);
        if (seat != null) {
            message = message.copy()
                    .append(button("[Execute " + who + "]", "/bots automation virgin " + seat,
                            "Execute " + who + " now; the nomination ends with no vote"))
                    .append(Component.literal(" "))
                    .append(button("[No effect]", "/bots automation virgin dismiss",
                            "Nothing happens; the nomination continues as normal"));
        }
        tellStorytellers(server, message);
        if (seat != null) {
            offer(server, new StorytellerPrompt("virgin", "Virgin nominated",
                    who + " (" + role + ") nominated " + virgin + ". " + advice,
                    List.of(StorytellerPrompt.Action.danger("Execute " + who, "/bots automation virgin " + seat),
                            StorytellerPrompt.Action.ok("No effect", "/bots automation virgin dismiss"))));
        }
    }

    /** The Storyteller approved the Virgin trigger: execute the nominator now. */
    public static String virginExecute(MinecraftServer server, UUID nominator) {
        if (nominator == null || !nominator.equals(pendingVirginNominator)
                || pendingVirginDay != ServerState.currentDay || !PhaseOperations.isDay()) {
            return "There is no Virgin decision waiting for that player today.";
        }
        if (!isAlive(nominator)) {
            clearPendingVirgin();
            return "That player is not alive.";
        }
        UUID nominee = pendingVirginNominee;
        clearPendingVirgin();
        NominationManager.resetNomination(server);
        announce(server, label(server, nominator) + " nominated " + label(server, nominee)
                + " and is executed immediately.", ChatFormatting.RED);
        ExecutionManager.executePlayer(server, nominator, false, null);
        return null;
    }

    /** The Storyteller ruled that the Virgin trigger has no effect. */
    public static String virginDismiss() {
        if (pendingVirginNominator == null) return "There is no Virgin decision waiting.";
        clearPendingVirgin();
        return null;
    }

    private static void clearPendingVirgin() {
        pendingVirginNominator = null;
        pendingVirginNominee = null;
        pendingVirginDay = -1;
        resolve("virgin");
    }

    // ------------------------------------------------------------------- Witch

    /** A Cursed player nominated: they should die, if the Witch still has her ability. */
    private static void offerWitchCurse(MinecraftServer server, UUID nominator) {
        if (!hasReminderFrom(nominator, "Cursed", Role.WITCH) || !isAlive(nominator)) return;
        String who = label(server, nominator);
        String advice;
        if (protectorDroisoned(Role.WITCH)) {
            advice = "The Witch is drunk or poisoned, so nothing should happen.";
        } else if (aliveNonTravellerCount() <= 3) {
            advice = "Only 3 players are alive, so the Witch has lost her ability.";
        } else {
            advice = who + " should die.";
        }
        pendingWitchVictim = nominator;
        pendingWitchDay = ServerState.currentDay;
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(nominator);
        Component message = Component.literal("Witch: " + who + " is Cursed and nominated. " + advice + " ")
                .withStyle(ChatFormatting.GOLD);
        if (seat != null) {
            message = message.copy()
                    .append(button("[Kill " + who + "]", "/bots automation witch " + seat,
                            who + " dies now; the nomination still goes ahead"))
                    .append(Component.literal(" "))
                    .append(button("[No effect]", "/bots automation witch dismiss", "Nothing happens"));
        }
        tellStorytellers(server, message);
        if (seat != null) {
            offer(server, new StorytellerPrompt("witch", "Witch curse",
                    who + " is Cursed and nominated. " + advice,
                    List.of(StorytellerPrompt.Action.danger("Kill " + who, "/bots automation witch " + seat),
                            StorytellerPrompt.Action.ok("No effect", "/bots automation witch dismiss"))));
        }
    }

    /** The Storyteller confirmed the Witch's curse. */
    public static String witchKill(MinecraftServer server, UUID victim) {
        if (victim == null || !victim.equals(pendingWitchVictim) || pendingWitchDay != ServerState.currentDay) {
            return "There is no Witch decision waiting for that player today.";
        }
        pendingWitchVictim = null;
        pendingWitchDay = -1;
        resolve("witch");
        if (!isAlive(victim)) return "That player is already dead.";
        removeReminderFrom(victim, "Cursed", Role.WITCH);
        killPublicly(server, victim);
        return null;
    }

    public static String witchDismiss() {
        if (pendingWitchVictim == null) return "There is no Witch decision waiting.";
        pendingWitchVictim = null;
        pendingWitchDay = -1;
        resolve("witch");
        return null;
    }

    /** A public, immediate death (Slayer shot, Witch curse): revealed at once, then the usual death rules. */
    static void killPublicly(MinecraftServer server, UUID target) {
        ServerState.PLAYER_DEATH_STATUS.put(target, true);
        DeathVisibility.remove(target);
        TriggeredNightOrderManager.onDeath(target, TriggeredNightOrderManager.DeathCause.GENERIC);
        announce(server, label(server, target) + " dies!", ChatFormatting.RED);
        StateBroadcaster.broadcastDeathStatus(server);
        StateBroadcaster.broadcastGrimoire(server);
        onDeath(server, target, false);
    }

    // -------------------------------------------------------------- Execution

    /**
     * Checked when the Storyteller presses Execute (dies): a player who should
     * survive gets one warning per day. Pressing Execute again kills them
     * anyway; Execute (lives) lets them survive.
     *
     * @return the warning, or null when the execution should go ahead.
     */
    public static String executionWarning(MinecraftServer server, UUID target) {
        if (target == null) return null;
        String reason = executionSurvivalReason(target);
        if (reason == null) return null;
        String key = target + "@" + ServerState.currentDay;
        if (!EXECUTION_WARNED.add(key)) return null;
        if (is(target, Role.FOOL) && reason.startsWith("the Fool")) {
            spendAbility(target, Role.FOOL);
            if (server != null) StateBroadcaster.broadcastGrimoire(server);
        }
        String who = server == null ? "That player" : label(server, target);
        return who + " should survive: " + reason + ". Use Execute (lives), or Execute (dies) again to kill them anyway.";
    }

    static String executionSurvivalReason(UUID target) {
        if (hasReminderFrom(target, "Survives Execution", Role.DEVILS_ADVOCATE) && !protectorDroisoned(Role.DEVILS_ADVOCATE)) {
            return "the Devil's Advocate protected them";
        }
        if (is(target, Role.SAILOR) && !droisoned(target)) return "the sober Sailor cannot die";
        if (is(target, Role.FOOL) && !droisoned(target) && !abilitySpent(target)) {
            return "the Fool survives their first death (their ability is now spent)";
        }
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(target, List.of())) {
            if (normalize(reminder.text()).equals("cannotdie") && !protectorDroisoned(Role.TEA_LADY)) {
                return "the Tea Lady says they cannot die";
            }
        }
        UUID pacifist = findAlive(Role.PACIFIST);
        PendingRoleAssignment role = actualRole(target);
        if (pacifist != null && !droisoned(pacifist) && role != null && role.isFinalGood()) {
            return "the Pacifist is alive, so this good player might survive (your choice)";
        }
        return null;
    }

        // ------------------------------------------------------------------ Slayer

    /**
     * A public Slayer shot. Anyone may claim it (bluffing is part of the game),
     * so the public line is the same whoever shoots; only the real, sober,
     * unspent Slayer hitting the real Demon kills. The shot spends a real
     * Slayer's ability even when drunk or poisoned.
     *
     * @return null on success, otherwise why the shot could not be made.
     */
    /**
     * Why {@code shooter} cannot claim the Slayer shot right now, or null when
     * they can. Anyone living and seated may claim it during the day: the
     * claim is a bluff everyone is allowed to make.
     */
    public static String slayerShooterRefusal(UUID shooter) {
        if (shooter == null) return "Only a seated player can claim the Slayer shot.";
        if (ServerState.gameEnded) return "The game has ended.";
        if (ServerState.currentDay <= 0 || ServerState.currentNight != ServerState.currentDay) {
            return "The Slayer can only shoot during the day.";
        }
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(shooter)) return "You are not seated in this game.";
        if (!isAlive(shooter)) return "Dead players cannot use the Slayer ability.";
        if (SLAYER_SHOT_TAKEN.contains(shooter)) return "You have already taken your one Slayer shot this game.";
        return null;
    }

    /** Uses up {@code shooter}'s one Slayer shot for this game. */
    public static void takeSlayerShot(UUID shooter) {
        if (shooter != null) SLAYER_SHOT_TAKEN.add(shooter);
    }

    /**
     * The Slayer shot at {@code target}. {@code announceClaim} posts
     * "X claims Slayer!" first, for {@code /slay <target>}; the crossbow
     * announced the claim when it was handed out.
     */
    public static String slayerShot(MinecraftServer server, UUID shooter, UUID target, boolean announceClaim) {
        if (server == null || shooter == null || target == null) return "Choose a player to shoot.";
        String refusal = slayerShooterRefusal(shooter);
        if (refusal != null) return refusal;
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(target)) return "That player is not seated.";
        if (shooter.equals(target)) return "You cannot shoot yourself.";
        if (!isAlive(target)) return label(server, target) + " is already dead.";

        String who = label(server, shooter);
        String whom = label(server, target);
        takeSlayerShot(shooter);
        boolean realSlayer = is(shooter, Role.SLAYER);
        boolean spent = realSlayer && abilitySpent(shooter);
        if (realSlayer && !spent) spendAbility(shooter, Role.SLAYER);

        PendingRoleAssignment targetRole = actualRole(target);
        boolean demon = targetRole != null && targetRole.getRoleType() == RoleType.DEMON;
        boolean kills = realSlayer && !spent && !droisoned(shooter) && demon;

        if (announceClaim) announce(server, who + " claims Slayer!", ChatFormatting.GOLD);
        announce(server, who + " shoots " + whom + "!", ChatFormatting.GOLD);
        String why;
        if (kills) why = who + " is the real Slayer and " + whom + " is the " + pretty(targetRole.getDisplayName()) + ": " + whom + " should die.";
        else if (!realSlayer) why = who + " is not the Slayer (" + roleName(shooter) + "), so nothing should happen.";
        else if (spent) why = who + " had already used the Slayer ability, so nothing should happen.";
        else if (droisoned(shooter)) why = who + " is the Slayer but drunk or poisoned, so nothing should happen. The ability is now spent.";
        else why = whom + " is the " + roleName(target) + ", not the Demon, so nothing should happen. The ability is now spent."
                + (is(target, Role.RECLUSE) ? " The Recluse may register as the Demon: your call." : "");

        pendingSlayerShooter = shooter;
        pendingSlayerTarget = target;
        pendingSlayerDay = ServerState.currentDay;
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(target);
        Component message = Component.literal("Slayer: " + why + " Decide: ").withStyle(ChatFormatting.GOLD)
                .append(button("[" + whom + " dies]", "/bots automation slayer " + seat, whom + " dies now"))
                .append(Component.literal(" "))
                .append(button("[Nothing happens]", "/bots automation slayer dismiss", "The shot has no effect"));
        tellStorytellers(server, message);
        offer(server, new StorytellerPrompt("slayer", "Slayer shot", who + " shot " + whom + ". " + why,
                List.of(StorytellerPrompt.Action.danger(whom + " dies", "/bots automation slayer " + seat),
                        StorytellerPrompt.Action.ok("No effect", "/bots automation slayer dismiss"))));
        StateBroadcaster.broadcastGrimoire(server);
        return null;
    }

    /** The Storyteller confirmed the Slayer's kill. */
    public static String slayerKill(MinecraftServer server, UUID target) {
        if (target == null || !target.equals(pendingSlayerTarget) || pendingSlayerDay != ServerState.currentDay) {
            return "There is no Slayer shot waiting for that player today.";
        }
        clearPendingSlayer();
        if (!isAlive(target)) return "That player is already dead.";
        killPublicly(server, target);
        return null;
    }

    /** The Storyteller ruled that the shot has no effect. */
    public static String slayerDismiss(MinecraftServer server) {
        if (pendingSlayerTarget == null) return "There is no Slayer shot waiting.";
        clearPendingSlayer();
        announce(server, "Nothing happens.", ChatFormatting.GRAY);
        return null;
    }

    private static void clearPendingSlayer() {
        pendingSlayerShooter = null;
        pendingSlayerTarget = null;
        pendingSlayerDay = -1;
        resolve("slayer");
    }

    // ------------------------------------------------------------ Protection

    /**
     * Why a Demon kill on {@code target} should not go through, or null when
     * nothing protects them. The Fool's one-off survival is consumed here.
     */
    public static String demonKillRefusal(MinecraftServer server, UUID target) {
        if (target == null) return null;
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(target, List.of())) {
            String text = normalize(reminder.text());
            if (text.equals("safe") || text.equals("protected")) {
                Role source = reminder.role().orElse(null);
                if (source == null || !protectorDroisoned(source)) {
                    String by = source == null ? "a reminder" : "the " + pretty(source.getDisplayName());
                    return "That player is protected tonight by " + by + " (\"" + reminder.text() + "\").";
                }
            }
            if (text.equals("cannotdie") && !protectorDroisoned(Role.TEA_LADY)) {
                return "That player cannot die tonight: Tea Lady (\"" + reminder.text() + "\").";
            }
        }
        if (is(target, Role.SOLDIER) && !droisoned(target)) {
            return "That player is the Soldier and is safe from the Demon.";
        }
        if (is(target, Role.SAILOR) && !droisoned(target)) {
            return "That player is the sober Sailor and cannot die.";
        }
        if (is(target, Role.FOOL) && !droisoned(target) && !abilitySpent(target)) {
            spendAbility(target, Role.FOOL);
            if (server != null) StateBroadcaster.broadcastGrimoire(server);
            return "That player is the Fool: the first time they would die, they do not. The Fool's ability is now spent.";
        }
        if (is(target, Role.MAYOR) && !droisoned(target)) {
            boolean warned = target.equals(mayorWarned) && mayorWarnedNight == ServerState.currentNight;
            if (!warned) {
                mayorWarned = target;
                mayorWarnedNight = ServerState.currentNight;
                return "That player is the Mayor: you may have another player die instead (Demon Kill them)."
                        + " Demon Kill the Mayor again to kill the Mayor anyway.";
            }
            mayorWarned = null;
            mayorWarnedNight = -1;
        }
        return null;
    }

    /** True when the one living holder of {@code role} is drunk or poisoned (or nobody holds it). */
    static boolean protectorDroisoned(Role role) {
        UUID holder = findAlive(role);
        return holder != null && droisoned(holder);
    }

    // ------------------------------------------------------- Deaths and wins

    /**
     * Called once a player has just been marked dead by any route. Handles the
     * Scarlet Woman becoming the Demon, the Saint losing the game, and then the
     * plain win checks.
     */
    public static void onDeath(MinecraftServer server, UUID dead, boolean execution) {
        onDeath(server, dead, execution, false);
    }

    /**
     * @param demonKill true when the Storyteller used Demon Kill, so a Demon
     *                  dying this way killed themselves (the Imp's star-pass).
     */
    public static void onDeath(MinecraftServer server, UUID dead, boolean execution, boolean demonKill) {
        if (server == null || dead == null || ServerState.gameEnded) return;
        PendingRoleAssignment deadRole = actualRole(dead);
        RavenkeeperAutomation.onDeath(server, dead, execution, demonKill || PhaseOperations.isNight());

        if (execution && mastermindDay >= 0 && ServerState.currentDay == mastermindDay + 1 && deadRole != null) {
            mastermindDay = -1;
            boolean good = deadRole.isFinalGood();
            prompt(server, good ? "EVIL" : "GOOD", "Mastermind: " + label(server, dead) + " was executed on the extra day, so "
                    + (good ? "good" : "evil") + " loses.", "mastermind-" + dead);
            return;
        }

        if (execution && deadRole != null && deadRole.getRoleType() == RoleType.MINION) {
            UUID minstrel = findAlive(Role.MINSTREL);
            if (minstrel != null && !droisoned(minstrel) && !hasReminderFrom(minstrel, "Everyone Is Drunk", Role.MINSTREL)) {
                StorytellerState.REMINDERS.computeIfAbsent(minstrel, ignored -> new ArrayList<>())
                        .add(new Reminder("Everyone Is Drunk", Optional.of(Role.MINSTREL)));
                minstrelDrunkDay = ServerState.currentDay;
                StateBroadcaster.broadcastGrimoire(server);
                tellStorytellers(server, "Minstrel: a Minion was executed, so every other player is drunk until dusk tomorrow."
                        + " \"Everyone Is Drunk\" was added to " + label(server, minstrel) + ".", ChatFormatting.GOLD);
            }
        }

        if (execution && hasReminderFrom(dead, "Twin", Role.EVIL_TWIN)) {
            UUID evilTwin = findAlive(Role.EVIL_TWIN);
            if (evilTwin != null && !droisoned(evilTwin)) {
                prompt(server, "EVIL", "The good twin (" + label(server, dead) + ") was executed while the Evil Twin lives.",
                        "eviltwin-" + dead);
                return;
            }
        }

        if (execution && is(dead, Role.SAINT) && !droisoned(dead)) {
            prompt(server, "EVIL", "The Saint (" + label(server, dead) + ") was executed.",
                    "saint-" + dead);
            return;
        }

        if (deadRole != null && deadRole.getRoleType() == RoleType.DEMON) {
            // The dead Demon still counted as alive at the moment they died.
            int aliveBefore = aliveNonTravellerCount() + 1;
            UUID scarlet = findAlive(Role.SCARLET_WOMAN);
            if (scarlet != null && aliveBefore >= 5 && !droisoned(scarlet)) {
                if (deadRole.isCustomRole()) {
                    tellStorytellers(server, "Scarlet Woman: " + label(server, scarlet)
                            + " becomes the Demon, but " + pretty(deadRole.getDisplayName())
                            + " is a custom role. Change their role in the Grimoire by hand.", ChatFormatting.GOLD);
                } else {
                    PendingRoleAssignment current = actualRole(scarlet);
                    ServerState.PLAYER_ROLES.put(scarlet, new PendingRoleAssignment(deadRole.role(), current.override()));
                    ServerState.PLAYER_PERCEIVED_ROLES.remove(scarlet);
                    StorytellerState.REMINDERS.computeIfAbsent(scarlet, ignored -> new ArrayList<>())
                            .add(new Reminder("Is Demon", Optional.of(Role.SCARLET_WOMAN)));
                    StateBroadcaster.broadcastGrimoire(server);
                    ServerPlayer online = server.getPlayerList().getPlayer(scarlet);
                    if (online != null) StateBroadcaster.sendRoleTo(online, false);
                    tellStorytellers(server, "Scarlet Woman: the " + pretty(deadRole.getDisplayName()) + " died with "
                            + aliveBefore + " players alive, so " + label(server, scarlet) + " is now the "
                            + pretty(deadRole.getDisplayName()) + ". Their role card has been updated.", ChatFormatting.GOLD);
                    BloodOnTheSharktower.LOGGER.info("Scarlet Woman {} became {} after {} died",
                            scarlet, pretty(deadRole.getDisplayName()), dead);
                }
            } else {
                if (scarlet != null && aliveBefore >= 5) {
                    tellStorytellers(server, "Scarlet Woman: " + label(server, scarlet)
                            + " is drunk or poisoned, so they do not become the Demon.", ChatFormatting.GOLD);
                }
                if (demonKill && is(dead, Role.IMP) && offerStarPass(server, dead)) return;
            }
            if (execution && !anyDemonAlive()) {
                UUID mastermind = findAlive(Role.MASTERMIND);
                if (mastermind != null && !droisoned(mastermind)) {
                    mastermindDay = ServerState.currentDay;
                    tellStorytellers(server, "Mastermind: the Demon was executed while " + label(server, mastermind)
                            + " lives, so the game continues for one more day. If a player is executed tomorrow,"
                            + " their team loses.", ChatFormatting.GOLD);
                    return;
                }
            }
        }
        checkWin(server);
    }

    // --------------------------------------------------------------- Star-pass

    /**
     * The Imp killed themselves: a living Minion becomes the Imp, and which one
     * is the Storyteller's choice. Offers one button per living Minion.
     *
     * @return true when a choice was offered (the win check waits for it).
     */
    private static boolean offerStarPass(MinecraftServer server, UUID imp) {
        List<UUID> minions = new ArrayList<>();
        for (Map.Entry<UUID, PendingRoleAssignment> entry : ServerState.PLAYER_ROLES.entrySet()) {
            PendingRoleAssignment role = entry.getValue();
            if (role != null && role.getRoleType() == RoleType.MINION && isAlive(entry.getKey())
                    && ServerState.PLAYER_SEAT_NUMBERS.containsKey(entry.getKey())) {
                minions.add(entry.getKey());
            }
        }
        if (minions.isEmpty()) return false;
        minions.sort(java.util.Comparator.comparingInt(id -> ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(id, 0)));
        pendingStarPassNight = ServerState.currentNight;
        Component message = Component.literal("Imp star-pass: " + label(server, imp)
                + " killed themselves. Choose the Minion who becomes the Imp: ").withStyle(ChatFormatting.GOLD);
        for (UUID minion : minions) {
            int seat = ServerState.PLAYER_SEAT_NUMBERS.get(minion);
            String name = label(server, minion) + " (" + pretty(actualRole(minion).getDisplayName()) + ")";
            message = message.copy().append(button("[" + name + "]", "/bots automation starpass " + seat,
                    name + " becomes the Imp")).append(Component.literal(" "));
        }
        tellStorytellers(server, message);
        List<StorytellerPrompt.Action> choices = new ArrayList<>();
        for (UUID minion : minions) {
            int seat = ServerState.PLAYER_SEAT_NUMBERS.get(minion);
            choices.add(StorytellerPrompt.Action.neutral(label(server, minion) + " (" + pretty(actualRole(minion).getDisplayName()) + ")",
                    "/bots automation starpass " + seat));
        }
        offer(server, new StorytellerPrompt("starpass", "Imp star-pass",
                label(server, imp) + " killed themselves. Choose the Minion who becomes the Imp.", choices));
        return true;
    }

    /** The Storyteller chose who catches the star-pass. */
    public static String starPass(MinecraftServer server, UUID minion) {
        if (pendingStarPassNight != ServerState.currentNight || !PhaseOperations.isNight()) {
            return "There is no star-pass waiting tonight.";
        }
        PendingRoleAssignment current = actualRole(minion);
        if (minion == null || current == null || current.getRoleType() != RoleType.MINION || !isAlive(minion)) {
            return "That player is not a living Minion.";
        }
        pendingStarPassNight = -1;
        resolve("starpass");
        ServerState.PLAYER_ROLES.put(minion, new PendingRoleAssignment(Role.IMP, current.override()));
        ServerState.PLAYER_PERCEIVED_ROLES.remove(minion);
        StateBroadcaster.broadcastGrimoire(server);
        ServerPlayer online = server.getPlayerList().getPlayer(minion);
        if (online != null) StateBroadcaster.sendRoleTo(online, false);
        tellStorytellers(server, "Star-pass: " + label(server, minion) + " (" + pretty(current.getDisplayName())
                + ") is now the Imp. Their role card has been updated; wake them to tell them.", ChatFormatting.GOLD);
        BloodOnTheSharktower.LOGGER.info("Star-pass: {} became the Imp", minion);
        return null;
    }

    // -------------------------------------------------------- Reminder expiry

    /** Dusk: clear reminders whose effect lasted "until dusk" or "tonight and tomorrow". */
    public static void onDusk(MinecraftServer server) {
        if (server != null) promptServer = server;
        clearPendingVirgin();
        pendingWitchVictim = null;
        pendingWitchDay = -1;
        resolve("witch");
        clearPendingSlayer();
        expireReminders(server, EXPIRE_AT_DUSK, "Dusk");
        PoisonerAutomation.onDusk(server);
        PsychopathAxe.onDusk();
        if (minstrelDrunkDay >= 0 && ServerState.currentDay > minstrelDrunkDay) {
            minstrelDrunkDay = -1;
            expireReminders(server, Map.of(Role.MINSTREL, Set.of("everyoneisdrunk")), "Dusk");
        }
        List<String> counted = countDownCourtier();
        if (!counted.isEmpty() && server != null) {
            StateBroadcaster.broadcastGrimoire(server);
            tellStorytellers(server, "Dusk: Courtier " + String.join(", ", counted) + ".", ChatFormatting.GRAY);
        }
    }

    /** Courtier: "Drunk 3" becomes "Drunk 2", then "Drunk 1", then is removed, one step each dusk. */
    static List<String> countDownCourtier() {
        List<String> changes = new ArrayList<>();
        for (Map.Entry<UUID, List<Reminder>> entry : StorytellerState.REMINDERS.entrySet()) {
            List<Reminder> list = entry.getValue();
            if (list == null) continue;
            for (int i = list.size() - 1; i >= 0; i--) {
                Reminder reminder = list.get(i);
                if (reminder.role().orElse(null) != Role.COURTIER) continue;
                String text = normalize(reminder.text());
                String seat = "seat " + ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(entry.getKey(), 0);
                switch (text) {
                    case "drunk3" -> list.set(i, new Reminder("Drunk 2", reminder.role(), reminder.customRoleId(), reminder.playerUuid()));
                    case "drunk2" -> list.set(i, new Reminder("Drunk 1", reminder.role(), reminder.customRoleId(), reminder.playerUuid()));
                    case "drunk1" -> list.remove(i);
                    default -> { continue; }
                }
                changes.add(seat + (text.equals("drunk1") ? " is sober again" : " now " + list.get(i).text()));
            }
        }
        StorytellerState.REMINDERS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        return changes;
    }

    /** Dawn: clear reminders that only protected the night just gone. */
    public static void onDawn(MinecraftServer server) {
        if (server != null) promptServer = server;
        pendingStarPassNight = -1;
        resolve("starpass");
        expireReminders(server, EXPIRE_AT_DAWN, "Dawn");
    }

    static List<String> expireReminders(MinecraftServer server, Map<Role, Set<String>> rules, String when) {
        List<String> cleared = new ArrayList<>();
        for (Map.Entry<UUID, List<Reminder>> entry : StorytellerState.REMINDERS.entrySet()) {
            List<Reminder> list = entry.getValue();
            if (list == null) continue;
            list.removeIf(reminder -> {
                Role source = reminder.role().orElse(null);
                Set<String> texts = source == null ? null : rules.get(source);
                if (texts == null || !texts.contains(normalize(reminder.text()))) return false;
                cleared.add((server == null ? "Seat " + ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(entry.getKey(), 0)
                        : label(server, entry.getKey())) + " " + reminder.text() + " (" + pretty(source.getDisplayName()) + ")");
                return true;
            });
        }
        StorytellerState.REMINDERS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        if (!cleared.isEmpty() && server != null) {
            StateBroadcaster.broadcastGrimoire(server);
            tellStorytellers(server, when + ": cleared expired reminders: " + String.join(", ", cleared) + ".",
                    ChatFormatting.GRAY);
        }
        return cleared;
    }

    /** Called when a day ends without an execution. */
    public static void onNoExecution(MinecraftServer server) {
        if (server == null || ServerState.gameEnded) return;
        UUID vortox = findAlive(Role.VORTOX);
        if (vortox != null && !droisoned(vortox)) {
            prompt(server, "EVIL", "No one was executed today while the Vortox (" + label(server, vortox) + ") lives.",
                    "vortox-day-" + ServerState.currentDay);
            return;
        }
        if (mastermindDay >= 0 && ServerState.currentDay == mastermindDay + 1) {
            mastermindDay = -1;
            tellStorytellers(server, "Mastermind: the extra day ended with no execution. Resolve the end of the game"
                    + " by the Mastermind rules.", ChatFormatting.GOLD);
            return;
        }
        UUID mayor = findAlive(Role.MAYOR);
        if (mayor != null && aliveNonTravellerCount() == 3 && !droisoned(mayor)) {
            prompt(server, "GOOD", "Only 3 players are alive, no execution happened today, and "
                    + label(server, mayor) + " is the Mayor.", "mayor-day-" + ServerState.currentDay);
            return;
        }
        checkWin(server);
    }

    /** The plain alive-count rules, shown to the Storyteller as a prompt. */
    public static void checkWin(MinecraftServer server) {
        if (server == null || ServerState.gameEnded) return;
        if (ServerState.currentNight == 0 && ServerState.currentDay == 0) return;
        boolean demonAlive = false;
        for (Map.Entry<UUID, PendingRoleAssignment> entry : ServerState.PLAYER_ROLES.entrySet()) {
            PendingRoleAssignment role = entry.getValue();
            if (role != null && role.getRoleType() == RoleType.DEMON && isAlive(entry.getKey())) demonAlive = true;
        }
        int alive = aliveNonTravellerCount();
        if (!demonAlive && twinsBothAlive()) {
            tellStorytellers(server, "No Demon is alive, but good cannot win while both twins live (Evil Twin).",
                    ChatFormatting.GOLD);
        } else if (!demonAlive) {
            prompt(server, "GOOD", "No Demon is alive. (Unless a Scarlet Woman, star-pass or similar replaced them.)",
                    "nodemon-" + ServerState.currentNight + "-" + ServerState.currentDay + "-" + alive);
        } else if (alive <= 2) {
            prompt(server, "EVIL", "Only " + alive + " non-Traveller player" + (alive == 1 ? "" : "s") + " alive.",
                    "alive-" + ServerState.currentNight + "-" + ServerState.currentDay + "-" + alive);
        }
    }

    private static void prompt(MinecraftServer server, String favoured, String reason, String key) {
        if (key.equals(lastPromptKey)) return;
        lastPromptKey = key;
        String team = favoured.equals("GOOD") ? "Good" : "Evil";
        Component message = Component.literal("Game over? " + reason + " " + team + " should win. ")
                .withStyle(ChatFormatting.GOLD)
                .append(button("[Good wins]", "/bots endGame good", "End the game: Good wins"))
                .append(Component.literal(" "))
                .append(button("[Evil wins]", "/bots endGame evil", "End the game: Evil wins"))
                .append(Component.literal("  or carry on if a rule says otherwise.").withStyle(ChatFormatting.GRAY));
        tellStorytellers(server, message);
        BloodOnTheSharktower.LOGGER.info("Win prompt ({}): {}", favoured, reason);
    }

    // --------------------------------------------------------------- Helpers

    static PendingRoleAssignment actualRole(UUID player) {
        return player == null ? null : ServerState.PLAYER_ROLES.get(player);
    }

    static boolean is(UUID player, Role role) {
        PendingRoleAssignment assignment = actualRole(player);
        return assignment != null && !assignment.isCustomRole() && assignment.role() == role;
    }

    static boolean isAlive(UUID player) {
        return player != null && ServerState.PLAYER_SEAT_NUMBERS.containsKey(player)
                && !Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(player));
    }

    static boolean isTraveller(UUID player) {
        PendingRoleAssignment assignment = actualRole(player);
        return assignment != null && assignment.getRoleType() == RoleType.TRAVELER;
    }

    static int aliveNonTravellerCount() {
        int count = 0;
        for (UUID player : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            if (isAlive(player) && !isTraveller(player) && actualRole(player) != null) count++;
        }
        return count;
    }

    static UUID findAlive(Role role) {
        for (UUID player : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            if (is(player, role) && isAlive(player)) return player;
        }
        return null;
    }

    static boolean hasReminder(UUID player, String text) {
        String wanted = normalize(text);
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(player, List.of())) {
            if (normalize(reminder.text()).equals(wanted)) return true;
        }
        return false;
    }

    /**
     * Drunk or poisoned: the Drunk, a Poisoned or Drunk reminder (including the
     * Courtier's "Drunk 3/2/1"), or the Minstrel's "Everyone Is Drunk" on
     * another living player (which spares the Minstrel and Travellers).
     */
    static boolean droisoned(UUID player) {
        if (is(player, Role.DRUNK) || hasReminder(player, "Poisoned")) return true;
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(player, List.of())) {
            if (normalize(reminder.text()).matches("drunk[0-9]*")) return true;
        }
        if (!isTraveller(player) && !is(player, Role.MINSTREL)) {
            for (Map.Entry<UUID, List<Reminder>> entry : StorytellerState.REMINDERS.entrySet()) {
                if (entry.getKey().equals(player)) continue;
                for (Reminder reminder : entry.getValue()) {
                    if (normalize(reminder.text()).equals("everyoneisdrunk")) return true;
                }
            }
        }
        return false;
    }

    static boolean hasReminderFrom(UUID player, String text, Role source) {
        if (player == null) return false;
        String wanted = normalize(text);
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(player, List.of())) {
            Role from = reminder.role().orElse(null);
            if (normalize(reminder.text()).equals(wanted) && (from == null || from == source)) return true;
        }
        return false;
    }

    private static void removeReminderFrom(UUID player, String text, Role source) {
        List<Reminder> list = StorytellerState.REMINDERS.get(player);
        if (list == null) return;
        String wanted = normalize(text);
        list.removeIf(reminder -> normalize(reminder.text()).equals(wanted)
                && (reminder.role().isEmpty() || reminder.role().get() == source));
        if (list.isEmpty()) StorytellerState.REMINDERS.remove(player);
    }

    static boolean anyDemonAlive() {
        for (Map.Entry<UUID, PendingRoleAssignment> entry : ServerState.PLAYER_ROLES.entrySet()) {
            PendingRoleAssignment role = entry.getValue();
            if (role != null && role.getRoleType() == RoleType.DEMON && isAlive(entry.getKey())) return true;
        }
        return false;
    }

    /** Evil Twin and the player marked "Twin" are both alive (and the Evil Twin is sober). */
    static boolean twinsBothAlive() {
        UUID evilTwin = findAlive(Role.EVIL_TWIN);
        if (evilTwin == null || droisoned(evilTwin)) return false;
        for (UUID player : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            if (isAlive(player) && hasReminderFrom(player, "Twin", Role.EVIL_TWIN)) return true;
        }
        return false;
    }

    static boolean abilitySpent(UUID player) {
        return hasReminder(player, NO_ABILITY) || hasReminder(player, "Ability Used");
    }

    static void spendAbility(UUID player, Role source) {
        StorytellerState.REMINDERS.computeIfAbsent(player, ignored -> new ArrayList<>())
                .add(new Reminder(NO_ABILITY, Optional.ofNullable(source)));
    }

    private static String roleName(UUID player) {
        PendingRoleAssignment assignment = actualRole(player);
        return assignment == null ? "no role" : pretty(assignment.getDisplayName());
    }

    /** "IMP" -> "Imp", "FORTUNE TELLER" -> "Fortune Teller"; mixed-case names are kept. */
    static String pretty(String name) {
        if (name == null || name.isEmpty() || !name.equals(name.toUpperCase(Locale.ROOT))) return name;
        StringBuilder out = new StringBuilder(name.length());
        boolean start = true;
        for (char ch : name.toCharArray()) {
            out.append(start ? ch : Character.toLowerCase(ch));
            start = !Character.isLetter(ch) && ch != '\'';
        }
        return out.toString();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String label(MinecraftServer server, UUID id) {
        if (id == null) return "Unknown";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return com.sharktower.bloodonthesharktower.profile.PlayerProfiles.displayName(online);
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
        return seat == null ? "Player" : "Seat " + seat;
    }

    static Component button(String text, String command, String hover) {
        return Component.literal(text)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    static void announce(MinecraftServer server, String text, ChatFormatting colour) {
        Component message = Component.literal(text).withStyle(colour);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(message);
    }

    private static void tellStorytellers(MinecraftServer server, String text, ChatFormatting colour) {
        tellStorytellers(server, Component.literal(text).withStyle(colour));
    }

    static void tellStorytellers(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StorytellerState.isStoryteller(player.getUUID())) player.sendSystemMessage(message);
        }
    }
}
