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
            Role.MINSTREL, Set.of("everyoneisdrunk"),
            Role.DEVILS_ADVOCATE, Set.of("survivesexecution"),
            Role.WITCH, Set.of("cursed"),
            Role.GODFATHER, Set.of("diedtoday"));

    /** Reminders that only last the night they were placed. */
    private static final Map<Role, Set<String>> EXPIRE_AT_DAWN = Map.of(
            Role.MONK, Set.of("safe"));

    private RoleAutomation() {}

    /** Forget the dedupe key so the next game can prompt again from scratch. */
    public static void reset() {
        lastPromptKey = "";
        clearPendingVirgin();
        pendingStarPassNight = -1;
        mayorWarned = null;
        mayorWarnedNight = -1;
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
        if (!is(nominee, Role.VIRGIN) || !isAlive(nominee) || abilitySpent(nominee)) return;
        spendAbility(nominee, Role.VIRGIN);
        StateBroadcaster.broadcastGrimoire(server);

        PendingRoleAssignment nominatorRole = actualRole(nominator);
        String virgin = label(server, nominee);
        String who = label(server, nominator);
        String role = nominatorRole == null ? "no role" : nominatorRole.getDisplayName();
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
    public static String slayerShot(MinecraftServer server, UUID shooter, UUID target) {
        if (server == null || shooter == null || target == null) return "Choose a player to shoot.";
        if (ServerState.gameEnded) return "The game has ended.";
        if (ServerState.currentDay <= 0 || ServerState.currentNight != ServerState.currentDay) {
            return "The Slayer can only shoot during the day.";
        }
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(shooter)) return "You are not seated in this game.";
        if (!isAlive(shooter)) return "Dead players cannot use the Slayer ability.";
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(target)) return "That player is not seated.";
        if (shooter.equals(target)) return "You cannot shoot yourself.";
        if (!isAlive(target)) return label(server, target) + " is already dead.";

        String who = label(server, shooter);
        String whom = label(server, target);
        boolean realSlayer = is(shooter, Role.SLAYER);
        boolean spent = realSlayer && abilitySpent(shooter);
        if (realSlayer && !spent) spendAbility(shooter, Role.SLAYER);

        PendingRoleAssignment targetRole = actualRole(target);
        boolean demon = targetRole != null && targetRole.getRoleType() == RoleType.DEMON;
        boolean kills = realSlayer && !spent && !droisoned(shooter) && demon;

        announce(server, who + " claims to be the Slayer and shoots " + whom + "!", ChatFormatting.GOLD);
        if (kills) {
            ServerState.PLAYER_DEATH_STATUS.put(target, true);
            DeathVisibility.remove(target);
            TriggeredNightOrderManager.onDeath(target, TriggeredNightOrderManager.DeathCause.GENERIC);
            announce(server, whom + " dies!", ChatFormatting.RED);
            StateBroadcaster.broadcastDeathStatus(server);
            StateBroadcaster.broadcastGrimoire(server);
            tellStorytellers(server, "Slayer: " + who + " is the real Slayer and " + whom + " is the "
                    + targetRole.getDisplayName() + ". The kill was applied.", ChatFormatting.GOLD);
            onDeath(server, target, false);
            return null;
        }
        announce(server, "Nothing happens.", ChatFormatting.GRAY);
        String why;
        if (!realSlayer) why = who + " is not the Slayer (" + roleName(shooter) + ").";
        else if (spent) why = who + " had already used the Slayer ability.";
        else if (droisoned(shooter)) why = who + " is the Slayer but drunk or poisoned. The ability is now spent.";
        else why = whom + " is the " + roleName(target) + ", not the Demon. The ability is now spent."
                + (is(target, Role.RECLUSE) ? " (Recluse may register as the Demon: use Mark Dead if you choose so.)" : "");
        tellStorytellers(server, "Slayer: " + why, ChatFormatting.GOLD);
        StateBroadcaster.broadcastGrimoire(server);
        return null;
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
                    String by = source == null ? "a reminder" : "the " + source.getDisplayName();
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
    private static boolean protectorDroisoned(Role role) {
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
                            + " becomes the Demon, but " + deadRole.getDisplayName()
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
                    tellStorytellers(server, "Scarlet Woman: the " + deadRole.getDisplayName() + " died with "
                            + aliveBefore + " players alive, so " + label(server, scarlet) + " is now the "
                            + deadRole.getDisplayName() + ". Their role card has been updated.", ChatFormatting.GOLD);
                    BloodOnTheSharktower.LOGGER.info("Scarlet Woman {} became {} after {} died",
                            scarlet, deadRole.getDisplayName(), dead);
                }
            } else {
                if (scarlet != null && aliveBefore >= 5) {
                    tellStorytellers(server, "Scarlet Woman: " + label(server, scarlet)
                            + " is drunk or poisoned, so they do not become the Demon.", ChatFormatting.GOLD);
                }
                if (demonKill && is(dead, Role.IMP) && offerStarPass(server, dead)) return;
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
            String name = label(server, minion) + " (" + actualRole(minion).getDisplayName() + ")";
            message = message.copy().append(button("[" + name + "]", "/bots automation starpass " + seat,
                    name + " becomes the Imp")).append(Component.literal(" "));
        }
        tellStorytellers(server, message);
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
        ServerState.PLAYER_ROLES.put(minion, new PendingRoleAssignment(Role.IMP, current.override()));
        ServerState.PLAYER_PERCEIVED_ROLES.remove(minion);
        StateBroadcaster.broadcastGrimoire(server);
        ServerPlayer online = server.getPlayerList().getPlayer(minion);
        if (online != null) StateBroadcaster.sendRoleTo(online, false);
        tellStorytellers(server, "Star-pass: " + label(server, minion) + " (" + current.getDisplayName()
                + ") is now the Imp. Their role card has been updated; wake them to tell them.", ChatFormatting.GOLD);
        BloodOnTheSharktower.LOGGER.info("Star-pass: {} became the Imp", minion);
        return null;
    }

    // -------------------------------------------------------- Reminder expiry

    /** Dusk: clear reminders whose effect lasted "until dusk" or "tonight and tomorrow". */
    public static void onDusk(MinecraftServer server) {
        expireReminders(server, EXPIRE_AT_DUSK, "Dusk");
    }

    /** Dawn: clear reminders that only protected the night just gone. */
    public static void onDawn(MinecraftServer server) {
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
                        : label(server, entry.getKey())) + " " + reminder.text() + " (" + source.getDisplayName() + ")");
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
        if (!demonAlive) {
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

    static boolean droisoned(UUID player) {
        return is(player, Role.DRUNK) || hasReminder(player, "Poisoned") || hasReminder(player, "Drunk");
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
        return assignment == null ? "no role" : assignment.getDisplayName();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String label(MinecraftServer server, UUID id) {
        if (id == null) return "Unknown";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getName().getString();
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
        return seat == null ? "Player" : "Seat " + seat;
    }

    private static Component button(String text, String command, String hover) {
        return Component.literal(text)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static void announce(MinecraftServer server, String text, ChatFormatting colour) {
        Component message = Component.literal(text).withStyle(colour);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(message);
    }

    private static void tellStorytellers(MinecraftServer server, String text, ChatFormatting colour) {
        tellStorytellers(server, Component.literal(text).withStyle(colour));
    }

    private static void tellStorytellers(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StorytellerState.isStoryteller(player.getUUID())) player.sendSystemMessage(message);
        }
    }
}
