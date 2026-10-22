package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCanHitMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void dbh$canHit(CallbackInfoReturnable<Boolean> cir) {
        if (DisplayHitboxes.isTargetable((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void dbh$isCollidable(CallbackInfoReturnable<Boolean> cir) {
        if (DisplayHitboxes.hasCollision((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"))
    private void dbh$collidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        DisplayHitboxConfig.get();
    }

    // getBoundingBox override DELETED to prevent GC memory leaks and Frustum Culling bugs!
}