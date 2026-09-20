package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Shared mouse-action handling for both the portrait and role token in the Grim. */
public final class GrimoirePlayerClicks {
    private GrimoirePlayerClicks() {}

    public static void handle(UUID playerId, int seat, PendingRoleAssignment assignment, MouseButtonEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean storyteller = ClientGrimoireEdits.isLocalStoryteller();

        // Minecraft's Shift modifier covers both Left Shift and Right Shift.
        // This means Left Shift is explicitly supported while retaining the
        // accessibility benefit of allowing Right Shift as well.
        boolean shift = event.hasShiftDown();
        // AssignRolesScreen may remap mouse coordinates for the virtual Scale-4
        // canvas. The preserved MouseButtonInfo is the authoritative button
        // identity after that remap; using event.button() here could misclassify
        // a physical left-click as the right-click nomination branch.
        int button = event.buttonInfo().button();

        if (storyteller && shift) {
            if (!ClientState.nominationsOpen) {
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "Open nominations before using Grimoire nomination shortcuts."));
                }
                return;
            }

            if (button == 0) {
                GrimoireInteractionState.toggleNominator(playerId);
                return;
            }

            if (button == 1) {
                UUID nominator = GrimoireInteractionState.selectedNominator();
                if (nominator == null) {
                    if (minecraft.player != null) {
                        minecraft.player.sendSystemMessage(Component.literal(
                                "Choose a nominator first with Shift + left-click."));
                    }
                    return;
                }

                ClientStorytellerActions.send("nominate_pair", nominator + "|" + playerId);
                GrimoireInteractionState.clearNominator();
                minecraft.gui.setScreen(new AssignRolesScreen());
                return;
            }
        }

        if (storyteller && button == 1) {
            minecraft.gui.setScreen(new GrimoirePlayerActionScreen(playerId, seat));
            return;
        }

        if (button == 0) {
            minecraft.gui.setScreen(new PlayerSetupScreen(playerId, seat, assignment));
        }
    }
}
