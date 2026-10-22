package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.entity.Entity;
import net.tworoundcats.displayblockhitboxes.DisplayBlockHitboxesClient;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {

    @Inject(method = "drawHitbox", at = @At("HEAD"), cancellable = true)
    private void dbh$hideDefaultHitbox(Entity entity, float tickProgress, boolean inLocalServer, CallbackInfo ci) {
        if (DisplayHitboxes.isTargetableType(entity.getType())) {
            DisplayBlockHitboxesClient.noteVanillaHitboxes();
            ci.cancel();
        }
    }
}