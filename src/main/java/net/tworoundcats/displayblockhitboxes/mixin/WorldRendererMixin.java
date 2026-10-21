package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.tworoundcats.displayblockhitboxes.DisplayBlockHitboxesClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderTargetBlockOutline", at = @At("RETURN"))
    private void dbh$drawDisplayOutline(CallbackInfo ci) {
        DisplayBlockHitboxesClient.renderOutline("outline");
    }

    @Inject(method = "renderBlockDamage", at = @At("RETURN"))
    private void dbh$drawDisplayOutlineAlways(CallbackInfo ci) {
        DisplayBlockHitboxesClient.renderOutline("damage");
    }
}