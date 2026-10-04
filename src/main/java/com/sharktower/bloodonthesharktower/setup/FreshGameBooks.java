package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Replace carried book-and-quill notes once when a fresh game's roles are sent. */
public final class FreshGameBooks {
    private static final Set<UUID> PENDING = new HashSet<>();
    private static long refreshedGeneration = Long.MIN_VALUE;
    private FreshGameBooks() {}
    public static void beginGame(MinecraftServer server, Collection<UUID> players) {
        if (ServerState.currentNight != 0 || ServerState.currentDay != 0
                || refreshedGeneration == ServerState.resetGeneration) return;
        refreshedGeneration = ServerState.resetGeneration;
        PENDING.clear(); PENDING.addAll(players);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) refreshIfPending(player);
    }
    /** Offline assigned players get the same fresh book when they reconnect. */
    public static void refreshIfPending(ServerPlayer player) {
        if (!PENDING.remove(player.getUUID())) return;
        boolean changed = false;
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.is(Items.WRITABLE_BOOK)) continue;
            items.set(slot, new ItemStack(Items.WRITABLE_BOOK, stack.getCount()));
            changed = true;
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.is(Items.WRITABLE_BOOK)) {
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.WRITABLE_BOOK, offhand.getCount()));
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
    }
    public static void clear() { PENDING.clear(); refreshedGeneration = Long.MIN_VALUE; }
}
