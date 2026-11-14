package com.sharktower.bloodonthesharktower.serverupdate;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Server-only link to the Sharktower website, which shows this server's status
 * to the owner and restarts it through AMP once an update is staged.
 *
 * <p>Configured by {@code config/blood_on_the_sharktower/site-link.json} on the
 * server (never shipped in the pack):
 * <pre>{"url": "http://127.0.0.1:18080", "key": "&lt;MINECRAFT_LINK_KEY&gt;"}</pre>
 * Optional {@code "packUrl"} overrides where the auto-update pack is read
 * from. Without the file the link is off; {@code /serverupdate} still works.
 */
public final class SiteLink {
    public static final String DEFAULT_PACK_URL =
            "https://raw.githubusercontent.com/gitgudscrub/BloodOnTheSharkTower/pack/pack.toml";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static String url;
    private static String key;
    private static String packUrl = DEFAULT_PACK_URL;
    private static volatile boolean warned;

    private SiteLink() {}

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(BloodOnTheSharktower.MOD_ID).resolve("site-link.json");
    }

    public static void load() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            BloodOnTheSharktower.LOGGER.info("Website link off (no {}).", file);
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            String u = json.has("url") ? json.get("url").getAsString().trim() : "";
            String k = json.has("key") ? json.get("key").getAsString().trim() : "";
            if (json.has("packUrl") && !json.get("packUrl").getAsString().isBlank()) {
                packUrl = json.get("packUrl").getAsString().trim();
            }
            if (u.isEmpty() || k.length() < 32) {
                BloodOnTheSharktower.LOGGER.warn("Website link off: {} needs a url and a key of 32+ characters.", file);
                return;
            }
            url = u.replaceAll("/+$", "");
            key = k;
            BloodOnTheSharktower.LOGGER.info("Website link on: reporting server status to {}.", url);
        } catch (IOException | RuntimeException e) {
            BloodOnTheSharktower.LOGGER.warn("Website link off: could not read {}: {}", file, e.toString());
        }
    }

    public static boolean enabled() {
        return url != null;
    }

    public static String packUrl() {
        return packUrl;
    }

    /** Fire-and-forget POST of a status snapshot; never blocks the server thread. */
    public static void sendStatus(JsonObject status) {
        if (!enabled()) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/internal/minecraft/status"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Sharktower-Minecraft-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(status)))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            boolean failed = error != null || response.statusCode() >= 300;
            if (failed && !warned) {
                warned = true;
                BloodOnTheSharktower.LOGGER.warn("Website link: status report failed ({}). Further failures are not logged.",
                        error != null ? error.toString() : "HTTP " + response.statusCode());
            } else if (!failed && warned) {
                warned = false;
                BloodOnTheSharktower.LOGGER.info("Website link: status reports are getting through again.");
            }
        });
    }
}
