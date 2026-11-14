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
    private boolean labelAbove;
    private int labelMaxWidth = DEFAULT_LABEL_WIDTH;

    public GrimoirePlayerWidget(int x, int y, int size, UUID playerId, int seat,
                                PendingRoleAssignment assignment, boolean dead) {
        super(x, y, size, size, Component.literal("Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
        this.assignment = assignment;
        this.dead = dead;
    }

    /**
     * Where the role name goes: above the token for the upper seats, whose
     * lower side faces the head ring and its seat numbers, and how wide it may
     * be before it wraps or shrinks (the gap to the neighbouring tokens).
     */
    public GrimoirePlayerWidget label(boolean above, int maxWidth) {
        this.labelAbove = above;
        this.labelMaxWidth = maxWidth;
        return this;
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
    /** Label width when the layout gives none: the token plus a little either side. */
    public static final int DEFAULT_LABEL_WIDTH = 50;
    private static final float LABEL_SCALE = 0.75F;
    private static final float LABEL_MIN_SCALE = 0.6F;
    private static final int LINE_HEIGHT = 9;

    /** A role name fitted to a width: one or two lines at a scale, cut short only as a last resort. */
    public record LabelFit(java.util.List<String> lines, float scale) {
        public int width() {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int widest = 0;
            for (String line : lines) widest = Math.max(widest, font.width(line));
            return Math.round((widest + 4) * scale);
        }

        public int height() {
            return Math.round((lines.size() * LINE_HEIGHT + 1) * scale);
        }
    }

    /**
     * Fits a role name into {@code maxWidth} GUI pixels: whole at 3/4 size if
     * it fits, else split over two lines at a space ("FORTUNE / TELLER"), else
     * shrunk down to 60%, and only then cut short with a dot.
     */
    public static LabelFit fitLabel(String displayName, int maxWidth) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        String name = displayName.toUpperCase(java.util.Locale.ROOT);
        float room = maxWidth - 4 * LABEL_SCALE;
        if (font.width(name) * LABEL_SCALE <= room) return new LabelFit(java.util.List.of(name), LABEL_SCALE);
        java.util.List<String> best = null;
        int bestWidth = Integer.MAX_VALUE;
        for (int i = name.indexOf(' '); i > 0; i = name.indexOf(' ', i + 1)) {
            String a = name.substring(0, i).trim();
            String b = name.substring(i + 1).trim();
            int width = Math.max(font.width(a), font.width(b));
            if (width < bestWidth) {
                bestWidth = width;
                best = java.util.List.of(a, b);
            }
        }
        java.util.List<String> lines = best != null ? best : java.util.List.of(name);
        int widest = best != null ? bestWidth : font.width(name);
        float scale = Math.max(LABEL_MIN_SCALE, Math.min(LABEL_SCALE, (maxWidth - 4 * LABEL_MIN_SCALE) / widest));
        int limit = (int) Math.floor((maxWidth - 4 * scale) / scale);
        java.util.List<String> cut = new java.util.ArrayList<>();
        for (String line : lines) {
            cut.add(font.width(line) <= limit ? line : font.plainSubstrByWidth(line, limit - font.width(".")) + ".");
        }
        return new LabelFit(cut, scale);
    }

    /** The area the role name covers, as {x, y, width, height}, for keeping rim reminders off it. */
    public static int[] labelArea(int tokenX, int tokenY, int size, boolean above, LabelFit fit) {
        int width = fit.width();
        int height = fit.height();
        int top = above ? tokenY - LABEL_GAP - height : tokenY + size + LABEL_GAP - 1;
        return new int[]{tokenX + size / 2 - width / 2, top, width, height + 1};
    }

    private void drawRoleLabel(GuiGraphicsExtractor graphics, ScriptRole role) {
        String name = role.getDisplayName();
        if (name == null || name.isEmpty()) return;
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        LabelFit fit = fitLabel(name, labelMaxWidth);
        float scale = fit.scale();
        int[] area = labelArea(getX(), getY(), this.width, labelAbove, fit);
        float cx = getX() + this.width / 2.0F;
        int colour = UiDrawing.opaque(UiDrawing.teamColor(role.getTeam()));
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, area[1] + scale);
        graphics.pose().scale(scale, scale);
        int widest = 0;
        for (String line : fit.lines()) widest = Math.max(widest, font.width(line));
        graphics.fill(-widest / 2 - 2, -1, widest / 2 + 2, fit.lines().size() * LINE_HEIGHT,
                UiTheme.dark() ? 0xC0101014 : 0xC0F5EDD9);
        for (int i = 0; i < fit.lines().size(); i++) {
            String line = fit.lines().get(i);
            graphics.text(font, line, -font.width(line) / 2, i * LINE_HEIGHT, colour, false);
        }
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
