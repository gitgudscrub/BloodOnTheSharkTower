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
 * A reminder in the Grimoire, drawn as a mini token on the rim of the role
 * token: a small parchment disc with the source character's icon and a ring
 * in the effect colour (red harmful, green protective, gold otherwise).
 * Hovering names it ("Poisoner: Poisoned"); clicking opens the reminder
 * editor. A "+N" token stands in for reminders that did not fit.
 */
public final class GrimoireReminderWidget extends AbstractWidget {
    private final UUID playerId;
    private final int seat;
    private final Reminder reminder;
    private final int overflow;

    public static GrimoireReminderWidget rim(int x, int y, int size, UUID playerId, int seat, Reminder reminder) {
        return new GrimoireReminderWidget(x, y, size, playerId, seat, reminder, 0);
    }

    public static GrimoireReminderWidget overflow(int x, int y, int size, UUID playerId, int seat, int hidden) {
        return new GrimoireReminderWidget(x, y, size, playerId, seat, null, hidden);
    }

    private GrimoireReminderWidget(int x, int y, int size, UUID playerId, int seat, Reminder reminder, int overflow) {
        super(x, y, size, size, Component.literal(
                overflow > 0 ? overflow + " more reminders"
                        : reminder == null || reminder.text().isBlank() ? "Reminder" : reminder.text()));
        this.playerId = playerId;
        this.seat = seat;
        this.reminder = reminder == null ? new Reminder("", java.util.Optional.empty()) : reminder;
        this.overflow = overflow;
    }

    /** Harmful (red), protective (green) or other (gold). */
    static int effectColour(String text) {
        String key = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (key.startsWith("poison") || key.startsWith("drunk") || key.equals("dead") || key.equals("cursed")
                || key.equals("everyoneisdrunk") || key.equals("noability") || key.equals("mad")
                || key.equals("diedtoday") || key.equals("evil")) {
            return UiTheme.CHIP_HARMFUL;
        }
        if (key.equals("safe") || key.equals("cannotdie") || key.equals("survivesexecution")
                || key.equals("protected") || key.equals("good")) {
            return UiTheme.CHIP_PROTECTIVE;
        }
        return UiTheme.GOLD_DIM;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float reveal = GrimoireRevealAnimation.progressForSeat(seat);
        if (!GrimoireRevealAnimation.beginElement(
                graphics, getX(), getY(), this.width, this.height, reveal)) return;
        try {
            int x = getX();
            int y = getY();
            int size = this.width;
            // Ink shadow, parchment face, effect ring.
            UiTheme.disc(graphics, x, y + 1, size, UiTheme.dark() ? 0xA0000000 : 0x50000000);
            UiTheme.disc(graphics, x, y, size, UiTheme.dark() ? 0xFFE6D9BB : 0xFFF7EFDC);
            if (overflow > 0) {
                UiTheme.ring(graphics, x, y, size, UiTheme.GOLD_DIM);
                drawMark(graphics, x, y, size, "+" + overflow, UiTheme.PARCHMENT_INK);
            } else {
                UiTheme.ring(graphics, x, y, size, effectColour(reminder.text()));
                drawIcon(graphics, x + 2, y + 2, size - 4);
            }

            if (isHovered()) {
                UiTheme.ring(graphics, x - 2, y - 2, size + 4, UiTheme.GOLD);
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

    /** Source character icon; Good/Evil get a coloured G/E; other text its first letter. */
    private void drawIcon(GuiGraphicsExtractor graphics, int x, int y, int size) {
        ScriptRole source = sourceRole();
        if (source != null) {
            UiDrawing.roleIcon(graphics, source, x, y, size);
            return;
        }
        String text = reminder.text();
        int ink = text.equalsIgnoreCase("Good") ? UiTheme.GOOD_BADGE
                : text.equalsIgnoreCase("Evil") ? UiTheme.EVIL_BADGE : UiTheme.PARCHMENT_INK;
        String mark = text.isEmpty() ? "·" : text.substring(0, 1).toUpperCase(Locale.ROOT);
        drawMark(graphics, x, y, size, mark, ink);
    }

    /** Small centred text (75%) inside a disc. */
    private static void drawMark(GuiGraphicsExtractor graphics, int x, int y, int size, String mark, int colour) {
        Font font = Minecraft.getInstance().font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + size / 2.0F, y + size / 2.0F);
        graphics.pose().scale(0.75F, 0.75F);
        graphics.text(font, mark, -font.width(mark) / 2, -4, colour, false);
        graphics.pose().popMatrix();
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
