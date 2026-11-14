package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * House visits: a night chooser (Poisoner, Monk, Imp, a Ravenkeeper killed
 * tonight) sleeps until the Storyteller comes to their house, then wakes and
 * may use their ability. Until then the player cannot choose.
 *
 * The Storyteller decides: house visits can be switched off (the Grimoire's
 * "House visits" toggle), and every sleeping player's Resolve card offers
 * <b>Wake now</b>, <b>Choose seat</b> (choose for them) and <b>I'll handle it</b>.
 * Without a house set for the seat, the player wakes at once as before.
 */
public final class HouseVisits {
    /** Same distance at which the Storyteller joins the house voice room. */
    static final double ARRIVE_RADIUS = 8.0D;
    private static final int CHECK_TICKS = 5;

    private record Sleeper(String promptId, Runnable wake) {}

    private static final Map<UUID, Sleeper> ASLEEP = new LinkedHashMap<>();
    /** Sleepers the Storyteller chose for; they are not woken and cannot change it. */
    private static final java.util.Set<UUID> CHOSEN_FOR = new java.util.HashSet<>();
    private static boolean enabled = true;
    private static int ticks;

    /** What a sleeping player is told if they try to choose. */
    static final String ASLEEP_REFUSAL = "You are asleep. You can use your ability when the Storyteller visits your house.";

    private HouseVisits() {}

    public static boolean enabled() {
        return enabled;
    }

    /** One line for the Storyteller after a toggle. */
    public static String describe() {
        return enabled
                ? "House visits ON: night choosers wake when you reach their house. Resolve can still wake or choose for them."
                : "House visits OFF: night choosers are woken at dusk.";
    }

    /** Turning visits off wakes everyone still asleep, so nobody is stuck. */
    public static void setEnabled(MinecraftServer server, boolean on) {
        enabled = on;
        if (!on) {
            for (UUID player : List.copyOf(ASLEEP.keySet())) wake(server, player);
        }
        if (server != null) StateBroadcaster.broadcastPrompts(server);
    }

    /**
     * Runs {@code wake} when a Storyteller reaches {@code player}'s house, or
     * at once when visits are off or the seat has no house. Meanwhile the
     * Storyteller's Resolve card {@code promptId} shows them asleep.
     */
    static void wakeOnVisit(MinecraftServer server, UUID player, String promptId, String title,
                            String chooseCommand, String dismissCommand, Runnable wake) {
        cancel(player);
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(player);
        if (!enabled || server == null || seat == null || SeatPositionManager.seatHome(seat) == null) {
            wake.run();
            return;
        }
        ASLEEP.put(player, new Sleeper(promptId, wake));
        String who = RoleAutomation.label(server, player);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(Component.literal("The Storyteller will visit your house tonight. Stay home: "
                    + "you can use your ability when they arrive.").withStyle(ChatFormatting.GRAY));
        }
        RoleAutomation.offer(server, new StorytellerPrompt(promptId, title,
                who + " (seat " + seat + ") is asleep. Visit their house to wake them"
                        + (online == null ? ". They are offline: choose the seat for them." : "."),
                List.of(StorytellerPrompt.Action.ok("Wake now", "/bots automation wake " + seat),
                        StorytellerPrompt.Action.neutral("Choose seat", StorytellerPrompt.PICK + chooseCommand),
                        StorytellerPrompt.Action.neutral("I'll handle it", dismissCommand))));
    }

    /** Why {@code player} cannot choose yet (asleep, or the Storyteller chose for them), or null. */
    static String refusal(UUID player) {
        if (player == null) return null;
        if (ASLEEP.containsKey(player)) return ASLEEP_REFUSAL;
        if (CHOSEN_FOR.contains(player)) return "The Storyteller has made your choice for you tonight.";
        return null;
    }

    /** The Storyteller chose for a sleeping player: they stay asleep and their choice stands. */
    static void chosenForThem(UUID player) {
        if (player != null && ASLEEP.remove(player) != null) CHOSEN_FOR.add(player);
    }

    /** Forget a player without waking them (their choice is resolved or dismissed). */
    static void cancel(UUID player) {
        if (player == null) return;
        ASLEEP.remove(player);
        CHOSEN_FOR.remove(player);
    }

    /** New game, or dawn: nobody is waiting any more. Keeps the Storyteller's on/off choice. */
    public static void reset() {
        ASLEEP.clear();
        CHOSEN_FOR.clear();
    }

    /** The Storyteller's Wake now. Returns why not, or null. */
    public static String wakeNow(MinecraftServer server, int seat) {
        for (Map.Entry<UUID, Integer> entry : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
            if (entry.getValue() == seat && ASLEEP.containsKey(entry.getKey())) {
                wake(server, entry.getKey());
                return null;
            }
        }
        return "Nobody at seat " + seat + " is waiting for a visit.";
    }

    private static void wake(MinecraftServer server, UUID player) {
        Sleeper sleeper = ASLEEP.remove(player);
        if (sleeper == null) return;
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(player);
        if (online != null) {
            // Only the woken player sees and hears this, so neighbours learn nothing.
            online.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            online.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("Use your ability").withStyle(ChatFormatting.GRAY)));
            online.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("The Storyteller is here").withStyle(ChatFormatting.GOLD)));
            online.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.MASTER,
                    online.getX(), online.getY(), online.getZ(), 1.0F, 1.0F, 0L));
        }
        sleeper.wake().run();
    }

    /** Wakes each sleeper whose house a Storyteller has reached (walking, flying, Spectator or teleport). */
    public static void serverTick(MinecraftServer server) {
        if (ASLEEP.isEmpty() || ++ticks < CHECK_TICKS) return;
        ticks = 0;
        List<ServerPlayer> storytellers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StorytellerState.isStoryteller(player.getUUID())) storytellers.add(player);
        }
        if (storytellers.isEmpty()) return;
        for (UUID player : List.copyOf(ASLEEP.keySet())) {
            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(player);
            if (seat == null) {
                ASLEEP.remove(player);
                continue;
            }
            for (ServerPlayer storyteller : storytellers) {
                if (SeatPositionManager.isPlayerNearHome(storyteller, seat, ARRIVE_RADIUS)) {
                    wake(server, player);
                    break;
                }
            }
        }
    }
}
