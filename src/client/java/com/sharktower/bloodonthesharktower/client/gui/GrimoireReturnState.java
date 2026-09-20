package com.sharktower.bloodonthesharktower.client.gui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Navigation coordinator for Grimoire sub-flows.
 *
 * Storyteller edits are server-authoritative. We wait for the updated Grimoire
 * snapshot, then reopen the Grim on a later client tick instead of from inside
 * the button/network callback. This avoids the 26.3 screen-close lifecycle
 * immediately undoing the navigation.
 */
public final class GrimoireReturnState {
    private static boolean returnAfterNextGrimoireSync;
    private static boolean suppressNextReveal;
    private static int deferredOpenTicks = -1;
    private static boolean registered;

    private GrimoireReturnState() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (deferredOpenTicks < 0) return;
            if (deferredOpenTicks-- > 0) return;

            deferredOpenTicks = -1;
            suppressNextReveal = true;
            client.gui.setScreen(new AssignRolesScreen());
        });
    }

    public static void requestAfterNextGrimoireSync() {
        returnAfterNextGrimoireSync = true;
    }

    /** Called after ClientState has applied an authoritative Grimoire snapshot. */
    public static boolean consumeAfterGrimoireSync() {
        if (!returnAfterNextGrimoireSync) return false;
        returnAfterNextGrimoireSync = false;

        // Wait one complete client tick after the network callback. A value of 1
        // means this END_CLIENT_TICK decrements first, then the following tick
        // performs the screen transition.
        deferredOpenTicks = 1;
        return true;
    }

    /** For local/back navigation where no server snapshot is expected. */
    public static void scheduleReturn() {
        deferredOpenTicks = 0;
    }

    public static void suppressNextReveal() {
        suppressNextReveal = true;
    }

    public static boolean consumeSuppressNextReveal() {
        if (!suppressNextReveal) return false;
        suppressNextReveal = false;
        return true;
    }

    public static void clear() {
        returnAfterNextGrimoireSync = false;
        suppressNextReveal = false;
        deferredOpenTicks = -1;
    }
}
