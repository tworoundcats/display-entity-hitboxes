package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.tworoundcats.displayblockhitboxes.DisplayBlockHitboxesClient;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("HEAD"))
    private void dbh$hideDefaultHitbox(EntityRenderState state, CameraRenderState cameraRenderState, double x, double y, double z, MatrixStack matrices, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (DisplayHitboxes.isTargetableType(state.entityType)) {
            if (state.hitbox != null) {
                DisplayBlockHitboxesClient.noteVanillaHitboxes();
                state.hitbox = null;
            }
        }
    }
}