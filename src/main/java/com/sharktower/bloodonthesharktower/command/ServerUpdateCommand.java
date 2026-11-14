package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.serverupdate.ServerUpdater;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * {@code /serverupdate now|aftergame|cancel|status}: console only (AMP's
 * console, which the Sharktower website drives). Players, even operators,
 * cannot run it, so updates are only ever pushed by the server owner.
 */
public final class ServerUpdateCommand {
    private ServerUpdateCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("serverupdate")
                        .requires(source -> source.getEntity() == null && Commands.LEVEL_OWNERS.check(source.permissions()))
                        .then(Commands.literal("now").executes(c -> reply(c, () -> ServerUpdater.start(false))))
                        .then(Commands.literal("aftergame").executes(c -> reply(c, () -> ServerUpdater.start(true))))
                        .then(Commands.literal("cancel").executes(c -> reply(c, ServerUpdater::cancel)))
                        .then(Commands.literal("status").executes(c -> reply(c, ServerUpdater::describe)))));
    }

    private static int reply(CommandContext<CommandSourceStack> context, Supplier<String> action) {
        String text = action.get();
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
