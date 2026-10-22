package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("itemTransform")
    ItemTransform dbh$getTransform();

    @Accessor("quads")
    ItemQuads dbh$getQuads();

    @Accessor("usesBlockLight")
    boolean dbh$getUseLight();

    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> dbh$getSpecialModelType();
}