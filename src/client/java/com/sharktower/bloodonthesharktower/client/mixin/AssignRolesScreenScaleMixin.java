package com.sharktower.bloodonthesharktower.client.mixin;

import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keep the Grimoire at a stable physical footprint regardless of Minecraft's
 * global GUI scale. The layout was most usable at the equivalent of GUI scale 2;
 * compensate both upward and downward so Auto/1x/2x/3x/4x use the same canvas.
 */
@Mixin(AssignRolesScreen.class)
public abstract class AssignRolesScreenScaleMixin {
    @Inject(method = "grimoireUiScale", at = @At("HEAD"), cancellable = true, remap = false)
    private void sharktower$stablePhysicalGrimoireScale(CallbackInfoReturnable<Float> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            cir.setReturnValue(1.0F);
            return;
        }

        int activeGuiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        cir.setReturnValue(2.0F / (float) activeGuiScale);
    }
}
