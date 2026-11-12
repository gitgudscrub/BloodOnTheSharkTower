package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.automation.RavenkeeperAutomation;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /raven <seat>}: the Ravenkeeper who died tonight chooses a player.
 * Usually clicked from the private seat list rather than typed. The
 * Storyteller decides what the Ravenkeeper is shown
 * ({@link RavenkeeperAutomation}).
 */
public final class RavenCommand {
    private RavenCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("raven")
                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                .executes(RavenCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only the Ravenkeeper can use /raven."));
            return 0;
        }
        var target = SetupOperations.playerBySeat(IntegerArgumentType.getInteger(context, "seat"));
        String refusal = RavenkeeperAutomation.choose(context.getSource().getServer(), player.getUUID(), target, false);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
}
