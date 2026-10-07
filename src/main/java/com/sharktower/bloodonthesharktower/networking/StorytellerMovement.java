package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.mixin.CommandNodeAccess;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Scoped Storyteller privileges; ordinary vanilla teleport remains permission gated. */
public final class StorytellerMovement {
    private record Previous(GameType mode, boolean mayfly, boolean flying) {}
    private static final Map<UUID, Previous> PREVIOUS = new HashMap<>();
    private StorytellerMovement() {}

    private static boolean allowed(CommandSourceStack source) {
        return source.getPlayer() != null && StorytellerState.isStoryteller(source.getPlayer().getUUID());
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StorytellerMovement::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) restore(player);
            PREVIOUS.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            // Brigadier merges nodes of the same name without replacing their
            // original requirement/redirect. Replace the alias explicitly while
            // preserving vanilla operator-only coordinate and targets branches.
            CommandNode<CommandSourceStack> old = dispatcher.getRoot().getChild("tp");
            CommandNode<CommandSourceStack> vanilla = old != null && old.getRedirect() != null ? old.getRedirect() : old;
            CommandNodeAccess<?> root = (CommandNodeAccess<?>) dispatcher.getRoot();
            root.sharktowerChildren().remove("tp");
            root.sharktowerLiterals().remove("tp");
            LiteralCommandNode<CommandSourceStack> tp = Commands.literal("tp")
                    .requires(source -> allowed(source) || (vanilla != null && vanilla.canUse(source)))
                    .then(Commands.argument("destination", EntityArgument.entity()).executes(context -> {
                        ServerPlayer actor = context.getSource().getPlayer();
                        if (!allowed(context.getSource())) {
                            if (vanilla == null || !vanilla.canUse(context.getSource())) return 0;
                            CommandNode<CommandSourceStack> destination = vanilla.getChild("destination");
                            return destination == null || destination.getCommand() == null ? 0 : destination.getCommand().run(context);
                        }
                        if (actor == null) return 0;
                        ServerPlayer target = EntityArgument.getPlayer(context, "destination");
                        actor.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(),
                                Set.of(), target.getYRot(), target.getXRot(), true);
                        actor.sendSystemMessage(Component.literal("Teleported to " + target.getName().getString() + "."));
                        return 1;
                    })).build();
            if (vanilla != null) for (CommandNode<CommandSourceStack> child : vanilla.getChildren()) {
                if (child.getName().equals("destination")) continue;
                CommandNode<CommandSourceStack> copy = child.createBuilder()
                        .requires(source -> vanilla.canUse(source) && child.canUse(source)).build();
                for (CommandNode<CommandSourceStack> descendant : child.getChildren()) copy.addChild(descendant);
                tp.addChild(copy);
            }
            dispatcher.getRoot().addChild(tp);
            dispatcher.register(Commands.literal("bots").then(Commands.literal("spectator")
                    .requires(StorytellerMovement::allowed).executes(context -> {
                        SetupOperations.Result result = toggleSpectator(context.getSource().getPlayer());
                        context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
                        return result.ok() ? 1 : 0;
                    })));
        });
    }

    private static void grant(ServerPlayer player) {
        PREVIOUS.computeIfAbsent(player.getUUID(), id -> new Previous(
                player.gameMode.getGameModeForPlayer(), player.getAbilities().mayfly, player.getAbilities().flying));
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    public static SetupOperations.Result toggleSpectator(ServerPlayer player) {
        if (player == null || !StorytellerState.isStoryteller(player.getUUID())) return SetupOperations.Result.fail("Storyteller control is required.");
        grant(player);
        GameType mode = player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                ? PREVIOUS.get(player.getUUID()).mode() : GameType.SPECTATOR;
        // A Storyteller who started in Spectator can still leave it explicitly.
        if (mode == GameType.SPECTATOR && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) mode = GameType.SURVIVAL;
        player.setGameMode(mode);
        grant(player);
        return SetupOperations.Result.ok("Storyteller mode: " + mode.getName() + ".");
    }

    public static void makeVisibleAtNight(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!StorytellerState.isStoryteller(player.getUUID())) continue;
            grant(player);
            if (player.isSpectator()) player.setGameMode(GameType.SURVIVAL);
            player.setInvisible(false);
            grant(player);
        }
    }

    public static void restore(ServerPlayer player) {
        Previous previous = PREVIOUS.remove(player.getUUID());
        if (previous == null) return;
        player.setGameMode(previous.mode());
        player.getAbilities().mayfly = previous.mayfly();
        player.getAbilities().flying = previous.flying() && previous.mayfly();
        player.onUpdateAbilities();
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean assigned = StorytellerState.isStoryteller(player.getUUID());
            boolean had = PREVIOUS.containsKey(player.getUUID());
            if (assigned) grant(player);
            else restore(player);
            if (assigned != had) server.getCommands().sendCommands(player);
        }
    }
}
