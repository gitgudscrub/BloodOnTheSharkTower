package com.sharktower.bloodonthesharktower.client.gui;

/**
 * One-shot navigation latch for Storyteller edits launched from the Grimoire.
 *
 * Server-owned edits are followed by an authoritative SendGrimoire snapshot.
 * Reopening the Grim only when that snapshot arrives avoids racing UI navigation
 * against the state update and guarantees the Storyteller lands on the updated
 * Grim rather than the world.
 */
public final class GrimoireReturnState {
    private static boolean returnAfterNextGrimoireSync;

    private GrimoireReturnState() {}

    public static void requestAfterNextGrimoireSync() {
        returnAfterNextGrimoireSync = true;
    }

    public static boolean consumeAfterGrimoireSync() {
        if (!returnAfterNextGrimoireSync) return false;
        returnAfterNextGrimoireSync = false;
        return true;
    }

    public static void clear() {
        returnAfterNextGrimoireSync = false;
    }
}
