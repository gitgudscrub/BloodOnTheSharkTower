package com.sharktower.bloodonthesharktower.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** First-person arms use a separate path from the full player-body renderer. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Redirect(method = "renderHand", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/UvMapping;)V"))
    private void sharktower$ghostFirstPersonHand(SubmitNodeCollector collector, ModelPart arm,
            PoseStack poses, RenderType renderType, int light, int overlay, UvMapping uvMapping) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean dead = minecraft.player != null
                && ClientState.playerDeathStatus.getOrDefault(minecraft.player.getUUID(), false);
        collector.submitModelPart(arm, poses, renderType, light, overlay, uvMapping,
                dead ? 0x80FFFFFF : 0xFFFFFFFF);
    }
}
