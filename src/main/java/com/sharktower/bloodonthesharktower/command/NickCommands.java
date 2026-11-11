package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.profile.Nicknames;
import com.sharktower.bloodonthesharktower.profile.PlayerProfiles;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /nick} lets a player choose the name everyone sees in chat, on their
 * name tag, in the tab list and in Sharktower screens. Cosmetic only: the real
 * username is kept for permissions and moderation, shown in the tab list next
 * to the nickname, and every change is announced to everyone.
 *
 * <pre>
 * /nick                 show your current nickname
 * /nick &lt;name&gt;          set a nickname
 * /nick clear           go back to your username
 * /nick reset &lt;player&gt;  operators: remove someone's nickname
 * </pre>
 */
public final class NickCommands {
    /** Minimum time between two changes by the same player, so names cannot be flicked mid-discussion. */
    private static final long COOLDOWN_MS = 60_000L;
    private static final Map<UUID, Long> LAST_CHANGE = new HashMap<>();

    private NickCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("nick")
                        .executes(NickCommands::executeShow)
                        .then(Commands.literal("clear").executes(NickCommands::executeClear))
                        .then(Commands.literal("reset")
                                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getServer().getPlayerNames(), builder))
                                        .executes(NickCommands::executeReset)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(NickCommands::executeSet))));
    }

    private static int executeShow(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            send(context, "Players use /nick <name> to choose a nickname and /nick clear to remove it.");
            return 0;
        }
        String nick = PlayerProfiles.nickname(player.getUUID());
        send(context, nick.isEmpty()
                ? "You have no nickname. Use /nick <name> to choose the name everyone sees."
                : "Your nickname is \"" + nick + "\". Use /nick clear to go back to " + player.getName().getString() + ".");
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can set a nickname."));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        String raw = StringArgumentType.getString(context, "name");
        String value = Nicknames.normalise(raw);
        if (Nicknames.key(value).equals(Nicknames.key(player.getName().getString()))) {
            return executeClear(context);
        }
        String refusal = Nicknames.refusal(value, PlayerProfiles.namesTakenByOthers(server, player.getUUID()));
        if (refusal != null) {
            context.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        String cooldown = cooldownRefusal(player.getUUID());
        if (cooldown != null) {
            context.getSource().sendFailure(Component.literal(cooldown));
            return 0;
        }
        String before = PlayerProfiles.displayName(player);
        if (!PlayerProfiles.setNickname(player.getUUID(), value)) {
            send(context, "Your nickname is already \"" + value + "\".");
            return 1;
        }
        LAST_CHANGE.put(player.getUUID(), System.currentTimeMillis());
        refresh(server, player);
        announce(server, before + " is now known as " + value + " (" + player.getName().getString() + ").");
        return 1;
    }

    private static int executeClear(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can clear a nickname."));
            return 0;
        }
        String before = PlayerProfiles.nickname(player.getUUID());
        if (!PlayerProfiles.clearNickname(player.getUUID())) {
            send(context, "You have no nickname; everyone already sees " + player.getName().getString() + ".");
            return 1;
        }
        MinecraftServer server = context.getSource().getServer();
        refresh(server, player);
        announce(server, before + " is now known by their username, " + player.getName().getString() + ".");
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer target = server.getPlayerList().getPlayerByName(name);
        if (target == null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (Nicknames.key(PlayerProfiles.nickname(online.getUUID())).equals(Nicknames.key(name))) target = online;
            }
        }
        if (target == null) {
            context.getSource().sendFailure(Component.literal("No online player is called " + name + "."));
            return 0;
        }
        String before = PlayerProfiles.nickname(target.getUUID());
        if (!PlayerProfiles.clearNickname(target.getUUID())) {
            send(context, target.getName().getString() + " has no nickname.");
            return 1;
        }
        refresh(server, target);
        announce(server, before + " is now known by their username, " + target.getName().getString() + ".");
        send(context, "Removed " + target.getName().getString() + "'s nickname.");
        return 1;
    }

    private static String cooldownRefusal(UUID player) {
        Long last = LAST_CHANGE.get(player);
        if (last == null) return null;
        long wait = COOLDOWN_MS - (System.currentTimeMillis() - last);
        if (wait <= 0) return null;
        return "You can change your nickname again in " + ((wait + 999) / 1000) + " seconds.";
    }

    /** Push the new name to chat, tab list, name tags and every Sharktower screen. */
    private static void refresh(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
        StateBroadcaster.broadcastPlayerProfiles(server);
        StateBroadcaster.broadcastPlayerDirectory(server);
        StateBroadcaster.broadcastGrimoire(server);
    }

    private static void announce(MinecraftServer server, String text) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), false);
    }

    private static void send(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
