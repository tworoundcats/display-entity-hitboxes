package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.render.GameRenderer;
import net.tworoundcats.displayblockhitboxes.DisplayBlockHitboxesClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "updateTargetedEntity", at = @At("RETURN"))
    private void dbh$pickDisplays(float tickDelta, CallbackInfo ci) {
        DisplayBlockHitboxesClient.pick(tickDelta);
    }
}