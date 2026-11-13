package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.GrimoireInteractionState;
import com.sharktower.bloodonthesharktower.client.gui.GrimoirePlayerClicks;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.client.gui.UiTheme;
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
        if (playerId.equals(ClientState.currentNominee)) {
            // A tight halo: wide enough to mark the nominee, small enough to
            // leave the rim reminders and the role name clear.
            int halo = Math.round(this.width * 1.4F);
            UiTheme.glow(graphics, getX() - (halo - this.width) / 2, getY() - (halo - this.height) / 2, halo,
                    UiTheme.nominee());
        }
        UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);
        if (currentDead) {
            // Dim the art; the shroud is hung at the top-left last, above the rings.
            UiTheme.disc(graphics, getX(), getY(), this.width, UiTheme.dark() ? 0x80120D14 : 0x60EBDFC3);
        }
        if (ClientState.isExiledTraveler(playerId)) {
            UiTheme.ring(graphics, getX() - 3, getY() - 3, this.width + 6, UiDrawing.DEAD);
            UiTheme.badge(graphics, getX() + this.width / 2 - 5, getY() + this.height / 2 - 5, 11, UiDrawing.DEAD, "X", 0xFFFFFFFF);
        }

        // Alignment ring just outside the gold rim; the G/E badge sits on the lower-right.
        AlignmentOverride alignment = ClientGrimoireEdits.visibleAlignmentFor(playerId);
        int alignmentColour = alignment == AlignmentOverride.FORCE_GOOD ? UiTheme.GOOD_BADGE : UiTheme.EVIL_BADGE;
        if (alignment != AlignmentOverride.DEFAULT) {
            UiTheme.ring(graphics, getX() - 3, getY() - 3, this.width + 6, alignmentColour);
        }
        if (!currentDead && ClientGrimoireEdits.isLocalStoryteller() && ClientState.pendingDeaths.contains(playerId)) {
            // Marked dead but not yet revealed: a lighter dim and the shroud, Storyteller-only.
            UiTheme.disc(graphics, getX(), getY(), this.width, UiTheme.dark() ? 0x50120D14 : 0x38EBDFC3);
        }

        if (GrimoireInteractionState.isSelectedNominator(playerId)) {
            UiTheme.ring(graphics, getX() - 5, getY() - 5, this.width + 10, UiTheme.yes());
            UiTheme.badge(graphics, getX() + this.width - 8, getY() - 4, 11, 0xFF2E8B3A, "N", 0xFFFFFFFF);
        } else if (playerId.equals(ClientState.currentNominee)) {
            UiTheme.ring(graphics, getX() - 5, getY() - 5, this.width + 10, UiTheme.nominee());
        } else if (isHovered()) {
            UiTheme.ring(graphics, getX() - 3, getY() - 3, this.width + 6, UiTheme.GOLD);
        }

        // The shroud, the G/E badge and the name go on last, so no ring covers them.
        if (currentDead || (ClientGrimoireEdits.isLocalStoryteller() && ClientState.pendingDeaths.contains(playerId))) {
            UiTheme.shroud(graphics, getX() - 4, getY() - 4, this.width / 2);
        }
        if (alignment != AlignmentOverride.DEFAULT
                && com.sharktower.bloodonthesharktower.client.config.ClientSettings.alignmentLetters) {
            String letter = alignment == AlignmentOverride.FORCE_GOOD ? "G" : "E";
            UiTheme.badge(graphics, getX() + this.width - 9, getY() + this.height - 9, 11, alignmentColour, letter, 0xFFFFFFFF);
        }
        if (com.sharktower.bloodonthesharktower.client.config.ClientSettings.grimoireLabels && role != null) {
            drawRoleLabel(graphics, role);
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

    /** Gap between the token and its name: clears the 5px nominator/nominee ring. */
    public static final int LABEL_GAP = 6;

    /** Role name under the token at 3/4 size, cut short so neighbouring labels do not collide. */
    private void drawRoleLabel(GuiGraphicsExtractor graphics, ScriptRole role) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        String name = role.getDisplayName();
        if (name == null || name.isEmpty()) return;
        name = name.toUpperCase(java.util.Locale.ROOT);
        float scale = 0.75F;
        int maxWidth = Math.round((this.width + 18) / scale);
        if (font.width(name) > maxWidth) name = font.plainSubstrByWidth(name, maxWidth - font.width(".")) + ".";
        int textWidth = font.width(name);
        float cx = getX() + this.width / 2.0F;
        float top = getY() + this.height + LABEL_GAP;
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, top);
        graphics.pose().scale(scale, scale);
        graphics.fill(-textWidth / 2 - 2, -1, textWidth / 2 + 2, 9, UiTheme.dark() ? 0xC0101014 : 0xC0F5EDD9);
        graphics.text(font, name, -textWidth / 2, 0, UiDrawing.opaque(UiDrawing.teamColor(role.getTeam())), false);
        graphics.pose().popMatrix();
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
