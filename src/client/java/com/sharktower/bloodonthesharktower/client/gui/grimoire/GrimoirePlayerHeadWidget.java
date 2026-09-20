package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.GrimoirePlayerClicks;
import com.sharktower.bloodonthesharktower.client.gui.GrimoirePlayerActionScreen;
import com.sharktower.bloodonthesharktower.client.gui.ReminderChooseScreen;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == 0 || buttonInfo.button() == 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        PendingRoleAssignment current = ClientGrimoireEdits.roleFor(playerId);

        if (event.hasShiftDown()) {
            GrimoirePlayerClicks.handle(playerId, seat, current == null ? assignment : current, event);
            return;
        }

        if (event.button() == 0) {
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                    new ReminderChooseScreen(playerId, seat));
            return;
        }

        if (event.button() == 1 && ClientGrimoireEdits.isLocalStoryteller()) {
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                    new GrimoirePlayerActionScreen(playerId, seat));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
