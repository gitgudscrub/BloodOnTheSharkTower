package com.sharktower.bloodonthesharktower.gametest;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.sharktower.bloodonthesharktower.automation.RoleAutomation;
import com.sharktower.bloodonthesharktower.automation.WraithNight;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
import com.sharktower.bloodonthesharktower.serverupdate.SiteGameStats;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A whole scripted game on a headless server, run by {@code gradlew check}:
 * a Storyteller and five connected test players, driven through the same
 * {@code /bots} and player commands the dev harness uses.
 *
 * Seats: 1 Imp, 2 Poisoner, 3 Monk, 4 Virgin, 5 Chef.
 * Night 1: the Poisoner poisons the Chef, the Storyteller confirms.
 * Day 1: the Chef nominates the Imp, three hands, the vote passes, the Imp
 * is executed, and the Storyteller is offered "Good wins".
 * Then a second game checks the "Evil wins" prompt at two alive, and a
 * third has a Wraith in seat 2: released into Spectator at dusk, the Chef
 * lights a Spirit Lantern, the Wraith visits and is revealed, and dawn brings
 * the Wraith home with the spent lantern gone.
 */
public final class FullGameTest {
    private static final String[] NAMES = {"Imp1", "Poisoner2", "Monk3", "Virgin4", "Chef5"};

    private static ServerPlayer join(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("sharktower-test:" + name).getBytes(StandardCharsets.UTF_8)), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void run(MinecraftServer server, ServerPlayer as, String command) {
        server.getCommands().performPrefixedCommand(as.createCommandSourceStack(), command);
    }

    private record Table(ServerPlayer storyteller, List<ServerPlayer> players) {}

    /** Joins the Storyteller and five players (once) and deals a fresh game. */
    private static final String[] BASIC = {"imp", "poisoner", "monk", "virgin", "chef"};

    private static Table deal(GameTestHelper helper) {
        return deal(helper, BASIC);
    }

