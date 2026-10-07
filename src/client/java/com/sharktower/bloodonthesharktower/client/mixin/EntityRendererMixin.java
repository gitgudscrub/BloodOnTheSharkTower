package com.sharktower.bloodonthesharktower.client.mixin;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method="extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",at=@At("RETURN"))
    private void sharktower$hideNightNames(Entity entity,EntityRenderState state,float partial,double nameDistance,double belowDistance,CallbackInfo ci) {
        if (entity instanceof Player && ClientState.phase()==GamePhase.NIGHT) { state.nameTag=null; state.scoreText=null; }
    }
}
