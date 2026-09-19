package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.hud.GameEndAnimationHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Input-holding screen used during the end-game cinematic.
 *
 * Like the original BOTB holding screen it intentionally draws nothing; the
 * cinematic remains a HUD render above the world while normal screen input is
 * held until the animation completes.
 */
public final class GameEndHoldingScreen extends Screen {
    public GameEndHoldingScreen() {
        super(Component.empty());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Render after the normal HUD/chat so the cinematic truly owns the
        // whole screen, matching the original BOTB presentation.
        GameEndAnimationHUD.render(graphics, Minecraft.getInstance());
    }

    @Override
    public void onClose() {
        // Do not let Escape dismiss the cinematic early.
        if (!GameEndAnimationHUD.isAnimating()) super.onClose();
    }
}
