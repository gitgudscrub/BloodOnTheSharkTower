package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import com.sharktower.bloodonthesharktower.client.gui.GrimoireInteractionState;
import com.sharktower.bloodonthesharktower.client.gui.GrimoirePlayerActionScreen;
import com.sharktower.bloodonthesharktower.client.gui.PlayerSetupScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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
        }

        if (GrimoireInteractionState.isSelectedNominator(playerId)) {
            graphics.outline(getX() - 3, getY() - 3, this.width + 6, this.height + 6, UiDrawing.YES);
            graphics.text(Minecraft.getInstance().font, "N",
                    getX() + this.width - 5, getY() - 7, UiDrawing.YES, true);
        } else if (playerId.equals(ClientState.currentNominee)) {
            graphics.outline(getX() - 3, getY() - 3, this.width + 6, this.height + 6, UiDrawing.GOLD);
        } else if (isHovered()) {
            graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, UiDrawing.GOLD);
        }
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == 0 || buttonInfo.button() == 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean storyteller = ClientGrimoireEdits.isLocalStoryteller();

        if (storyteller && event.hasShiftDown()) {
            if (!ClientState.nominationsOpen) {
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal(
                            "Open nominations before using Grimoire nomination shortcuts."));
                }
                return;
            }

            if (event.button() == 0) {
                GrimoireInteractionState.toggleNominator(playerId);
                return;
            }

            if (event.button() == 1) {
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

        if (storyteller && event.button() == 1) {
            minecraft.gui.setScreen(new GrimoirePlayerActionScreen(playerId, seat));
            return;
        }

        minecraft.gui.setScreen(new PlayerSetupScreen(playerId, seat, assignment));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
