package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "getLight", at = @At("HEAD"), cancellable = true)
    private void dbh$originIndependentLight(T entity, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof DisplayEntity display
                && DisplayHitboxConfig.get().originIndependentLighting
                && (display instanceof DisplayEntity.BlockDisplayEntity || display instanceof DisplayEntity.ItemDisplayEntity)) {
            cir.setReturnValue(DisplayHitboxes.sampleLight(display));
        }
    }
}