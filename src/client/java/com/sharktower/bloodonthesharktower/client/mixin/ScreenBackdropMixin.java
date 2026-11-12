package com.sharktower.bloodonthesharktower.client.mixin;

import com.sharktower.bloodonthesharktower.client.gui.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every Sharktower screen gets the theme's backdrop over the blurred world:
 * a dark vignette in Candlelit, a parchment sheet in Parchment. Vanilla and
 * other mods' screens are left alone.
 */
@Mixin(Screen.class)
public abstract class ScreenBackdropMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void bots$themeBackdrop(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!getClass().getName().startsWith("com.sharktower.bloodonthesharktower.")) return;
        UiTheme.screenBackdrop(graphics, width, height);
    }
}
