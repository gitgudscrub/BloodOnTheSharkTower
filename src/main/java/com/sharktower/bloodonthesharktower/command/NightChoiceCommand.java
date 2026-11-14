package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.automation.NightChoices;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /protect <seat>} (Monk) and {@code /attack <seat>} (Imp): tonight's
 * choice, usually clicked from the private seat list. The Storyteller
 * confirms it ({@link NightChoices}).
 */
public final class NightChoiceCommand {
    private NightChoiceCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("protect")
                    .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                            .executes(context -> execute(context, NightChoices.Kind.MONK))));
            dispatcher.register(Commands.literal("attack")
                    .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                            .executes(context -> execute(context, NightChoices.Kind.IMP))));
        });
    }

    private static int execute(CommandContext<CommandSourceStack> context, NightChoices.Kind kind) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only a player can choose."));
            return 0;
        }
        var target = SetupOperations.playerBySeat(IntegerArgumentType.getInteger(context, "seat"));
        String refusal = NightChoices.choose(context.getSource().getServer(), kind, player.getUUID(), target, false);
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
}
