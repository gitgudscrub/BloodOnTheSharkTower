package com.sharktower.bloodonthesharktower.mixin;

import com.sharktower.bloodonthesharktower.profile.PlayerProfiles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The tab list shows "Nickname (username)" for nicknamed players, so everyone
 * can always see which account is behind a nickname.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabListMixin {
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void sharktower$nickname(CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        String nick = PlayerProfiles.nickname(self.getUUID());
        if (nick.isEmpty()) return;
        cir.setReturnValue(Component.literal(nick)
                .append(Component.literal(" (" + self.getName().getString() + ")").withStyle(ChatFormatting.GRAY)));
    }
}
