package com.sharktower.bloodonthesharktower.practice;

import com.mojang.authlib.GameProfile;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.automation.HouseVisits;
import com.sharktower.bloodonthesharktower.automation.SeatPicks;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.networking.PlayerActionHandler;
import com.sharktower.bloodonthesharktower.networking.SeatPickS2CPayload;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Practice players: stand-in players a Storyteller adds to rehearse a game
 * alone, or to try a full town before real players arrive.
 *
 * <p>Each one is a real server-side player on a connection that goes
 * nowhere, so seating, dealing, the Grimoire, deaths, teleports and Watch
 * live treat them like anyone else. On top of that they:
 * <ul>
 *   <li>walk home at night and to their town-square seat by day, chat in
 *   small groups during the day until nominations open;</li>
 *   <li>raise or lower their hand in each vote ({@link PracticePlan#raiseHand});</li>
 *   <li>answer night seat picks (Monk, Imp, Poisoner, Ravenkeeper) once the
 *   Storyteller has visited, like a player clicking a seat.</li>
 * </ul>
 * They walk in straight lines (through walls, following the ground), never
 * speak, and a game with any practice player seated is never sent to the
 * Sharktower site. They leave when cleared or when the server stops.
 */
public final class PracticePlayers {
    private PracticePlayers() {}

    private static final class Bot {
        final UUID id;
        final double speed;
        double wanderX, wanderZ;
        long nextWander;
        long voteKey = -1;      // which vote this bot has decided on
        long decideAt;          // tick to decide its hand
        boolean decided;
        String pickKey;         // the night pick answered (title + command)
        long pickAt;

        Bot(UUID id, double speed) {
            this.id = id;
            this.speed = speed;
        }
    }

    private static final Map<UUID, Bot> BOTS = new LinkedHashMap<>();
    private static final Random RANDOM = new Random();
    private static PracticePlan.Votes votes = PracticePlan.Votes.RANDOM;
    private static long tick;
    private static long voteCounter;
    private static boolean voteWasOn;
    // Shared chat spots during the day, so practice players gather in groups.
    private static final List<double[]> SPOTS = new ArrayList<>();
    private static long spotsUntil;

    /** A connection that drops everything it is asked to send. */
    private static final class NowhereConnection extends Connection {
        NowhereConnection() {
            super(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(this);
        }

        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {}
        @Override public String getLoggableAddress(boolean logIps) { return "practice"; }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PracticePlayers::serverTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(PracticePlayers::removeAll);
    }

    public static int count() {
        return BOTS.size();
    }

    /** True when any seated player is a practice player: the game is practice, not for stats. */
    public static boolean anySeated() {
        for (UUID id : ServerState.PLAYER_SEAT_NUMBERS.keySet()) if (BOTS.containsKey(id)) return true;
        for (UUID id : StorytellerState.PENDING_SEAT_NUMBERS.keySet()) if (BOTS.containsKey(id)) return true;
        return false;
    }

    public static PracticePlan.Votes votes() {
        return votes;
    }

    public static void setVotes(PracticePlan.Votes mode) {
        votes = mode == null ? PracticePlan.Votes.RANDOM : mode;
    }

    /** Adds up to {@code count} practice players around {@code near}. Returns their names. */
    public static List<String> add(MinecraftServer server, ServerPlayer near, int count) {
        int room = PracticePlan.MAX - BOTS.size();
        List<String> taken = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) taken.add(p.getGameProfile().name());
        List<String> names = PracticePlan.pickNames(Math.max(0, Math.min(count, room)), taken);
        ServerLevel level = near.level();
        List<String> added = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            UUID id = UUID.nameUUIDFromBytes(("sharktower-practice:" + name).getBytes(StandardCharsets.UTF_8));
            if (server.getPlayerList().getPlayer(id) != null) continue;
            GameProfile profile = new GameProfile(id, name);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer bot = new ServerPlayer(server, level, profile, cookie.clientInformation());
            double angle = Math.PI * 2 * i / Math.max(1, names.size());
            double x = near.getX() + Math.cos(angle) * 2.5, z = near.getZ() + Math.sin(angle) * 2.5;
            bot.snapTo(x, groundY(level, x, near.getY(), z), z, PracticePlan.yaw(near.getX() - x, near.getZ() - z), 0);
            BOTS.put(id, new Bot(id, 3.9 + RANDOM.nextDouble() * 0.7));
            try {
                server.getPlayerList().placeNewPlayer(new NowhereConnection(), bot, cookie);
            } catch (RuntimeException e) {
                BOTS.remove(id);
                BloodOnTheSharktower.LOGGER.warn("Could not add practice player {}", name, e);
                continue;
            }
            bot.setGameMode(GameType.ADVENTURE);
            bot.getAbilities().invulnerable = true;
            bot.onUpdateAbilities();
            added.add(name);
        }
        return added;
    }

    /** Takes every practice player off the server. Their seats stay, like a real player leaving. */
    public static int removeAll(MinecraftServer server) {
        int removed = 0;
        for (UUID id : new ArrayList<>(BOTS.keySet())) {
            ServerPlayer bot = server.getPlayerList().getPlayer(id);
            if (bot != null) {
                try {
                    bot.connection.onDisconnect(new DisconnectionDetails(Component.literal("Practice over")));
                } catch (RuntimeException e) {
                    server.getPlayerList().remove(bot);
                }
                removed++;
            }
            BOTS.remove(id);
        }
        return removed;
    }

    public static List<String> names(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (UUID id : BOTS.keySet()) {
            ServerPlayer bot = server.getPlayerList().getPlayer(id);
            if (bot == null) continue;
            Integer seat = ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(id, StorytellerState.PENDING_SEAT_NUMBERS.get(id));
            out.add(bot.getGameProfile().name() + (seat != null ? " (seat " + seat + ")" : ""));
        }
        return out;
    }

    // ------------------------------------------------------------ every tick

    private static void serverTick(MinecraftServer server) {
        if (BOTS.isEmpty()) return;
        tick++;
        boolean voteOn = DaytimeState.isVoteInProgress();
        if (voteOn && !voteWasOn) voteCounter++;
        voteWasOn = voteOn;
        for (Bot b : new ArrayList<>(BOTS.values())) {
            ServerPlayer bot = server.getPlayerList().getPlayer(b.id);
            if (bot == null) {
                BOTS.remove(b.id);
                continue;
            }
            try {
                walk(bot, b);
                if (voteOn) vote(server, bot, b);
                pick(server, bot, b);
            } catch (RuntimeException e) {
                BloodOnTheSharktower.LOGGER.debug("Practice player {} skipped a tick", bot.getGameProfile().name(), e);
            }
        }
    }

    private static Integer seat(UUID id) {
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
        return seat != null ? seat : StorytellerState.PENDING_SEAT_NUMBERS.get(id);
    }

    private static Boolean good(UUID id) {
        PendingRoleAssignment role = StorytellerState.effectiveGrimoireRoles().get(id);
        return role == null ? null : role.isFinalGood();
    }

    // ------------------------------------------------------------ walking

    private static void walk(ServerPlayer bot, Bot b) {
        Integer seat = seat(b.id);
        SeatPositionManager.Position centre = SeatPositionManager.clockCenter();
        SeatPositionManager.Position target = null;
        boolean wander = false;
        boolean started = ServerState.currentNight > 0 || ServerState.currentDay > 0;
        if (seat != null && started && !ServerState.gameEnded) {
            if (PhaseOperations.isNight()) {
                target = SeatPositionManager.seatHome(seat);
            } else if (DaytimeState.areNominationsOpen() || DaytimeState.hasActiveNomination()
                    || DaytimeState.isVoteInProgress()) {
                target = SeatPositionManager.townSquareSeat(seat);
            } else {
                wander = true;
            }
        } else if (seat != null) {
            target = SeatPositionManager.townSquareSeat(seat);
        }
        if (wander && centre != null) {
            if (tick >= spotsUntil) {
                SPOTS.clear();
                for (int i = 0; i < 3; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 6 + RANDOM.nextDouble() * 8;
                    SPOTS.add(new double[]{centre.x() + Math.cos(a) * r, centre.z() + Math.sin(a) * r});
                }
                spotsUntil = tick + 20 * 25;
            }
            if (tick >= b.nextWander) {
                double[] spot = SPOTS.get(RANDOM.nextInt(SPOTS.size()));
                b.wanderX = spot[0] + (RANDOM.nextDouble() - 0.5) * 5;
                b.wanderZ = spot[1] + (RANDOM.nextDouble() - 0.5) * 5;
                b.nextWander = tick + 20 * (8 + RANDOM.nextInt(10));
            }
            target = new SeatPositionManager.Position(b.wanderX, bot.getY(), b.wanderZ);
        } else if (wander && seat != null) {
            target = SeatPositionManager.townSquareSeat(seat);
        }
        if (target == null) return;

        ServerLevel level = bot.level();
        double dx = target.x() - bot.getX(), dz = target.z() - bot.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 160) {  // too far to walk: arrive like a teleport
            bot.teleportTo(target.x(), target.y(), target.z());
            level.getChunkSource().move(bot);
            return;
        }
        if (dist < 0.05) {
            if (centre != null && !wander) face(bot, centre.x() - bot.getX(), centre.z() - bot.getZ());
            return;
        }
        double speed = dist > 40 ? b.speed * 1.35 : b.speed;
        double[] next = PracticePlan.step(bot.getX(), bot.getZ(), target.x(), target.z(), speed);
        boolean arrived = next[0] == target.x() && next[1] == target.z();
        double y = arrived && !wander ? target.y() : groundY(level, next[0], bot.getY(), next[1]);
        bot.setPos(next[0], y, next[1]);
        bot.setOnGround(true);
        face(bot, dx, dz);
        if (tick % 4 == 0 || arrived) level.getChunkSource().move(bot);
    }

    private static void face(ServerPlayer bot, double dx, double dz) {
        if (dx * dx + dz * dz < 1.0e-4) return;
        float yaw = PracticePlan.yaw(dx, dz);
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.setYBodyRot(yaw);
    }

    /** Standing height at (x, z) near {@code y}: on top of the first solid block with room above, or y. */
    static double groundY(ServerLevel level, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y + 0.01);
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos feet = new BlockPos(bx, by + dy, bz);
            BlockPos below = feet.below();
            VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
            if (floor.isEmpty()) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
            return below.getY() + Math.min(1.0, floor.max(Direction.Axis.Y));
        }
        return y;
    }

    // ------------------------------------------------------------ votes

    private static void vote(MinecraftServer server, ServerPlayer bot, Bot b) {
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(b.id);
        if (seat == null || StorytellerState.isStoryteller(b.id)) return;
        if (b.voteKey != voteCounter) {
            b.voteKey = voteCounter;
            b.decided = false;
            b.decideAt = tick + 10 + RANDOM.nextInt(50);  // half a second to three seconds
        }
        if (b.decided || tick < b.decideAt) return;
        b.decided = true;
        if (DaytimeState.getLockedVotes().containsKey(b.id)) return;
        UUID nominee = DaytimeState.getCurrentNominee();
        boolean dead = Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(b.id));
        boolean up = PracticePlan.raiseHand(votes, good(b.id), nominee == null ? null : good(nominee), dead, RANDOM);
        if (up != DaytimeState.isHandRaised(b.id)) PlayerActionHandler.setHand(bot, server, up);
    }

    // ------------------------------------------------------------ night picks

    private static void pick(MinecraftServer server, ServerPlayer bot, Bot b) {
        SeatPickS2CPayload open = SeatPicks.openFor(b.id);
        if (open == null || open.command() == null || open.command().isBlank()) {
            b.pickKey = null;
            return;
        }
        String key = open.title() + "|" + open.command();
        if (key.equals(b.pickKey)) return;
        if (HouseVisits.refusal(b.id) != null) return;  // the Storyteller hasn't visited yet
        if (b.pickAt == 0) {
            b.pickAt = tick + 40 + RANDOM.nextInt(60);   // think for 2 to 5 seconds
            return;
        }
        if (tick < b.pickAt) return;
        b.pickAt = 0;
        b.pickKey = key;
        Integer own = seat(b.id);
        Map<Integer, boolean[]> seats = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
            boolean alive = !Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(e.getKey()));
            seats.put(e.getValue(), new boolean[]{alive, !Boolean.FALSE.equals(good(e.getKey()))});
        }
        int target = PracticePlan.pickSeat(own == null ? 0 : own, open.excludeSeat(), good(b.id), seats, RANDOM);
        if (target <= 0) return;
        String command = open.command().trim();
        if (command.startsWith("/")) command = command.substring(1);
        server.getCommands().performPrefixedCommand(bot.createCommandSourceStack(), command + " " + target);
    }
}
