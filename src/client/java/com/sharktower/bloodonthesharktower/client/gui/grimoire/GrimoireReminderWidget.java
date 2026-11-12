package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.ReminderChooseScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.client.gui.UiTheme;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * A reminder in the Grimoire.
 *
 * Normally drawn as a labelled chip: the source character's icon followed by
 * the reminder text ("Poisoned", "Drunk 3"), with a coloured edge for the kind
 * of effect. The text is what tells two reminders from the same character
 * apart, so it is always visible. When there is no room for a chip the widget
 * falls back to the small icon-only square, and an overflow chip ("+2") stands
 * in for reminders that did not fit. Every form opens the reminder editor.
 */
public final class GrimoireReminderWidget extends AbstractWidget {
    public static final int CHIP_HEIGHT = 11;
    public static final int CHIP_MAX_WIDTH = 86;
    private static final int ICON = 9;

    private static final int HARMFUL = UiTheme.CHIP_HARMFUL;
    private static final int PROTECTIVE = UiTheme.CHIP_PROTECTIVE;
    private static final int NEUTRAL = UiTheme.CHIP_NEUTRAL;

    private final UUID playerId;
    private final int seat;
    private final Reminder reminder;
    private final boolean chip;
    private final int overflow;

    /** Icon-only square (the fallback when no chip fits). */
    public GrimoireReminderWidget(int x, int y, int size, UUID playerId, int seat, Reminder reminder) {
        this(x, y, size, size, playerId, seat, reminder, false, 0);
    }

    /** Labelled chip. */
    public static GrimoireReminderWidget chip(int x, int y, UUID playerId, int seat, Reminder reminder) {
        return new GrimoireReminderWidget(x, y, chipWidth(reminder), CHIP_HEIGHT, playerId, seat, reminder, true, 0);
    }

    /** "+N" chip for reminders that did not fit. */
    public static GrimoireReminderWidget overflow(int x, int y, UUID playerId, int seat, int hidden) {
        return new GrimoireReminderWidget(x, y, overflowWidth(hidden), CHIP_HEIGHT, playerId, seat, null, true, hidden);
    }

    private GrimoireReminderWidget(int x, int y, int width, int height, UUID playerId, int seat,
                                   Reminder reminder, boolean chip, int overflow) {
        super(x, y, width, height, Component.literal(
                overflow > 0 ? overflow + " more reminders"
                        : reminder == null || reminder.text().isBlank() ? "Reminder" : reminder.text()));
        this.playerId = playerId;
        this.seat = seat;
        this.reminder = reminder == null ? new Reminder("", java.util.Optional.empty()) : reminder;
        this.chip = chip;
        this.overflow = overflow;
    }

    public static int chipWidth(Reminder reminder) {
        Font font = Minecraft.getInstance().font;
        String text = reminder == null ? "" : reminder.text();
        return Math.min(CHIP_MAX_WIDTH, 9 + ICON + 3 + font.width(text) + 4);
    }

    public static int overflowWidth(int hidden) {
        return Minecraft.getInstance().font.width("+" + hidden) + 6;
    }

    /** Harmful (red), protective (green) or other (parchment). The words say it too. */
    static int effectColour(String text) {
        String key = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (key.startsWith("poison") || key.startsWith("drunk") || key.equals("dead") || key.equals("cursed")
                || key.equals("everyoneisdrunk") || key.equals("noability") || key.equals("mad")
                || key.equals("diedtoday") || key.equals("evil")) {
            return HARMFUL;
        }
        if (key.equals("safe") || key.equals("cannotdie") || key.equals("survivesexecution")
                || key.equals("protected") || key.equals("good")) {
            return PROTECTIVE;
        }
        return NEUTRAL;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float reveal = GrimoireRevealAnimation.progressForSeat(seat);
        if (!GrimoireRevealAnimation.beginElement(
                graphics, getX(), getY(), this.width, this.height, reveal)) return;
        try {
            if (overflow > 0) drawOverflow(graphics);
            else if (chip) drawChip(graphics);
            else drawSquare(graphics);

            if (isHovered()) {
                graphics.outline(getX() - 1, getY() - 1, width + 2, height + 2, UiDrawing.GOLD);
                GrimoireHoverHints.set(overflow > 0 ? overflow + " more reminders — click to see and edit"
                        : reminder.text().isBlank() ? "Reminder slot — click to edit"
                        : hoverText() + " — click to edit reminders");
            }
        } finally {
            GrimoireRevealAnimation.endElement(graphics);
        }
    }

