package com.sharktower.bloodonthesharktower.daytime;

import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.sound.ModSounds;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Clockwise vote coordinator. The physical redstone clock from BOTB is replaced
 * by a server tick clock that locks each player's current hand state in seat order.
 */
public final class ElectionManager {
    public static final int PISTON_POWER_DELAY_TICKS = 2;
    public static final int START_COUNTDOWN_TICKS = 60;
    private static long observedVoteStart = -1L;
    private static int countdownTicksRemaining;
    private static int ticksUntilNextSeat = VotePresentationSettings.stepTicks();

    private ElectionManager() {}

    public static boolean isCleanupInProgress() { return false; }

    public static List<UUID> buildElectionOrder(UUID target, Map<UUID, Integer> seats) {
        return ElectionState.buildElectionOrder(target, seats);
    }

    /** Retained API surface; registration is performed once from the mod initializer. */
    public static void registerTickScheduler() {}

    /** Called from END_SERVER_TICK. */
    public static void serverTick(MinecraftServer server) {
        boolean clockRunning = DaytimeState.isVoteInProgress() || DaytimeState.isExileSupportInProgress();
        if (server == null || !clockRunning || !ElectionState.isVotingPhaseActive()) {
            observedVoteStart = -1L;
            countdownTicksRemaining = 0;
            ticksUntilNextSeat = VotePresentationSettings.stepTicks();
            return;
        }

        long start = ElectionState.getStartTime();
        if (start != observedVoteStart) {
            observedVoteStart = start;
            countdownTicksRemaining = START_COUNTDOWN_TICKS;
            ticksUntilNextSeat = VotePresentationSettings.stepTicks();
            freezePlayersInSeats(server, ElectionState.getElectionOrder());
            sendStartSounds(server);
            StateBroadcaster.broadcastVoteState(server);
        }

        // The physical lock lasts only while the clock itself is running. Once
        // the final seat has been counted players remain sitting if they want,
        // but they are free to stand while the Storyteller resolves the result.
        if (ElectionState.isVotingPhaseComplete()) return;

        // Voting is a physical town-square moment: keep every seated player on
        // their configured cushion/seat until the clockwise sweep is complete.
        keepPlayersFrozen(server, ElectionState.getElectionOrder());

        // Give the table a clear three-second visual countdown before the first
        // seat can lock. Clients animate 3/2/1 from the vote-start state; this
        // server-side hold makes that presentation authoritative rather than
        // allowing the first voter to be counted underneath it.
        if (countdownTicksRemaining > 0) {
            countdownTicksRemaining--;
            return;
        }

        if (--ticksUntilNextSeat > 0) return;
        ticksUntilNextSeat = VotePresentationSettings.stepTicks();

        UUID voter = ElectionState.getCurrentVoter();
        if (voter != null) {
            if (DaytimeState.isExileSupportInProgress()) {
                ExileSupportManager.lockSupportFromHand(server, voter);
            } else {
                VotingManager.lockVote(server, voter);
            }
            sendClockTickSound(server);
        }
        ElectionState.advanceVotingClock();
        StateBroadcaster.broadcastVoteState(server);

        if (ElectionState.isVotingPhaseComplete()) {
            boolean exile = DaytimeState.isExileSupportInProgress();
            int count = exile ? ExileSupportManager.effectiveSupportCount() : VotingManager.effectiveVoteCount();
            Component message = Component.literal((exile ? "Exile clock complete — " : "Vote clock complete — ")
                            + count + " vote(s) counted. ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(exile
                                    ? "Storyteller may Resolve Exile or adjust the count."
                                    : "Storyteller may Finish Vote or adjust the count.")
                            .withStyle(ChatFormatting.GRAY));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(message);
        }
    }

    public static List<UUID> getElectionOrder() { return ElectionState.getElectionOrder(); }
    public static long getElectionStartTime() { return ElectionState.getStartTime(); }

    public static void runVotingPhase(MinecraftServer server, ElectionConfig config, Set<UUID> deadPlayers) {
        if (config.getType() == ElectionType.EXILE_SUPPORT) {
            ExileSupportManager.startExileSupport(server);
        } else {
            VotingManager.startVote(
                    server,
                    deadPlayers,
                    config.applyOrganGrinderMode(),
                    DaytimeState.isVoudonModeActive(),
                    DaytimeState.getVoudonPlayerUuid(),
                    Set.of()
            );
        }
    }

    public static void cancelVotingPhase(MinecraftServer server) {
        if (DaytimeState.isExileSupportInProgress()) DaytimeState.cancelExileSupportVote();
        else DaytimeState.resetVote();
        ElectionState.resetVotingPhase();
        StateBroadcaster.broadcastDaytimeState(server);
        StateBroadcaster.broadcastVoteState(server);
    }

    public static void cancelAllTimers() {
        ElectionState.resetVotingPhase();
        observedVoteStart = -1L;
        countdownTicksRemaining = 0;
        ticksUntilNextSeat = VotePresentationSettings.stepTicks();
    }

    public static int calculateThreshold(int alivePlayers, int totalPlayers, ElectionConfig config) {
        int base = config.useAliveForThreshold() ? alivePlayers : totalPlayers;
        return Math.max(1, (int) Math.ceil(Math.max(0, base) / 2.0));
    }

    public static void freezePlayersInSeats(MinecraftServer server, List<UUID> players) {
        if (server == null || players == null) return;
        for (UUID id : players) {
            if (id == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
            if (player == null || seat == null) continue;
            SeatPositionManager.Position position = SeatPositionManager.townSquareSeat(seat);
            if (position == null) continue;
            seatPlayerAt(position, player);
        }
    }

    public static void keepPlayersFrozen(MinecraftServer server, List<UUID> players) {
        if (server == null || players == null) return;
        final double toleranceSq = 0.035D;
        for (UUID id : players) {
            if (id == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
            if (player == null || seat == null) continue;
            SeatPositionManager.Position position = SeatPositionManager.townSquareSeat(seat);
            if (position == null) continue;

            // Native 26.3 cushions are rideable entities. If this player is still
            // riding the cushion at their configured seat, leave them alone so
            // looking around and voice chat remain completely natural.
            if (isRidingSeatCushion(player, position)) continue;

            double dx = player.getX() - position.x();
            double dy = player.getY() - position.y();
            double dz = player.getZ() - position.z();
            if (dx * dx + dy * dy + dz * dz > toleranceSq || player.getVehicle() == null) {
                seatPlayerAt(position, player);
            }
        }
    }

    /**
     * Return the player to their configured vote seat and, when a vanilla 26.3
     * Cushion is present there, mount it so they visibly sit. Positional locking
     * remains as a fallback for maps that use a different chair build.
     */
    private static void seatPlayerAt(SeatPositionManager.Position position, ServerPlayer player) {
        player.teleportTo(position.x(), position.y(), position.z());
        Cushion cushion = nearestCushion(player, position);
        if (cushion != null && player.getVehicle() != cushion) {
            player.startRiding(cushion);
        }
    }

    private static boolean isRidingSeatCushion(ServerPlayer player, SeatPositionManager.Position position) {
        if (!(player.getVehicle() instanceof Cushion cushion)) return false;
        double dx = cushion.getX() - position.x();
        double dy = cushion.getY() - position.y();
        double dz = cushion.getZ() - position.z();
        return dx * dx + dy * dy + dz * dz <= 2.25D;
    }

    private static Cushion nearestCushion(ServerPlayer player, SeatPositionManager.Position position) {
        AABB search = new AABB(
                position.x() - 0.85D, position.y() - 1.25D, position.z() - 0.85D,
                position.x() + 0.85D, position.y() + 1.25D, position.z() + 0.85D
        );
        Cushion best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Cushion cushion : player.level().getEntitiesOfClass(Cushion.class, search)) {
            double dx = cushion.getX() - position.x();
            double dy = cushion.getY() - position.y();
            double dz = cushion.getZ() - position.z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cushion;
            }
        }
        return best;
    }
    public static void resetAllLevers(MinecraftServer server, List<UUID> players) {
        DaytimeState.clearLeverStates();
        StateBroadcaster.broadcastVoteState(server);
    }
    public static void powerAllSeatPistons(MinecraftServer server) {}
    public static void restoreAllPistonPower(MinecraftServer server, List<UUID> players) {}
    public static void sendStartSounds(MinecraftServer server) {
        playTownSquareSound(server, ModSounds.VOTE_START, 1.2F);
    }

    /** Play exactly one clock tick when one seat is counted. */
    public static void sendClockTickSound(MinecraftServer server) {
        playTownSquareSound(server, ModSounds.CLOCK_TICK, 1.0F);
    }

    private static void playTownSquareSound(MinecraftServer server, net.minecraft.sounds.SoundEvent sound, float volume) {
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer anchor = server.getPlayerList().getPlayers().getFirst();
        SeatPositionManager.Position center = SeatPositionManager.clockCenter();
        double x = center == null ? anchor.getX() : center.x();
        double y = center == null ? anchor.getY() : center.y();
        double z = center == null ? anchor.getZ() : center.z();
        anchor.level().playSound(null, x, y, z, sound, SoundSource.MASTER, volume, 1.0F);
    }

    public static void sendStopSounds(MinecraftServer server) {
        // Seat ticks are one-shot sounds, so there is no persistent sound to stop.
    }
}
