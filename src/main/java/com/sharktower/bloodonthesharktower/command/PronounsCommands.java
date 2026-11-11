package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.profile.PlayerProfiles;
import com.sharktower.bloodonthesharktower.profile.Pronouns;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /pronouns} lets any player choose the pronouns shown under their name
 * tag, in the player sidebar and in Grimoire hover text. Cosmetic only.
 *
 * <pre>
 * /pronouns                 show your current pronouns
 * /pronouns they/them       set pronouns (presets are suggested, custom text allowed)
 * /pronouns clear           remove them
 * </pre>
 */
public final class PronounsCommands {
    private PronounsCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("pronouns")
                        .executes(PronounsCommands::executeShow)
                        .then(Commands.literal("clear").executes(PronounsCommands::executeClear))
                        .then(Commands.argument("pronouns", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(Pronouns.PRESETS, builder))
                                .executes(PronounsCommands::executeSet))));
    }

    private static int executeShow(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            send(context, "Only players have pronouns. Players use /pronouns <pronouns> or /pronouns clear.");
            return 0;
        }
        String current = PlayerProfiles.pronouns(player.getUUID());
        if (current.isEmpty()) {
            send(context, "You have not set pronouns. Use /pronouns they/them (or any text) to show them under your name.");
        } else {
            send(context, "Your pronouns are shown as \"" + current + "\". Use /pronouns clear to remove them.");
        }
        return 1;
    }

    private static int executeSet(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            send(context, "Only players can set pronouns.");
            return 0;
        }
        String raw = StringArgumentType.getString(context, "pronouns");
        String refusal = Pronouns.refusal(raw);
        if (refusal != null) {
            context.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        String value = Pronouns.normalise(raw);
        if (PlayerProfiles.setPronouns(player.getUUID(), value)) {
            StateBroadcaster.broadcastPlayerProfiles(player.level().getServer());
        }
        send(context, "Your pronouns are now shown as \"" + value + "\".");
        return 1;
    }

    private static int executeClear(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            send(context, "Only players can clear pronouns.");
            return 0;
        }
        if (PlayerProfiles.clearPronouns(player.getUUID())) {
            StateBroadcaster.broadcastPlayerProfiles(player.level().getServer());
            send(context, "Your pronouns have been removed.");
        } else {
            send(context, "You had no pronouns set.");
        }
        return 1;
    }

    private static void send(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
