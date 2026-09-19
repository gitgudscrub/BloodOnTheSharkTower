package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.NominationManager;
import com.sharktower.bloodonthesharktower.daytime.ExileManager;
import com.sharktower.bloodonthesharktower.daytime.ExileSupportManager;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-authoritative player interactions exposed by the central Storyteller token. */
public final class PlayerActionHandler {
    private PlayerActionHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PlayerActionC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;

            String action = payload.action() == null ? "" : payload.action().trim().toLowerCase();
            String argument = payload.argument() == null ? "" : payload.argument().trim();

            if (ServerState.gameEnded && !"leave_private".equals(action)) {
                player.sendSystemMessage(Component.literal("The game has ended. Open the Final Grimoire to review the roles."));
                return;
            }

            switch (action) {
                case "request_private_storyteller" -> requestPrivate(player, server, argument);
                case "nominate_storyteller" -> nominateStoryteller(player, server, argument);
                case "call_for_exile" -> callForExile(player, server, argument);
                case "toggle_hand" -> toggleHand(player, server, null);
                case "raise_hand" -> toggleHand(player, server, true);
                case "lower_hand" -> toggleHand(player, server, false);
                case "leave_private" -> leavePrivate(player);
                default -> player.sendSystemMessage(Component.literal("Unknown BOTS player action: " + payload.action()));
            }
        });
    }

    private static void leavePrivate(ServerPlayer player) {
        NightChatManager.Result result = NightChatManager.leavePrivate(player.getUUID());
        player.sendSystemMessage(Component.literal(result.message())
                .withStyle(result.ok() ? ChatFormatting.GRAY : ChatFormatting.RED));
    }

    private static void toggleHand(ServerPlayer player, MinecraftServer server, Boolean forcedState) {
        UUID id = player.getUUID();
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(id)) {
            player.sendSystemMessage(Component.literal("You must be seated to raise a voting hand."));
            return;
        }
        if (StorytellerState.isStoryteller(id)) {
            player.sendSystemMessage(Component.literal("Storytellers do not raise player voting hands."));
            return;
        }
        boolean exileVoting = DaytimeState.hasActiveExile() || DaytimeState.isExileSupportInProgress();
        if (DaytimeState.isExiledTraveler(id)) {
            player.sendSystemMessage(Component.literal("You have been exiled and are no longer participating in votes."));
            return;
        }
        if (!DaytimeState.areNominationsOpen() && !DaytimeState.isVoteInProgress() && !exileVoting) {
            player.sendSystemMessage(Component.literal("Voting hands are available during nominations or an exile call."));
            return;
        }
        if (!exileVoting && Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id)) && DaytimeState.hasUsedGhostVote(id)) {
            DaytimeState.setRaisedHand(id, false);
            StateBroadcaster.broadcastVoteState(server);
            player.sendSystemMessage(Component.literal("Your ghost vote has already been used."));
            return;
        }
        boolean alreadyLocked = exileVoting
                ? DaytimeState.getLockedExileSupportVotes().containsKey(id)
                : DaytimeState.getLockedVotes().containsKey(id);
        if ((DaytimeState.isVoteInProgress() || DaytimeState.isExileSupportInProgress()) && alreadyLocked) {
            player.sendSystemMessage(Component.literal("Your vote has already been counted."));
            return;
        }

        boolean next = forcedState != null ? forcedState : !DaytimeState.isHandRaised(id);
        DaytimeState.setRaisedHand(id, next);
        StateBroadcaster.broadcastVoteState(server);
        player.sendSystemMessage(Component.literal(exileVoting
                ? (next ? "Exile support hand raised." : "Exile support hand lowered.")
                : (next ? "Voting hand raised." : "Voting hand lowered.")));
    }


    private static void callForExile(ServerPlayer caller, MinecraftServer server, String argument) {
        UUID target = parseUuid(argument);
        if (target == null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(target)) {
            caller.sendSystemMessage(Component.literal("That Traveller is no longer available for exile."));
            return;
        }
        if (!ExileManager.callForExile(server, caller.getUUID(), target, false)) {
            caller.sendSystemMessage(Component.literal("That exile call is not currently legal. Exile is daytime-only and no other election may be active."));
            return;
        }
        String targetName = ServerStateNameHelper.nameOf(server, target);
        Component announcement = Component.literal(caller.getName().getString() + " calls to exile " + targetName
                        + ". " + ExileSupportManager.currentThreshold() + " support vote(s) required.")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(announcement);
        StateBroadcaster.broadcastCurrentState(server);
    }

    private static void requestPrivate(ServerPlayer player, MinecraftServer server, String argument) {
        UUID storytellerId = parseUuid(argument);
        if (storytellerId == null || !StorytellerState.isStoryteller(storytellerId)) {
            player.sendSystemMessage(Component.literal("That Storyteller is no longer available."));
            return;
        }
        ServerPlayer storyteller = server.getPlayerList().getPlayer(storytellerId);
        if (storyteller == null) {
            player.sendSystemMessage(Component.literal("That Storyteller is not currently online."));
            return;
        }

        NightChatManager.InviteResult result = NightChatManager.createPlayerRequest(player.getUUID(), storytellerId);
        if (!result.ok()) {
            player.sendSystemMessage(Component.literal(result.message()));
            return;
        }

        int seat = ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(player.getUUID(), 0);
        String acceptCommand = "/bots private accept " + result.token();
        Component request = Component.literal(player.getName().getString()
                        + (seat > 0 ? " (Seat " + seat + ")" : "")
                        + " is requesting a private conversation. ")
                .withStyle(ChatFormatting.AQUA)
                .append(actionButton("[ACCEPT]", acceptCommand,
                        "Move you and this player into a private voice room"));
        storyteller.sendSystemMessage(request);
        player.sendSystemMessage(Component.literal("Private chat request sent to "
                + storyteller.getName().getString() + ". It expires in 60 seconds."));
    }

    private static void nominateStoryteller(ServerPlayer nominator, MinecraftServer server, String argument) {
        UUID storytellerId = parseUuid(argument);
        if (storytellerId == null || !StorytellerState.isStoryteller(storytellerId)) {
            nominator.sendSystemMessage(Component.literal("That Storyteller is no longer available."));
            return;
        }
        if (ServerState.currentScript == null || !ServerState.currentScript.roles().contains(Role.ATHEIST)) {
            nominator.sendSystemMessage(Component.literal("The Storyteller can only be nominated when Atheist is on the current script."));
            return;
        }

        int alive = VotingManager.alivePlayerCount();
        boolean ok = NominationManager.executeStorytellerNomination(
                server, nominator.getUUID(), storytellerId, alive
        );
        if (!ok) {
            nominator.sendSystemMessage(Component.literal("That Storyteller nomination is not currently legal."));
            return;
        }

        String storytellerName = ServerStateNameHelper.nameOf(server, storytellerId);
        Component announcement = Component.literal(nominator.getName().getString()
                + " nominated Storyteller " + storytellerName + ".")
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(announcement);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Component actionButton(String label, String command, String hoverText) {
        return Component.literal(label)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
    }

    /** Tiny helper kept local to avoid broadening the player-directory API just for one message. */
    private static final class ServerStateNameHelper {
        private static String nameOf(MinecraftServer server, UUID id) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null ? "Storyteller" : player.getName().getString();
        }
    }
}
