package com.sharktower.bloodonthesharktower.practice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The decisions practice players make, with no Minecraft types so they can be
 * regression tested on their own: names, hands in a vote, night seat picks
 * and how far to walk each tick. {@link PracticePlayers} does the rest.
 */
public final class PracticePlan {
    private PracticePlan() {}

    /** Most practice players a server will hold (the Grimoire seats 16). */
    public static final int MAX = 15;
    /** Profile names end with this, so everyone can tell them from real players. */
    public static final String SUFFIX = "_bot";
    static final List<String> NAMES = List.of(
            "Ada", "Bram", "Cosima", "Dorian", "Elspeth", "Fitz", "Greta", "Hollis",
            "Ivo", "Juniper", "Kit", "Lucan", "Mirela", "Nell", "Osric", "Pim");

    /** How practice players vote: by their own lights, always, or never. */
    public enum Votes {
        RANDOM, YES, NO;

        public static Votes parse(String raw) {
            if (raw == null) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "random", "mixed" -> RANDOM;
                case "yes", "up", "all" -> YES;
                case "no", "down", "none" -> NO;
                default -> null;
            };
        }
    }

    /** The next {@code count} free names (with {@link #SUFFIX}), skipping any in {@code taken} (any case). */
    public static List<String> pickNames(int count, Collection<String> taken) {
        Set<String> used = new java.util.HashSet<>();
        for (String t : taken) used.add(t.toLowerCase(Locale.ROOT));
        List<String> out = new ArrayList<>();
        for (String base : NAMES) {
            if (out.size() >= count) break;
            String name = base + SUFFIX;
            if (!used.contains(name.toLowerCase(Locale.ROOT))) out.add(name);
        }
        return out;
    }

    /**
     * Whether to raise a hand for this nomination. Evil practice players
     * protect their own team and like executing good players; the dead are
     * careful with their one ghost vote.
     */
    public static boolean raiseHand(Votes mode, Boolean selfGood, Boolean nomineeGood, boolean dead, Random random) {
        if (mode == Votes.YES) return true;
        if (mode == Votes.NO) return false;
        double chance = 0.45;
        if (Boolean.FALSE.equals(selfGood)) {
            chance = Boolean.FALSE.equals(nomineeGood) ? 0.1 : 0.65;
        }
        if (dead) chance *= 0.5;
        return random.nextDouble() < chance;
    }

    /**
     * A seat to pick at night: any living seated player other than
     * themselves and {@code excludedSeat} (the Monk can't pick themselves).
     * Evil players prefer good targets. Returns 0 when nobody fits.
     *
     * @param seats seat number to {alive, good} for everyone seated
     */
    public static int pickSeat(int ownSeat, int excludedSeat, Boolean selfGood,
                               Map<Integer, boolean[]> seats, Random random) {
        List<Integer> any = new ArrayList<>();
        List<Integer> good = new ArrayList<>();
        for (Map.Entry<Integer, boolean[]> e : new java.util.TreeMap<>(seats).entrySet()) {
            int seat = e.getKey();
            if (seat == ownSeat || seat == excludedSeat || !e.getValue()[0]) continue;
            any.add(seat);
            if (e.getValue()[1]) good.add(seat);
        }
        List<Integer> from = Boolean.FALSE.equals(selfGood) && !good.isEmpty() ? good : any;
        return from.isEmpty() ? 0 : from.get(random.nextInt(from.size()));
    }

    /**
     * One tick of walking from {@code (x, z)} towards {@code (tx, tz)} at
     * {@code speed} blocks a second: the new {x, z}. Arrives exactly.
     */
    public static double[] step(double x, double z, double tx, double tz, double speed) {
        double dx = tx - x, dz = tz - z, d = Math.sqrt(dx * dx + dz * dz);
        double move = speed / 20.0;
        if (d <= move || d < 1.0e-6) return new double[]{tx, tz};
        return new double[]{x + dx / d * move, z + dz / d * move};
    }

    /** Minecraft yaw (degrees) for facing along {@code (dx, dz)}. */
    public static float yaw(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }
}
