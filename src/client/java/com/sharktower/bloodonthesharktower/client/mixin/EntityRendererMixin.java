package com.sharktower.bloodonthesharktower.client.mixin;
import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player name tags: hidden at Night, and outside Night the vanilla below-name
 * line (normally a scoreboard score) shows the player's chosen pronouns. Using
 * the vanilla second line keeps the tag one stacked unit, so the role token and
 * vote marker renderers can sit above it without overlapping.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method="extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",at=@At("RETURN"))
    private void sharktower$playerNameTags(Entity entity,EntityRenderState state,float partial,double nameDistance,double belowDistance,CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        if (ClientState.phase()==GamePhase.NIGHT) { state.nameTag=null; state.scoreText=null; return; }
        if (state.nameTag==null || !ClientSettings.pronounTags) return;
        String pronouns = ClientState.pronouns(entity.getUUID());
        if (!pronouns.isEmpty()) state.scoreText = Component.literal(pronouns).withStyle(ChatFormatting.GRAY);
    }
}
