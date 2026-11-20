package com.sharktower.bloodonthesharktower.guide;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Three written books: the Player Guide (how to play here), the Script Guide
 * (every character on the current script: ability and a tip for playing it)
 * and the Storyteller Guide (how to run a game here).
 *
 * Players get the Player Guide when they first join, the Script Guide when
 * roles are sent, and the Storyteller Guide when they claim Storyteller.
 * {@code /guide player|script|storyteller} gives a fresh copy any time.
 */
public final class GuideBooks {
    public enum Kind { PLAYER, SCRIPT, STORYTELLER }

    private static final String MARKER = "sharktower_guide";
    private static final String DIR = "/data/" + BloodOnTheSharktower.MOD_ID + "/guides/";
    private static final Gson GSON = new Gson();
    /** Players who got the Player Guide on joining this server run. */
    private static final Set<UUID> WELCOMED = new HashSet<>();
    private static Map<String, String> tips;

    private GuideBooks() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer player = handler.player;
            if (WELCOMED.add(player.getUUID()) && !has(player, Kind.PLAYER)) {
                give(player, Kind.PLAYER, false);
            }
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("guide")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) player.sendSystemMessage(menu());
                            return 1;
                        })
                        .then(Commands.literal("player").executes(context -> giveCommand(context.getSource().getPlayer(), Kind.PLAYER)))
                        .then(Commands.literal("script").executes(context -> giveCommand(context.getSource().getPlayer(), Kind.SCRIPT)))
                        .then(Commands.literal("storyteller").executes(context -> giveCommand(context.getSource().getPlayer(), Kind.STORYTELLER)))));
    }

    private static int giveCommand(ServerPlayer player, Kind kind) {
        if (player == null) return 0;
        if (kind == Kind.SCRIPT && ServerState.currentScript == null) {
            player.sendSystemMessage(Component.literal("No script is chosen yet. The Storyteller picks one in the Grimoire.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        return give(player, kind, true) ? 1 : 0;
    }

    private static Component menu() {
        MutableComponent line = Component.literal("Guide books: ").withStyle(ChatFormatting.GOLD);
        String[][] options = {{"Player", "player", "How to play Blood on the Sharktower"},
                {"Script", "script", "Every character on the current script"},
                {"Storyteller", "storyteller", "How to run a game"}};
        for (int i = 0; i < options.length; i++) {
            String[] o = options[i];
            if (i > 0) line.append(Component.literal("  "));
            line.append(Component.literal("[" + o[0] + "]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/guide " + o[1]))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(o[2])))));
        }
        return line;
    }

    // ------------------------------------------------------------- Delivery

    /** Send Roles: every seated player and Storyteller gets this script's guide. */
    public static void onRolesSent(MinecraftServer server, Collection<UUID> players) {
        if (server == null || ServerState.currentScript == null) return;
        Set<UUID> readers = new HashSet<>(players);
        readers.addAll(StorytellerState.STORYTELLERS);
        for (UUID id : readers) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) give(player, Kind.SCRIPT, false);
        }
    }

    /** A new Storyteller gets the Storyteller Guide (once). */
    public static void onStorytellerClaimed(ServerPlayer player) {
        if (player != null && !has(player, Kind.STORYTELLER)) give(player, Kind.STORYTELLER, false);
    }

    /** Replaces any older copy of this guide with a fresh one. Returns false if there was no room. */
    public static boolean give(ServerPlayer player, Kind kind, boolean announce) {
        ItemStack book = create(kind);
        if (book == null) return false;
        remove(player, kind);
        if (!player.getInventory().add(book)) {
            player.sendSystemMessage(Component.literal("No room for the " + title(kind) + ". Make space and type /guide "
                    + kind.name().toLowerCase(Locale.ROOT) + ".").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal((announce ? "Here is the " : "You have been given the ") + title(kind)
                + ". Right-click it to read." + (announce ? "" : " Type /guide for the others."))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    static String title(Kind kind) {
        return switch (kind) {
            case PLAYER -> "Player Guide";
            case SCRIPT -> "Script Guide";
            case STORYTELLER -> "Storyteller Guide";
        };
    }

    public static boolean has(ServerPlayer player, Kind kind) {
        var items = player.getInventory().getNonEquipmentItems();
        for (ItemStack stack : items) if (isGuide(stack, kind)) return true;
        return isGuide(player.getItemBySlot(EquipmentSlot.OFFHAND), kind) || isGuide(player.containerMenu.getCarried(), kind);
    }

    private static void remove(ServerPlayer player, Kind kind) {
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            if (isGuide(items.get(slot), kind)) items.set(slot, ItemStack.EMPTY);
        }
        if (isGuide(player.getItemBySlot(EquipmentSlot.OFFHAND), kind)) player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static boolean isGuide(ItemStack stack, Kind kind) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && kind.name().equals(data.copyTag().getStringOr(MARKER, ""));
    }

    // ------------------------------------------------------------- Books

    /** The book, or null (Script Guide without a script). */
    public static ItemStack create(Kind kind) {
        BookPages pages = new BookPages();
        String author = "Blood on the Sharktower";
        switch (kind) {
            case PLAYER -> pages.markdown(resource("player_guide.txt"));
            case STORYTELLER -> pages.markdown(resource("storyteller_guide.txt"));
            case SCRIPT -> {
                Script script = ServerState.currentScript;
                if (script == null) return null;
                scriptGuide(pages, script);
                if (script.author() != null && !script.author().isBlank()) author = script.author();
            }
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        String name = kind == Kind.SCRIPT ? "Script Guide: " + ServerState.currentScript.name() : title(kind);
        book.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));
        CompoundTag marker = new CompoundTag();
        marker.putString(MARKER, kind.name());
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        List<Filterable<Component>> content = new ArrayList<>();
        for (Component page : pages.pages()) content.add(Filterable.passThrough(page));
        String shortTitle = name.length() > 32 ? name.substring(0, 32) : name;
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(shortTitle), author, 0, List.copyOf(content), true));
        return book;
    }

    static void scriptGuide(BookPages pages, Script script) {
        pages.newPage();
        pages.heading(script.name() == null || script.name().isBlank() ? "Script Guide" : script.name(), ChatFormatting.DARK_RED);
        if (script.author() != null && !script.author().isBlank()) pages.paragraph("by " + script.author(), ChatFormatting.DARK_GRAY);
        pages.paragraph("Every character that might be in play, one per page: what it does and how to play it.", null);
        List<ScriptRole> roles = new ArrayList<>(script.allRoles());
        int[] counts = new int[RoleType.values().length];
        for (ScriptRole role : roles) counts[role.getTeam().ordinal()]++;
        StringBuilder summary = new StringBuilder();
        for (RoleType type : List.of(RoleType.TOWNSFOLK, RoleType.OUTSIDER, RoleType.MINION, RoleType.DEMON, RoleType.TRAVELER)) {
            if (counts[type.ordinal()] > 0) summary.append(counts[type.ordinal()]).append(' ').append(teamName(type, counts[type.ordinal()])).append('\n');
        }
        pages.paragraph(summary.toString().trim(), null);
        pages.paragraph("Get a fresh copy with /guide script.", ChatFormatting.DARK_GRAY);

        for (ScriptRole role : roles) character(pages, role);
        for (ScriptRole role : script.allFabledAndLoric()) character(pages, role);
    }

    private static void character(BookPages pages, ScriptRole role) {
        pages.newPage();
        ChatFormatting colour = teamColour(role.getTeam());
        pages.heading(prettyName(role.getDisplayName()), colour);
        pages.paragraph(teamName(role.getTeam(), 1), ChatFormatting.DARK_GRAY);
        if (role.getAbility() != null && !role.getAbility().isBlank()) pages.paragraph(role.getAbility(), null);
        String tip = tips().get(role.getId());
        if (tip != null && !tip.isBlank()) {
            pages.subheading("How to play", colour);
            pages.paragraph(tip, null);
        }
    }

    static String prettyName(String name) {
        if (name == null || name.isBlank()) return "Character";
        if (!name.equals(name.toUpperCase(Locale.ROOT))) return name;
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (char c : name.toCharArray()) {
            out.append(start ? c : Character.toLowerCase(c));
            start = c == ' ' || c == '-' || c == '\'';
        }
        return out.toString();
    }

    static String teamName(RoleType type, int count) {
        String name = switch (type) {
            case TOWNSFOLK -> "Townsfolk";
            case OUTSIDER -> "Outsider";
            case MINION -> "Minion";
            case DEMON -> "Demon";
            case TRAVELER -> "Traveller";
            case FABLED -> "Fabled";
            case LORIC -> "Loric";
            case NONE -> "Character";
        };
        return count == 1 || type == RoleType.TOWNSFOLK ? name : name + "s";
    }

    static ChatFormatting teamColour(RoleType type) {
        return switch (type) {
            case TOWNSFOLK -> ChatFormatting.DARK_BLUE;
            case OUTSIDER -> ChatFormatting.DARK_AQUA;
            case MINION, DEMON -> ChatFormatting.DARK_RED;
            case TRAVELER -> ChatFormatting.DARK_PURPLE;
            case FABLED, LORIC -> ChatFormatting.GOLD;
            case NONE -> ChatFormatting.BLACK;
        };
    }

    /** Whether a character has a "How to play" tip (tests). */
    public static boolean hasTip(String roleId) {
        return tips().containsKey(roleId);
    }

    private static synchronized Map<String, String> tips() {
        if (tips == null) {
            Map<String, String> loaded = new java.util.HashMap<>();
            try {
                JsonObject json = GSON.fromJson(resource("role_tips.json"), JsonObject.class);
                for (var entry : json.entrySet()) loaded.put(entry.getKey(), entry.getValue().getAsString());
            } catch (RuntimeException e) {
                BloodOnTheSharktower.LOGGER.warn("Guide books: role tips could not be read", e);
            }
            tips = Map.copyOf(loaded);
        }
        return tips;
    }

    static String resource(String name) {
        try (InputStream in = GuideBooks.class.getResourceAsStream(DIR + name)) {
            if (in == null) return "# Missing\n\nThis guide is missing from the mod.";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "# Missing\n\nThis guide could not be read.";
        }
    }
}
