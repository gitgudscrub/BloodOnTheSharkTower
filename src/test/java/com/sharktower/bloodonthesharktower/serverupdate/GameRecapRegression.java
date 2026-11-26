package com.sharktower.bloodonthesharktower.serverupdate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Plain Java regression executable: the game recap's timeline and vote log. */
public final class GameRecapRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void same(String actual, String expected) {
        check(expected.equals(actual), "expected '" + expected + "' but got '" + actual + "'");
    }

    public static void main(String[] args) {
        UUID ada = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID bram = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID cyd = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID storyteller = UUID.fromString("99999999-9999-9999-9999-999999999999");
        Map<UUID, Integer> seats = Map.of(ada, 1, bram, 2, cyd, 3);
        Map<UUID, Boolean> dead = new HashMap<>(Map.of(ada, false, bram, false, cyd, false));

        GameRecap.clear();
        // Setup: dead/alive changes don't count, and the phase starts at 0.
        GameRecap.sample(1, 0, false, seats, dead, 1000);
        dead.put(cyd, true);
        GameRecap.sample(1, 0, false, seats, dead, 1100);
        dead.put(cyd, false);
        GameRecap.sample(1, 0, false, seats, dead, 1200);
        // Night 1, then Ada dies in the night; day 1: Bram is executed.
        GameRecap.sample(1, 1, false, seats, dead, 2000);
        dead.put(ada, true);
        GameRecap.sample(1, 1, false, seats, dead, 2500);
        GameRecap.sample(1, 2, false, seats, dead, 3000);
        same(GameRecap.timeline(seats).toString(),
                "[{\"type\":\"phase\",\"time\":1000,\"phase\":0},{\"type\":\"phase\",\"time\":2000,\"phase\":1},"
                        + "{\"type\":\"death\",\"time\":2500,\"phase\":1,\"seat\":1},{\"type\":\"phase\",\"time\":3000,\"phase\":2}]");

        // A Storyteller nominee (no seat) is left out of the vote log.
        GameRecap.onExile(cyd, storyteller, 1, 2);
        GameRecap.onExile(null, cyd, 3, 2);
        same(GameRecap.votes(seats).toString(),
                "[{\"phase\":" + GameRecap.phaseNow() + ",\"time\":" + GameRecap.votes(seats).get(0).getAsJsonObject().get("time")
                        + ",\"nominee\":3,\"type\":\"exile\",\"votes\":3,\"majority\":2,\"voters\":[]}]");

        // The game ending stops the recording; a reset (or a return to setup) clears it.
        GameRecap.sample(1, 2, true, seats, dead, 4000);
        dead.put(bram, true);
        GameRecap.sample(1, 2, true, seats, dead, 4100);
        check(GameRecap.timeline(seats).size() == 4, "nothing is recorded after End Game");
        GameRecap.sample(2, 0, false, seats, dead, 5000);
        same(GameRecap.timeline(seats).toString(), "[{\"type\":\"phase\",\"time\":5000,\"phase\":0}]");
        check(GameRecap.votes(seats).isEmpty(), "a new game starts with no votes");

        // The log keeps only the newest entries.
        GameRecap.clear();
        for (int i = 0; i < GameRecap.MAX_TIMELINE + 25; i++) GameRecap.sample(3, 1 + (i % 2), false, seats, dead, i);
        check(GameRecap.timeline(seats).size() == GameRecap.MAX_TIMELINE, "timeline is capped");
        same(GameRecap.timeline(seats).get(GameRecap.MAX_TIMELINE - 1).getAsJsonObject().get("time").toString(),
                String.valueOf(GameRecap.MAX_TIMELINE + 24));

        // Seats unknown at the end are dropped rather than guessed.
        GameRecap.clear();
        GameRecap.sample(4, 1, false, seats, dead, 10);
        Map<UUID, Integer> moved = Map.of(ada, 1);
        dead.put(cyd, true);
        GameRecap.sample(4, 1, false, seats, dead, 20);
        check(GameRecap.timeline(moved).size() == 1, "a death at a seat that no longer exists is dropped");
        check(List.of(GameRecap.timeline(seats).get(1).getAsJsonObject().get("seat").getAsInt()).equals(List.of(3)),
                "deaths name the 1-based seat");
        GameRecap.clear();

        // A game that ends without being sent (stats off, practice, no server) forgets the last sent game,
        // so Cancel End Game can't take back an older real game.
        try {
            java.lang.reflect.Field sent = SiteGameStats.class.getDeclaredField("sentGameId");
            sent.setAccessible(true);
            sent.set(null, "an-older-game");
            SiteGameStats.onGameEnd(null, "good");
            check(sent.get(null) == null, "an unsent game clears the id Cancel End Game would remove");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }

        System.out.println("GameRecapRegression passed " + checks + " checks");
    }
}
