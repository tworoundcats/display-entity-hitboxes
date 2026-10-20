package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCanHitMixin {

    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private void dbh$canHit(CallbackInfoReturnable<Boolean> cir) {
        if (DisplayHitboxes.isTargetable((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isCollidable", at = @At("HEAD"), cancellable = true)
    private void dbh$isCollidable(CallbackInfoReturnable<Boolean> cir) {
        if (DisplayHitboxes.hasCollision((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "collidesWith", at = @At("HEAD"))
    private void dbh$collidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        DisplayHitboxConfig.get();
    }

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void dbh$getBoundingBox(CallbackInfoReturnable<Box> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof DisplayEntity display) || !DisplayHitboxes.isTargetable(display)) return;

        Box box = DisplayHitboxes.computeBox(display, false);
        if (box != null) cir.setReturnValue(box);
    }
}