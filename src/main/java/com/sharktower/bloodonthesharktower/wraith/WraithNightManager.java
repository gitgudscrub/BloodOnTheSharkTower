package com.sharktower.bloodonthesharktower.wraith;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.networking.WraithTrackingS2CPayload;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative Wraith night journey and once-per-game Spirit Lantern.
 * All identities, voice memberships, charges and game-mode changes remain server-owned.
 */
public final class WraithNightManager {
    private static final double HOUSE_RADIUS = 8.0D;
    private static final Map<UUID, GameType> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> VOICE_SEATS = new HashMap<>();
    private static final Map<UUID, Integer> SELECTED_ST_SEATS = new HashMap<>();
    private static final Map<UUID, Integer> ARRIVED_ST_SEATS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_WRAITH_HOUSE = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_NEAR_DOOR = new HashMap<>();
    private static final Set<UUID> LANTERN_SPENT = new HashSet<>();
    /** Recall requests that could not finish while a Wraith was disconnected. */
    private static final Set<UUID> PENDING_RESTORE = new HashSet<>();
    private static final Set<UUID> LANTERN_LIT = new HashSet<>();
    private static final Set<UUID> IDENTITY_REVEALED = new HashSet<>();
    private static boolean presenceSent;
    private static boolean lanternWindowOpen;
    private static int lastNight = -1;
    private static int ticks;

    private WraithNightManager() {}

    public static boolean onScript() {
        return ServerState.currentScript != null && ServerState.currentScript.hasRole("wraith");
    }

    public static synchronized void newGame(MinecraftServer server) {
        recallAll(server);
        LANTERN_SPENT.clear();
        LANTERN_LIT.clear();
        lanternWindowOpen = false;
        lastNight = -1;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!onScript() || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(player.getUUID())) continue;
            if (StorytellerState.isStoryteller(player.getUUID())) continue;
            // The soul lantern is a visible prop. The server-side spent flag, not
            // the physical stack, is the authoritative once-per-game charge.
            if (!containsLantern(player)) player.getInventory().add(new ItemStack(Items.SOUL_LANTERN));
            player.sendSystemMessage(Component.literal(
                    "Spirit Lantern: hold the Soul Lantern and press L at the start of ONE night to arm it until Dawn.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static boolean containsLantern(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.SOUL_LANTERN)) return true;
        }
        return false;
    }

