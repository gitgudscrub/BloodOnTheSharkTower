package com.sharktower.bloodonthesharktower.integration;

import com.google.gson.Gson;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.CustomRole;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side bridge to the standalone Blood on the Sharktower Grimoire Renderer.
 *
 * The renderer is deliberately not a gameplay dependency: every request is
 * asynchronous and failures are logged without changing game state.
 *
 * Configuration:
 * - BOTS_RENDERER_URL (or -Dblood_on_the_sharktower.rendererUrl)
 * - BOTS_RENDERER_TOKEN (or -Dblood_on_the_sharktower.rendererToken)
 *
 * The default renderer URL is the local service used by the Sharktower server:
 * http://127.0.0.1:8767
 *
 * Set BOTS_RENDERER_URL=off to disable the bridge.
 */
public final class GrimoireRendererBridge {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String DEFAULT_RENDERER_URL = "http://127.0.0.1:8767";

    private GrimoireRendererBridge() {}

    /** Pre-fetch/cache all role art as soon as a script is loaded. */
    public static void preloadScriptAsync(Script script) {
        if (script == null) return;
        postAsync(
                "/icons/preload",
                buildScriptIconPayload(script),
                "script preload for '" + script.name() + "'"
        );
    }

    /** Lightweight game-start verification; this does not request downloads. */
    public static void checkScriptAsync(Script script) {
        if (script == null) return;
        postAsync(
                "/icons/check",
                buildScriptIconPayload(script),
                "game-start icon check for '" + script.name() + "'"
        );
    }

    /** Send the authoritative final game state to the renderer. */
    public static void renderFinalGrimoireAsync(MinecraftServer server, String winningTeam) {
        if (server == null || ServerState.currentScript == null) return;
        postAsync(
                "/render",
                buildFinalGamePayload(server, winningTeam),
                "final Grimoire render"
        );
    }

    private static Map<String, Object> buildScriptIconPayload(Script script) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("script", script.name());

        List<Map<String, Object>> roles = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (ScriptRole role : script.allRoles()) {
            addScriptRole(roles, seen, role);
        }
        for (ScriptRole role : script.fabled()) {
            addScriptRole(roles, seen, role);
        }
        for (ScriptRole role : script.loric()) {
            addScriptRole(roles, seen, role);
        }

