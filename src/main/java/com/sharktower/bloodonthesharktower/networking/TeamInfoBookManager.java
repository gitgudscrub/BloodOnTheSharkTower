package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Temporary private memory aid for automatic Minion/Demon starting information.
 *
 * The chat delivery remains authoritative. This book mirrors that exact message
 * so players can reopen the information during the Night without scrolling
 * through chat. Marked books are removed as soon as Day begins (and on setup or
 * game end), including when a player reconnects later with a stale copy saved in
 * their inventory.
 */
public final class TeamInfoBookManager {
    private static final String MARKER_KEY = "blood_on_the_sharktower_team_info_book";
    private static final int PAGE_CHARACTER_LIMIT = 900;

    private TeamInfoBookManager() {}

    public static boolean give(ServerPlayer player, String kind, String message) {
        if (player == null || message == null || message.isBlank()) return false;

        removeTemporaryBooks(player);
        ItemStack book = createBook(kind, message);
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

        if (emptySlot < 0) {
            player.sendSystemMessage(Component.literal(
                    "No empty inventory slot for your temporary team-info book. The same information is still in chat.")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        items.set(emptySlot, book);
        player.getInventory().setChanged();
        return true;
    }

    /** Called each server tick so Day/setup cleanup also catches reconnecting players. */
    public static void serverTick(MinecraftServer server) {
        if (server == null) return;
        if (PhaseOperations.isNight() && !ServerState.gameEnded) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeTemporaryBooks(player);
        }
    }

    public static void clearAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeTemporaryBooks(player);
        }
    }

    private static ItemStack createBook(String kind, String message) {
        boolean demon = "demon".equalsIgnoreCase(kind);
        String label = demon ? "Demon Info" : "Minion Info";
        int night = Math.max(1, ServerState.currentNight);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.CUSTOM_NAME,
                Component.literal(label + " — Night " + night).withStyle(ChatFormatting.LIGHT_PURPLE));

        CompoundTag marker = new CompoundTag();
        marker.putBoolean(MARKER_KEY, true);
        marker.putString("type", demon ? "demon" : "minion");
        marker.putInt("night", night);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));

        List<Filterable<Component>> pages = pages(message);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(label + " N" + night),
                "Blood on the Sharktower",
                0,
                pages,
                true
        ));
        return book;
    }

    private static List<Filterable<Component>> pages(String message) {
        String normalized = message.replace("\r\n", "\n").trim();
        List<Filterable<Component>> pages = new ArrayList<>();
        int offset = 0;
        while (offset < normalized.length()) {
            int end = Math.min(normalized.length(), offset + PAGE_CHARACTER_LIMIT);
            if (end < normalized.length()) {
                int newline = normalized.lastIndexOf('\n', end);
                if (newline > offset) end = newline;
            }
            String page = normalized.substring(offset, end).trim();
            if (!page.isEmpty()) pages.add(Filterable.passThrough(Component.literal(page)));
            offset = end;
            while (offset < normalized.length() && normalized.charAt(offset) == '\n') offset++;
        }
        if (pages.isEmpty()) pages.add(Filterable.passThrough(Component.literal("Team information.")));
        return List.copyOf(pages);
    }

    private static void removeTemporaryBooks(ServerPlayer player) {
        boolean changed = false;
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            if (!isTemporaryBook(items.get(slot))) continue;
            items.set(slot, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) player.getInventory().setChanged();
    }

    private static boolean isTemporaryBook(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
    }
}
