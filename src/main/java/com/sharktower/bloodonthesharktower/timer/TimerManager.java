package com.sharktower.bloodonthesharktower.timer;

import com.sharktower.bloodonthesharktower.networking.TimerStateS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Logical 26.2 port of BOTB TimerManager.
 * Boss-bar rendering/daylight interpolation are deferred to the UI/world batch.
 */
public final class TimerManager {
    private static Timer updateTimer;
    private static int remainingSeconds;
    private static int totalSeconds;
    private static boolean active;
    private static boolean paused;
    private static boolean syncDaylight;

    private TimerManager() {}

    public static synchronized void startTimer(MinecraftServer server, int seconds, boolean shouldSyncDaylight) {
        stopInternal();
        totalSeconds = Math.max(1, seconds);
        remainingSeconds = totalSeconds;
        active = true;
        paused = false;
        syncDaylight = shouldSyncDaylight;
        broadcastTimerState(server);

        updateTimer = new Timer("bots-timer", true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                server.execute(() -> tick(server));
            }
        }, 1000L, 1000L);
    }

    public static synchronized void pauseTimer(MinecraftServer server) {
        if (!active) return;
        paused = true;
        broadcastTimerState(server);
    }

    public static synchronized void resumeTimer(MinecraftServer server) {
        if (!active) return;
        paused = false;
        broadcastTimerState(server);
    }

    public static synchronized void stopTimer(MinecraftServer server) {
        stopInternal();
        broadcastTimerState(server);
    }

    private static synchronized void tick(MinecraftServer server) {
        if (!active || paused) return;
        if (remainingSeconds > 0) remainingSeconds--;
        if (remainingSeconds <= 0) {
            stopInternal();
            broadcastTimerState(server, true);
            return;
        }
        broadcastTimerState(server, false);
    }

    private static void stopInternal() {
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer = null;
        }
        active = false;
        paused = false;
        remainingSeconds = 0;
        syncDaylight = false;
    }

    public static void broadcastTimerState(MinecraftServer server) {
        broadcastTimerState(server, false);
    }

    private static void broadcastTimerState(MinecraftServer server, boolean completedNaturally) {
        TimerStateS2CPayload payload = new TimerStateS2CPayload(
                active, paused, remainingSeconds, totalSeconds, completedNaturally);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void addPlayer(ServerPlayer player) {
        ServerPlayNetworking.send(player, new TimerStateS2CPayload(
                active, paused, remainingSeconds, totalSeconds, false));
    }

    public static void removePlayer(ServerPlayer player) {
        // Boss-bar membership returns in the UI batch. Logical timer is global.
    }

    public static boolean isActive() { return active; }
    public static boolean isPaused() { return paused; }
    public static int getRemainingSeconds() { return remainingSeconds; }
    public static int getTotalSeconds() { return totalSeconds; }
    public static boolean isSyncDaylight() { return syncDaylight; }
}
