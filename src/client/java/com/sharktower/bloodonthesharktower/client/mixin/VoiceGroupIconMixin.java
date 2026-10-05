package com.sharktower.bloodonthesharktower.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;
/** SVC otherwise advertises another player's private group beside their nametag. */
@Pseudo
@Mixin(targets="de.maxhenkel.voicechat.voice.client.RenderEvents",remap=false)
public abstract class VoiceGroupIconMixin {
    @Inject(method="renderPlayerIcon",at=@At("HEAD"),cancellable=true,remap=false)
    private void sharktower$hideGroup(UUID id,boolean discrete,Component name,Identifier texture,PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo ci) {
        if ((!ClientState.playerSeatNumbers.isEmpty() || !ClientState.storytellerPlayers.isEmpty())
                && texture.getNamespace().equals("voicechat") && texture.getPath().equals("icons/group")) ci.cancel();
    }
}
