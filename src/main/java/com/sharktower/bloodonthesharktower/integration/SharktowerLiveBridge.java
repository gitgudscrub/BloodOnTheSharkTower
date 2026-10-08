package com.sharktower.bloodonthesharktower.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.states.DeathVisibility;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional server-only, outbound publisher for the external Sharktower Live website.
 * Sends an explicit allowlist of PUBLIC state; never serializes roles, alignments,
 * Storyteller reminders, secret night actions, or Grimoire data.
 *
 * Configure with config/sharktower-live-bridge.properties in the server instance:
 * enabled=true
 * url=https://spectator.example.com/api/bridge/state
 * token=<same random BRIDGE_TOKEN as the website>
 *
 * An AMP/Docker Minecraft instance cannot normally reach the Linux host using
 * 127.0.0.1. The HTTPS Cloudflare hostname also works as an outbound URL.
 */
public final class SharktowerLiveBridge {
    private static final int SNAPSHOT_INTERVAL_TICKS = 10; // Detect public-state changes about twice per second
    private static final long UNCHANGED_HEARTBEAT_MS = 5_000L;
    private static final long RETRY_DELAY_MS = 2_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private static final AtomicBoolean CONNECTED = new AtomicBoolean();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static volatile Settings settings;
    private static volatile long lastWarningMs;
    private static volatile long lastSuccessfulSendMs;
    private static volatile String lastSuccessfulPayload;
    private static long lastAttemptMs;
    private static int ticks;

    private SharktowerLiveBridge() {}

