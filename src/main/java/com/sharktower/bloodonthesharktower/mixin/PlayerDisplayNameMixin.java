package com.sharktower.bloodonthesharktower.mixin;

import com.sharktower.bloodonthesharktower.profile.PlayerProfiles;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Shows a player's {@code /nick} wherever Minecraft uses their display name:
 * chat, death and join messages on the server, and name tags on the client.
 * Only the visible name changes; {@code getName()} and the game profile keep
 * the real username for permissions, commands and logs, and the vanilla hover
 * text on a chat name still identifies the real account.
 */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @ModifyArg(
            method = "getDisplayName",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 1)
    private Component sharktower$nickname(Component name) {
        Player self = (Player) (Object) this;
        String nick = self.level().isClientSide()
                ? ClientState.nickname(self.getUUID())
                : PlayerProfiles.nickname(self.getUUID());
        return nick.isEmpty() ? name : Component.literal(nick);
    }
}
