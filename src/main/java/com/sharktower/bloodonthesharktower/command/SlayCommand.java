package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.automation.RoleAutomation;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /slay <player|seat>}: the public Slayer shot.
 *
 * Any living seated player may use it during the day, because claiming to be
 * the Slayer is a bluff everyone is allowed to make. The server works out
 * whether anything happens ({@link RoleAutomation#slayerShot}).
 */
public final class SlayCommand {
    private SlayCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("slay")
                        .executes(SlayCommand::executeHelp)
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(seatedNames(context.getSource().getServer()), builder))
                                .executes(SlayCommand::executeShot))));
    }

    private static int executeHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "Usage: /slay <player or seat number>. Publicly claim the Slayer ability and shoot someone."), false);
        return 1;
    }

    private static int executeShot(CommandContext<CommandSourceStack> context) {
        ServerPlayer shooter = context.getSource().getPlayer();
        if (shooter == null) {
            context.getSource().sendFailure(Component.literal("Only a seated player can use /slay."));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        UUID target = resolve(server, StringArgumentType.getString(context, "target"));
        if (target == null) {
            shooter.sendSystemMessage(Component.literal("No seated player matches that name or seat number.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String refusal = RoleAutomation.slayerShot(server, shooter.getUUID(), target);
        if (refusal != null) {
            shooter.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static List<String> seatedNames(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) names.add(com.sharktower.bloodonthesharktower.profile.PlayerProfiles.displayName(online));
        }
        return names;
    }

    /** A player name (case-insensitive) or a seat number. */
    private static UUID resolve(MinecraftServer server, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return null;
        try {
            int seat = Integer.parseInt(text);
            for (var entry : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
                if (entry.getValue() == seat) return entry.getKey();
            }
            return null;
        } catch (NumberFormatException ignored) {
            // fall through to a name lookup
        }
        String wanted = text.toLowerCase(Locale.ROOT);
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online == null) continue;
            if (online.getName().getString().toLowerCase(Locale.ROOT).equals(wanted)) return id;
            if (com.sharktower.bloodonthesharktower.profile.PlayerProfiles.nickname(id).toLowerCase(Locale.ROOT).equals(wanted)) return id;
        }
        return null;
    }
}
