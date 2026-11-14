package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.automation.PsychopathRoshambo;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /roshambo <rock|paper|scissors>}: a pick in the executed
 * Psychopath's rock-paper-scissors. Usually clicked from the chat buttons
 * ({@link PsychopathRoshambo}).
 */
public final class RoshamboCommand {
    private RoshamboCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("roshambo")
                        .then(Commands.argument("pick", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (String pick : new String[]{"rock", "paper", "scissors"}) builder.suggest(pick);
                                    return builder.buildFuture();
                                })
                                .executes(RoshamboCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only a player can play rock-paper-scissors."));
            return 0;
        }
        String refusal = PsychopathRoshambo.pick(context.getSource().getServer(), player.getUUID(),
                PsychopathRoshambo.Pick.parse(StringArgumentType.getString(context, "pick")));
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
}
