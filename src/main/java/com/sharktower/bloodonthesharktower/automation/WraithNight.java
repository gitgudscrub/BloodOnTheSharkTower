package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Wraith: "You may choose to visit other players at night. You visit
 * other evil players when they wake."
 *
 * At dusk a living, sober Wraith is released into Spectator mode and may fly
 * anywhere. Inside a house they hear that house's voice (relayed to them
 * alone, so they never appear in the house's voice group and nobody hears
 * them). When the Storyteller's visit wakes an evil player ({@link HouseVisits}),
 * the Wraith is brought to that house. At dawn, on death, reset or game end
 * they return home in their usual game mode.
 *
 * Spirit Lantern (house rule): when the script has the Wraith, every seated
 * player gets a lantern they may light once per game, at night. If the
 * Wraith enters a house whose lantern is lit, the owner learns who it is.
 *
 * The Storyteller decides throughout: Recall now / Release anyway / Bring to
 * a seat on the Resolve card and {@code /bots automation wraith ...}, and can
 * give a spent lantern back.
 */
public final class WraithNight {
    static final String PROMPT = "wraith";
    private static final String LANTERN_KEY = "sharktower_spirit_lantern";
    private static final String LIT_KEY = "lit";
    private static final int CHECK_TICKS = 10;

    private static volatile UUID roaming;
    private static volatile int insideSeat;
    private static final Map<UUID, GameType> PREVIOUS_MODE = new HashMap<>();
    /** Wraiths recalled while offline: restored when they next join. */
    private static final Map<UUID, GameType> RECALL_ON_JOIN = new HashMap<>();
    private static final Set<UUID> LANTERN_SPENT = new HashSet<>();
    private static final Set<UUID> LANTERN_LIT = new HashSet<>();
    private static final Set<Integer> REVEALED = new HashSet<>();
    private static MinecraftServer server;
    private static int ticks;

