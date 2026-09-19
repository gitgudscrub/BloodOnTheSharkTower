package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.PlayerSetupScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Clickable 32px player role token used by the 0.8.0 BOTB-style Grimoire. */
public final class GrimoirePlayerWidget extends AbstractWidget {
    private final UUID playerId;
    private final int seat;
    private final PendingRoleAssignment assignment;
    private final boolean dead;

    public GrimoirePlayerWidget(int x, int y, int size, UUID playerId, int seat,
                                PendingRoleAssignment assignment, boolean dead) {
        super(x, y, size, size, Component.literal("Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
        this.assignment = assignment;
        this.dead = dead;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PendingRoleAssignment current = ClientGrimoireEdits.roleFor(playerId);
        if (current == null) current = assignment;
        ScriptRole role = UiDrawing.roleOf(current);
        boolean currentDead = ClientState.playerDeathStatus.getOrDefault(playerId, dead);
        UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);
        if (ClientState.isExiledTraveler(playerId)) {
            graphics.outline(getX() - 2, getY() - 2, this.width + 4, this.height + 4, 0xFFAAAAAA);
            graphics.text(Minecraft.getInstance().font, "E", getX() + this.width / 2 - 3,
                    getY() + this.height / 2 - 4, 0xFFFFFFFF, true);
        } else if (currentDead) {
            graphics.outline(getX() - 2, getY() - 2, this.width + 4, this.height + 4, 0xFFFF5555);
            graphics.text(Minecraft.getInstance().font, "X", getX() + this.width / 2 - 3,
                    getY() + this.height / 2 - 4, 0xFFFF5555, true);
        } else if (isHovered()) {
            graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, UiDrawing.GOLD);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft.getInstance().gui.setScreen(new PlayerSetupScreen(playerId, seat, assignment));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
