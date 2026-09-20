package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import com.sharktower.bloodonthesharktower.client.gui.RoleSelectionScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
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
        PendingRoleAssignment perceived = ClientState.grimoirePerceivedRoles.get(playerId);
        ScriptRole role = UiDrawing.roleOf(perceived);

        if (role == null) {
            UiDrawing.emptyRoleSlot(graphics, getX(), getY(), this.width);
        } else {
            UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);
            graphics.outline(getX(), getY(), this.width, this.height, 0xFFD8D8D8);
        }

        if (isHovered()) {
            graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, UiDrawing.GOLD);
            GrimoireHoverHints.set("Seat " + seat + " believed role — click to edit");
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft.getInstance().gui.setScreen(
                RoleSelectionScreen.forPerceivedRole(seat, new AssignRolesScreen()));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
