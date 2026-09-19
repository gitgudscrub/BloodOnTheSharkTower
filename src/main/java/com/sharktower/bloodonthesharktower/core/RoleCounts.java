package com.sharktower.bloodonthesharktower.core;

/**
 * Standard BOTC base role distribution by non-Traveller player count.
 *
 * This intentionally mirrors the source mod's public setup-count display. It
 * never derives the numbers from the actual bag, so hidden setup modifiers
 * such as Baron do not leak through the HUD.
 */
public final class RoleCounts {
    private RoleCounts() {}

    public static RoleCountInfo getCounts(int playerCount) {
        if (playerCount < 1) return null;
        if (playerCount <= 5) return new RoleCountInfo(3, 0, 1, 1);
        if (playerCount == 6) return new RoleCountInfo(3, 1, 1, 1);
        if (playerCount == 7) return new RoleCountInfo(5, 0, 1, 1);
        if (playerCount == 8) return new RoleCountInfo(5, 1, 1, 1);
        if (playerCount == 9) return new RoleCountInfo(5, 2, 1, 1);
        if (playerCount == 10) return new RoleCountInfo(7, 0, 2, 1);
        if (playerCount == 11) return new RoleCountInfo(7, 1, 2, 1);
        if (playerCount == 12) return new RoleCountInfo(7, 2, 2, 1);
        if (playerCount == 13) return new RoleCountInfo(9, 0, 3, 1);
        if (playerCount == 14) return new RoleCountInfo(9, 1, 3, 1);
        return new RoleCountInfo(9, 2, 3, 1);
    }

    public record RoleCountInfo(int townsfolk, int outsiders, int minions, int demons) {}
}
