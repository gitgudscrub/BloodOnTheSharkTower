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
    // Set by UiTheme.apply(); read at draw time.
    public static int BACKGROUND = 0xE0141418;
    public static int BACKGROUND_HOVER = 0xE8222634;
    public static int BACKGROUND_INACTIVE = 0xB00E0E12;
    public static int BORDER_SOFT = 0xFF5D5D68;
    public static int BORDER_HOVER = 0xFFFFD166;
    public static int TEXT_INACTIVE = 0xFF7C7C86;

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
        // Bevel: a light line along the top, a dark one along the bottom.
        boolean dark = UiTheme.dark();
        graphics.fill(getX() + 1, getY() + 1, getX() + this.width - 1, getY() + 2, dark ? 0x18FFFFFF : 0x80FFFFFF);
        graphics.fill(getX() + 1, getY() + this.height - 2, getX() + this.width - 1, getY() + this.height - 1, dark ? 0x60000000 : 0x30000000);
        graphics.outline(getX(), getY(), this.width, this.height, highlighted ? BORDER_HOVER : BORDER_SOFT);
        if (highlighted && this.active) {
            graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, dark ? 0x60C9A24A : 0x405C4315);
        }

        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int colour = this.active ? labelColour(message) : TEXT_INACTIVE;
        if (!this.active && !dark) colour = TEXT_INACTIVE;
        else if (this.active && !dark) colour = parchmentInk(colour);
        String text = message.getString();
        int maxWidth = this.width - 8;
        // Labels stay in the regular font so every button on a screen matches;
        // the display face is for titles, section labels and role names.
        // A long label (a long player name, say) shrinks to fit, down to 60%,
        // and is only shortened with an ellipsis past that.
        float scale = Math.max(0.6F, Math.min(1.0F, maxWidth / (float) Math.max(1, font.width(text))));
        int room = (int) (maxWidth / scale);
        if (font.width(text) > room) {
            String ellipsis = "…";
            text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(ellipsis))) + ellipsis;
        }
        float textX = getX() + (this.width - font.width(text) * scale) / 2;
        float textY = getY() + (this.height - font.lineHeight * scale) / 2 + scale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, colour, dark);
        graphics.pose().popMatrix();
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

    /**
     * On the light Parchment buttons the bright chat colours (green, grey,
     * yellow, aqua...) wash out, so each one is swapped for a darker ink
     * with the same meaning. Other colours (team colours) pass through.
     */
    static int parchmentInk(int colour) {
        return switch (colour & 0xFFFFFF) {
            case 0xFFFFFF -> UiDrawing.TEXT;
            case 0x55FF55 -> 0xFF2E6B2E; // green: safe / ok
            case 0xAAAAAA -> 0xFF5E5240; // grey: quiet actions
            case 0xFF5555 -> 0xFF9E2A20; // red: danger
            case 0xFFFF55 -> 0xFF7A5C00; // yellow
            case 0xFFAA00 -> 0xFF8A5A10; // gold
            case 0x55FFFF -> 0xFF1F6670; // aqua
            case 0x5555FF -> 0xFF2F4FA0; // blue
            case 0xFF55FF -> 0xFF8A2C8A; // light purple
            default -> colour;
        };
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
