package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Shared player interaction helpers for the Grimoire.
 *
 * Mouse-button identity is deliberately resolved by AssignRolesScreen before
 * these helpers are called. That avoids the 26.3 MouseButtonInfo ambiguity that
 * caused physical LMB to enter the RMB branch and physical RMB to be ignored.
 */
public final class GrimoirePlayerClicks {
    private GrimoirePlayerClicks() {}

    /** Shift+LMB shortcut used by both portrait and role token. */
    public static void handleShiftLeft(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientGrimoireEdits.isLocalStoryteller()) return;

        if (!ClientState.nominationsOpen) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(
                        "Open nominations before using Grimoire nomination shortcuts."));
            }
            return;
        }

        GrimoireInteractionState.toggleNominator(playerId);
    }

    /** Physical RMB resolved at screen level. */
    public static void handleRight(
            UUID playerId,
            int seat,
            PendingRoleAssignment assignment,
            boolean shift
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientGrimoireEdits.isLocalStoryteller()) return;

        if (shift) {
            if (!ClientState.nominationsOpen) {
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "Open nominations before using Grimoire nomination shortcuts."));
                }
                return;
            }

            UUID nominator = GrimoireInteractionState.selectedNominator();
            if (nominator == null) {
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "Choose a nominator first with Shift + left-click."));
                }
                return;
            }

            GrimoireReturnState.requestAfterNextGrimoireSync();
            ClientStorytellerActions.send("nominate_pair", nominator + "|" + playerId);
            GrimoireInteractionState.clearNominator();
            return;
        }

        minecraft.gui.setScreen(new GrimoirePlayerActionScreen(playerId, seat));
    }

    /** Normal LMB on a role token. */
    public static void handleRoleLeft(
            UUID playerId,
            int seat,
            PendingRoleAssignment assignment,
            boolean shift
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (shift && ClientGrimoireEdits.isLocalStoryteller()) {
            handleShiftLeft(playerId);
            return;
        }

        minecraft.gui.setScreen(new PlayerSetupScreen(playerId, seat, assignment));
    }
}
