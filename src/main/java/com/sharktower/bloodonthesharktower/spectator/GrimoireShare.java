package com.sharktower.bloodonthesharktower.spectator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.serverupdate.SiteLink;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Website spectators can ask to see the Grimoire; a Storyteller approves or
 * rejects each request in game. The Grimoire is only sent to the website while
 * at least one approved spectator has Watch live open, and the site only shows
 * it to approved spectators, for the current game only.
 *
 * <p>Requests arrive in the website's reply to the live state post, as
 * {@code {"requests": [{"id", "name"}]}}. The id and name are checked and the
 * name is only ever shown as plain text; nothing else from the site is used.
 */
public final class GrimoireShare {
    private static final Gson GSON = new Gson();
    private static final Pattern ID = Pattern.compile("[0-9a-f]{16}");
    private static final int MAX_REMINDERS = 20;

    /** Requests already shown to the Storytellers (id -> spectator name). */
    private static final Map<String, String> ANNOUNCED = new ConcurrentHashMap<>();
    private static volatile boolean wanted;
    private static volatile MinecraftServer server;

    private GrimoireShare() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            server = null;
            wanted = false;
            ANNOUNCED.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("bots").then(Commands.literal("watchlive")
                        .requires(GrimoireShare::isStoryteller)
                        .then(Commands.literal("approve").then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> decide(c, true))))
                        .then(Commands.literal("reject").then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> decide(c, false))))
                        .then(Commands.literal("revoke").executes(GrimoireShare::revoke)))));
    }

    static boolean wanted() {
        return wanted;
    }

    private static boolean isStoryteller(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null && StorytellerState.isStoryteller(player.getUUID());
    }

    /** HTTP thread: the website's reply to a live state post. */
    static void readReply(JsonObject reply) {
        wanted = reply.has("grimoire") && reply.get("grimoire").isJsonPrimitive() && reply.get("grimoire").getAsBoolean();
        JsonElement raw = reply.get("requests");
        Map<String, String> open = new LinkedHashMap<>();
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement e : raw.getAsJsonArray()) {
                if (open.size() >= 10 || !e.isJsonObject()) break;
                JsonObject o = e.getAsJsonObject();
                String id = o.has("id") && o.get("id").isJsonPrimitive() ? o.get("id").getAsString() : "";
                String name = o.has("name") && o.get("name").isJsonPrimitive() ? cleanName(o.get("name").getAsString()) : "";
                if (ID.matcher(id).matches() && !name.isEmpty()) open.put(id, name);
            }
        }
        ANNOUNCED.keySet().retainAll(open.keySet());
        for (Map.Entry<String, String> request : open.entrySet()) {
            if (ANNOUNCED.putIfAbsent(request.getKey(), request.getValue()) == null) {
                MinecraftServer s = server;
                if (s != null) s.execute(() -> announce(s, request.getKey(), request.getValue()));
            }
        }
    }

    /** Plain display text: no control or formatting characters, 32 at most. */
    static String cleanName(String raw) {
        String name = raw.replaceAll("[\\p{Cntrl}§]", "").strip();
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    private static void announce(MinecraftServer s, String id, String name) {
        com.sharktower.bloodonthesharktower.BloodOnTheSharktower.LOGGER.info(
                "Watch live: {} asks to see the Grimoire (request {}).", name, id);
        MutableComponent message = Component.literal("[Watch live] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (website spectator) asks to see the Grimoire for this game. ")
                        .withStyle(ChatFormatting.GRAY))
                .append(button("[Approve]", ChatFormatting.GREEN, "/bots watchlive approve " + id,
                        "Let " + name + " see roles, alignments and reminders until this game ends"))
                .append(Component.literal(" "))
                .append(button("[Reject]", ChatFormatting.RED, "/bots watchlive reject " + id, "Say no"));
        for (UUID st : List.copyOf(StorytellerState.STORYTELLERS)) {
            ServerPlayer player = s.getPlayerList().getPlayer(st);
            if (player != null) player.sendSystemMessage(message);
        }
    }

    private static MutableComponent button(String label, ChatFormatting colour, String command, String hover) {
        return Component.literal(label).withStyle(colour, ChatFormatting.BOLD).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static int decide(CommandContext<CommandSourceStack> c, boolean approve) {
        String id = StringArgumentType.getString(c, "id");
        String name = ANNOUNCED.remove(id);
        CommandSourceStack source = c.getSource();
        if (!ID.matcher(id).matches() || name == null) {
            source.sendFailure(Component.literal("That request has already been answered or has expired."));
            return 0;
        }
        if (!SiteLink.enabled()) {
            source.sendFailure(Component.literal("The website link is off on this server."));
            return 0;
        }
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("approve", approve);
        body.addProperty("by", source.getTextName());
        SiteLink.post("/internal/minecraft/grimoire", GSON.toJson(body), "Grimoire decisions", null);
        source.sendSuccess(() -> Component.literal(approve
                ? name + " can now see the Grimoire until this game ends. /bots watchlive revoke takes it back."
                : "Rejected " + name + "'s Grimoire request.").withStyle(ChatFormatting.GRAY), false);
        tellOtherStorytellers(source, (approve ? "approved " : "rejected ") + name + "'s Grimoire request.");
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        if (!SiteLink.enabled()) {
            source.sendFailure(Component.literal("The website link is off on this server."));
            return 0;
        }
        JsonObject body = new JsonObject();
        body.addProperty("revokeAll", true);
        body.addProperty("by", source.getTextName());
        SiteLink.post("/internal/minecraft/grimoire", GSON.toJson(body), "Grimoire decisions", null);
        source.sendSuccess(() -> Component.literal("No website spectator can see the Grimoire now.")
                .withStyle(ChatFormatting.GRAY), false);
        tellOtherStorytellers(source, "took Grimoire access away from all website spectators.");
        return 1;
    }

    private static void tellOtherStorytellers(CommandSourceStack source, String what) {
        ServerPlayer me = source.getPlayer();
        Component line = Component.literal("[Watch live] " + source.getTextName() + " " + what).withStyle(ChatFormatting.GRAY);
        for (UUID st : List.copyOf(StorytellerState.STORYTELLERS)) {
            if (me != null && st.equals(me.getUUID())) continue;
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(st);
            if (player != null) player.sendSystemMessage(line);
        }
    }

    /** Server thread: the Storyteller's view, for approved spectators only. */
    static JsonObject snapshot(MinecraftServer server) {
        boolean setup = ServerState.currentNight == 0 && ServerState.currentDay == 0 && !ServerState.gameEnded;
        Map<UUID, Integer> seats = setup ? StorytellerState.effectiveGrimoireSeats() : ServerState.PLAYER_SEAT_NUMBERS;
        Map<UUID, PendingRoleAssignment> roles = StorytellerState.effectiveGrimoireRoles();
        Map<UUID, PendingRoleAssignment> believed = StorytellerState.effectiveGrimoirePerceivedRoles();
        Map<UUID, Boolean> dead = ServerState.PLAYER_DEATH_STATUS;

        List<Map.Entry<UUID, Integer>> order = new ArrayList<>(seats.entrySet());
        order.sort(Comparator.comparingInt(Map.Entry::getValue));
        JsonArray seatList = new JsonArray();
        for (Map.Entry<UUID, Integer> seat : order) {
            UUID id = seat.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("seat", seat.getValue());
            o.addProperty("name", player != null ? player.getName().getString() : "(offline)");
            o.addProperty("dead", Boolean.TRUE.equals(dead.get(id)));
            PendingRoleAssignment role = roles.get(id);
            if (role != null && role.getScriptRole() != null) {
                o.addProperty("role", role.getDisplayName());
                o.addProperty("type", role.getRoleType().name().toLowerCase(java.util.Locale.ROOT));
                o.addProperty("good", role.isFinalGood());
            }
            PendingRoleAssignment thinks = believed.get(id);
            if (thinks != null && thinks.getScriptRole() != null && (role == null
                    || !thinks.getDisplayName().equals(role.getDisplayName()))) {
                o.addProperty("believes", thinks.getDisplayName());
            }
            JsonArray reminders = new JsonArray();
            for (Reminder r : List.copyOf(StorytellerState.REMINDERS.getOrDefault(id, List.of()))) {
                if (reminders.size() >= MAX_REMINDERS) break;
                String from = r.role().map(x -> x.getDisplayName()).orElse("");
                reminders.add(from.isEmpty() || from.equals(r.text()) ? r.text() : from + ": " + r.text());
            }
            o.add("reminders", reminders);
            seatList.add(o);
        }
        JsonArray bluffs = new JsonArray();
        for (ScriptRole bluff : List.copyOf(StorytellerState.DEMON_BLUFFS)) bluffs.add(bluff.getDisplayName());

        JsonObject g = new JsonObject();
        g.addProperty("script", ServerState.currentScript != null ? ServerState.currentScript.name() : "");
        g.addProperty("setup", setup);
        g.add("seats", seatList);
        g.add("bluffs", bluffs);
        return g;
    }

    /** For tests: which request ids are waiting for an answer. */
    static Set<String> announced() {
        return Set.copyOf(ANNOUNCED.keySet());
    }
}
