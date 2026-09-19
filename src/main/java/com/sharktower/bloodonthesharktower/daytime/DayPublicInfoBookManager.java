package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-facing public-information memory aid for Flowergirl and Town Crier.
 *
 * The book contains only information that was publicly observable during the
 * preceding Day: who cast a locked YES vote and who made a nomination. It is
 * automatically issued once the relevant player reaches their configured house
 * at Night and is removed at Dawn. It never contains the hidden Demon/Minion
 * answer that the Storyteller gives for the role ability.
 */
public final class DayPublicInfoBookManager {
    private static final String MARKER_KEY = "blood_on_the_sharktower_public_info_book";
    private static final double HOME_RADIUS = 5.0D;
    private static final int ENTRIES_PER_PAGE = 10;

    private static final Map<UUID, PublicAction> YES_VOTERS = new LinkedHashMap<>();
    private static final Map<UUID, PublicAction> NOMINATORS = new LinkedHashMap<>();
    private static int trackedDay = -1;

    private DayPublicInfoBookManager() {}

    public static synchronized void recordVote(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null || ServerState.currentDay <= 0) return;
        ensureDay(ServerState.currentDay);
        record(YES_VOTERS, server, playerId);
    }

    public static synchronized void recordNomination(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null || ServerState.currentDay <= 0) return;
        ensureDay(ServerState.currentDay);
        record(NOMINATORS, server, playerId);
    }

    /** Called every server tick; work is intentionally tiny for the small BOTC roster. */
    public static synchronized void serverTick(MinecraftServer server) {
        if (server == null) return;

        if (!PhaseOperations.isNight() || ServerState.gameEnded) {
            // Dawn/Setup cleanup also catches a player who was offline when Dawn
            // happened and later reconnects with a temporary book still saved.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                removeTemporaryBooks(player);
            }

            if (PhaseOperations.isDay()) {
                ensureDay(ServerState.currentDay);
            } else if (ServerState.currentDay == 0 && ServerState.currentNight == 0) {
                trackedDay = -1;
                YES_VOTERS.clear();
                NOMINATORS.clear();
            }
            return;
        }

        // There is no preceding public Day before Night 1.
        if (ServerState.currentDay <= 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (StorytellerState.isStoryteller(playerId)) {
                removeTemporaryBooks(player);
                continue;
            }

            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(playerId);
            if (seat == null || seat <= 0) continue;
            if (SeatPositionManager.seatHome(seat) == null) continue;
            if (!SeatPositionManager.isPlayerNearHome(player, seat, HOME_RADIUS)) continue;

            java.util.EnumSet<BookType> desiredBooks = desiredBooksFor(playerId);
            removeTemporaryBooksExcept(player, desiredBooks);
            for (BookType type : desiredBooks) {
                ensureBook(player, type);
            }
        }
    }

    private static void ensureDay(int day) {
        if (day <= 0 || trackedDay == day) return;
        trackedDay = day;
        YES_VOTERS.clear();
        NOMINATORS.clear();
    }

    private static void record(Map<UUID, PublicAction> target, MinecraftServer server, UUID playerId) {
        String name = playerId.toString().substring(0, 8);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) name = player.getName().getString();

        PublicAction existing = target.get(playerId);
        if (existing == null) target.put(playerId, new PublicAction(name, 1));
        else target.put(playerId, new PublicAction(existing.name(), existing.count() + 1));
    }

    private static java.util.EnumSet<BookType> desiredBooksFor(UUID playerId) {
        java.util.EnumSet<BookType> desired = java.util.EnumSet.noneOf(BookType.class);

        Role apparentRole = apparentOfficialRole(playerId);
        if (apparentRole == Role.FLOWERGIRL) desired.add(BookType.FLOWERGIRL);
        if (apparentRole == Role.TOWN_CRIER) desired.add(BookType.TOWN_CRIER);

        // Demon bluffs are public-role cover identities, so a Demon bluffing
        // Flowergirl/Town Crier should get the same public-history aid as the
        // real character. The book still contains only publicly observable
        // votes/nominations and therefore reveals no hidden role information.
        PendingRoleAssignment actual = ServerState.PLAYER_ROLES.get(playerId);
        if (actual != null && actual.getRoleType() == RoleType.DEMON) {
            for (ScriptRole bluff : StorytellerState.DEMON_BLUFFS) {
                if (bluff == null) continue;
                if (Role.FLOWERGIRL.getId().equals(bluff.getId())) desired.add(BookType.FLOWERGIRL);
                if (Role.TOWN_CRIER.getId().equals(bluff.getId())) desired.add(BookType.TOWN_CRIER);
            }
        }

        return desired;
    }

    private static Role apparentOfficialRole(UUID playerId) {
        PendingRoleAssignment actual = ServerState.PLAYER_ROLES.get(playerId);
        if (actual == null || !actual.isOfficialRole()) return Role.NO_ROLE;

        // Drunk/Marionette should receive the QoL aid for the role they believe
        // they are, not for their hidden true character.
        if (actual.role() == Role.DRUNK || actual.role() == Role.MARIONETTE) {
            PendingRoleAssignment perceived = ServerState.PLAYER_PERCEIVED_ROLES.get(playerId);
            if (perceived != null && perceived.isOfficialRole()) return perceived.role();
        }
        return actual.role();
    }

    private static void ensureBook(ServerPlayer player, BookType type) {
        if (hasTemporaryBook(player, type)) return;

        Map<UUID, PublicAction> source = type == BookType.FLOWERGIRL ? YES_VOTERS : NOMINATORS;
        ItemStack book = createBook(type, source);
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
            items.set(emptySlot, book);
            player.getInventory().setChanged();
            player.sendSystemMessage(Component.literal(type.displayName + " public-info book added until Dawn.")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.literal("No empty inventory slot for your " + type.displayName
                    + " public-info book.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static ItemStack createBook(BookType type, Map<UUID, PublicAction> source) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        String shortTitle = type == BookType.FLOWERGIRL ? "Flowergirl Day " + trackedDay : "Town Crier Day " + trackedDay;
        book.set(DataComponents.CUSTOM_NAME,
                Component.literal(type.displayName + " — Day " + trackedDay).withStyle(ChatFormatting.AQUA));

        CompoundTag marker = new CompoundTag();
        marker.putBoolean(MARKER_KEY, true);
        marker.putString("type", type.id);
        marker.putInt("day", trackedDay);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));

        List<Filterable<Component>> pages = new ArrayList<>();
        List<PublicAction> entries = new ArrayList<>(source.values());
        if (entries.isEmpty()) {
            pages.add(Filterable.passThrough(Component.literal(
                    type.header(trackedDay) + "\n\nNo players " + type.emptyVerb + " today.\n\n"
                            + "This book contains public information only and will be removed at Dawn.")));
        } else {
            for (int start = 0; start < entries.size(); start += ENTRIES_PER_PAGE) {
                int end = Math.min(entries.size(), start + ENTRIES_PER_PAGE);
                StringBuilder text = new StringBuilder(type.header(trackedDay)).append("\n\n");
                for (int i = start; i < end; i++) {
                    PublicAction action = entries.get(i);
                    text.append(i + 1).append(". ").append(action.name());
                    if (action.count() > 1) text.append("  x").append(action.count());
                    text.append('\n');
                }
                if (end == entries.size()) {
                    text.append("\nPublic information only. Removed at Dawn.");
                }
                pages.add(Filterable.passThrough(Component.literal(text.toString())));
            }
        }

        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(shortTitle),
                "Blood on the Sharktower",
                0,
                pages,
                true
        ));
        return book;
    }

    private static boolean hasTemporaryBook(ServerPlayer player, BookType type) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!isTemporaryBook(stack)) continue;
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            CompoundTag tag = data.copyTag();
            if (type.id.equals(tag.getStringOr("type", "")) && tag.getIntOr("day", -1) == trackedDay) return true;
        }
        return false;
    }

    private static void removeTemporaryBooksExcept(ServerPlayer player, java.util.Set<BookType> keep) {
        boolean changed = false;
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!isTemporaryBook(stack)) continue;

            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = data == null ? null : data.copyTag();
            String typeId = tag == null ? "" : tag.getStringOr("type", "");
            int day = tag == null ? -1 : tag.getIntOr("day", -1);

            boolean retained = day == trackedDay && keep.stream().anyMatch(type -> type.id.equals(typeId));
            if (retained) continue;

            items.set(slot, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) player.getInventory().setChanged();
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

    private record PublicAction(String name, int count) {}

    private enum BookType {
        FLOWERGIRL("flowergirl", "Flowergirl", "Day %d YES voters", "cast a YES vote"),
        TOWN_CRIER("town_crier", "Town Crier", "Day %d nominators", "made a nomination");

        private final String id;
        private final String displayName;
        private final String headerPattern;
        private final String emptyVerb;

        BookType(String id, String displayName, String headerPattern, String emptyVerb) {
            this.id = id;
            this.displayName = displayName;
            this.headerPattern = headerPattern;
            this.emptyVerb = emptyVerb;
        }

        private String header(int day) {
            return String.format(java.util.Locale.ROOT, headerPattern, day);
        }
    }
}
