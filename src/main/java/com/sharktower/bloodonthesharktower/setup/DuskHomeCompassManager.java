package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A.11 dusk navigation helper.
 *
 * While Night is active, every connected seated non-Storyteller player who has
 * not yet reached their configured seat home receives a temporary compass that
 * points to that home's coordinates. Reaching the home removes the compass for
 * the rest of that Night. Dawn/setup/end-game cleanup removes any leftovers.
 */
public final class DuskHomeCompassManager {
    private static final String MARKER_KEY = "blood_on_the_sharktower_home_compass";
    private static final double HOME_REACHED_RADIUS = 4.0D;

    private static final Set<UUID> ARRIVED_THIS_NIGHT = new HashSet<>();
    private static int trackedNight = -1;

    private DuskHomeCompassManager() {}

    public static void serverTick(MinecraftServer server) {
        if (server == null) return;

        if (!PhaseOperations.isNight() || ServerState.gameEnded) {
            trackedNight = -1;
            ARRIVED_THIS_NIGHT.clear();
            // Running this while Day/Setup is active also cleans an offline
            // player's leftover compass as soon as they reconnect.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                removeHomeCompasses(player);
            }
            return;
        }

        int currentNight = Math.max(1, ServerState.currentNight);
        if (trackedNight != currentNight) {
            trackedNight = currentNight;
            ARRIVED_THIS_NIGHT.clear();
            // Never carry a previous Night's temporary compass forward.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                removeHomeCompasses(player);
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            if (StorytellerState.isStoryteller(playerId)) {
                removeHomeCompasses(player);
                continue;
            }

            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(playerId);
            if (seat == null) {
                removeHomeCompasses(player);
                continue;
            }

            SeatPositionManager.Position home = SeatPositionManager.seatHome(seat);
            if (home == null) {
                removeHomeCompasses(player);
                continue;
            }

            if (ARRIVED_THIS_NIGHT.contains(playerId)) {
                removeHomeCompasses(player);
                continue;
            }

            if (SeatPositionManager.isPlayerNearHome(player, seat, HOME_REACHED_RADIUS)) {
                boolean wasNavigating = removeHomeCompasses(player) > 0;
                ARRIVED_THIS_NIGHT.add(playerId);
                if (wasNavigating) {
                    player.sendSystemMessage(Component.literal("HOME REACHED").withStyle(ChatFormatting.GREEN));
                }
                continue;
            }

            if (!hasHomeCompass(player)) {
                giveHomeCompass(server, player, home);
            }
        }
    }

    private static void giveHomeCompass(MinecraftServer server, ServerPlayer player, SeatPositionManager.Position home) {
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Home Compass").withStyle(ChatFormatting.GOLD)
        );

        CompoundTag marker = new CompoundTag();
        marker.putBoolean(MARKER_KEY, true);
        compass.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));

        BlockPos target = BlockPos.containing(home.x(), home.y(), home.z());
        GlobalPos globalTarget = GlobalPos.of(server.overworld().dimension(), target);
        compass.set(
                DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(globalTarget), false)
        );

        // Prefer the hotbar so the compass is immediately useful. If all nine
        // hotbar slots are occupied, use another empty main-inventory slot.
        // We deliberately never overwrite or drop a player's existing item.
        var items = player.getInventory().getNonEquipmentItems();
        int emptySlot = -1;
        int hotbarEnd = Math.min(9, items.size());
        for (int slot = 0; slot < hotbarEnd; slot++) {
            if (items.get(slot).isEmpty()) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) {
            for (int slot = hotbarEnd; slot < items.size(); slot++) {
                if (items.get(slot).isEmpty()) {
                    emptySlot = slot;
                    break;
                }
            }
        }

        if (emptySlot >= 0) {
            items.set(emptySlot, compass);
            player.getInventory().setChanged();
        }
    }

    private static boolean hasHomeCompass(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isHomeCompass(stack)) return true;
        }
        return false;
    }

    /** Returns how many Sharktower Home Compasses were removed. */
    private static int removeHomeCompasses(ServerPlayer player) {
        int removed = 0;
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!isHomeCompass(stack)) continue;
            items.set(slot, ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) {
            player.getInventory().setChanged();
        }
        return removed;
    }

    private static boolean isHomeCompass(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.COMPASS)) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        return customData.copyTag().getBooleanOr(MARKER_KEY, false);
    }
}
