package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    private void dbh$originIndependentLight(T entity, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof Display display
                && DisplayHitboxConfig.get().originIndependentLighting
                && (display instanceof Display.BlockDisplay || display instanceof Display.ItemDisplay)) {
            cir.setReturnValue(DisplayHitboxes.sampleLight(display));
        }
    }
}