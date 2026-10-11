package com.sharktower.bloodonthesharktower.mixin;

import com.sharktower.bloodonthesharktower.wraith.WraithNightManager;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Vanilla spectators may teleport directly to players via the spectator hotbar.
 * Wraiths must instead follow the Storyteller physically; deny only this shortcut.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class WraithSpectatorTeleportMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleTeleportToEntityPacket", at = @At("HEAD"), cancellable = true)
    private void sharktower$denyWraithTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        UUID playerId = player.getUUID();
        if (WraithNightManager.isActiveWraith(playerId)) ci.cancel();
    }
}
