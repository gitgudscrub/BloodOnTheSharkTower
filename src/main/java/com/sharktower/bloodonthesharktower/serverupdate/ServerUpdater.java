package com.sharktower.bloodonthesharktower.serverupdate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.networking.VersionCheck;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Updates this server to the latest auto-update pack, started from the console
 * ({@code /serverupdate now|aftergame}), which the Sharktower website sends
 * through AMP.
 *
 * <p>Steps: optionally wait for the current game to end; run packwiz-installer
 * in server mode on the server folder (it skips client-only mods); move aside
 * duplicate old JARs ({@link ModDedupe}); warn players for 60 seconds (skipped
 * when nobody is online); then report READY. The website sees READY and restarts the server through AMP.
 * The new files only load on that restart.
 *
 * <p>A pack that needs a different Minecraft version is refused: that change
 * is made in AMP by hand.
 *
 * <p>Timing runs on its own once-a-second clock, not server ticks: a dedicated
 * server stops ticking a minute after the last player leaves.
 */
public final class ServerUpdater {
    public enum State { IDLE, WAITING, STAGING, COUNTDOWN, READY, ERROR }

    private static final String BOOTSTRAP_URL =
            "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar";
    private static final String BOOTSTRAP_SHA256 = "a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c";
    private static final int COUNTDOWN_SECONDS = 60;
    private static final int STATUS_EVERY_SECONDS = 30;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile State state = State.IDLE;
    private static volatile String target = "";
    private static volatile String message = "";
    private static volatile Instant changedAt = Instant.now();
    private static volatile Instant countdownEnd = Instant.EPOCH;
    private static int lastAnnounced;
    private static int statusSeconds;
    private static volatile MinecraftServer server;
    private static ScheduledExecutorService clock;

    private ServerUpdater() {}