    public static void tick(MinecraftServer server) {
        // Tick callback runs on Minecraft's main thread. HTTP is always asynchronous.
        if (server == null) return;
        if (settings == null) settings = readSettings();
        Settings cfg = settings;
        if (!cfg.enabled() || ++ticks % SNAPSHOT_INTERVAL_TICKS != 0) return;
        if (IN_FLIGHT.get()) return;

        String body;
        try {
            body = makePublicSnapshot(server).toString();
        } catch (Exception e) {
            warnRateLimited("Unable to build spectator snapshot: " + e.getClass().getSimpleName());
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttemptMs < RETRY_DELAY_MS && lastAttemptMs > lastSuccessfulSendMs) return;
        if (body.equals(lastSuccessfulPayload)
                && now - lastSuccessfulSendMs < UNCHANGED_HEARTBEAT_MS) return;
        if (!IN_FLIGHT.compareAndSet(false, true)) return;
        lastAttemptMs = now;

        HttpRequest request = HttpRequest.newBuilder(cfg.uri())
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.token())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        CONNECTED.set(false);
                        warnRateLimited("Spectator bridge connection failed (" + error.getClass().getSimpleName() + ").");
                    } else if (response.statusCode() != 200) {
                        CONNECTED.set(false);
                        warnRateLimited("Spectator bridge returned HTTP " + response.statusCode() + ".");
                    } else {
                        lastSuccessfulPayload = body;
                        lastSuccessfulSendMs = System.currentTimeMillis();
                        if (CONNECTED.compareAndSet(false, true)) {
                            BloodOnTheSharktower.LOGGER.info("Sharktower Live bridge connected successfully (HTTP 200).");
                        }
                    }
                    IN_FLIGHT.set(false);
                });
    }

    private static JsonObject makePublicSnapshot(MinecraftServer server) {
        JsonObject snapshot = new JsonObject();
        // During SETUP, seating changes are held in the Storyteller's pending
        // seat map until roles are committed. Match the normal client state sync.
        boolean setup = ServerState.currentNight == 0
                && ServerState.currentDay == 0 && !ServerState.gameEnded;
        Map<UUID, Integer> seats = setup
                ? StorytellerState.effectiveGrimoireSeats()
                : ServerState.PLAYER_SEAT_NUMBERS;
        boolean active = !ServerState.gameEnded
                && (!seats.isEmpty() || ServerState.currentNight > 0 || ServerState.currentDay > 0);
        String phase = ServerState.gameEnded ? "ended"
                : ServerState.currentDay <= 0 && ServerState.currentNight <= 0 ? "setup"
                : ServerState.currentNight != ServerState.currentDay ? "night" : "day";
        snapshot.addProperty("live", active);
        snapshot.addProperty("gameId", "sharktower-" + ServerState.resetGeneration);
        snapshot.addProperty("phase", phase);
        snapshot.addProperty("day", Math.max(0, ServerState.currentDay));
        snapshot.addProperty("night", Math.max(0, ServerState.currentNight));

        JsonArray players = new JsonArray();
        JsonArray conversations = new JsonArray();
        JsonArray storytellers = new JsonArray();
        // Storyteller identity is already public in the in-game player directory.
        // Publish only online assigned Storytellers, never their private activity.
        List<ServerPlayer> activeStorytellers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StorytellerState.isStoryteller(player.getUUID())) activeStorytellers.add(player);
        }
        activeStorytellers.sort(Comparator.comparing(
                player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        for (ServerPlayer storyteller : activeStorytellers) {
            JsonObject identity = new JsonObject();
            identity.addProperty("id", storyteller.getUUID().toString());
            identity.addProperty("name", storyteller.getName().getString());
            storytellers.add(identity);
        }
        snapshot.add("storytellers", storytellers);
        if (!active) {
            snapshot.add("players", players);
            snapshot.add("conversations", conversations);
            return snapshot;
        }

        Map<UUID, Boolean> publicDeaths = DeathVisibility.visible(
                ServerState.PLAYER_DEATH_STATUS, false);

        // Only seated, online non-Storytellers. Never expose Storyteller presence
        // in confidential chats, even through the conversation membership list.
        List<ServerPlayer> seated = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (seats.containsKey(id)
                    && !StorytellerState.isStoryteller(id)) seated.add(player);
        }
        seated.sort(Comparator.comparingInt(p ->
                seats.getOrDefault(p.getUUID(), Integer.MAX_VALUE)));

        Map<String, List<String>> roomMembers = new HashMap<>();
        if ("day".equals(phase)) roomMembers.put("town-square", new ArrayList<>());
        for (ServerPlayer player : seated) {
            UUID id = player.getUUID();
            String route = NightChatManager.routeCode(id);
            String group = null;
            if ("day".equals(phase)) {
                if ("DAY_SHARED".equals(route)) group = "town-square";
                else if (route != null && route.startsWith("DAY_ZONE:")) {
                    group = "zone-" + route.substring("DAY_ZONE:".length());
                }
                // ST private, house and unconnected routes are excluded, not
                // mistakenly classified as Town Square.
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("name", player.getName().getString());
            entry.addProperty("alive", !Boolean.TRUE.equals(publicDeaths.get(id)));
            if (group == null) entry.add("chatGroup", com.google.gson.JsonNull.INSTANCE);
            else entry.addProperty("chatGroup", group);
            players.add(entry);
            if (group != null) roomMembers.computeIfAbsent(group, key -> new ArrayList<>())
                    .add(id.toString());
        }
        roomMembers.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(room -> {
            JsonObject conversation = new JsonObject();
            conversation.addProperty("id", room.getKey());
            conversation.addProperty("name", "town-square".equals(room.getKey())
                    ? "Town Square" : "Private Chat: " + room.getKey().substring(5));
            JsonArray ids = new JsonArray();
            for (String id : room.getValue()) ids.add(id);
            conversation.add("playerIds", ids);
            conversations.add(conversation);
        });

        snapshot.add("players", players);
        snapshot.add("conversations", conversations);
        return snapshot;
    }

    private static Settings readSettings() {
        Properties properties = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("sharktower-live-bridge.properties");
        if (Files.isRegularFile(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException e) {
                BloodOnTheSharktower.LOGGER.warn(
                        "Sharktower Live bridge configuration could not be read ({}). Disabled.", e.getClass().getSimpleName());
                return Settings.disabled();
            }
        }
        String enabled = setting("SHARKTOWER_LIVE_BRIDGE_ENABLED", properties, "enabled");
        if (!Boolean.parseBoolean(enabled)) return Settings.disabled();

        String target = setting("SHARKTOWER_LIVE_BRIDGE_URL", properties, "url");
        String token = setting("SHARKTOWER_LIVE_BRIDGE_TOKEN", properties, "token");
        if (target == null || token == null || token.length() < 32
                || token.contains("\r") || token.contains("\n")) {
            BloodOnTheSharktower.LOGGER.warn("Sharktower Live bridge enabled but URL/token configuration is invalid; disabled.");
            return Settings.disabled();
        }
        try {
            URI uri = URI.create(target.trim());
            boolean isHttps = "https".equalsIgnoreCase(uri.getScheme());
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
            if ((!isHttps && !localHttp) || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getRawQuery() != null || !"/api/bridge/state".equals(uri.getPath())) {
                throw new IllegalArgumentException("Invalid bridge endpoint");
            }
            BloodOnTheSharktower.LOGGER.info("Sharktower Live public-state bridge enabled (no audio or Grimoire data).");
            return new Settings(true, uri, token);
        } catch (IllegalArgumentException e) {
            BloodOnTheSharktower.LOGGER.warn("Sharktower Live bridge URL invalid; use HTTPS or local loopback HTTP and /api/bridge/state. Disabled.");
            return Settings.disabled();
        }
    }

    private static String setting(String env, Properties properties, String key) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = properties.getProperty(key);
        return value == null ? null : value.trim();
    }

    private static void warnRateLimited(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarningMs < 60_000L) return;
        lastWarningMs = now;
        BloodOnTheSharktower.LOGGER.warn(message);
    }

    private record Settings(boolean enabled, URI uri, String token) {
        static Settings disabled() { return new Settings(false, null, null); }
    }
}
