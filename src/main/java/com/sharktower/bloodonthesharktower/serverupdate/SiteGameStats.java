package com.sharktower.bloodonthesharktower.serverupdate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.command.BotsCommands;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.profile.PlayerProfiles;
import com.sharktower.bloodonthesharktower.snapshot.MatchSnapshotManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Finished games go to the Sharktower site, where they count in members'
 * stats and achievements like a Grim Poster game, and get a recap page
 * (phases, deaths, executions and votes from {@link GameRecap}). Sent when the Storyteller
 * ends the game (Good/Evil wins); Cancel End Game takes it back. Players link
 * their Minecraft account with {@code /link} (a code to enter on the site).
 *
 * The Storyteller decides: {@code /bots stats off} keeps this server's games
 * out of the stats until {@code /bots stats on}. Games with test seats are
 * never sent. Nothing is sent without the website link configured.
 */
public final class SiteGameStats {
    private static final Gson GSON = new Gson();
    private static volatile boolean enabled = true;
    /** The id the current game was sent under, so a cancelled end can be taken back. */
    private static String sentGameId;

    private SiteGameStats() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("link").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    link(context.getSource().getServer(), player);
                    return 1;
                })));
    }

    public static boolean enabled() {
        return enabled;
    }

    /** The Storyteller's switch. Returns a line to show them. */
    public static String setEnabled(boolean on) {
        enabled = on;
        if (!SiteLink.enabled()) return "Game stats " + (on ? "ON" : "OFF") + ", but this server has no website link set up.";
        return on ? "Game stats ON: finished games count in players' Sharktower stats and achievements."
                : "Game stats OFF: games on this server are not sent to the Sharktower site.";
    }

    /** End Game (first reveal): send the finished game. */
    public static void onGameEnd(MinecraftServer server, String winner) {
        // Forget the last game first: a game that isn't sent must not let Cancel End Game remove an older one.
        sentGameId = null;
        if (server == null || !enabled || !SiteLink.enabled()) return;
        if (!BotsCommands.syntheticTestNames().isEmpty()) return;
        if (com.sharktower.bloodonthesharktower.practice.PracticePlayers.anySeated()) {
            tellStorytellers(server, "Practice game: not sent to the Sharktower site.");
            return;
        }
        JsonObject game = build(server, winner);
        if (game == null) return;
        sentGameId = game.get("gameId").getAsString();
        SiteLink.post("/internal/minecraft/game", GSON.toJson(game), "game stats", reply ->
                server.execute(() -> tellStorytellers(server, "Game sent to the Sharktower site for stats and achievements."
                        + " Cancel End Game takes it back.")));
    }

    /** Cancel End Game: the game no longer counts. */
    public static void onEndCancelled() {
        String id = sentGameId;
        sentGameId = null;
        if (id == null || !SiteLink.enabled()) return;
        JsonObject body = new JsonObject();
        body.addProperty("gameId", id);
        SiteLink.post("/internal/minecraft/game/remove", GSON.toJson(body), "game stats removal", null);
    }

    public static JsonObject build(MinecraftServer server, String winner) {
        String side = winner == null ? "" : winner.toLowerCase(Locale.ROOT);
        if (!side.equals("good") && !side.equals("evil")) return null;
        Map<UUID, PendingRoleAssignment> start = MatchSnapshotManager.startingRoles();
        JsonArray players = new JsonArray();
        for (Map.Entry<UUID, Integer> seat : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
            PendingRoleAssignment role = ServerState.PLAYER_ROLES.get(seat.getKey());
            if (role == null || seat.getValue() == null || seat.getValue() <= 0) continue;
            JsonObject p = new JsonObject();
            p.addProperty("seat", seat.getValue());
            p.addProperty("uuid", seat.getKey().toString());
            p.addProperty("name", name(server, seat.getKey()));
            p.addProperty("roleId", roleId(role));
            p.addProperty("roleName", role.getDisplayName());
            p.addProperty("team", role.getRoleType().name().toLowerCase(Locale.ROOT));
            p.addProperty("alignment", role.isFinalGood() ? "good" : "evil");
            p.addProperty("dead", Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(seat.getKey())));
            PendingRoleAssignment first = start == null ? null : start.get(seat.getKey());
            if (first != null) p.addProperty("startRoleId", roleId(first));
            players.add(p);
        }
        if (players.isEmpty()) return null;
        JsonArray storytellers = new JsonArray();
        for (UUID id : StorytellerState.STORYTELLERS) {
            JsonObject s = new JsonObject();
            s.addProperty("uuid", id.toString());
            s.addProperty("name", name(server, id));
            storytellers.add(s);
        }
        JsonObject game = new JsonObject();
        game.addProperty("gameId", UUID.randomUUID().toString());
        game.addProperty("endedAt", System.currentTimeMillis());
        game.addProperty("script", ServerState.currentScript == null ? "Unknown script" : ServerState.currentScript.name());
        game.addProperty("winner", side);
        game.add("players", players);
        game.add("storytellers", storytellers);
        // The game's story for the website recap (and vote/death achievements).
        GameRecap.catchUp();
        game.add("timeline", GameRecap.timeline(ServerState.PLAYER_SEAT_NUMBERS));
        game.add("votes", GameRecap.votes(ServerState.PLAYER_SEAT_NUMBERS));
        return game;
    }

    /** Official ids match BOTC's (e.g. "fortuneteller"); custom roles keep their own id. */
    private static String roleId(PendingRoleAssignment role) {
        return role.getRoleId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static String name(MinecraftServer server, UUID id) {
        String name = PlayerProfiles.displayName(server, id);
        if (name == null || name.isBlank()) {
            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
            return seat == null ? "Storyteller" : "Seat " + seat;
        }
        return name;
    }

    // ------------------------------------------------------------------ /link

    private static void link(MinecraftServer server, ServerPlayer player) {
        if (!SiteLink.enabled()) {
            player.sendSystemMessage(Component.literal("This server is not linked to the Sharktower site.").withStyle(ChatFormatting.GRAY));
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUUID().toString());
        body.addProperty("name", player.getGameProfile().name());
        UUID id = player.getUUID();
        player.sendSystemMessage(Component.literal("Asking the Sharktower site for a link code...").withStyle(ChatFormatting.GRAY));
        SiteLink.post("/internal/minecraft/link-code", GSON.toJson(body), "link codes", reply -> {
            JsonObject json = GSON.fromJson(reply, JsonObject.class);
            String code = json.get("code").getAsString();
            String url = json.has("url") ? json.get("url").getAsString() : "";
            int minutes = json.has("minutes") ? json.get("minutes").getAsInt() : 10;
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) online.sendSystemMessage(linkMessage(code, url, minutes));
            });
        });
    }

    static Component linkMessage(String code, String url, int minutes) {
        Component codeText = Component.literal(code).withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(code))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
        var message = Component.literal("Your Sharktower link code: ").withStyle(ChatFormatting.YELLOW).append(codeText);
        if (url != null && url.startsWith("https://")) {
            message.append(Component.literal(". Enter it on ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("My stats").withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
        } else {
            message.append(Component.literal(". Enter it on the Sharktower site under My stats").withStyle(ChatFormatting.YELLOW));
        }
        return message.append(Component.literal(" within " + minutes + " minutes. Your games here then count in your stats and achievements.")
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void tellStorytellers(MinecraftServer server, String text) {
        Component message = Component.literal(text).withStyle(ChatFormatting.GRAY);
        for (UUID id : StorytellerState.STORYTELLERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(message);
        }
    }
}
