package com.sharktower.bloodonthesharktower.mixin;

import com.sharktower.bloodonthesharktower.automation.GunslingerGun;
import com.sharktower.bloodonthesharktower.automation.SlayerCrossbow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A bolt from the Slayer's crossbow never hurts anyone or sticks in the
 * world: the hit is handed to {@link SlayerCrossbow#boltHit} instead. The
 * Gunslinger's crossbow works the same way ({@link GunslingerGun#boltHit}).
 */
@Mixin(AbstractArrow.class)
public abstract class SlayerBoltMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void sharktower$slayerHitEntity(EntityHitResult hit, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (GunslingerGun.isGunslingerBolt(self)) {
            ci.cancel();
            GunslingerGun.boltHit(self, hit.getEntity());
            return;
        }
        if (!SlayerCrossbow.isSlayerBolt(self)) return;
        ci.cancel();
        SlayerCrossbow.boltHit(self, hit.getEntity());
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void sharktower$slayerHitBlock(BlockHitResult hit, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (GunslingerGun.isGunslingerBolt(self)) {
            ci.cancel();
            GunslingerGun.boltHit(self, null);
            return;
        }
        if (!SlayerCrossbow.isSlayerBolt(self)) return;
        ci.cancel();
        SlayerCrossbow.boltHit(self, null);
    }
}
