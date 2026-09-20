package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.ReminderChooseScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Small clickable reminder token placed around a Grimoire role token. */
public final class GrimoireReminderWidget extends AbstractWidget {
    private final UUID playerId;
    private final int seat;
    private final Reminder reminder;

    public GrimoireReminderWidget(int x, int y, int size, UUID playerId, int seat, Reminder reminder) {
        super(x, y, size, size, Component.literal(
                reminder == null || reminder.text().isBlank() ? "Reminder" : reminder.text()));
        this.playerId = playerId;
        this.seat = seat;
        this.reminder = reminder == null
                ? new Reminder("", java.util.Optional.empty())
                : reminder;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (reminder.text().isBlank()) {
            UiDrawing.emptyReminderSlot(graphics, getX(), getY(), width);
        } else {
            ScriptRole sourceRole = sourceRole();
            if (sourceRole != null) {
                // Original-style reminder token: use the source character's icon
                // rather than a generic first-letter tile. This makes e.g.
                // Poisoner's "Poisoned" marker visibly a Poisoner reminder.
                UiDrawing.roleToken(graphics, sourceRole, getX(), getY(), width);
                graphics.outline(getX(), getY(), width, height,
                        isHovered() ? UiDrawing.GOLD : 0xFFE6D6A8);
            } else if (reminder.text().equalsIgnoreCase("Good")) {
                drawThumb(graphics, true);
            } else if (reminder.text().equalsIgnoreCase("Evil")) {
                drawThumb(graphics, false);
            } else {
                // Generic reminders with no universal symbol retain the
                // parchment/letter fallback.
                graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFFE6D6A8);
                graphics.outline(getX(), getY(), width, height,
                        isHovered() ? UiDrawing.GOLD : UiDrawing.BLACK);
                String mark = reminder.text().substring(0, 1).toUpperCase();
                var font = Minecraft.getInstance().font;
                graphics.text(font, mark,
                        getX() + (width - font.width(mark)) / 2,
                        getY() + (height - font.lineHeight) / 2, 0xFF111111, false);
            }
        }
        if (isHovered()) {
            graphics.outline(getX() - 1, getY() - 1, width + 2, height + 2, UiDrawing.GOLD);
            GrimoireHoverHints.set(reminder.text().isBlank()
                    ? "Reminder slot — click to edit"
                    : reminder.text() + " — click to edit reminders");
        }
    }

    /**
     * Tiny pixel-art thumbs so Good/Evil have a universal visual marker without
     * depending on emoji/font availability. Good is thumbs-up, Evil thumbs-down.
     */
    private void drawThumb(GuiGraphicsExtractor graphics, boolean up) {
        int x = getX();
        int y = getY();
        int bg = up ? UiDrawing.GOOD : UiDrawing.EVIL;
        int hand = 0xFFFFE0B2;
        int edge = 0xFF5A432D;

        graphics.fill(x, y, x + width, y + height, bg);
        graphics.outline(x, y, width, height, isHovered() ? UiDrawing.GOLD : UiDrawing.BLACK);

        if (up) {
            // Palm.
            graphics.fill(x + 6, y + 6, x + 12, y + 12, hand);
            // Raised thumb.
            graphics.fill(x + 9, y + 2, x + 12, y + 7, hand);
            graphics.fill(x + 8, y + 3, x + 10, y + 6, hand);
            // Wrist.
            graphics.fill(x + 3, y + 8, x + 7, y + 13, hand);
            // A couple of dark pixels make the silhouette readable at 16px.
            graphics.fill(x + 5, y + 7, x + 6, y + 12, edge);
            graphics.fill(x + 11, y + 4, x + 12, y + 6, edge);
        } else {
            // Palm.
            graphics.fill(x + 6, y + 4, x + 12, y + 10, hand);
            // Lowered thumb.
            graphics.fill(x + 9, y + 9, x + 12, y + 14, hand);
            graphics.fill(x + 8, y + 10, x + 10, y + 13, hand);
            // Wrist.
            graphics.fill(x + 3, y + 3, x + 7, y + 8, hand);
            graphics.fill(x + 5, y + 4, x + 6, y + 9, edge);
            graphics.fill(x + 11, y + 10, x + 12, y + 12, edge);
        }
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