    public static synchronized void beginNight(MinecraftServer server) {
        if (!onScript()) return;
        if (ServerState.currentNight == lastNight && lanternWindowOpen) return;
        lastNight = ServerState.currentNight;
        lanternWindowOpen = true;
        LANTERN_LIT.clear();
        IDENTITY_REVEALED.clear();
        presenceSent = false;
        SELECTED_ST_SEATS.clear();
        ARRIVED_ST_SEATS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerState.PLAYER_SEAT_NUMBERS.containsKey(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "Spirit Lantern choice: hold your lantern and press L NOW to spend your one light this game.")
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    public static synchronized String light(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!PhaseOperations.isNight() || !onScript() || !lanternWindowOpen) {
            return "You may only light your lantern at the beginning of a night, before visits begin.";
        }
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(id) || StorytellerState.isStoryteller(id)) {
            return "Only seated players may use a Spirit Lantern.";
        }
        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id))) return "Dead players cannot light a lantern.";
        if (!player.getMainHandItem().is(Items.SOUL_LANTERN)
                && !player.getOffhandItem().is(Items.SOUL_LANTERN)) {
            return "Hold your Spirit Lantern (Soul Lantern) to light it.";
        }
        if (LANTERN_SPENT.contains(id)) return "Your Spirit Lantern has already been spent this game.";
        LANTERN_SPENT.add(id);
        LANTERN_LIT.add(id);
        return "Your Spirit Lantern is lit for tonight. This was your only light for the game.";
    }

    /** A script Wraith suppresses ordinary night-order teleportation. */
    public static synchronized String selectVisit(MinecraftServer server, ServerPlayer storyteller, UUID targetId) {
        if (!PhaseOperations.isNight() || !onScript()) return "Wraith travel rules are not active.";
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(targetId);
        if (seat == null || SeatPositionManager.seatHome(seat) == null) return "No configured house for that player.";
        lanternWindowOpen = false;
        SELECTED_ST_SEATS.put(storyteller.getUUID(), seat);
        ARRIVED_ST_SEATS.remove(storyteller.getUUID());
        SeatPositionManager.Position home = SeatPositionManager.seatHome(seat);
        ServerPlayNetworking.send(storyteller, new WraithTrackingS2CPayload(
                "T|" + home.x() + "|" + home.z() + "|" + targetId));
        return "House " + seat + " highlighted. Fly there for the night visit; Up Arrow no longer teleports.";
    }

    /** Only the opening Wraith visit may teleport the Storyteller. */
    public static synchronized String release(MinecraftServer server, ServerPlayer storyteller, UUID wraithId) {
        if (!PhaseOperations.isNight() || !onScript()) return "Not a Wraith night.";
        PendingRoleAssignment role = StorytellerState.effectiveGrimoireRoles().get(wraithId);
        if (role == null || role.isCustomRole() || role.role() != Role.WRAITH) return "Selected player is not Wraith.";
        if (ACTIVE.containsKey(wraithId)) return "Wraith has already been released tonight.";
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(wraithId);
        if (seat == null || !SeatPositionManager.teleportPlayerToHome(storyteller, seat)) {
            return "Configure the Wraith's house first.";
        }
        if (Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(wraithId)) || isBlocked(wraithId)) {
            return "Visited Wraith. Wraith has no usable ability tonight.";
        }
        ServerPlayer player = server.getPlayerList().getPlayer(wraithId);
        if (player == null) return "Wraith is offline; Storyteller visited their house.";
        if (!SeatPositionManager.isPlayerNearHome(player, seat, HOUSE_RADIUS)) {
            return "Storyteller has visited Wraith. Wraith must return home to begin Spectator Mode.";
        }
        ACTIVE.putIfAbsent(wraithId, player.gameMode.getGameModeForPlayer());
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("Wraith released. Follow the Storyteller; fly through walls to observe silently.")
                .withStyle(ChatFormatting.AQUA));
        return "Wraith released into Spectator Mode. Continue to the first role manually.";
    }

    /** Last Wraith night-order entry: recall, but do NOT teleport the Storyteller. */
    public static synchronized String finish(MinecraftServer server) {
        lanternWindowOpen = false;
        recallAll(server);
        return "Wraith recalled home and returned to Adventure Mode. Dawn is ready.";
    }

    public static synchronized void recallAll(MinecraftServer server) {
        for (UUID id : new HashSet<>(ACTIVE.keySet())) recallOne(server, id);
        ACTIVE.clear();
        VOICE_SEATS.clear();
        LAST_WRAITH_HOUSE.clear();
        LAST_NEAR_DOOR.clear();
        SELECTED_ST_SEATS.clear();
        ARRIVED_ST_SEATS.clear();
        LANTERN_LIT.clear();
        lanternWindowOpen = false;
        if (server != null) {
            for (ServerPlayer storyteller : server.getPlayerList().getPlayers()) {
                if (StorytellerState.isStoryteller(storyteller.getUUID()))
                    ServerPlayNetworking.send(storyteller, new WraithTrackingS2CPayload("OFF"));
            }
            NightChatManager.reconcile(server);
        }
    }

    private static void recallOne(MinecraftServer server, UUID id) {
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
        if (player == null) {
            PENDING_RESTORE.add(id);
            return;
        }
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
        // Always move to a known safe house/seat BEFORE restoring collision.
        boolean moved = seat != null && SeatPositionManager.teleportPlayerToHome(player, seat);
        if (!moved && seat != null) moved = SeatPositionManager.teleportPlayerToTownSquare(player, seat);
        if (!moved) {
            // No safe teleport: leave in spectator rather than materializing in blocks.
            if (PENDING_RESTORE.add(id)) {
                player.sendSystemMessage(Component.literal(
                        "Wraith recall needs a configured house/seat; ask the Storyteller to move you safely.")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        player.setGameMode(GameType.ADVENTURE);
        PENDING_RESTORE.remove(id);
        player.sendSystemMessage(Component.literal("Wraith recalled. Adventure Mode restored.")
                .withStyle(ChatFormatting.GRAY));
        ServerPlayNetworking.send(player, new WraithTrackingS2CPayload("OFF"));
    }

    public static synchronized boolean isActiveWraith(UUID id) {
        return id != null && ACTIVE.containsKey(id);
    }

    public static synchronized Integer voiceSeat(UUID id) {
        return ACTIVE.containsKey(id) ? VOICE_SEATS.get(id) : null;
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null) return;
        // A disconnected Wraith can log back in after Dawn; finish their recall then.
        for (UUID id : new HashSet<>(PENDING_RESTORE)) {
            if (!ACTIVE.containsKey(id)) recallOne(server, id);
        }
        if (!PhaseOperations.isNight() || ServerState.gameEnded) {
            if (!ACTIVE.isEmpty()) recallAll(server);
            return;
        }
        if (!onScript()) {
            if (!ACTIVE.isEmpty()) recallAll(server);
            return;
        }
        ticks++;
        if (ticks % 5 != 0) return;
        LANTERN_LIT.removeIf(id -> Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id)));

        for (UUID id : new HashSet<>(ACTIVE.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            PendingRoleAssignment role = StorytellerState.effectiveGrimoireRoles().get(id);
            if (role == null || role.isCustomRole() || role.role() != Role.WRAITH
                    || Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id)) || isBlocked(id)) {
                ACTIVE.remove(id);
                VOICE_SEATS.remove(id);
                recallOne(server, id);
                continue;
            }
            if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
            int house = nearestHouse(player);
            int previous = LAST_WRAITH_HOUSE.getOrDefault(id, -1);
            boolean nearDoor = house > 0 && nearDoor(player);
            boolean wasNearDoor = LAST_NEAR_DOOR.getOrDefault(id, false);
            if (house != previous) {
                VOICE_SEATS.remove(id);
                if (house > 0 && house != ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(id, -1)
                        && LANTERN_LIT.contains(playerAtSeatId(house)) && !presenceSent) {
                    presenceSent = true;
                    tellHouseOccupant(server, house, "Your lantern flickers. An unnatural presence is nearby.");
                }
            }
            if (house > 0 && nearDoor && (house != previous || !wasNearDoor)) VOICE_SEATS.put(id, house);
            LAST_WRAITH_HOUSE.put(id, house);
            LAST_NEAR_DOOR.put(id, nearDoor);
            Integer speakingIn = VOICE_SEATS.get(id);
            if (speakingIn != null && LANTERN_LIT.contains(playerAtSeatId(speakingIn))) {
                UUID occupant = playerAtSeatId(speakingIn);
                if (occupant != null && IDENTITY_REVEALED.add(occupant)) {
                    tellHouseOccupant(server, speakingIn,
                            "Your lantern exposes the Wraith: " + player.getName().getString());
                }
            }
            ServerPlayer storyteller = server.getPlayerList().getPlayers().stream()
                    .filter(st -> StorytellerState.isStoryteller(st.getUUID())).findFirst().orElse(null);
            if (storyteller != null) {
                ServerPlayNetworking.send(player, new WraithTrackingS2CPayload(
                        "W|" + storyteller.getX() + "|" + storyteller.getZ() + "|" + storyteller.getUUID()));
            }
        }

        for (ServerPlayer st : server.getPlayerList().getPlayers()) {
            if (!StorytellerState.isStoryteller(st.getUUID())) continue;
            int selected = SELECTED_ST_SEATS.getOrDefault(st.getUUID(), -1);
            if (selected <= 0 || ARRIVED_ST_SEATS.containsKey(st.getUUID())) continue;
            if (!SeatPositionManager.isPlayerNearHome(st, selected, HOUSE_RADIUS)) continue;
            ARRIVED_ST_SEATS.put(st.getUUID(), selected);
            UUID occupant = playerAtSeatId(selected);
            PendingRoleAssignment target = occupant == null ? null : StorytellerState.effectiveGrimoireRoles().get(occupant);
            if (target == null || target.isFinalGood()) continue;
            for (UUID id : new HashSet<>(ACTIVE.keySet())) {
                ServerPlayer wraith = server.getPlayerList().getPlayer(id);
                if (wraith == null || !SeatPositionManager.teleportPlayerToHome(wraith, selected)) continue;
                VOICE_SEATS.put(id, selected);
                // Preserve the evil-visit voice override across the next house-change tick.
                LAST_WRAITH_HOUSE.put(id, selected);
                LAST_NEAR_DOOR.put(id, nearDoor(wraith));
                wraith.sendSystemMessage(Component.literal("An evil player wakes. You join their house voice chat.")
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }

    private static UUID playerAtSeatId(int seat) {
        for (Map.Entry<UUID, Integer> entry : ServerState.PLAYER_SEAT_NUMBERS.entrySet())
            if (entry.getValue() != null && entry.getValue() == seat) return entry.getKey();
        return null;
    }

    private static void tellHouseOccupant(MinecraftServer server, int seat, String message) {
        UUID occupant = playerAtSeatId(seat);
        ServerPlayer target = occupant == null ? null : server.getPlayerList().getPlayer(occupant);
        if (target != null) target.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
    }

    private static int nearestHouse(ServerPlayer player) {
        int result = -1;
        double closest = HOUSE_RADIUS * HOUSE_RADIUS;
        for (int seat : ServerState.PLAYER_SEAT_NUMBERS.values()) {
            SeatPositionManager.Position pos = SeatPositionManager.seatHome(seat);
            if (pos == null) continue;
            double sq = player.distanceToSqr(pos.x(), pos.y(), pos.z());
            if (sq <= closest) { closest = sq; result = seat; }
        }
        return result;
    }

    private static boolean nearDoor(ServerPlayer player) {
        BlockPos origin = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        for (int x = -1; x <= 1; x++) for (int y = -2; y <= 2; y++) for (int z = -1; z <= 1; z++) {
            if (player.level().getBlockState(origin.offset(x, y, z)).is(BlockTags.DOORS)) return true;
        }
        return false;
    }

    private static boolean isBlocked(UUID id) {
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(id, java.util.List.of())) {
            String text = reminder.text().toLowerCase(java.util.Locale.ROOT);
            if (text.contains("poison") || text.contains("drunk") || text.contains("no ability")) return true;
        }
        return false;
    }
}
