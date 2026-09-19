package com.sharktower.bloodonthesharktower.core;

import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.sound.ModSounds;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;

/**
 * Original BOTB-style phase presentation: world time, chat callouts and sounds.
 *
 * A.11 keeps the town clock frozen at a presentation time for each phase:
 * Setup/Day = midday (6000), Nominations = evening (13000), Night = 18000.
 * The clock is re-applied every server tick so vanilla time progression cannot
 * drift the map away from the intended phase lighting.
 */
public final class PhasePresentation {
    public static final long TIME_DAY = 6000L;
    public static final long TIME_NOMINATIONS = 13000L;
    public static final long TIME_DUSK = 18000L;

    private PhasePresentation() {}

    public static void dusk(MinecraftServer server, int night) {
        if (server == null) return;
        setOverworldClock(server, TIME_DUSK);
        announce(server, Component.literal("Night falls...").withStyle(ChatFormatting.DARK_PURPLE));
        announce(server, Component.literal("--- Night " + Math.max(1, night) + " ---")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        ModSounds.playForAll(server, ModSounds.DUSK);
    }

    public static void dawn(MinecraftServer server, int day) {
        if (server == null) return;
        // Day presentation now settles at midday rather than allowing the sun to advance.
        setOverworldClock(server, TIME_DAY);
        announce(server, Component.literal("Dawn breaks!").withStyle(ChatFormatting.GOLD));
        announce(server, Component.literal("--- Day " + Math.max(1, day) + " ---")
                .withStyle(ChatFormatting.YELLOW));
        ModSounds.playForAll(server, ModSounds.DAWN);
    }

    public static void nominationsOpen(MinecraftServer server) {
        if (server == null) return;
        setOverworldClock(server, TIME_NOMINATIONS);
        announce(server, Component.literal("Nominations are open!").withStyle(ChatFormatting.YELLOW));
        // The original mod used call_back here, not the individual nomination sting.
        ModSounds.playForAll(server, ModSounds.CALL_BACK);
    }

    public static void nomination(MinecraftServer server) {
        if (server == null) return;
        ModSounds.playForAll(server, ModSounds.NOMINATION);
    }

    /**
     * Keeps the world lighting locked to the current Clocktower phase.
     *
     * This intentionally uses only the already-authoritative server counters and
     * nominations flag, so it does not create a second game-state machine.
     */
    public static void serverTick(MinecraftServer server) {
        if (server == null) return;

        long targetTime;
        if (ServerState.currentNight == 0 && ServerState.currentDay == 0) {
            targetTime = TIME_DAY;
        } else if (ServerState.currentNight != ServerState.currentDay) {
            targetTime = TIME_DUSK;
        } else if (DaytimeState.areNominationsOpen()) {
            targetTime = TIME_NOMINATIONS;
        } else {
            targetTime = TIME_DAY;
        }

        setOverworldClock(server, targetTime);
    }

    /** Minecraft 26.3 stores day/night time on ServerClockManager. */
    private static void setOverworldClock(MinecraftServer server, long timeOfDay) {
        var level = server.overworld();
        var defaultClock = level.dimensionType().defaultClock();
        if (defaultClock.isEmpty()) return;

        Holder<WorldClock> clock = defaultClock.get();
        server.clockManager().setTotalTicks(clock, timeOfDay);
    }

    private static void announce(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