    private static Table deal(GameTestHelper helper, String[] roles) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer storyteller = server.getPlayerList().getPlayerByName("Storyteller");
        if (storyteller == null) storyteller = join(server, helper.getLevel(), "Storyteller");
        List<ServerPlayer> players = new ArrayList<>();
        for (String name : NAMES) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(name);
            players.add(p != null ? p : join(server, helper.getLevel(), name));
        }
        run(server, storyteller, "bots storyteller claim");
        helper.assertTrue(StorytellerState.isStoryteller(storyteller.getUUID()), "Storyteller claim failed");
        run(server, storyteller, "bots resetGame");
        for (int i = 0; i < NAMES.length; i++) {
            run(server, storyteller, "bots seatPlayer " + NAMES[i] + " " + (i + 1));
            run(server, storyteller, "bots assignRole " + (i + 1) + " " + roles[i]);
        }
        run(server, storyteller, "bots sendRoles");
        helper.assertValueEqual(ServerState.PLAYER_SEAT_NUMBERS.size(), 5, "seated players after Send Roles");
        helper.assertTrue(ServerState.PLAYER_ROLES.values().stream().anyMatch(r -> r.role() == Role.IMP), "the Imp was dealt");
        return new Table(storyteller, players);
    }

    private static boolean hasPrompt(String id) {
        return RoleAutomation.prompts().stream().anyMatch(p -> p.id().equals(id));
    }

    @GameTest(maxTicks = 1200)
    public void wholeGame(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Table t = deal(helper);
        ServerPlayer st = t.storyteller();
        ServerPlayer chef = t.players().get(4);

        SeatPositionManager.clear();
        helper.startSequence()
                // Night 1: the Poisoner wakes (no houses set, so at once) and poisons the Chef.
                .thenExecute(() -> {
                    run(server, st, "bots dusk");
                    helper.assertTrue(hasPrompt("poisoner"), "the Poisoner should be choosing at dusk");
                    run(server, t.players().get(1), "poison 5");
                    run(server, st, "bots automation poisoner poison");
                    boolean poisoned = StorytellerState.REMINDERS.getOrDefault(chef.getUUID(), List.of()).stream()
                            .map(Reminder::text).anyMatch("Poisoned"::equalsIgnoreCase);
                    helper.assertTrue(poisoned, "the Chef should carry the Poisoner's reminder");
                    helper.assertTrue(!hasPrompt("poisoner"), "the Poisoner prompt should be resolved");
                })
                // Day 1: the Chef nominates the Imp and three players vote.
                .thenExecuteAfter(5, () -> {
                    run(server, st, "bots dawn");
                    helper.assertTrue(ServerState.currentDay == 1, "day 1 should have begun");
                    run(server, st, "bots nominations open");
                    run(server, st, "bots nominatePair 5 1");
                    helper.assertTrue(DaytimeState.hasActiveNomination(), "the nomination should be active");
                    for (int seat : new int[]{3, 4, 5}) run(server, t.players().get(seat - 1), "bots hand up");
                    run(server, st, "bots runVote");
                    for (int seat : new int[]{3, 4, 5}) run(server, t.players().get(seat - 1), "bots hand up");
                })
                // The clock hand passes every seat, then the Storyteller resolves the vote.
                .thenWaitUntil(() -> helper.assertTrue(VotingManager.canResolveVote(),
                        "the vote clock should reach every seat"))
                .thenExecute(() -> {
                    run(server, st, "bots resolveVote");
                    helper.assertTrue(DaytimeState.getMarkedForExecution() != null,
                            "three of five votes should put the Imp on the block");
                    helper.assertValueEqual(DaytimeState.getMarkedForExecution(), t.players().get(0).getUUID(),
                            "the Imp is on the block");
                    run(server, st, "bots execute");
                    helper.assertTrue(Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(t.players().get(0).getUUID())), "the Imp should be dead");
                    helper.assertValueEqual(RoleAutomation.lastWinPrompt(), "GOOD",
                            "the Storyteller should be offered Good wins");
                    // The record the Sharktower site gets for stats and achievements.
                    JsonObject record = SiteGameStats.build(server, "GOOD");
                    helper.assertTrue(record != null, "a stats record is built");
                    helper.assertValueEqual(record.get("winner").getAsString(), "good", "stats winner");
                    helper.assertValueEqual(record.getAsJsonArray("players").size(), 5, "stats players");
                    JsonObject imp = null;
                    for (var p : record.getAsJsonArray("players")) {
                        if (p.getAsJsonObject().get("seat").getAsInt() == 1) imp = p.getAsJsonObject();
                    }
                    helper.assertTrue(imp != null, "seat 1 is in the stats record");
                    helper.assertValueEqual(imp.get("roleId").getAsString(), "imp", "stats role id");
                    helper.assertValueEqual(imp.get("team").getAsString(), "demon", "stats team");
                    helper.assertValueEqual(imp.get("alignment").getAsString(), "evil", "stats alignment");
                    helper.assertTrue(imp.get("dead").getAsBoolean(), "the executed Imp is dead in the stats record");
                    helper.assertValueEqual(record.getAsJsonArray("storytellers").size(), 1, "stats storytellers");
                })
                // Game 2 (same test: the game state is server-wide, so games run one after another).
                .thenExecuteAfter(5, () -> {
                    deal(helper);
                    run(server, st, "bots dusk");
                    run(server, st, "bots dawn");
                    for (int seat : new int[]{3, 4, 5}) run(server, st, "bots markDead " + seat);
                    helper.assertValueEqual((int) ServerState.PLAYER_DEATH_STATUS.values().stream().filter(d -> !d).count(), 2, "two players alive");
                    helper.assertValueEqual(RoleAutomation.lastWinPrompt(), "EVIL",
                            "the Storyteller should be offered Evil wins");
                })
                // Game 3: the Wraith visits at night.
                .thenExecuteAfter(5, () -> {
                    Table w = deal(helper, new String[]{"imp", "wraith", "monk", "virgin", "chef"});
                    ServerPlayer wraith = w.players().get(1);
                    ServerPlayer chef3 = w.players().get(4);
                    double x = st.getX(), y = st.getY(), z = st.getZ();
                    wraith.teleportTo(x, y, z);
                    SeatPositionManager.setSeatHome(2, wraith);
                    chef3.teleportTo(x + 40, y, z + 40);
                    SeatPositionManager.setSeatHome(5, chef3);
                    run(server, st, "bots dusk");
                    helper.assertValueEqual(WraithNight.roaming(), wraith.getUUID(), "the Wraith is released at dusk");
                    helper.assertValueEqual(wraith.gameMode.getGameModeForPlayer(), GameType.SPECTATOR, "the Wraith roams in Spectator");
                    helper.assertTrue(WraithNight.hasLantern(chef3), "the Chef has a Spirit Lantern");
                    run(server, chef3, "lantern");
                    helper.assertTrue(WraithNight.lanternLit(chef3.getUUID()), "the Chef's lantern is lit");
                    // Through a wall: straight into the Chef's house, no door.
                    wraith.teleportTo(x + 41, y, z + 40);
                })
                .thenWaitUntil(() -> helper.assertTrue(WraithNight.alerted(5), "the lit lantern warns a Wraith is in the house"))
                .thenExecute(() -> {
                    ServerPlayer wraith = server.getPlayerList().getPlayerByName(NAMES[1]);
                    helper.assertTrue(!WraithNight.revealed(5), "through a wall the Wraith is not named");
                    helper.assertTrue(WraithNight.listenerIn("house:5") == null, "through a wall the Wraith cannot hear");
                    // Now through the house's door.
                    double x = st.getX(), y = st.getY(), z = st.getZ();
                    net.minecraft.core.BlockPos door = net.minecraft.core.BlockPos.containing(x + 43, y, z + 40);
                    helper.getLevel().setBlock(door, net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState(), 3);
                    wraith.teleportTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
                })
                .thenWaitUntil(() -> helper.assertTrue(WraithNight.revealed(5), "by the door the lantern names the Wraith"))
                .thenExecute(() -> {
                    ServerPlayer wraith = server.getPlayerList().getPlayerByName(NAMES[1]);
                    helper.assertValueEqual(WraithNight.listenerIn("house:5"), wraith.getUUID(), "by the door the Wraith hears the house");
                    double x = st.getX(), y = st.getY(), z = st.getZ();
                    helper.getLevel().setBlock(net.minecraft.core.BlockPos.containing(x + 43, y, z + 40),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    // Leaving the house stops the listening; the Storyteller can bring them back.
                    wraith.teleportTo(x + 80, y, z + 80);
                })
                .thenWaitUntil(() -> helper.assertTrue(WraithNight.listenerIn("house:5") == null, "leaving the house stops listening"))
                .thenExecute(() -> run(server, st, "bots automation wraith visit 5"))
                .thenWaitUntil(() -> helper.assertTrue(WraithNight.listenerIn("house:5") != null, "brought by the Storyteller, the Wraith hears"))
                .thenExecute(() -> {
                    ServerPlayer wraith = server.getPlayerList().getPlayerByName(NAMES[1]);
                    ServerPlayer chef3 = server.getPlayerList().getPlayerByName(NAMES[4]);
                    run(server, st, "bots dawn");
                    helper.assertTrue(WraithNight.roaming() == null, "dawn recalls the Wraith");
                    helper.assertTrue(wraith.gameMode.getGameModeForPlayer() != GameType.SPECTATOR, "the Wraith is back in their usual mode");
                    helper.assertTrue(SeatPositionManager.isPlayerNearHome(wraith, 2, 2), "the Wraith is home");
                    helper.assertTrue(!WraithNight.hasLantern(chef3), "the lit lantern goes out at dawn");
                    run(server, st, "bots dusk");
                    helper.assertTrue(!WraithNight.hasLantern(chef3), "a spent lantern is not given again");
                    run(server, st, "bots automation wraith recall");
                    helper.assertTrue(WraithNight.roaming() == null, "the Storyteller can recall the Wraith");
                    run(server, st, "bots resetGame");
                    SeatPositionManager.clear();
                })
                .thenSucceed();
    }
}
