package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.automation.PoisonerAutomation;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /poison <seat>}: the Poisoner chooses tonight's target. Usually
 * clicked from the private seat list. The Storyteller places the reminder
 * ({@link PoisonerAutomation}).
 */
public final class PoisonCommand {
    private PoisonCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("poison")
                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                .executes(PoisonCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only the Poisoner can use /poison."));
            return 0;
        }
        var target = SetupOperations.playerBySeat(IntegerArgumentType.getInteger(context, "seat"));
        String refusal = PoisonerAutomation.choose(context.getSource().getServer(), player.getUUID(), target, false);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
}
