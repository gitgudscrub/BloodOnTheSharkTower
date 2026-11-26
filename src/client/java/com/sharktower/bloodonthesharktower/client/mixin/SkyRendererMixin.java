package com.sharktower.bloodonthesharktower.client.mixin;

import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Night lock: no sun, moon or stars, which fog does not cover. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void bots$nightLockSky(CallbackInfo ci) {
        if (ClientState.nightLocked) ci.cancel();
    }
}
