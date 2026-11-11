package com.sharktower.bloodonthesharktower.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * The one button every Sharktower screen uses.
 *
 * Draws the same dark panel, soft border and gold hover ring as the role card
 * and the other HUD panels, so screens stop looking like a vanilla debug menu.
 * Text colour follows the label's own style when it has one (team and action
 * colours), otherwise it is white; inactive buttons are muted. Labels that do
 * not fit are trimmed with an ellipsis rather than overflowing the button.
 *
 * The builder mirrors {@code SharktowerButton.builder(...)} so call sites only change the
 * class name.
 */
public class SharktowerButton extends AbstractWidget {
    public static final int BACKGROUND = 0xE0141418;
    public static final int BACKGROUND_HOVER = 0xE8222634;
    public static final int BACKGROUND_INACTIVE = 0xB00E0E12;
    public static final int BORDER_SOFT = 0xFF5D5D68;
    public static final int BORDER_HOVER = UiDrawing.GOLD;
    public static final int TEXT_INACTIVE = 0xFF7C7C86;

    @FunctionalInterface
    public interface OnPress {
        void onPress(SharktowerButton button);
    }

    private final OnPress onPress;

    protected SharktowerButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean highlighted = this.active && isHoveredOrFocused();
        int background = !this.active ? BACKGROUND_INACTIVE : highlighted ? BACKGROUND_HOVER : BACKGROUND;
        graphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, background);
        graphics.outline(getX(), getY(), this.width, this.height, highlighted ? BORDER_HOVER : BORDER_SOFT);

        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int colour = this.active ? labelColour(message) : TEXT_INACTIVE;
        String text = message.getString();
        int maxWidth = this.width - 8;
        if (font.width(text) > maxWidth) {
            String ellipsis = "…";
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }
        int textX = getX() + (this.width - font.width(text)) / 2;
        int textY = getY() + (this.height - font.lineHeight) / 2 + 1;
        graphics.text(font, text, textX, textY, colour, true);
    }

    /** The label's own colour when it was styled (team or action colour), otherwise white. */
    private static int labelColour(Component message) {
        TextColor styled = message.getStyle().getColor();
        if (styled != null) return UiDrawing.opaque(styled.getValue());
        for (Component sibling : message.getSiblings()) {
            TextColor colour = sibling.getStyle().getColor();
            if (colour != null) return UiDrawing.opaque(colour.getValue());
        }
        return UiDrawing.TEXT;
    }

    public void press() {
        this.onPress.onPress(this);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active || !this.visible) return false;
        if (event.isSelection()) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            press();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** Same shape as {@code Button.Builder}. */
    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Tooltip tooltip;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public SharktowerButton build() {
            SharktowerButton button = new SharktowerButton(x, y, width, height, message, onPress);
            if (tooltip != null) button.setTooltip(tooltip);
            return button;
        }
    }

    /** Convenience for the many "Back" / "Done" footers. */
    public static Component label(String text, ChatFormatting colour) {
        return colour == null ? Component.literal(text) : Component.literal(text).withStyle(colour);
    }
}
