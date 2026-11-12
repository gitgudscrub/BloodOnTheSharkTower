package com.sharktower.bloodonthesharktower.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dedicated non-overlapping help page for Grimoire mouse shortcuts. */
public final class GrimoireControlsScreen extends Screen {
    public GrimoireControlsScreen() {
        super(Component.literal("Grimoire Controls"));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back to Grimoire"), b ->
                        this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(this.width / 2 - 90, this.height - 30, 180, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int y = 28;
        int wrap = Math.max(160, this.width - 32);

        drawCentered(graphics, "GRIMOIRE CONTROLS", cx, y, UiDrawing.GOLD, true, wrap);
        y += 22;
        y = drawCentered(graphics, "Left-click portrait/token — Edit role, alignment and reminders", cx, y, UiDrawing.TEXT, false, wrap) + 6;
        y = drawCentered(graphics, "Storyteller right-click portrait/token — Open Player Actions", cx, y, UiDrawing.TEXT, false, wrap) + 6;
        y = drawCentered(graphics, "Shift + Left-click — Select / clear nominator", cx, y, UiDrawing.YES, false, wrap) + 6;
        y = drawCentered(graphics, "Shift + Right-click — Nominate that player", cx, y, UiDrawing.YES, false, wrap) + 12;
        y = drawCentered(graphics, "Left Shift and Right Shift are both accepted.", cx, y, UiDrawing.MUTED, false, wrap) + 3;
        y = drawCentered(graphics, "The same actions are available as normal buttons via Right-click > Player Actions.",
                cx, y, UiDrawing.MUTED, false, wrap) + 10;
        y = drawCentered(graphics, "Blue = Good (G), red = Evil (E), grey = no alignment read.",
                cx, y, UiDrawing.MUTED, false, wrap) + 3;
        drawCentered(graphics, "Player borders follow personal Good/Evil reminders; Storyteller borders show actual alignment.",
                cx, y, UiDrawing.MUTED, false, wrap);
    }

    /** Draws centred text wrapped to {@code wrap} pixels and returns the y below it. */
    private int drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int colour, boolean shadow, int wrap) {
        for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.literal(text), wrap)) {
            graphics.text(this.font, line, centerX - this.font.width(line) / 2, y, colour, shadow);
            y += 10;
        }
        return y;
    }

}
