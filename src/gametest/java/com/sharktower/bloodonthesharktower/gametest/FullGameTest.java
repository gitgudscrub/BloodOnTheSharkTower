package com.sharktower.bloodonthesharktower.gametest;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.sharktower.bloodonthesharktower.automation.RoleAutomation;
import com.sharktower.bloodonthesharktower.automation.WraithNight;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
import com.sharktower.bloodonthesharktower.guide.GuideBooks;
import com.sharktower.bloodonthesharktower.practice.PracticePlayers;
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
    private static final String[] PRACTICE = {"Ada_bot", "Bram_bot", "Cosima_bot", "Dorian_bot", "Elspeth_bot"};
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

    /** Runs {@code command} as {@code as} and returns what it said in chat. */
    private static String said(MinecraftServer server, ServerPlayer as, String command) {
        StringBuilder out = new StringBuilder();
        net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { out.append(message.getString()).append(" | "); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        server.getCommands().performPrefixedCommand(as.createCommandSourceStack().withSource(capture), command);
        return out.toString();
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
        run(server, storyteller, "bots testscript");
        for (int i = 0; i < NAMES.length; i++) {
            run(server, storyteller, "bots seatPlayer " + NAMES[i] + " " + (i + 1));
            run(server, storyteller, "bots assignRole " + (i + 1) + " " + roles[i]);
        }
        run(server, storyteller, "bots sendRoles");
        helper.assertValueEqual(ServerState.PLAYER_SEAT_NUMBERS.size(), 5, "seated players after Send Roles");
        helper.assertTrue(ServerState.PLAYER_ROLES.values().stream().anyMatch(r -> r.role() == Role.IMP), "the Imp was dealt");
        // Guide books: the Script Guide at Send Roles, the Storyteller Guide on claiming.
        for (ServerPlayer p : players) helper.assertTrue(GuideBooks.has(p, GuideBooks.Kind.SCRIPT), p.getName().getString() + " has the Script Guide");
        helper.assertTrue(GuideBooks.has(storyteller, GuideBooks.Kind.SCRIPT), "the Storyteller has the Script Guide");
        helper.assertTrue(GuideBooks.has(storyteller, GuideBooks.Kind.STORYTELLER), "the Storyteller has the Storyteller Guide");
        return new Table(storyteller, players);
    }

    private static boolean hasPrompt(String id) {
        return RoleAutomation.prompts().stream().anyMatch(p -> p.id().equals(id));
    }

    /** Every playable character has a tip, and every guide builds into pages. */
    private static void checkGuides(GameTestHelper helper) {
        for (Role role : Role.values()) {
            switch (role.getType()) {
                case FABLED, LORIC, NONE -> { continue; }
                default -> helper.assertTrue(GuideBooks.hasTip(role.getId()), "a How to play tip for " + role.getId());
            }
        }
        for (GuideBooks.Kind kind : GuideBooks.Kind.values()) {
            var book = GuideBooks.create(kind);
            helper.assertTrue(book != null, kind + " guide builds");
            var content = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
            helper.assertTrue(content != null && content.pages().size() >= 5, kind + " guide has pages");
            for (var page : content.pages()) {
                String text = page.raw().getString();
                helper.assertTrue(text.split("\n", -1).length <= 14, kind + " page fits: " + text);
            }
        }
    }

    @GameTest(maxTicks = 2400)
    public void wholeGame(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Table t = deal(helper);
        checkGuides(helper);
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
                // Game 4: practice players fill the town, pick at night, walk to their seats and vote.
                .thenExecuteAfter(5, () -> {
                    run(server, st, "bots storyteller claim");
                    run(server, st, "bots resetGame");
                    // The earlier games' players step out of the town (as if they had left).
                    for (String name : NAMES) {
                        run(server, st, "bots unseatPlayer " + name);
                        ServerState.PLAYER_SEAT_NUMBERS.remove(server.getPlayerList().getPlayerByName(name).getUUID());
                    }
                    run(server, st, "bots testscript");
                    run(server, st, "bots practice votes yes");
                    run(server, st, "bots practice add 5");
                    helper.assertValueEqual(PracticePlayers.count(), 5, "five practice players");
                    String[] roles = {"imp", "poisoner", "monk", "virgin", "chef"};
                    for (int i = 0; i < 5; i++) {
                        helper.assertTrue(server.getPlayerList().getPlayerByName(PRACTICE[i]) != null, PRACTICE[i] + " is on the server");
                        String seated = said(server, st, "bots seatPlayer " + PRACTICE[i] + " " + (i + 1));
                        helper.assertTrue(StorytellerState.PENDING_SEAT_NUMBERS.containsKey(
                                server.getPlayerList().getPlayerByName(PRACTICE[i]).getUUID()), PRACTICE[i] + " seated: " + seated);
                        String dealt = said(server, st, "bots assignRole " + (i + 1) + " " + roles[i]);
                        helper.assertTrue(StorytellerState.PENDING_ROLES.containsKey(
                                server.getPlayerList().getPlayerByName(PRACTICE[i]).getUUID()), PRACTICE[i] + " dealt: " + dealt);
                    }
                    String sent = said(server, st, "bots sendRoles");
                    helper.assertTrue(!ServerState.PLAYER_ROLES.isEmpty(), "roles sent: " + sent);
                    helper.assertTrue(PracticePlayers.anySeated(), "a practice game");
                    run(server, st, "bots dusk");
                })
                // No houses set, so the practice Poisoner picks a seat by itself.
                .thenWaitUntil(() -> helper.assertTrue(RoleAutomation.prompts().stream()
                        .anyMatch(p -> p.id().equals("poisoner") && p.body().contains(" chose ")),
                        "the practice Poisoner should choose a seat"))
                .thenExecute(() -> {
                    run(server, st, "bots automation poisoner dismiss");
                    double x = st.getX(), y = st.getY(), z = st.getZ();
                    for (int seat = 1; seat <= 5; seat++) {
                        double a = Math.PI * 2 * seat / 5;
                        st.teleportTo(x + Math.cos(a) * 7, y, z + Math.sin(a) * 7);
                        SeatPositionManager.setTownSquareSeat(seat, st);
                    }
                    st.teleportTo(x, y, z);
                    run(server, st, "bots dawn");
                    run(server, st, "bots nominations open");
                    run(server, st, "bots nominatePair 3 1");
                    run(server, st, "bots runVote");
                })
                .thenWaitUntil(() -> helper.assertTrue(VotingManager.canResolveVote(), "the vote clock should pass every seat"))
                .thenExecute(() -> {
                    run(server, st, "bots resolveVote");
                    helper.assertValueEqual(DaytimeState.getMarkedForExecution(),
                            server.getPlayerList().getPlayerByName(PRACTICE[0]).getUUID(),
                            "every practice hand went up, so seat 1 is on the block");
                })
                // They walked from where they joined to their town-square seats.
                .thenWaitUntil(() -> {
                    for (int seat = 1; seat <= 5; seat++) {
                        ServerPlayer bot = server.getPlayerList().getPlayerByName(PRACTICE[seat - 1]);
                        var at = SeatPositionManager.townSquareSeat(seat);
                        double d = Math.hypot(bot.getX() - at.x(), bot.getZ() - at.z());
                        helper.assertTrue(d < 0.2, PRACTICE[seat - 1] + " should reach seat " + seat + " (" + d + " away)");
                    }
                })
                .thenExecute(() -> {
                    run(server, st, "bots practice clear");
                    helper.assertValueEqual(PracticePlayers.count(), 0, "practice players cleared");
                    helper.assertTrue(server.getPlayerList().getPlayerByName(PRACTICE[0]) == null, "practice players left the server");
                    run(server, st, "bots practice votes random");
                    run(server, st, "bots resetGame");
                    SeatPositionManager.clear();
                })
                .thenSucceed();
    }
}
