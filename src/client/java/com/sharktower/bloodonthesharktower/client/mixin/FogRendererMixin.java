package com.sharktower.bloodonthesharktower.client.mixin;

import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Night lock: thick dark fog a few blocks out, so a locked player cannot see out of their house. */
@Mixin(value = FogRenderer.class, priority = 500) // before Sodium copies the fog values at RETURN
public abstract class FogRendererMixin {
    private static final float NIGHT_FOG_START = 2.0F;
    private static final float NIGHT_FOG_END = 9.0F;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void bots$nightLockFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkness,
                                   ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!ClientState.nightLocked) return;
        FogData fog = cir.getReturnValue();
        fog.environmentalStart = Math.min(fog.environmentalStart, NIGHT_FOG_START);
        fog.environmentalEnd = Math.min(fog.environmentalEnd, NIGHT_FOG_END);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, NIGHT_FOG_START);
        fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, NIGHT_FOG_END);
        fog.skyEnd = Math.min(fog.skyEnd, NIGHT_FOG_END);
        fog.cloudEnd = Math.min(fog.cloudEnd, NIGHT_FOG_END);
        fog.color.set(0.02F, 0.02F, 0.03F, 1.0F);
    }
}
