package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.Minecraft;
import net.tworoundcats.displayblockhitboxes.DisplayBlockHitboxesClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Minecraft.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick", at = @At("RETURN"))
    private void dbh$pickDisplays(float tickDelta, CallbackInfo ci) {
        DisplayBlockHitboxesClient.pick(tickDelta);
    }
}