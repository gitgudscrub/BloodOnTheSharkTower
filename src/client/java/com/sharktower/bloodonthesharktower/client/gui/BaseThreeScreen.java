package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Dedicated picker for Trouble Brewing, Bad Moon Rising, and Sects & Violets.
 * Base 3 remains visually and functionally separate from custom scripts.
 */
public final class BaseThreeScreen extends Screen {
    private static boolean openGrimoireAfterScriptSync;

    public BaseThreeScreen() {
        super(Component.literal("Blood on the Sharktower — Base 3"));
    }

    @Override
    protected void init() {
        int buttonWidth = 240;
        int buttonHeight = 28;
        int left = (this.width - buttonWidth) / 2;
        int top = Math.max(58, this.height / 2 - 72);
        int gap = 10;

        this.addRenderableWidget(Button.builder(
                        Component.literal("Trouble Brewing").withStyle(ChatFormatting.DARK_RED),
                        b -> load("tb"))
                .bounds(left, top, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(
                        Component.literal("Bad Moon Rising").withStyle(ChatFormatting.GOLD),
                        b -> load("bmr"))
                .bounds(left, top + buttonHeight + gap, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(
                        Component.literal("Sects & Violets").withStyle(ChatFormatting.DARK_PURPLE),
                        b -> load("snv"))
                .bounds(left, top + (buttonHeight + gap) * 2, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back to Script Builder"), b ->
                        this.minecraft.gui.setScreen(new ScriptBuilderScreen()))
                .bounds((this.width - 180) / 2, this.height - 34, 180, 20).build());
    }

    private void load(String id) {
        // Keep this picker open while the server replaces the script. Opening
        // a fresh Grimoire before the authoritative script/reset packets arrive
        // can leave Minecraft back in the world when the setup state refreshes.
        // CoreStateReceivers consumes this flag on SendScript and opens the Grim
        // only after the new Base 3 script is actually installed client-side.
        openGrimoireAfterScriptSync = true;
        ClientStorytellerActions.send("load_base3", id);
        ScriptBuilderScreen.invalidateSelectionCache();
    }

    public static boolean consumeOpenGrimoireAfterScriptSync() {
        if (!openGrimoireAfterScriptSync) return false;
        openGrimoireAfterScriptSync = false;
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = "Base 3 Scripts";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, true);

        String subtitle = "Bundled separately from custom scripts — one click loads the full official script";
        graphics.text(this.font, subtitle, (this.width - this.font.width(subtitle)) / 2, 34, UiDrawing.MUTED, false);

        int infoY = Math.max(58, this.height / 2 - 72) + 3 * 38 + 10;
        String info = "Trouble Brewing: 22 roles   •   Bad Moon Rising: 25   •   Sects & Violets: 25";
        graphics.text(this.font, info, (this.width - this.font.width(info)) / 2, infoY, UiDrawing.TEXT, false);
    }
}
