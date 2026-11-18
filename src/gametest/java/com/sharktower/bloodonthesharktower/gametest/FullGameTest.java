package com.sharktower.bloodonthesharktower.gametest;

import com.mojang.authlib.GameProfile;
import com.sharktower.bloodonthesharktower.automation.RoleAutomation;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
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
 * Then a second game checks the "Evil wins" prompt at two alive.
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
    private static Table deal(GameTestHelper helper) {
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
        String[] roles = {"imp", "poisoner", "monk", "virgin", "chef"};
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
                .thenSucceed();
    }
}
