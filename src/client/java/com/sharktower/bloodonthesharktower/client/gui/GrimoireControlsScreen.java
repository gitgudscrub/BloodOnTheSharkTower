package com.sharktower.bloodonthesharktower.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dedicated non-overlapping help page for Grimoire mouse shortcuts. */
public final class GrimoireControlsScreen extends Screen {
    public GrimoireControlsScreen() {
        super(Component.literal("Grimoire Controls"));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b ->
                        this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(this.width / 2 - 90, this.height - 30, 180, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int y = 28;

        drawCentered(graphics, "GRIMOIRE CONTROLS", cx, y, UiDrawing.GOLD, true);
        y += 26;
        drawCentered(graphics, "Left-click portrait/token — Edit role, alignment and reminders",
                cx, y, UiDrawing.TEXT, false);
        y += 18;
        drawCentered(graphics, "Right-click portrait/token — Open Player Actions",
                cx, y, UiDrawing.TEXT, false);
        y += 18;
        drawCentered(graphics, "Shift + Left-click — Select / clear nominator",
                cx, y, UiDrawing.YES, false);
        y += 18;
        drawCentered(graphics, "Shift + Right-click — Nominate that player",
                cx, y, UiDrawing.YES, false);
        y += 26;
        drawCentered(graphics, "Left Shift and Right Shift are both accepted.",
                cx, y, UiDrawing.MUTED, false);
        y += 15;
        drawCentered(graphics, "The same actions are available as normal buttons via Right-click > Player Actions.",
                cx, y, UiDrawing.MUTED, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, colour, shadow);
    }
}
