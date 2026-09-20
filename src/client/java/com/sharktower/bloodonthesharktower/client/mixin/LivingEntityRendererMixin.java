package com.sharktower.bloodonthesharktower.client.mixin;

import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes BOTS-dead player bodies render as translucent ghosts.
 *
 * The public death map is already synchronized to every client, so this is a
 * presentation-only client hook and does not introduce any hidden information.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState> {
    private static final int SHARKTOWER_GHOST_ALPHA = 0x80;

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void sharktower$useTranslucentRenderType(
            S state,
            boolean isBodyVisible,
            boolean forceTransparent,
            boolean appearGlowing,
            CallbackInfoReturnable<RenderType> cir
    ) {
        if (!isBodyVisible || !sharktower$isDeadPlayer(state)) return;
        cir.setReturnValue(RenderTypes.entityTranslucent(getTextureLocation(state), appearGlowing));
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void sharktower$applyGhostAlpha(S state, CallbackInfoReturnable<Integer> cir) {
        if (!sharktower$isDeadPlayer(state)) return;

        int base = cir.getReturnValue();
        int vanillaAlpha = (base >>> 24) & 0xFF;
        int alpha = Math.min(vanillaAlpha, SHARKTOWER_GHOST_ALPHA);
        cir.setReturnValue((alpha << 24) | (base & 0x00FFFFFF));
    }

    private static boolean sharktower$isDeadPlayer(LivingEntityRenderState state) {
        if (!(state instanceof AvatarRenderState avatarState)) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;

        Entity entity = minecraft.level.getEntity(avatarState.id);
        if (!(entity instanceof Player player)) return false;

        return ClientState.playerDeathStatus.getOrDefault(player.getUUID(), false);
    }
}
