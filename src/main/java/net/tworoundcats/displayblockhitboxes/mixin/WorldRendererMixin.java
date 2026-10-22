package net.tworoundcats.displayblockhitboxes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.tworoundcats.displayblockhitboxes.DisplayBlockHitboxesClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "submitBlockOutline", at = @At("RETURN"))
    private void dbh$drawDisplayOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelRenderState, CallbackInfo ci) {
        DisplayBlockHitboxesClient.renderOutline(collector);
    }
}