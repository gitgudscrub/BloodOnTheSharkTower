package com.sharktower.bloodonthesharktower.practice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Plain Java regression executable: what practice players decide. */
public final class PracticePlanRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // Names: suffixed, in order, skipping names in use (any case), never more than asked.
        check(PracticePlan.pickNames(3, List.of()).equals(List.of("Ada_bot", "Bram_bot", "Cosima_bot")), "first names");
        check(PracticePlan.pickNames(2, List.of("ada_BOT", "Steve")).equals(List.of("Bram_bot", "Cosima_bot")), "skips taken");
        check(PracticePlan.pickNames(40, List.of()).size() == PracticePlan.NAMES.size(), "no more names than the list");
        for (String n : PracticePlan.pickNames(40, List.of())) check(n.length() <= 16, "name fits Minecraft's limit: " + n);
        check(PracticePlan.NAMES.size() > PracticePlan.MAX, "enough names for a full town");

        // Vote modes.
        check(PracticePlan.Votes.parse("YES") == PracticePlan.Votes.YES, "parse yes");
        check(PracticePlan.Votes.parse(" random ") == PracticePlan.Votes.RANDOM, "parse random");
        check(PracticePlan.Votes.parse("maybe") == null, "parse junk");
        Random r = new Random(1);
        check(PracticePlan.raiseHand(PracticePlan.Votes.YES, false, false, true, r), "yes always raises");
        check(!PracticePlan.raiseHand(PracticePlan.Votes.NO, true, false, false, r), "no never raises");

        // Random mode: evil protect evil, push good; the dead are careful.
        check(rate(false, false, false) < 0.2, "evil rarely votes on evil");
        check(rate(false, true, false) > 0.5, "evil often votes on good");
        double alive = rate(true, true, false), dead = rate(true, true, true);
        check(alive > 0.35 && alive < 0.55, "good votes about half the time: " + alive);
        check(dead < alive * 0.7, "the dead vote less: " + dead + " vs " + alive);

        // Night picks: never themselves, the excluded seat or the dead; evil prefer good.
        Map<Integer, boolean[]> seats = new HashMap<>();
        seats.put(1, new boolean[]{true, false});   // evil, alive
        seats.put(2, new boolean[]{true, true});    // good, alive
        seats.put(3, new boolean[]{false, true});   // good, dead
        seats.put(4, new boolean[]{true, true});    // good, alive
        for (int i = 0; i < 200; i++) {
            int pick = PracticePlan.pickSeat(2, 4, true, seats, r);
            check(pick == 1, "a good chooser in seat 2, seat 4 excluded, seat 3 dead: only seat 1 is left, got " + pick);
            int evil = PracticePlan.pickSeat(1, 0, false, seats, r);
            check(evil == 2 || evil == 4, "an evil chooser picks a living good player, got " + evil);
        }
        Map<Integer, boolean[]> lonely = Map.of(1, new boolean[]{true, true});
        check(PracticePlan.pickSeat(1, 0, true, lonely, r) == 0, "nobody to pick");

        // Walking: 20 ticks at 4 blocks a second covers 4 blocks, and arrives exactly.
        double[] at = {0, 0};
        for (int i = 0; i < 20; i++) at = PracticePlan.step(at[0], at[1], 10, 0, 4);
        check(Math.abs(at[0] - 4) < 1e-9 && at[1] == 0, "4 blocks in a second: " + at[0]);
        for (int i = 0; i < 40; i++) at = PracticePlan.step(at[0], at[1], 10, 0, 4);
        check(at[0] == 10 && at[1] == 0, "arrives exactly");
        // Yaw: Minecraft faces south (+z) at 0 and west (-x) at 90.
        check(Math.abs(PracticePlan.yaw(0, 1)) < 1e-3, "south is 0");
        check(Math.abs(PracticePlan.yaw(-1, 0) - 90) < 1e-3, "west is 90");

        System.out.println("PracticePlanRegression: " + checks + " checks passed.");
    }

    private static double rate(boolean selfGood, boolean nomineeGood, boolean dead) {
        Random r = new Random(7);
        int up = 0, n = 20000;
        for (int i = 0; i < n; i++) if (PracticePlan.raiseHand(PracticePlan.Votes.RANDOM, selfGood, nomineeGood, dead, r)) up++;
        return up / (double) n;
    }
}
