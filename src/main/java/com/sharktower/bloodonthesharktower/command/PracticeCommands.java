package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.practice.PracticePlan;
import com.sharktower.bloodonthesharktower.practice.PracticePlayers;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /bots practice}: the Storyteller adds stand-in players to rehearse a
 * game alone ({@link PracticePlayers}).
 */
public final class PracticeCommands {
    private PracticeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal(BotsCommands.ROOT)
                        .then(Commands.literal("practice")
                                .executes(PracticeCommands::status)
                                .then(Commands.literal("add")
                                        .executes(c -> add(c, 7))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, PracticePlan.MAX))
                                                .executes(c -> add(c, IntegerArgumentType.getInteger(c, "count")))))
                                .then(Commands.literal("clear").executes(PracticeCommands::clear))
                                .then(Commands.literal("votes")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    b.suggest("random");
                                                    b.suggest("yes");
                                                    b.suggest("no");
                                                    return b.buildFuture();
                                                })
                                                .executes(PracticeCommands::votes))))));
    }

    private static boolean storyteller(CommandContext<CommandSourceStack> c) {
        ServerPlayer player = c.getSource().getPlayer();
        if (player == null || StorytellerState.isStoryteller(player.getUUID())) return true;
        say(c, "Only the Storyteller can manage practice players. Claim it with /bots storyteller claim.", ChatFormatting.RED);
        return false;
    }

    private static void say(CommandContext<CommandSourceStack> c, String text, ChatFormatting colour) {
        c.getSource().sendSystemMessage(Component.literal(text).withStyle(colour));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        List<String> names = PracticePlayers.names(c.getSource().getServer());
        say(c, names.isEmpty() ? "No practice players on the server."
                : "Practice players (" + names.size() + "): " + String.join(", ", names), ChatFormatting.GOLD);
        say(c, "Votes: " + PracticePlayers.votes().name().toLowerCase(java.util.Locale.ROOT)
                + ". /bots practice add <1-" + PracticePlan.MAX + ">, /bots practice clear, /bots practice votes <random|yes|no>.",
                ChatFormatting.GRAY);
        return Command.SINGLE_SUCCESS;
    }

    private static int add(CommandContext<CommandSourceStack> c, int count) {
        if (!storyteller(c)) return 0;
        ServerPlayer near = c.getSource().getPlayer();
        if (near == null) {
            say(c, "Run this in game: practice players appear around you.", ChatFormatting.RED);
            return 0;
        }
        List<String> added = PracticePlayers.add(c.getSource().getServer(), near, count);
        if (added.isEmpty()) {
            say(c, "No room for more practice players (" + PracticePlan.MAX + " at most).", ChatFormatting.RED);
            return 0;
        }
        say(c, "Added " + added.size() + " practice player" + (added.size() == 1 ? "" : "s") + ": "
                + String.join(", ", added) + ".", ChatFormatting.GOLD);
        say(c, "Seat and deal as usual (Seat All seats them too). They walk home at night, come to the square by day,"
                + " vote, and make night picks after your visit. Practice games are not sent to the site."
                + " /bots practice clear when you're done.", ChatFormatting.GRAY);
        return added.size();
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        if (!storyteller(c)) return 0;
        int removed = PracticePlayers.removeAll(c.getSource().getServer());
        say(c, removed == 0 ? "No practice players to remove."
                : "Removed " + removed + " practice player" + (removed == 1 ? "" : "s")
                + ". Their seats stay until you reset the game.", ChatFormatting.GOLD);
        return Command.SINGLE_SUCCESS;
    }

    private static int votes(CommandContext<CommandSourceStack> c) {
        if (!storyteller(c)) return 0;
        PracticePlan.Votes mode = PracticePlan.Votes.parse(StringArgumentType.getString(c, "mode"));
        if (mode == null) {
            say(c, "Use random, yes or no.", ChatFormatting.RED);
            return 0;
        }
        PracticePlayers.setVotes(mode);
        say(c, switch (mode) {
            case RANDOM -> "Practice players vote by their own lights (evil ones protect their team).";
            case YES -> "Practice players raise their hand in every vote.";
            case NO -> "Practice players keep their hands down.";
        }, ChatFormatting.GOLD);
        return Command.SINGLE_SUCCESS;
    }
}