    private WraithNight() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(WraithNight::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            GameType mode = RECALL_ON_JOIN.remove(handler.player.getUUID());
            if (mode != null) restore(handler.player, mode);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> useLantern(player, player.getItemInHand(hand)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> useLantern(player, player.getItemInHand(hand)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("lantern").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    String refusal = light(player);
                    if (refusal != null) context.getSource().sendFailure(Component.literal(refusal));
                    return refusal == null ? 1 : 0;
                })));
    }

    // ------------------------------------------------------------- Phases

    /** Dusk: hand out lanterns, then release the Wraith (or ask the Storyteller). */
    static void onDusk(MinecraftServer srv) {
        if (srv == null) return;
        server = srv;
        recall("A new night");
        LANTERN_LIT.clear();
        REVEALED.clear();
        if (ServerState.gameEnded) return;
        giveLanterns(srv);
        UUID wraith = RoleAutomation.findAlive(Role.WRAITH);
        if (wraith == null) return;
        String who = RoleAutomation.label(srv, wraith);
        if (RoleAutomation.droisoned(wraith)) {
            ServerPlayer player = srv.getPlayerList().getPlayer(wraith);
            if (player != null) {
                player.sendSystemMessage(Component.literal("Wraith: you may not visit other players tonight.")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            RoleAutomation.offer(srv, new StorytellerPrompt(PROMPT, "Wraith",
                    who + " is drunk or poisoned, so they stay home tonight and have been told.",
                    List.of(StorytellerPrompt.Action.neutral("Release anyway", "/bots automation wraith release"),
                            StorytellerPrompt.Action.ok("OK", "/bots automation wraith dismiss"))));
            return;
        }
        release(srv);
    }

    /** Dawn: everyone's lantern goes out and the Wraith comes home. */
    static void onDawn(MinecraftServer srv) {
        if (srv != null) server = srv;
        recall("Dawn");
        LANTERN_LIT.clear();
        REVEALED.clear();
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) removeLanterns(player, true);
    }

    /** A dead Wraith may not visit. */
    static void onDeath(UUID dead) {
        if (dead != null && dead.equals(roaming)) recall("You died");
    }

    /** New game: lanterns are taken back and recharged; the Wraith comes home. */
    public static void reset() {
        recall("The game was reset");
        LANTERN_SPENT.clear();
        LANTERN_LIT.clear();
        REVEALED.clear();
        if (server != null) for (ServerPlayer player : server.getPlayerList().getPlayers()) removeLanterns(player, false);
    }

    // ------------------------------------------------------------- Wraith

    /** Releases the living Wraith into Spectator mode. Returns why not, or null. */
    public static String release(MinecraftServer srv) {
        if (srv != null) server = srv;
        if (server == null) return "No server.";
        UUID wraith = RoleAutomation.findAlive(Role.WRAITH);
        if (wraith == null) return "There is no living Wraith.";
        if (wraith.equals(roaming)) return "The Wraith is already roaming.";
        String who = RoleAutomation.label(server, wraith);
        ServerPlayer player = server.getPlayerList().getPlayer(wraith);
        if (player == null) {
            RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Wraith",
                    who + " is offline, so they cannot visit tonight.",
                    List.of(StorytellerPrompt.Action.ok("OK", "/bots automation wraith dismiss"))));
            return null;
        }
        PREVIOUS_MODE.put(wraith, player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                ? GameType.SURVIVAL : player.gameMode.getGameModeForPlayer());
        player.setGameMode(GameType.SPECTATOR);
        roaming = wraith;
        insideSeat = 0;
        player.sendSystemMessage(Component.literal("Wraith: you may visit other players tonight. Fly anywhere (in Spectator, "
                + "press 1 or middle-click to jump to a player). Inside a house you hear its voices; nobody hears you. "
                + "You are brought to evil players when they wake. At dawn you return home.")
                .withStyle(ChatFormatting.DARK_PURPLE));
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Wraith",
                who + " is roaming in Spectator. They hear any house they enter and are brought to evil players "
                        + "you wake. They return home at dawn.",
                List.of(StorytellerPrompt.Action.neutral("Recall now", "/bots automation wraith recall"),
                        StorytellerPrompt.Action.neutral("Bring to seat",
                                StorytellerPrompt.PICK + "/bots automation wraith visit "),
                        StorytellerPrompt.Action.ok("OK", "/bots automation wraith dismiss"))));
        return null;
    }

    /** The Storyteller's Recall now. Returns why not, or null. */
    public static String recallNow() {
        if (roaming == null) return "The Wraith is not roaming.";
        recall("The Storyteller called you home");
        return null;
    }

    /** Closes the Wraith's Resolve card without changing anything. */
    public static String dismiss() {
        RoleAutomation.resolve(PROMPT);
        return null;
    }

    /** The Storyteller brings the roaming Wraith to a seat's house. Returns why not, or null. */
    public static String visit(int seat) {
        if (roaming == null || server == null) return "The Wraith is not roaming.";
        if (SeatPositionManager.seatHome(seat) == null) return "Seat " + seat + " has no house set.";
        ServerPlayer player = server.getPlayerList().getPlayer(roaming);
        if (player == null) return "The Wraith is offline.";
        SeatPositionManager.teleportPlayerToHome(player, seat);
        return null;
    }

    /** Called when the Storyteller's visit wakes {@code woken}: an evil player brings the Wraith. */
    static void onWake(MinecraftServer srv, UUID woken) {
        UUID wraith = roaming;
        if (srv == null || wraith == null || woken == null || woken.equals(wraith)) return;
        PendingRoleAssignment role = RoleAutomation.actualRole(woken);
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(woken);
        if (role == null || role.isFinalGood() || seat == null || SeatPositionManager.seatHome(seat) == null) return;
        ServerPlayer player = srv.getPlayerList().getPlayer(wraith);
        if (player == null) return;
        SeatPositionManager.teleportPlayerToHome(player, seat);
        player.sendSystemMessage(Component.literal("Wraith: an evil player wakes, " + RoleAutomation.label(srv, woken)
                + " (seat " + seat + "). You are with them.").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void recall(String why) {
        UUID wraith = roaming;
        roaming = null;
        insideSeat = 0;
        if (wraith == null) return;
        RoleAutomation.resolve(PROMPT);
        GameType mode = PREVIOUS_MODE.getOrDefault(wraith, GameType.SURVIVAL);
        PREVIOUS_MODE.remove(wraith);
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(wraith);
        if (player == null) {
            RECALL_ON_JOIN.put(wraith, mode);
            return;
        }
        restore(player, mode);
        player.sendSystemMessage(Component.literal("Wraith: " + why + ". You are back home.").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void restore(ServerPlayer player, GameType mode) {
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(player.getUUID());
        if (seat != null) SeatPositionManager.teleportPlayerToHome(player, seat);
        player.setGameMode(mode);
    }

    // ------------------------------------------------------------- Voice

    /** The roaming Wraith, if they are inside {@code room} ("house:N"); read on the voice thread. */
    public static UUID listenerIn(String room) {
        UUID wraith = roaming;
        int seat = insideSeat;
        if (wraith == null || seat <= 0 || room == null) return null;
        return room.equals("house:" + seat) ? wraith : null;
    }

    /** True while a Wraith is inside some house, so the voice thread only looks up rooms then. */
    public static boolean anyListener() {
        return roaming != null && insideSeat > 0;
    }

    // ------------------------------------------------------------- Tick

    private static void tick(MinecraftServer srv) {
        UUID wraith = roaming;
        if (wraith == null || ++ticks < CHECK_TICKS) return;
        ticks = 0;
        server = srv;
        if (ServerState.gameEnded || !PhaseOperations.isNight()) {
            recall(ServerState.gameEnded ? "The game is over" : "Dawn");
            return;
        }
        ServerPlayer player = srv.getPlayerList().getPlayer(wraith);
        if (player == null) return;
        int nearest = 0;
        double best = Double.MAX_VALUE;
        double radius = HouseVisits.ARRIVE_RADIUS;
        for (int seat : new HashSet<>(ServerState.PLAYER_SEAT_NUMBERS.values())) {
            var home = SeatPositionManager.seatHome(seat);
            if (home == null) continue;
            double d = player.distanceToSqr(home.x(), home.y(), home.z());
            if (d <= radius * radius && d < best) {
                best = d;
                nearest = seat;
            }
        }
        if (nearest == insideSeat) return;
        insideSeat = nearest;
        if (nearest <= 0) return;
        Integer own = ServerState.PLAYER_SEAT_NUMBERS.get(wraith);
        if (own != null && own == nearest) return;
        UUID owner = SetupOwner.bySeat(nearest);
        String house = "house " + nearest + (owner == null ? "" : " (" + RoleAutomation.label(srv, owner) + ")");
        boolean lit = owner != null && LANTERN_LIT.contains(owner) && REVEALED.add(nearest);
        if (lit) {
            ServerPlayer ownerPlayer = srv.getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                ownerPlayer.sendSystemMessage(Component.literal("Your Spirit Lantern flares: "
                        + RoleAutomation.label(srv, wraith) + ", the Wraith, is in your house.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        RoleAutomation.tellStorytellers(srv, Component.literal("Wraith entered " + house
                + (lit ? ". Their lit Spirit Lantern revealed the Wraith." : ".")).withStyle(ChatFormatting.DARK_PURPLE));
    }

    // ------------------------------------------------------------- Lantern

    /** Lanterns are handed out when the script has the Wraith, or one was dealt anyway. */
    private static boolean scriptHasWraith() {
        if (ServerState.PLAYER_ROLES.values().stream().anyMatch(r -> r.role() == Role.WRAITH)) return true;
        if (ServerState.currentScript == null) return false;
        for (ScriptRole role : ServerState.currentScript.allRoles()) {
            if (!role.isCustom() && role.asRole() == Role.WRAITH) return true;
        }
        return false;
    }

    private static void giveLanterns(MinecraftServer srv) {
        if (!scriptHasWraith()) return;
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) {
            if (LANTERN_SPENT.contains(id)) continue;
            ServerPlayer player = srv.getPlayerList().getPlayer(id);
            if (player == null || hasLantern(player)) continue;
            if (player.getInventory().add(lantern(false))) {
                player.sendSystemMessage(Component.literal("You have a Spirit Lantern. Light it once per game, at night "
                        + "(right-click it, or type /lantern): if the Wraith enters your house that night, you learn who it is.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    /** The Storyteller gives a seat's lantern back. Returns why not, or null. */
    public static String giveBack(int seat) {
        UUID id = SetupOwner.bySeat(seat);
        if (id == null) return "Nobody is in seat " + seat + ".";
        LANTERN_SPENT.remove(id);
        LANTERN_LIT.remove(id);
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
        if (player != null) {
            removeLanterns(player, false);
            player.getInventory().add(lantern(false));
            player.sendSystemMessage(Component.literal("The Storyteller gave you a fresh Spirit Lantern.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return null;
    }

    private static InteractionResult useLantern(Player player, ItemStack stack) {
        if (!isLantern(stack)) return InteractionResult.PASS;
        // Never place the lantern as a block.
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            String refusal = light(serverPlayer);
            if (refusal != null) serverPlayer.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    /** Lights {@code player}'s lantern for the rest of the night. Returns why not, or null. */
    public static String light(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(id)) return "Only seated players have a Spirit Lantern.";
        if (LANTERN_LIT.contains(id)) return "Your Spirit Lantern is already lit tonight.";
        if (LANTERN_SPENT.contains(id)) return "Your Spirit Lantern is spent for this game.";
        if (!PhaseOperations.isNight()) return "Light your Spirit Lantern at night.";
        if (!scriptHasWraith()) return "There is no Wraith on this script.";
        LANTERN_SPENT.add(id);
        LANTERN_LIT.add(id);
        removeLanterns(player, false);
        player.getInventory().add(lantern(true));
        player.sendSystemMessage(Component.literal("Your Spirit Lantern is lit until dawn. If the Wraith enters your house "
                + "tonight, you will know who it is.").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (server != null) {
            RoleAutomation.tellStorytellers(server, Component.literal(RoleAutomation.label(server, id)
                    + " lit their Spirit Lantern.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return null;
    }

    private static ItemStack lantern(boolean lit) {
        ItemStack stack = new ItemStack(lit ? Items.SOUL_LANTERN : Items.LANTERN);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(lit ? "Spirit Lantern (lit tonight)" : "Spirit Lantern")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(LANTERN_KEY, true);
        marker.putBoolean(LIT_KEY, lit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return stack;
    }

    static boolean isLantern(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.is(Items.LANTERN) || stack.is(Items.SOUL_LANTERN))) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(LANTERN_KEY, false);
    }

    private static boolean isLit(ItemStack stack) {
        return isLantern(stack) && stack.get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr(LIT_KEY, false);
    }

    public static boolean hasLantern(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isLantern(inventory.getItem(slot))) return true;
        }
        return isLantern(player.containerMenu.getCarried());
    }

    /** Removes Spirit Lanterns ({@code litOnly}: just the lit ones, at dawn). */
    private static void removeLanterns(ServerPlayer player, boolean litOnly) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (litOnly ? isLit(stack) : isLantern(stack)) inventory.setItem(slot, ItemStack.EMPTY);
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (litOnly ? isLit(carried) : isLantern(carried)) player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    /** Seat lookup without a dependency on setup code. */
    private static final class SetupOwner {
        static UUID bySeat(int seat) {
            for (Map.Entry<UUID, Integer> entry : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
                if (entry.getValue() == seat) return entry.getKey();
            }
            return null;
        }
    }

    /** True while {@code player}'s lantern is lit tonight (tests). */
    public static boolean lanternLit(UUID player) {
        return LANTERN_LIT.contains(player);
    }

    /** True once a lit lantern revealed the Wraith in {@code seat}'s house tonight (tests). */
    public static boolean revealed(int seat) {
        return REVEALED.contains(seat);
    }

    /** For the Storyteller's status line. */
    public static UUID roaming() {
        return roaming;
    }
}