        root.put("roles", roles);
        return root;
    }

    private static void addScriptRole(
            List<Map<String, Object>> roles,
            Set<String> seen,
            ScriptRole role
    ) {
        if (role == null || !seen.add(role.getId().toLowerCase(Locale.ROOT))) return;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("role", role.getDisplayName());
        entry.put("team", rendererTeam(role.getTeam()));
        entry.put("alignment", role.isDefaultGood() ? "good" : "evil");

        if (role instanceof ScriptRole.Custom custom) {
            putImages(entry, "image", custom.customRole());
        } else if (role instanceof ScriptRole.Fabled fabled) {
            String image = fabled.fabledCharacter().imageUrl();
            if (image != null && !image.isBlank()) entry.put("image", image);
        }

        roles.add(entry);
    }

    private static Map<String, Object> buildFinalGamePayload(
            MinecraftServer server,
            String winningTeam
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("game_id", UUID.randomUUID().toString());
        root.put("winner", winningTeam == null ? "" : winningTeam.toUpperCase(Locale.ROOT));
        root.put("script", ServerState.currentScript.name());
        root.put("storyteller", storytellerNames(server));

        List<Map<String, Object>> players = ServerState.PLAYER_SEAT_NUMBERS.entrySet().stream()
                .sorted(Comparator.comparingInt(entry ->
                        entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()))
                .map(entry -> playerPayload(server, entry.getKey(), entry.getValue()))
                .filter(java.util.Objects::nonNull)
                .toList();
        root.put("players", players);
        return root;
    }

    private static Map<String, Object> playerPayload(
            MinecraftServer server,
            UUID playerId,
            Integer seatNumber
    ) {
        PendingRoleAssignment assignment = ServerState.PLAYER_ROLES.get(playerId);
        if (assignment == null) return null;
        assignment = assignment.resolveCustomRole(ServerState.currentScript);

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("seat", seatNumber == null ? 0 : seatNumber);
        player.put("name", playerName(server, playerId));
        player.put("role", assignment.getDisplayName());
        player.put("team", rendererTeam(assignment.getRoleType()));
        player.put("alignment", assignment.isFinalGood() ? "good" : "evil");
        player.put("alive", !ServerState.PLAYER_DEATH_STATUS.getOrDefault(playerId, false));

        if (assignment.isCustomRole()) {
            assignment.customRole().ifPresent(custom -> putImages(player, "image", custom));
        }

        PendingRoleAssignment perceived = ServerState.PLAYER_PERCEIVED_ROLES.get(playerId);
        if (perceived != null) {
            perceived = perceived.resolveCustomRole(ServerState.currentScript);
            player.put("believed_role", perceived.getDisplayName());
            if (perceived.isCustomRole()) {
                perceived.customRole().ifPresent(custom -> putImages(player, "believed_image", custom));
            }
        }

        List<String> reminders = StorytellerState.REMINDERS
                .getOrDefault(playerId, List.of())
                .stream()
                .map(Reminder::text)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        if (!reminders.isEmpty()) player.put("reminders", reminders);

        return player;
    }

    private static void putImages(Map<String, Object> target, String key, CustomRole role) {
        if (role == null || role.imageUrls().isEmpty()) return;
        if (role.imageUrls().size() == 1) {
            target.put(key, role.imageUrls().getFirst());
        } else {
            target.put(key, role.imageUrls());
        }
        target.put("homebrew_source", "script");
    }

    private static String storytellerNames(MinecraftServer server) {
        List<String> names = StorytellerState.STORYTELLERS.stream()
                .map(server.getPlayerList()::getPlayer)
                .filter(java.util.Objects::nonNull)
                .map(player -> player.getName().getString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return names.isEmpty() ? "Storyteller" : String.join(" + ", names);
    }

    private static String playerName(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player == null ? playerId.toString().substring(0, 8) : player.getName().getString();
    }

    private static String rendererTeam(RoleType type) {
        if (type == null) return "unknown";
        return switch (type) {
            case TOWNSFOLK -> "townsfolk";
            case OUTSIDER -> "outsider";
            case MINION -> "minion";
            case DEMON -> "demon";
            case TRAVELER -> "traveller";
            case FABLED -> "fabled";
            case LORIC -> "loric";
            case NONE -> "unknown";
        };
    }

    private static void postAsync(String path, Map<String, Object> payload, String description) {
        String baseUrl = rendererBaseUrl();
        if (baseUrl == null) return;

        URI endpoint;
        try {
            endpoint = URI.create(baseUrl + path);
        } catch (IllegalArgumentException exception) {
            BloodOnTheSharktower.LOGGER.warn(
                    "Grimoire renderer {} skipped: invalid renderer URL '{}'.",
                    description,
                    baseUrl
            );
            return;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)));

        String token = rendererToken();
        if (token != null) builder.header("X-Renderer-Token", token);

        HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, failure) -> {
                    if (failure != null) {
                        BloodOnTheSharktower.LOGGER.warn(
                                "Grimoire renderer {} could not reach {}: {}",
                                description,
                                endpoint,
                                failure.getMessage()
                        );
                        return;
                    }

                    String body = response.body() == null ? "" : response.body().replaceAll("\\s+", " ").trim();
                    if (body.length() > 500) body = body.substring(0, 500) + "...";

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        BloodOnTheSharktower.LOGGER.info(
                                "Grimoire renderer {} succeeded (HTTP {}): {}",
                                description,
                                response.statusCode(),
                                body
                        );
                    } else {
                        BloodOnTheSharktower.LOGGER.warn(
                                "Grimoire renderer {} failed (HTTP {}): {}",
                                description,
                                response.statusCode(),
                                body
                        );
                    }
                });
    }

    private static String rendererBaseUrl() {
        String configured = firstNonBlank(
                System.getProperty("blood_on_the_sharktower.rendererUrl"),
                System.getenv("BOTS_RENDERER_URL")
        );
        String value = configured == null ? DEFAULT_RENDERER_URL : configured.trim();
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("disabled")) return null;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String rendererToken() {
        return firstNonBlank(
                System.getProperty("blood_on_the_sharktower.rendererToken"),
                System.getenv("BOTS_RENDERER_TOKEN")
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