    public static void register() {
        SiteLink.load();
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            clock = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Sharktower update clock");
                t.setDaemon(true);
                return t;
            });
            clock.scheduleAtFixedRate(ServerUpdater::everySecond, 1, 1, TimeUnit.SECONDS);
            sendStatus();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            server = null;
            if (clock != null) clock.shutdownNow();
        });
    }

    public static State state() {
        return state;
    }

    public static String describe() {
        String base = "Server update: " + state.name().toLowerCase();
        if (!target.isEmpty()) base += " (" + target + ")";
        return message.isEmpty() ? base : base + ". " + message;
    }

    /** Start an update; {@code afterGame} waits for the current game to end first. */
    public static String start(boolean afterGame) {
        if (state == State.WAITING || state == State.STAGING || state == State.COUNTDOWN) {
            return "An update is already under way: " + describe();
        }
        if (afterGame && StorytellerState.gameInProgress()) {
            set(State.WAITING, "", "Waiting for the current game to end.");
        } else {
            beginStaging();
        }
        return describe();
    }

    public static String cancel() {
        if (state == State.STAGING) {
            return "Downloading the update now; it cannot be cancelled. The server will not restart until the countdown ends.";
        }
        if (state == State.WAITING || state == State.COUNTDOWN || state == State.READY || state == State.ERROR) {
            boolean announced = state == State.COUNTDOWN || state == State.READY;
            set(State.IDLE, "", "Cancelled. Staged files (if any) load on the next restart.");
            if (announced) broadcast("Server restart cancelled.", ChatFormatting.GREEN);
        }
        return describe();
    }

    /** Update clock thread. Game state is only read here, never changed. */
    private static void everySecond() {
        try {
            MinecraftServer s = server;
            if (s == null) return;
            if (state == State.WAITING && !StorytellerState.gameInProgress()) {
                beginStaging();
            }
            if (state == State.COUNTDOWN) {
                long seconds = Math.max(0, Duration.between(Instant.now(), countdownEnd).toSeconds());
                if (s.getPlayerCount() == 0 || seconds == 0) {
                    set(State.READY, target, "Waiting for the restart.");
                    broadcast("Server restarting now to update to " + target + ". Relaunch your game to update too.",
                            ChatFormatting.GOLD);
                } else if (seconds != lastAnnounced && (seconds == 30 || seconds == 10 || seconds <= 5)) {
                    lastAnnounced = (int) seconds;
                    broadcast("Server restarting to update in " + seconds + "s.", ChatFormatting.GOLD);
                }
            }
            if (++statusSeconds >= STATUS_EVERY_SECONDS) {
                statusSeconds = 0;
                sendStatus();
            }
        } catch (RuntimeException e) {
            // A racy read of game state; the next second tries again.
            BloodOnTheSharktower.LOGGER.debug("Update clock skipped a second", e);
        }
    }

    private static void beginStaging() {
        set(State.STAGING, "", "Downloading the update.");
        Thread thread = new Thread(ServerUpdater::stage, "Sharktower server update");
        thread.setDaemon(true);
        thread.start();
    }

    /** Runs off the server thread. */
    private static void stage() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
            PackInfo pack = PackInfo.parse(fetch(SiteLink.packUrl()));
            String running = VersionCheck.modVersion();
            String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
            if (!pack.minecraft().equals(minecraft)) {
                fail(pack.version(), "The pack needs Minecraft " + pack.minecraft() + " but the server runs "
                        + minecraft + ". Change the Minecraft version in AMP first.");
                return;
            }
            if (pack.version().equals(running)) {
                set(State.IDLE, pack.version(), "Already on the latest version.");
                return;
            }

            Path work = gameDir.resolve(".sharktower-update");
            Files.createDirectories(work);
            Path bootstrap = work.resolve("packwiz-installer-bootstrap.jar");
            ensureBootstrap(bootstrap);

            String java = ProcessHandle.current().info().command().orElse("java");
            ProcessBuilder builder = new ProcessBuilder(java, "-jar", bootstrap.toString(),
                    "-g", "-s", "server", "--pack-folder", gameDir.toString(), SiteLink.packUrl())
                    .directory(work.toFile())
                    .redirectErrorStream(true);
            Process process = builder.start();
            Deque<String> tail = new ArrayDeque<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    BloodOnTheSharktower.LOGGER.info("[packwiz] {}", line);
                    tail.addLast(line);
                    if (tail.size() > 3) tail.removeFirst();
                }
            }
            if (!process.waitFor(10, TimeUnit.MINUTES) || process.exitValue() != 0) {
                process.destroyForcibly();
                fail(pack.version(), "packwiz-installer failed: " + String.join(" / ", tail));
                return;
            }

            List<String> moved = ModDedupe.run(gameDir, work.resolve("old-mods"));
            if (!moved.isEmpty()) {
                BloodOnTheSharktower.LOGGER.info("Moved old duplicate mods to .sharktower-update/old-mods: {}", moved);
            }
            countdownEnd = Instant.now().plusSeconds(COUNTDOWN_SECONDS);
            lastAnnounced = COUNTDOWN_SECONDS;
            set(State.COUNTDOWN, pack.version(), "Update downloaded; restarting after a " + COUNTDOWN_SECONDS + "s warning.");
            broadcast("Server restarting to update to " + pack.version() + " in " + COUNTDOWN_SECONDS + "s.",
                    ChatFormatting.GOLD);
        } catch (Exception e) {
            BloodOnTheSharktower.LOGGER.error("Server update failed", e);
            fail(target, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void ensureBootstrap(Path bootstrap) throws IOException, InterruptedException {
        if (Files.isRegularFile(bootstrap) && BOOTSTRAP_SHA256.equals(sha256(bootstrap))) return;
        Path temp = bootstrap.resolveSibling("packwiz-installer-bootstrap.jar.download");
        HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(URI.create(BOOTSTRAP_URL))
                .timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("bootstrap download: HTTP " + response.statusCode());
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!BOOTSTRAP_SHA256.equals(sha256(temp))) {
            Files.deleteIfExists(temp);
            throw new IOException("bootstrap download did not match its pinned SHA-256");
        }
        Files.move(temp, bootstrap, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String fetch(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Cache-Control", "no-cache")
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException(url + ": HTTP " + response.statusCode());
        return response.body();
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void fail(String version, String why) {
        set(State.ERROR, version == null ? "" : version, why);
    }

    private static void set(State next, String version, String why) {
        state = next;
        target = version == null ? "" : version;
        message = why;
        changedAt = Instant.now();
        BloodOnTheSharktower.LOGGER.info(describe());
        sendStatus();
    }

    private static void broadcast(String text, ChatFormatting colour) {
        MinecraftServer s = server;
        if (s == null) return;
        s.execute(() -> s.getPlayerList().broadcastSystemMessage(
                Component.literal("[Sharktower] " + text).withStyle(colour), false));
    }

    /**
     * Snapshot and send; the HTTP call is async. Runs on the update clock or
     * the staging thread and only reads: a paused, empty server never runs
     * queued tasks, and the report must still go out.
     */
    private static synchronized void sendStatus() {
        MinecraftServer s = server;
        if (s == null || !SiteLink.enabled()) return;
        JsonObject status = new JsonObject();
        status.addProperty("version", VersionCheck.modVersion());
        status.addProperty("minecraft", FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        JsonArray names = new JsonArray();
        for (ServerPlayer player : List.copyOf(s.getPlayerList().getPlayers())) names.add(player.getGameProfile().name());
        status.addProperty("players", names.size());
        status.add("playerNames", names);
        boolean inGame;
        try {
            inGame = StorytellerState.gameInProgress();
        } catch (RuntimeException e) {
            inGame = true; // changed mid-read: report busy, the next report corrects it
        }
        status.addProperty("gameInProgress", inGame);
        status.addProperty("day", ServerState.currentDay);
        status.addProperty("night", ServerState.currentNight);
        JsonObject update = new JsonObject();
        update.addProperty("state", state.name().toLowerCase());
        update.addProperty("target", target);
        update.addProperty("message", message);
        update.addProperty("at", changedAt.toString());
        status.add("update", update);
        SiteLink.sendStatus(status);
    }
}
