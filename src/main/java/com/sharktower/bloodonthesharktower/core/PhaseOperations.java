package com.sharktower.bloodonthesharktower.core;

import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.minecraft.server.MinecraftServer;

/**
 * Authoritative Dusk/Dawn lifecycle for Blood on the Sharktower.
 *
 * Phase counters, daytime cleanup, state sync and Simple Voice Chat routing all
 * move together through this class. This keeps the real game path and the test
 * commands on the same implementation instead of allowing Night Chat to drift
 * away from the server's current phase.
 */
public final class PhaseOperations {
    private PhaseOperations() {}

    public static synchronized Result enterNight(MinecraftServer server) {
        if (server == null) return Result.fail("Server is not available.");
        if (ServerState.gameEnded) return Result.fail("The game has ended. Reset for the next game before starting Night.");

        boolean alreadyNight = isNight();
        if (!alreadyNight) {
            if (ServerState.currentNight == 0 && ServerState.currentDay == 0) {
                ServerState.currentNight = 1;
            } else {
                ServerState.currentNight = Math.max(ServerState.currentNight, ServerState.currentDay + 1);
            }

            // Nominations, an in-progress vote, marks and exile state are daytime
            // concepts. Ghost-vote history deliberately survives between days.
            DaytimeState.resetDaily();
            PhasePresentation.dusk(server, ServerState.currentNight);
        }

        NightChatManager.Result voice = NightChatManager.start();
        StateBroadcaster.broadcastCurrentState(server);

        if (!voice.ok()) {
            return Result.ok((alreadyNight ? "Night " + ServerState.currentNight + " remains active. "
                    : "Dusk complete — Night " + ServerState.currentNight + " started. ")
                    + "Night Chat is waiting for Simple Voice Chat and will reconcile automatically: "
                    + voice.message());
        }
        return Result.ok((alreadyNight ? "Night " + ServerState.currentNight + " resynced. "
                : "Dusk complete — Night " + ServerState.currentNight + " started. ") + voice.message());
    }

    public static synchronized Result enterDay(MinecraftServer server) {
        if (server == null) return Result.fail("Server is not available.");
        if (ServerState.gameEnded) return Result.fail("The game has ended. Reset for the next game before starting Day.");

        boolean alreadyDay = isDay();
        int day = Math.max(1, Math.max(ServerState.currentNight, ServerState.currentDay));
        ServerState.currentNight = day;
        ServerState.currentDay = day;

        if (!alreadyDay) {
            ServerState.executionToday = false;
            DaytimeState.resetDaily();
            StorytellerState.resetDailyNightInfo();
            PhasePresentation.dawn(server, day);
        }

        NightChatManager.Result voice = NightChatManager.stop();
        StateBroadcaster.broadcastCurrentState(server);

        if (!voice.ok()) {
            return Result.fail("Day " + day + " started, but Night Chat cleanup could not complete: "
                    + voice.message());
        }
        return Result.ok((alreadyDay ? "Day " + day + " resynced. "
                : "Dawn complete — Day " + day + " started. ") + voice.message());
    }

    /** Test-only lightweight setup phase switch; this is not a full game reset. */
    public static synchronized Result enterSetupForTest(MinecraftServer server) {
        if (server == null) return Result.fail("Server is not available.");
        ServerState.currentNight = 0;
        ServerState.currentDay = 0;
        ServerState.executionToday = false;
        DaytimeState.resetDaily();
        StorytellerState.resetDailyNightInfo();
        NightChatManager.Result voice = NightChatManager.stop();
        StateBroadcaster.broadcastCurrentState(server);
        return voice.ok()
                ? Result.ok("Returned to SETUP test phase. " + voice.message())
                : Result.fail("Returned to SETUP test phase, but voice cleanup failed: " + voice.message());
    }

    public static boolean isNight() {
        return !(ServerState.currentNight == 0 && ServerState.currentDay == 0)
                && ServerState.currentNight != ServerState.currentDay;
    }

    public static boolean isDay() {
        return ServerState.currentNight > 0 && ServerState.currentNight == ServerState.currentDay;
    }

    public record Result(boolean ok, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }
}
