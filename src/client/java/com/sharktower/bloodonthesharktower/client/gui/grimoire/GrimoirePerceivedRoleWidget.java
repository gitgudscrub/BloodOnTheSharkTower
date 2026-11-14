package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import com.sharktower.bloodonthesharktower.client.gui.RoleSelectionScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.client.gui.UiTheme;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Storyteller-only secondary role token for characters that do not know their
 * true identity (currently the Drunk and Marionette).
 */
public final class GrimoirePerceivedRoleWidget extends AbstractWidget {
    private final UUID playerId;
    private final int seat;

    public GrimoirePerceivedRoleWidget(int x, int y, int size, UUID playerId, int seat) {
        super(x, y, size, size, Component.literal("Believed role for seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float reveal = GrimoireRevealAnimation.progressForSeat(seat);
        if (!GrimoireRevealAnimation.beginElement(
                graphics, getX(), getY(), this.width, this.height, reveal)) return;
        try {
        PendingRoleAssignment perceived = ClientState.grimoirePerceivedRoles.get(playerId);
        ScriptRole role = UiDrawing.roleOf(perceived);

        if (role == null) {
            UiTheme.emptyToken(graphics, getX(), getY(), this.width);
        } else {
            UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);

        }

        if (isHovered()) {
            UiTheme.ring(graphics, getX() - 2, getY() - 2, this.width + 4, UiTheme.GOLD);
            GrimoireHoverHints.set("Seat " + seat + " believed role — click to edit");
        }
        } finally {
            GrimoireRevealAnimation.endElement(graphics);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (com.sharktower.bloodonthesharktower.client.gui.SeatPick.tryPick(seat)) return;
        Minecraft.getInstance().gui.setScreen(
                RoleSelectionScreen.forPerceivedRole(seat, new AssignRolesScreen()));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
