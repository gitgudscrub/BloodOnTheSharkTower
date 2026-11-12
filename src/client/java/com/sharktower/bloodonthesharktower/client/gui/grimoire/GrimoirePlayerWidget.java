package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.GrimoireInteractionState;
import com.sharktower.bloodonthesharktower.client.gui.GrimoirePlayerClicks;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
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
        float reveal = GrimoireRevealAnimation.progressForSeat(seat);
        if (!GrimoireRevealAnimation.beginElement(
                graphics, getX(), getY(), this.width, this.height, reveal)) return;
        try {
        PendingRoleAssignment current = ClientGrimoireEdits.roleFor(playerId);
        if (current == null) current = assignment;
        ScriptRole role = UiDrawing.roleOf(current);
        boolean currentDead = ClientState.playerDeathStatus.getOrDefault(playerId, dead);
        UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);
        if (ClientState.isExiledTraveler(playerId)) {
            graphics.outline(getX() - 2, getY() - 2, this.width + 4, this.height + 4, 0xFFAAAAAA);
            graphics.text(Minecraft.getInstance().font, "E", getX() + this.width / 2 - 3,
                    getY() + this.height / 2 - 4, 0xFFFFFFFF, true);
        }

        // Keep this ring outside the token/shroud but inside nomination highlights.
        AlignmentOverride alignment = ClientGrimoireEdits.visibleAlignmentFor(playerId);
        int border = switch (alignment) {
            case FORCE_GOOD -> UiDrawing.GOOD;
            case FORCE_BAD -> UiDrawing.EVIL;
            case DEFAULT -> UiDrawing.BORDER;
        };
        graphics.outline(getX() - 2, getY() - 2, this.width + 4, this.height + 4, border);
        graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, border);
        if (com.sharktower.bloodonthesharktower.client.config.ClientSettings.alignmentLetters
                && alignment != AlignmentOverride.DEFAULT) {
            String letter = alignment == AlignmentOverride.FORCE_GOOD ? "G" : "E";
            int bx = getX() + this.width - 6;
            int by = getY() + this.height - 7;
            graphics.fill(bx - 1, by - 1, bx + 7, by + 9, 0xE0101014);
            graphics.text(Minecraft.getInstance().font, letter, bx + 1, by, border | 0xFF000000, false);
        }
        if (com.sharktower.bloodonthesharktower.client.config.ClientSettings.grimoireLabels && role != null) {
            drawRoleLabel(graphics, role);
        }

        if (ClientGrimoireEdits.isLocalStoryteller() && ClientState.pendingDeaths.contains(playerId)) {
            drawPendingDeathQuestionMark(graphics, getX() + 1, getY() + 1);
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

        if (isHovered()) {
            String name = ClientState.playerName(playerId, seat);
            String pronouns = ClientState.pronouns(playerId);
            if (!pronouns.isEmpty()) name = name + " (" + pronouns + ")";
            if (ClientGrimoireEdits.isLocalStoryteller() && ClientState.nominationsOpen) {
                GrimoireHoverHints.set(name
                        + " role — LMB edit | RMB actions | Shift+LMB nominator | Shift+RMB nominee");
            } else {
                GrimoireHoverHints.set(name + " role — LMB edit"
                        + (ClientGrimoireEdits.isLocalStoryteller() ? " | RMB actions" : ""));
            }
        }
        } finally {
            GrimoireRevealAnimation.endElement(graphics);
        }
    }

    /** Role name under the token at 3/4 size, cut short so neighbouring labels do not collide. */
    private void drawRoleLabel(GuiGraphicsExtractor graphics, ScriptRole role) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        String name = role.getDisplayName();
        if (name == null || name.isEmpty()) return;
        float scale = 0.75F;
        int maxWidth = Math.round((this.width + 18) / scale);
        if (font.width(name) > maxWidth) name = font.plainSubstrByWidth(name, maxWidth - font.width(".")) + ".";
        int textWidth = font.width(name);
        float cx = getX() + this.width / 2.0F;
        float top = getY() + this.height + 3;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, top);
        graphics.pose().scale(scale, scale);
        graphics.fill(-textWidth / 2 - 2, -1, textWidth / 2 + 2, 9, 0xC0101014);
        graphics.text(font, name, -textWidth / 2, 0, UiDrawing.opaque(UiDrawing.teamColor(role.getTeam())), false);
        graphics.pose().popMatrix();
    }

    /**
     * Larger pixel-art pending-death marker. This is intentionally about 50%
     * larger than Minecraft's normal '?' glyph so the ST can spot an unrevealed
     * death at a glance without changing the surrounding role-token size.
     */
    private static void drawPendingDeathQuestionMark(GuiGraphicsExtractor graphics, int x, int y) {
        drawQuestionMarkPixels(graphics, x + 1, y + 1, 0xCC3A2D0A);
        drawQuestionMarkPixels(graphics, x, y, UiDrawing.GOLD);
    }

    private static void drawQuestionMarkPixels(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        graphics.fill(x + 2, y, x + 8, y + 2, colour);
        graphics.fill(x + 8, y + 2, x + 10, y + 6, colour);
        graphics.fill(x + 5, y + 6, x + 10, y + 8, colour);
        graphics.fill(x + 4, y + 8, x + 6, y + 11, colour);
        graphics.fill(x + 4, y + 12, x + 6, y + 14, colour);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        GrimoirePlayerClicks.handleRoleLeft(
                playerId,
                seat,
                ClientGrimoireEdits.roleFor(playerId),
                event.hasShiftDown()
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
