package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.ReminderChooseScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
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
    private final String text;

    public GrimoireReminderWidget(int x, int y, int size, UUID playerId, int seat, String text) {
        super(x, y, size, size, Component.literal(text == null ? "Reminder" : text));
        this.playerId = playerId;
        this.seat = seat;
        this.text = text == null ? "" : text;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (text.isBlank()) {
            UiDrawing.emptyReminderSlot(graphics, getX(), getY(), width);
        } else {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFFE6D6A8);
            graphics.outline(getX(), getY(), width, height, isHovered() ? UiDrawing.GOLD : UiDrawing.BLACK);
            String mark = text.substring(0, 1).toUpperCase();
            var font = Minecraft.getInstance().font;
            graphics.text(font, mark,
                    getX() + (width - font.width(mark)) / 2,
                    getY() + (height - font.lineHeight) / 2, 0xFF111111, false);
        }
        if (isHovered()) {
            graphics.outline(getX() - 1, getY() - 1, width + 2, height + 2, UiDrawing.GOLD);
        }
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