    private String hoverText() {
        ScriptRole source = sourceRole();
        return source == null ? reminder.text() : source.getDisplayName() + ": " + reminder.text();
    }

    private void drawChip(GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int edge = effectColour(reminder.text());
        // Parchment chip (ink chip in light mode), rounded ends, a wax-seal dot in the effect colour.
        graphics.fill(x + 1, y, x + width - 1, y + height, UiTheme.chipBackground());
        graphics.fill(x, y + 1, x + width, y + height - 1, UiTheme.chipBackground());
        graphics.fill(x + 1, y, x + width - 1, y + 1, UiTheme.chipBorder());
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, UiTheme.chipBorder());
        graphics.fill(x, y + 1, x + 1, y + height - 1, UiTheme.chipBorder());
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, UiTheme.chipBorder());
        graphics.fill(x + 3, y + 3, x + 7, y + 8, edge);
        graphics.fill(x + 4, y + 2, x + 6, y + 9, edge);
        drawIcon(graphics, x + 9, y + 1, ICON);
        String text = reminder.text();
        int room = width - (9 + ICON + 3) - 2;
        if (font.width(text) > room) text = font.plainSubstrByWidth(text, room - font.width(".")) + ".";
        graphics.text(font, text, x + 9 + ICON + 3, y + 2, UiTheme.chipText(), false);
    }

    private void drawOverflow(GuiGraphicsExtractor graphics) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, UiTheme.chipBackground());
        graphics.outline(getX(), getY(), width, height, UiTheme.chipBorder());
        graphics.text(font, "+" + overflow, getX() + 3, getY() + 2, UiTheme.dark() ? 0xFF8A5A10 : UiTheme.AMBER, false);
    }

    private void drawSquare(GuiGraphicsExtractor graphics) {
        if (reminder.text().isBlank()) {
            UiDrawing.emptyReminderSlot(graphics, getX(), getY(), width);
            return;
        }
        drawIcon(graphics, getX(), getY(), width);
        graphics.outline(getX(), getY(), width, height, effectColour(reminder.text()));
    }

    /** Source character icon; Good/Evil get a coloured G/E; other text its first letter on parchment. */
    private void drawIcon(GuiGraphicsExtractor graphics, int x, int y, int size) {
        Font font = Minecraft.getInstance().font;
        ScriptRole source = sourceRole();
        if (source != null) {
            UiDrawing.roleIcon(graphics, source, x, y, size);
            return;
        }
        String text = reminder.text();
        int background = text.equalsIgnoreCase("Good") ? UiDrawing.GOOD
                : text.equalsIgnoreCase("Evil") ? UiDrawing.EVIL : 0xFFE6D6A8;
        int ink = background == 0xFFE6D6A8 ? 0xFF111111 : 0xFFFFFFFF;
        String mark = text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.fill(x, y, x + size, y + size, background | 0xFF000000);
        graphics.text(font, mark, x + (size - font.width(mark) + 1) / 2, y + (size - 8) / 2 + 1, ink, false);
    }

    private ScriptRole sourceRole() {
        if (reminder.role().isPresent()) {
            return new ScriptRole.Official(reminder.role().get());
        }
        if (reminder.customRoleId().isPresent() && ClientState.currentScript != null) {
            String id = reminder.customRoleId().get();
            for (ScriptRole role : ClientState.currentScript.allRoles()) {
                if (role.getId().equalsIgnoreCase(id)) return role;
            }
        }
        return null;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft.getInstance().gui.setScreen(new ReminderChooseScreen(playerId, seat));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
