package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.PhasePresentation;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.ExecutionManager;
import com.sharktower.bloodonthesharktower.daytime.ExileManager;
import com.sharktower.bloodonthesharktower.daytime.ExileSupportManager;
import com.sharktower.bloodonthesharktower.daytime.NominationManager;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
import com.sharktower.bloodonthesharktower.daytime.VotePresentationSettings;
import com.sharktower.bloodonthesharktower.nightorder.TriggeredNightOrderManager;
import com.sharktower.bloodonthesharktower.setup.BaseThreeScripts;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative dispatcher for original-style Grimoire controls. */
public final class StorytellerActionHandler {
    private StorytellerActionHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StorytellerActionC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;

            // Storyteller UI packets must never promote an ordinary player.
            // The Storyteller claims control explicitly with /bots storyteller claim.
            if (!StorytellerState.isStoryteller(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "That Grimoire action is Storyteller-only. Your personal Grimoire edits stay on your client.")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }

            SetupOperations.Result result = dispatch(server, player, payload.action(), payload.argument());
            player.sendSystemMessage(Component.literal(result.message()));

            // Setup changes are intentionally re-broadcast through the same
            // state family used by reconnect/join sync. Storytellers receive
            // pending Grimoire state; players continue to receive committed roles.
            StateBroadcaster.broadcastCurrentState(server);
        });
    }

    private static SetupOperations.Result dispatch(MinecraftServer server, ServerPlayer actor, String action, String argument) {
        String op = action == null ? "" : action.trim().toLowerCase();
        String arg = argument == null ? "" : argument.trim();

        if (ServerState.gameEnded
                && !op.equals("end_game_good")
                && !op.equals("end_game_evil")
                && !op.equals("end_game_cancel")
                && !op.equals("reset_for_next_game")
                && !op.equals("game_complete")
                && !op.equals("reset_hard")) {
            return SetupOperations.Result.fail("End-game reveal is active. Reset for the next game or cancel the reveal first.");
        }

        try {
            return switch (op) {
                case "shuffle_roles" -> SetupOperations.shuffleRoles();
                case "shuffle_seats" -> SetupOperations.shuffleSeats();
                case "randomize_roles" -> SetupOperations.randomizeRoles();
                case "distribute_role_bag" -> SetupOperations.distributeRoleBag(
                        arg.isBlank() ? java.util.List.of() : java.util.Arrays.asList(arg.split("\\|"))
                );
                case "randomize_bluffs" -> SetupOperations.randomizeBluffs();
                case "clear_bluffs" -> SetupOperations.clearBluffs();
                case "send_roles" -> SetupOperations.commitSetup(server);
                case "discard" -> SetupOperations.discardPending(server);
                case "reset_soft" -> SetupOperations.resetGame(server, false);
                case "reset_hard" -> SetupOperations.resetGame(server, true);
                case "game_complete" -> SetupOperations.completeGame(server);
                case "end_game_good" -> endGame(server, "GOOD");
                case "end_game_evil" -> endGame(server, "EVIL");
                case "end_game_cancel" -> cancelEndGame(server);
                case "reset_for_next_game" -> resetForNextGame(server);
                case "phase_night" -> asSetupResult(PhaseOperations.enterNight(server));
                case "phase_day" -> asSetupResult(PhaseOperations.enterDay(server));
                case "night_visit" -> nightVisit(server, actor, UUID.fromString(arg));
                case "mark_dead" -> markDead(server, Integer.parseInt(arg), false);
                case "demon_kill" -> markDead(server, Integer.parseInt(arg), true);
                case "revive_player" -> revivePlayer(server, Integer.parseInt(arg));
                case "nominations_open" -> openNominations(server);
                case "nominations_close" -> closeNominations(server);
                case "nominate_pair" -> nominatePair(server, arg);
                case "nomination_cancel" -> cancelNomination(server);
                case "vote_start" -> startVote(server);
                case "vote_finish" -> finishVote(server);
                case "vote_cancel" -> cancelVote(server);
                case "vote_override_delta" -> adjustVoteOverride(server, Integer.parseInt(arg));
                case "vote_override_clear" -> clearVoteOverride(server);
                case "execute_marked" -> executeMarked(server, true);
                case "execute_marked_survives" -> executeMarked(server, false);
                case "no_execution" -> noExecution(server);
                case "exile_call_pair" -> callExile(server, arg);
                case "exile_start" -> startExile(server);
                case "exile_finish" -> finishExile(server);
                case "exile_cancel_vote" -> cancelExileVote(server);
                case "exile_reset" -> resetExile(server);
                case "exile_override_delta" -> adjustExileOverride(server, Integer.parseInt(arg));
                case "exile_override_clear" -> clearExileOverride(server);
                case "set_vote_step_ticks" -> setVoteStepTicks(server, Integer.parseInt(arg));
                case "set_clock_scale" -> setClockScale(server, Double.parseDouble(arg));
                case "send_to_seats" -> {
                    int moved = SeatPositionManager.sendAllToTownSquare(server, SetupOperations.workingSeats());
                    yield SetupOperations.Result.ok("Sent " + moved + " connected player(s) to town-square seats.");
                }
                case "send_home" -> {
                    int moved = SeatPositionManager.sendAllHome(server, SetupOperations.workingSeats());
                    yield SetupOperations.Result.ok("Sent " + moved + " connected player(s) home.");
                }
                case "assign_role" -> {
                    String[] parts = split(arg, 2);
                    yield SetupOperations.assignRole(Integer.parseInt(parts[0]), parts[1]);
                }
                case "assign_perceived_role" -> {
                    String[] parts = split(arg, 2);
                    yield SetupOperations.assignPerceivedRole(Integer.parseInt(parts[0]), parts[1]);
                }
                case "clear_perceived_role" -> SetupOperations.clearPerceivedRole(Integer.parseInt(arg));
                case "clear_role" -> SetupOperations.clearRole(Integer.parseInt(arg));
                case "alignment_default" -> SetupOperations.setAlignment(Integer.parseInt(arg), AlignmentOverride.DEFAULT);
                case "alignment_good" -> SetupOperations.setAlignment(Integer.parseInt(arg), AlignmentOverride.FORCE_GOOD);
                case "alignment_evil" -> SetupOperations.setAlignment(Integer.parseInt(arg), AlignmentOverride.FORCE_BAD);
                case "add_reminder" -> {
                    String[] parts = split(arg, 2);
                    yield SetupOperations.addReminder(Integer.parseInt(parts[0]), parts[1]);
                }
                case "add_role_reminder" -> {
                    String[] parts = split(arg, 3);
                    yield SetupOperations.addRoleReminder(Integer.parseInt(parts[0]), parts[1], parts[2]);
                }
                case "clear_reminders" -> SetupOperations.clearReminders(Integer.parseInt(arg));
                case "remove_reminder" -> {
                    String[] parts = split(arg, 2);
                    yield SetupOperations.removeReminder(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
                case "seat_player" -> SetupOperations.seatNext(java.util.UUID.fromString(arg));
                case "load_script_json" -> SetupOperations.loadScriptJson(arg);
                case "load_base3" -> BaseThreeScripts.load(arg);
                case "add_bluff" -> SetupOperations.addBluff(arg);
                case "set_bluff" -> {
                    String[] parts = split(arg, 2);
                    yield SetupOperations.setBluff(Integer.parseInt(parts[0]), parts[1]);
                }
                default -> SetupOperations.Result.fail("Unknown Storyteller UI action: " + action);
            };
        } catch (RuntimeException ex) {
            return SetupOperations.Result.fail("Could not perform Storyteller action: " + ex.getMessage());
        }
    }

    private static SetupOperations.Result markDead(MinecraftServer server, int seat, boolean demonKill) {
        if (ServerState.currentNight == 0 && ServerState.currentDay == 0) {
            return SetupOperations.Result.fail("Death state is only available after the game has started.");
        }
        UUID target = playerAtSeat(seat);
        if (target == null) return SetupOperations.Result.fail("No player is assigned to seat " + seat + ".");
        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(target))) {
            return SetupOperations.Result.fail("Seat " + seat + " is already dead.");
        }
        if (demonKill && !PhaseOperations.isNight()) {
            return SetupOperations.Result.fail("Demon Kill is only available during Night.");
        }

        ServerState.PLAYER_DEATH_STATUS.put(target, true);
        TriggeredNightOrderManager.DeathCause cause = demonKill
                ? TriggeredNightOrderManager.DeathCause.DEMON
                : (PhaseOperations.isNight()
                    ? TriggeredNightOrderManager.DeathCause.NIGHT
                    : TriggeredNightOrderManager.DeathCause.GENERIC);
        TriggeredNightOrderManager.onDeath(target, cause);
        StateBroadcaster.broadcastDeathStatus(server);
        StateBroadcaster.broadcastGrimoire(server);

        return SetupOperations.Result.ok("Marked seat " + seat + " dead"
                + (demonKill ? " (Demon kill)." : "."));
    }

    private static SetupOperations.Result revivePlayer(MinecraftServer server, int seat) {
        UUID target = playerAtSeat(seat);
        if (target == null) return SetupOperations.Result.fail("No player is assigned to seat " + seat + ".");
        if (!Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(target))) {
            return SetupOperations.Result.fail("Seat " + seat + " is already alive.");
        }

        ServerState.PLAYER_DEATH_STATUS.put(target, false);
        TriggeredNightOrderManager.onRevived(target);
        StateBroadcaster.broadcastDeathStatus(server);
        StateBroadcaster.broadcastGrimoire(server);
        return SetupOperations.Result.ok("Revived seat " + seat + ". Any unresolved triggers caused by that death were removed.");
    }

    private static UUID playerAtSeat(int seat) {
        if (seat <= 0) return null;
        for (java.util.Map.Entry<UUID, Integer> entry : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == seat) return entry.getKey();
        }
        return null;
    }

    /**
     * Original-style night-order visit action. Selecting a role in the Storyteller
     * phase bar moves the Storyteller to that player's configured house and starts
     * the same house-bound private-chat invitation flow as /bots teleportToSeat.
     */
    private static SetupOperations.Result nightVisit(MinecraftServer server, ServerPlayer storyteller, UUID targetId) {
        if (storyteller == null || !StorytellerState.isStoryteller(storyteller.getUUID())) {
            return SetupOperations.Result.fail("Only the Storyteller can use night visits.");
        }
        if (!PhaseOperations.isNight()) {
            return SetupOperations.Result.fail("Night role visits are only available during Night.");
        }
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(targetId);
        if (seat == null || seat <= 0) {
            return SetupOperations.Result.fail("That player is no longer seated.");
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            return SetupOperations.Result.fail("Seat " + seat + " is not currently connected.");
        }

        UUID currentPartner = NightChatManager.privatePartner(storyteller.getUUID());
        if (currentPartner != null && !currentPartner.equals(targetId)) {
            NightChatManager.Result left = NightChatManager.leavePrivate(storyteller.getUUID());
            if (!left.ok()) {
                return SetupOperations.Result.fail("Could not close the previous private chat: " + left.message());
            }
            ServerPlayer previous = server.getPlayerList().getPlayer(currentPartner);
            if (previous != null) {
                previous.sendSystemMessage(Component.literal(
                        "The Storyteller left your private voice chat. You remain private until you choose to leave.")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        if (!SeatPositionManager.teleportPlayerToHome(storyteller, seat)) {
            return SetupOperations.Result.fail("Seat " + seat
                    + " has no configured home. Use /bots setSeatHome " + seat + " first.");
        }

        // Spy/Widow Grimoire information is deliberately Storyteller-confirmed.
        // The visit posts a clickable share prompt to the Storyteller instead of
        // silently pushing a separate read-only screen to the player.
        String grimoireShare = promptTrueGrimoireShare(storyteller, target);

        // If this player is already the Storyteller's active private partner, the
        // visit is complete; do not create a duplicate invitation. Re-activating
        // the visit still re-sends the snapshot, which gives the player a way to
        // reopen it if they closed the view accidentally.
        if (targetId.equals(NightChatManager.privatePartner(storyteller.getUUID()))) {
            return SetupOperations.Result.ok("Visited " + target.getName().getString()
                    + " (Seat " + seat + "); private chat already connected." + grimoireShare);
        }

        NightChatManager.InviteResult invite = NightChatManager.createStorytellerHouseInvite(
                storyteller.getUUID(), targetId, seat);
        if (!invite.ok()) {
            return SetupOperations.Result.fail("Teleported to Seat " + seat
                    + ", but could not create the private-chat invite: " + invite.message());
        }

        String acceptCommand = "/bots private accept " + invite.token();
        Component message = Component.literal("The Storyteller wants to speak with you privately. ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("[JOIN PRIVATE CHAT]")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand(acceptCommand))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                        "Join the Storyteller's private voice room")))));
        target.sendSystemMessage(message);

        return SetupOperations.Result.ok("Visited " + target.getName().getString()
                + " (Seat " + seat + "); private-chat invite sent." + grimoireShare);
    }

    /**
     * During a Spy/Widow visit, ask the Storyteller to explicitly share the
     * current Storyteller Grimoire instead of auto-sending it.
     */
    private static String promptTrueGrimoireShare(ServerPlayer storyteller, ServerPlayer target) {
        if (storyteller == null || target == null) return "";

        UUID id = target.getUUID();
        PendingRoleAssignment actual = ServerState.PLAYER_ROLES.get(id);
        if (actual == null || actual.isCustomRole() || actual.role() == null) return "";

        Role role = actual.role();
        boolean eligible = role == Role.SPY
                || (role == Role.WIDOW && ServerState.currentNight == 1 && ServerState.currentDay == 0);
        if (!eligible) return "";

        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id))) {
            return " " + role.getDisplayName() + " is dead; no Grimoire share prompt was created.";
        }

        if (isDroisonedForGrimoire(id)) {
            storyteller.sendSystemMessage(Component.literal(
                            role.getDisplayName() + " is currently Droisoned. True Grimoire sharing is blocked; give false information manually if appropriate.")
                    .withStyle(ChatFormatting.RED));
            return " True Grimoire share blocked because " + role.getDisplayName() + " is Droisoned.";
        }

        String shareCommand = "/bots sharegrimoire " + id;
        Component prompt = Component.literal(role.getDisplayName() + " may see the Grimoire. ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("[SHARE GRIMOIRE]")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand(shareCommand))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                        "Share your current Storyteller Grimoire, reminders and Demon bluffs")))));
        storyteller.sendSystemMessage(prompt);
        return " Grimoire share prompt sent.";
    }

    /**
     * Called by the Storyteller's clickable chat action. Re-check every rule at
     * click time so a stale prompt cannot leak the true Grim after state changes.
     */
    public static SetupOperations.Result shareAbilityGrimoire(
            MinecraftServer server,
            ServerPlayer storyteller,
            UUID targetId
    ) {
        if (server == null || storyteller == null || !StorytellerState.isStoryteller(storyteller.getUUID())) {
            return SetupOperations.Result.fail("Only the Storyteller can share a Spy/Widow Grimoire.");
        }
        if (!PhaseOperations.isNight()) {
            return SetupOperations.Result.fail("Spy/Widow Grimoire sharing is only available during Night.");
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) return SetupOperations.Result.fail("That Spy/Widow is no longer connected.");

        PendingRoleAssignment actual = ServerState.PLAYER_ROLES.get(targetId);
        if (actual == null || actual.isCustomRole() || actual.role() == null) {
            return SetupOperations.Result.fail("That player does not have an eligible official Spy/Widow ability.");
        }

        Role role = actual.role();
        boolean eligible = role == Role.SPY
                || (role == Role.WIDOW && ServerState.currentNight == 1 && ServerState.currentDay == 0);
        if (!eligible) {
            return SetupOperations.Result.fail("That player is not currently eligible to see the true Grimoire.");
        }
        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(targetId))) {
            return SetupOperations.Result.fail(role.getDisplayName() + " is dead.");
        }
        if (isDroisonedForGrimoire(targetId)) {
            return SetupOperations.Result.fail(role.getDisplayName()
                    + " is Droisoned; true Grimoire sharing remains blocked.");
        }

        java.util.List<String> bluffIds = StorytellerState.DEMON_BLUFFS.stream()
                .map(bluff -> bluff.getId())
                .toList();

        Map<UUID, PendingRoleAssignment> shareRoles = StorytellerState.effectiveGrimoireRoles();
        boolean magicianJinxApplied = hasInPlayMagician(shareRoles);
        if (magicianJinxApplied) {
            shareRoles = applyMagicianGrimoireJinx(shareRoles);
        }

        ServerPlayNetworking.send(target, new AbilityGrimoireS2CPayload(
                shareRoles,
                StorytellerState.effectiveGrimoireSeats(),
                StorytellerState.REMINDERS,
                bluffIds,
                role.getId()
        ));

        String playerMessage = "The Storyteller shared the Grimoire with you. "
                + "True roles and Storyteller reminders were added to your personal Grim; "
                + "your own reminder notes were kept. Demon bluffs were also shared.";
        if (magicianJinxApplied) {
            playerMessage += " Magician jinx applied: the Magician and Demon character tokens were removed.";
        }
        target.sendSystemMessage(Component.literal(playerMessage)
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        return SetupOperations.Result.ok("Shared the current Storyteller Grimoire with "
                + target.getName().getString() + " (" + role.getDisplayName() + ")."
                + (magicianJinxApplied
                    ? " Magician jinx applied: Magician and Demon character tokens hidden."
                    : ""));
    }

    private static boolean hasInPlayMagician(Map<UUID, PendingRoleAssignment> roles) {
        if (roles == null || roles.isEmpty()) return false;
        for (PendingRoleAssignment assignment : roles.values()) {
            if (assignment == null || assignment.isCustomRole()) continue;
            if (assignment.role() == Role.MAGICIAN) return true;
        }
        return false;
    }

    /**
     * Magician / Spy and Magician / Widow jinx:
     * when the evil information role sees the Grimoire, remove the Magician and
     * Demon character tokens from that shared view only.
     *
     * The Storyteller's real Grimoire is not changed. Explicit NO_ROLE entries
     * are used rather than removing map keys so the receiving client's local role
     * guesses are definitely overwritten with a blank token.
     */
    private static Map<UUID, PendingRoleAssignment> applyMagicianGrimoireJinx(
            Map<UUID, PendingRoleAssignment> roles
    ) {
        Map<UUID, PendingRoleAssignment> filtered = new java.util.HashMap<>(roles);
        PendingRoleAssignment blank = new PendingRoleAssignment(
                Role.NO_ROLE,
                AlignmentOverride.DEFAULT
        );

        for (Map.Entry<UUID, PendingRoleAssignment> entry : roles.entrySet()) {
            PendingRoleAssignment assignment = entry.getValue();
            if (assignment == null) continue;

            boolean magician = !assignment.isCustomRole() && assignment.role() == Role.MAGICIAN;
            boolean demon = assignment.getRoleType() == com.sharktower.bloodonthesharktower.core.RoleType.DEMON;

            if (magician || demon) {
                filtered.put(entry.getKey(), blank);
            }
        }

        return Map.copyOf(filtered);
    }

    private static boolean isDroisonedForGrimoire(UUID playerId) {
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(playerId, java.util.List.of())) {
            if (reminder == null || reminder.text() == null) continue;
            String text = reminder.text().trim();
            if (text.equalsIgnoreCase("Poisoned") || text.equalsIgnoreCase("Drunk")) return true;
        }
        return false;
    }

    private static SetupOperations.Result openNominations(MinecraftServer server) {
        Set<UUID> players = new HashSet<>(ServerState.PLAYER_SEAT_NUMBERS.keySet());
        if (players.isEmpty()) return SetupOperations.Result.fail("No seated players are available.");
        DaytimeState.openNominations(players, ServerState.deadPlayers(), java.util.List.of(), java.util.List.of());
        VotingManager.clearLastResult();
        PhasePresentation.nominationsOpen(server);
        return SetupOperations.Result.ok("Nominations opened for " + players.size() + " player(s).");
    }

    private static SetupOperations.Result closeNominations(MinecraftServer server) {
        DaytimeState.closeNominations();
        announce(server, Component.literal("Nominations are closed.").withStyle(ChatFormatting.GRAY));
        return SetupOperations.Result.ok("Nominations closed.");
    }

    private static SetupOperations.Result nominatePair(MinecraftServer server, String arg) {
        String[] parts = split(arg, 2);
        UUID nominator = UUID.fromString(parts[0]);
        UUID nominee = UUID.fromString(parts[1]);
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(nominator)) {
            return SetupOperations.Result.fail("The selected nominator is not seated.");
        }
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(nominee)) {
            return SetupOperations.Result.fail("The selected nominee is not seated.");
        }
        if (!NominationManager.executeNomination(server, nominator, nominee, VotingManager.alivePlayerCount())) {
            return SetupOperations.Result.fail("That nomination is not currently legal.");
        }
        announce(server, Component.literal(label(server, nominator) + " nominates " + label(server, nominee) + ".")
                .withStyle(ChatFormatting.GOLD));
        PhasePresentation.nomination(server);
        return SetupOperations.Result.ok("Nomination accepted. " + VotingManager.currentHandsRequired() + " vote(s) required.");
    }

    private static SetupOperations.Result cancelNomination(MinecraftServer server) {
        if (!DaytimeState.hasActiveNomination()) return SetupOperations.Result.fail("There is no active nomination.");
        NominationManager.resetNomination(server);
        announce(server, Component.literal("The current nomination was cancelled by the Storyteller.")
                .withStyle(ChatFormatting.GRAY));
        return SetupOperations.Result.ok("Current nomination cancelled.");
    }

    private static SetupOperations.Result startVote(MinecraftServer server) {
        if (!DaytimeState.hasActiveNomination()) return SetupOperations.Result.fail("There is no active nomination to vote on.");
        if (DaytimeState.isVoteInProgress()) return SetupOperations.Result.fail("A vote is already running.");
        VotingManager.startVote(server, ServerState.deadPlayers(), false, false, null, Set.of());
        UUID nominee = DaytimeState.getCurrentNominee();
        announce(server, Component.literal("Voting begins on " + label(server, nominee) + ". Raise your hand before the clock reaches your seat.")
                .withStyle(ChatFormatting.AQUA));
        return SetupOperations.Result.ok("Clockwise vote started. " + VotingManager.currentHandsRequired() + " vote(s) required.");
    }

    private static SetupOperations.Result finishVote(MinecraftServer server) {
        if (!DaytimeState.isVoteInProgress()) return SetupOperations.Result.fail("No vote is currently running.");
        if (!VotingManager.canResolveVote()) return SetupOperations.Result.fail("The vote clock has not reached every seat yet.");
        VotingManager.Result result = VotingManager.resolveVote(server);
        String resultText = switch (result.result()) {
            case MARKED -> label(server, result.nominee()) + " is on the block with " + result.votes() + " vote(s).";
            case TIE -> "The vote ties the current high vote at " + result.votes() + ". Nobody is on the block.";
            case NOT_ENOUGH -> label(server, result.nominee()) + " receives " + result.votes() + " vote(s), not enough to take the block.";
            case NONE -> "The vote is still pending.";
        };
        announce(server, Component.literal(resultText).withStyle(
                result.result() == VotingManager.VoteResult.MARKED ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return SetupOperations.Result.ok(resultText);
    }

    private static SetupOperations.Result cancelVote(MinecraftServer server) {
        if (!DaytimeState.isVoteInProgress()) return SetupOperations.Result.fail("No vote is currently running.");
        VotingManager.resetVote(server);
        announce(server, Component.literal("The vote was cancelled. The nomination remains active.")
                .withStyle(ChatFormatting.GRAY));
        return SetupOperations.Result.ok("Vote cancelled; nomination kept.");
    }

    private static SetupOperations.Result adjustVoteOverride(MinecraftServer server, int delta) {
        if (delta == 0) return SetupOperations.Result.ok("Vote count unchanged.");
        if (!VotingManager.adjustVoteCountOverride(server, delta)) {
            return SetupOperations.Result.fail("Count overrides are available after the vote clock completes and before Finish Vote.");
        }
        return SetupOperations.Result.ok("Storyteller vote-count override: " + VotingManager.effectiveVoteCount() + ".");
    }

    private static SetupOperations.Result clearVoteOverride(MinecraftServer server) {
        if (!DaytimeState.isVoteInProgress()) return SetupOperations.Result.fail("No vote is currently running.");
        VotingManager.clearVoteCountOverride(server);
        return SetupOperations.Result.ok("Vote-count override cleared. Locked count: " + VotingManager.effectiveVoteCount() + ".");
    }

    private static SetupOperations.Result executeMarked(MinecraftServer server, boolean dies) {
        UUID marked = DaytimeState.getMarkedForExecution();
        if (marked == null) {
            if (DaytimeState.getStorytellerMFE() != null) {
                return SetupOperations.Result.fail("The Storyteller is on the block. Resolve the Atheist outcome manually.");
            }
            return SetupOperations.Result.fail("Nobody is currently on the block.");
        }

        String name = label(server, marked);
        if (dies) {
            ExecutionManager.executePlayer(server, marked, false, null);
            announce(server, Component.literal(name + " is executed and dies.")
                    .withStyle(ChatFormatting.RED));
            return SetupOperations.Result.ok("Executed " + name + "; they died.");
        }

        ExecutionManager.executePlayerFail(server, marked, false, null);
        announce(server, Component.literal(name + " is executed but does not die.")
                .withStyle(ChatFormatting.GOLD));
        return SetupOperations.Result.ok("Executed " + name + "; they survived.");
    }

    private static SetupOperations.Result noExecution(MinecraftServer server) {
        ExecutionManager.noExecution(server);
        announce(server, Component.literal("The day ends with no execution.").withStyle(ChatFormatting.GRAY));
        return SetupOperations.Result.ok("Day closed with no execution.");
    }



    private static SetupOperations.Result callExile(MinecraftServer server, String arg) {
        String[] parts = split(arg, 2);
        UUID caller = UUID.fromString(parts[0]);
        UUID traveler = UUID.fromString(parts[1]);
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(caller)) {
            return SetupOperations.Result.fail("The selected exile caller is not seated.");
        }
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(traveler)) {
            return SetupOperations.Result.fail("The selected Traveller is not seated.");
        }
        if (!ExileManager.callForExile(server, caller, traveler, false)) {
            return SetupOperations.Result.fail("That exile call is not currently legal. Exiles are daytime-only and the target must be an active Traveller.");
        }
        announce(server, Component.literal(label(server, caller) + " calls to exile " + label(server, traveler) + ".")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return SetupOperations.Result.ok("Exile call accepted. " + ExileSupportManager.currentThreshold() + " support vote(s) required.");
    }

    private static SetupOperations.Result startExile(MinecraftServer server) {
        if (!DaytimeState.hasActiveExile()) return SetupOperations.Result.fail("There is no active exile call.");
        if (!ExileSupportManager.startExileSupport(server)) {
            return SetupOperations.Result.fail("The exile support clock is already running or cannot start.");
        }
        announce(server, Component.literal("Exile support voting begins. Dead players may vote without spending a ghost vote.")
                .withStyle(ChatFormatting.AQUA));
        return SetupOperations.Result.ok("Exile clock started. " + ExileSupportManager.currentThreshold() + " support vote(s) required.");
    }

    private static SetupOperations.Result finishExile(MinecraftServer server) {
        if (!DaytimeState.isExileSupportInProgress()) return SetupOperations.Result.fail("No exile support vote is running.");
        if (!ExileSupportManager.canResolve()) return SetupOperations.Result.fail("The exile clock has not reached every active player yet.");
        UUID target = DaytimeState.getCurrentExileTarget();
        ExileSupportManager.Result result = ExileSupportManager.resolve(server);
        String text = label(server, target) + (result.exiled() ? " is EXILED" : " is NOT exiled")
                + " with " + result.support() + "/" + result.threshold() + " support.";
        announce(server, Component.literal(text).withStyle(result.exiled() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        return SetupOperations.Result.ok(text);
    }

    private static SetupOperations.Result cancelExileVote(MinecraftServer server) {
        if (!ExileSupportManager.cancelSupportVote(server)) {
            return SetupOperations.Result.fail("No exile support vote is currently running.");
        }
        announce(server, Component.literal("The exile vote was cancelled. The exile call remains active.")
                .withStyle(ChatFormatting.GRAY));
        return SetupOperations.Result.ok("Exile vote cancelled; exile call kept.");
    }

    private static SetupOperations.Result resetExile(MinecraftServer server) {
        if (!DaytimeState.hasActiveExile()) return SetupOperations.Result.fail("There is no active exile call.");
        ExileManager.resetExile(server);
        announce(server, Component.literal("The exile call was cancelled by the Storyteller.").withStyle(ChatFormatting.GRAY));
        return SetupOperations.Result.ok("Exile call cancelled.");
    }

    private static SetupOperations.Result adjustExileOverride(MinecraftServer server, int delta) {
        if (delta == 0) return SetupOperations.Result.ok("Exile count unchanged.");
        if (!ExileSupportManager.adjustSupportOverride(server, delta)) {
            return SetupOperations.Result.fail("Exile count overrides are available after the exile clock completes.");
        }
        return SetupOperations.Result.ok("Storyteller exile-count override: " + ExileSupportManager.effectiveSupportCount() + ".");
    }

    private static SetupOperations.Result clearExileOverride(MinecraftServer server) {
        if (!DaytimeState.isExileSupportInProgress()) return SetupOperations.Result.fail("No exile support vote is running.");
        ExileSupportManager.clearSupportOverride(server);
        return SetupOperations.Result.ok("Exile-count override cleared. Locked support: " + ExileSupportManager.lockedSupportCount() + ".");
    }

    private static SetupOperations.Result endGame(MinecraftServer server, String winner) {
        SetupOperations.Result result = SetupOperations.beginEndGame(server, winner);
        if (!result.ok()) return result;

        ChatFormatting colour = "GOOD".equals(winner) ? ChatFormatting.AQUA : ChatFormatting.RED;
        announce(server, Component.literal("GAME OVER — " + winner + " WINS")
                .withStyle(colour, ChatFormatting.BOLD));
        announce(server, Component.literal("The Final Grimoire is now revealed. Press your Grimoire key to view it.")
                .withStyle(ChatFormatting.GOLD));
        return result;
    }

    private static SetupOperations.Result cancelEndGame(MinecraftServer server) {
        SetupOperations.Result result = SetupOperations.cancelEndGame(server);
        if (result.ok()) {
            announce(server, Component.literal("End-game reveal cancelled by the Storyteller.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return result;
    }

    private static SetupOperations.Result resetForNextGame(MinecraftServer server) {
        SetupOperations.Result result = SetupOperations.completeGame(server);
        if (result.ok()) {
            announce(server, Component.literal("The match has been reset to its start-of-game checkpoint.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return result;
    }

    private static SetupOperations.Result setVoteStepTicks(MinecraftServer server, int ticks) {
        if (DaytimeState.isVoteInProgress() || DaytimeState.isExileSupportInProgress()) {
            return SetupOperations.Result.fail("Vote speed cannot be changed while an election clock is running.");
        }
        VotePresentationSettings.setStepTicks(ticks);
        StateBroadcaster.broadcastVoteState(server);
        return SetupOperations.Result.ok(String.format(java.util.Locale.ROOT,
                "Vote speed set to %.2f second(s) per seat.", VotePresentationSettings.stepTicks() / 20.0D));
    }

    private static SetupOperations.Result setClockScale(MinecraftServer server, double scale) {
        SeatPositionManager.setClockHandScale(Math.max(1.5D, Math.min(8.0D, scale)));
        StateBroadcaster.broadcastVoteState(server);
        return SetupOperations.Result.ok(String.format(java.util.Locale.ROOT,
                "Clock-hand scale set to %.2f.", SeatPositionManager.clockHandScale()));
    }

    private static void announce(MinecraftServer server, Component message) {
        for (ServerPlayer target : server.getPlayerList().getPlayers()) target.sendSystemMessage(message);
    }

    private static String label(MinecraftServer server, UUID id) {
        if (id == null) return "Unknown";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getName().getString();
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
        return seat == null ? "Player" : "Seat " + seat;
    }

    private static SetupOperations.Result asSetupResult(PhaseOperations.Result result) {
        return result.ok()
                ? SetupOperations.Result.ok(result.message())
                : SetupOperations.Result.fail(result.message());
    }

    private static String[] split(String input, int expected) {
        String[] parts = input.split("\\|", expected);
        if (parts.length < expected) throw new IllegalArgumentException("missing action argument");
        return parts;
    }
}
