package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.GrimoirePlayerClicks;
import com.sharktower.bloodonthesharktower.client.gui.ReminderChooseScreen;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Invisible interaction surface over a rendered player portrait in the Grimoire.
 *
 * Original BOTB interaction split:
 *  - left-click head: reminders
 *  - left-click role token: role/alignment
 *  - right-click: Sharktower accessibility/game actions
 *  - Shift clicks: nomination shortcuts
 *
 * Keeping head and role as different targets removes a whole extra menu step.
 */
public final class GrimoirePlayerHeadWidget extends AbstractWidget {
    private final UUID playerId;
    private final int seat;
    private final PendingRoleAssignment assignment;

    public GrimoirePlayerHeadWidget(
            int x,
            int y,
            int size,
            UUID playerId,
            int seat,
            PendingRoleAssignment assignment
    ) {
        super(x, y, size, size, Component.literal("Seat " + seat + " player"));
        this.playerId = playerId;
        this.seat = seat;
        this.assignment = assignment;
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        // Intentionally invisible. AssignRolesScreen renders the face and labels.
        if (isHovered()) {
            if (ClientGrimoireEdits.isLocalStoryteller()
                    && com.sharktower.bloodonthesharktower.states.ClientState.nominationsOpen) {
                GrimoireHoverHints.set("Seat " + seat
                        + " player — LMB reminders | RMB actions | Shift+LMB nominator | Shift+RMB nominee");
            } else {
                GrimoireHoverHints.set("Seat " + seat + " player — LMB reminders | RMB actions");
            }
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.hasShiftDown() && ClientGrimoireEdits.isLocalStoryteller()) {
            GrimoirePlayerClicks.handleShiftLeft(playerId);
            return;
        }

        net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                new ReminderChooseScreen(playerId, seat));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
