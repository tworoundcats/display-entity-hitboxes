package net.tworoundcats.displayblockhitboxes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.tworoundcats.displayblockhitboxes.mixin.ItemRenderStateAccessor;
import net.tworoundcats.displayblockhitboxes.mixin.LayerRenderStateAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ItemModelBounds {
    private record Key(Item item, DataComponentMap components) {}

    private static final Cache<Key, VoxelShape> CACHE = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    private ItemModelBounds() {}

    public static void clearCache() {
        CACHE.invalidateAll();
    }

    public static VoxelShape getShape(Level world, ItemStack stack) {
        Key key = new Key(stack.getItem(), stack.copy().getComponents());

        try {
            return CACHE.get(key, () -> computeShape(world, stack));
        } catch (Exception e) {
            return computeShape(world, stack);
        }
    }

    private static final class Flat {
        SpriteContents sprite;
        float x0 = Float.NaN, y0, x1, y1;
        float zMin = Float.MAX_VALUE, zMax = -Float.MAX_VALUE;
    }

    private static ItemStackRenderState stateFor(Level world, ItemStack stack, ItemDisplayContext mode) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().appendItemLayers(state, stack, mode, world, null, 0);
        return state;
    }

    private static ItemTransform transformOf(ItemStackRenderState state) {
        ItemRenderStateAccessor acc = (ItemRenderStateAccessor) state;
        if (acc.dbh$getLayerCount() == 0) return ItemTransform.NO_TRANSFORM;
        ItemTransform t = ((LayerRenderStateAccessor) (Object) acc.dbh$getLayers()[0]).dbh$getTransform();
        return t == null ? ItemTransform.NO_TRANSFORM : t;
    }

    private static ItemDisplayContext modeOf(int mode) {
        ItemDisplayContext[] modes = ItemDisplayContext.values();
        return mode >= 0 && mode < modes.length ? modes[mode] : ItemDisplayContext.NONE;
    }

    private static VoxelShape computeShape(Level world, ItemStack stack) {
        ItemStackRenderState state = stateFor(world, stack, ItemDisplayContext.NONE);
        ItemRenderStateAccessor acc = (ItemRenderStateAccessor) state;
        ItemStackRenderState.LayerRenderState[] layers = acc.dbh$getLayers();
        int count = acc.dbh$getLayerCount();
        VoxelShape shape = Shapes.empty();

        for (int i = 0; i < count; i++) {
            LayerRenderStateAccessor layer = (LayerRenderStateAccessor) (Object) layers[i];
            if (layer.dbh$getSpecialModelType() != null) continue;

            List<BakedQuad> quads = layers[i].prepareQuadList();
            if (quads.isEmpty()) continue;

            Flat flat = layer.dbh$getUseLight() ? null : new Flat();
            shape = addQuads(shape, quads, flat);

            if (flat != null && !Float.isNaN(flat.x0)) {
                shape = addFlat(shape, flat);
            }
        }

        if (shape.isEmpty()) {
            shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        }
        return shape.optimize();
    }

    // Opaque pixels of the texture become solid boxes, which assumes a 16x16 texture, like vanilla items
    private static VoxelShape addFlat(VoxelShape shape, Flat f) {
        float zMin = f.zMin, zMax = f.zMax;
        if (zMax - zMin < 0.001f) { zMin -= 1.0f / 32.0f; zMax += 1.0f / 32.0f; }

        if (f.sprite == null) {
            return Shapes.or(shape, Shapes.box(f.x0, f.y0, zMin, f.x1, f.y1, zMax));
        }

        int sw = f.sprite.width(), sh = f.sprite.height();
        int res = DisplayHitboxConfig.get().itemTextureResolution;
        final int nx = res == 0 ? sw : res, ny = res == 0 ? sh : res;
        float w = f.x1 - f.x0, h = f.y1 - f.y0;
        for (int py = 0; py < ny; py++) {
            int start = -1;
            for (int px = 0; px <= nx; px++) {
                boolean solid = px < nx && !f.sprite.isTransparent(0, Math.min(sw - 1, px * sw / nx), Math.min(sh - 1, py * sh / ny));
                if (solid && start < 0) {
                    start = px;
                } else if (!solid && start >= 0) {
                    shape = Shapes.or(shape, Shapes.box(
                            f.x0 + w * start / nx, f.y1 - h * (py + 1) / ny, zMin,
                            f.x0 + w * px / nx, f.y1 - h * py / ny, zMax));
                    start = -1;
                }
            }
        }
        return shape;
    }

    private static VoxelShape addQuads(VoxelShape shape, List<BakedQuad> quads, Flat flat) {
        for (BakedQuad quad : quads) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            for (int j = 0; j < 4; j++) {
                org.joml.Vector3fc pos = quad.position(j);
                float x = pos.x(), y = pos.y(), z = pos.z();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }

            if (flat != null && (maxZ - minZ) < 0.001f && (maxX - minX) * (maxY - minY) > 0.25f) {
                flat.zMin = Math.min(flat.zMin, minZ);
                flat.zMax = Math.max(flat.zMax, maxZ);
                if (Float.isNaN(flat.x0)) { flat.x0 = minX; flat.y0 = minY; flat.x1 = maxX; flat.y1 = maxY; }
                if (quad.direction() == Direction.SOUTH) {
                    flat.sprite = quad.materialInfo().sprite().contents();
                }
                continue;
            }

            if (maxX - minX < 0.001f) { minX -= 0.005f; maxX += 0.005f; }
            if (maxY - minY < 0.001f) { minY -= 0.005f; maxY += 0.005f; }
            if (maxZ - minZ < 0.001f) { minZ -= 0.005f; maxZ += 0.005f; }

            shape = Shapes.or(shape, Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return shape;
    }

    public static Matrix4f getInternalTransform(Level world, ItemStack stack, int mode) {
        ItemDisplayContext tm = modeOf(mode);
        ItemStackRenderState state = stateFor(world, stack, tm);

        Matrix4f m = new Matrix4f().rotateY((float) Math.PI);
        ItemTransform tf = transformOf(state);
        if (tf != ItemTransform.NO_TRANSFORM) {
            boolean left = mode == 1 || mode == 3;
            float ry = tf.rotation().y(), rz = tf.rotation().z();
            if (left) { ry = -ry; rz = -rz; }
            m.translate((left ? -1 : 1) * tf.translation().x(), tf.translation().y(), tf.translation().z());
            m.rotateXYZ(tf.rotation().x() * DisplayHitboxes.DEG, ry * DisplayHitboxes.DEG, rz * DisplayHitboxes.DEG);
            m.scale(tf.scale().x(), tf.scale().y(), tf.scale().z());
        }

        m.translate(-0.5f, -0.5f, -0.5f);
        return m;
    }

    public static void applyInternalTransform(PoseStack matrices, Level world, ItemStack stack, int mode) {
        ItemDisplayContext tm = modeOf(mode);
        ItemStackRenderState state = stateFor(world, stack, tm);

        matrices.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.PI));
        ItemTransform tf = transformOf(state);
        if (tf != ItemTransform.NO_TRANSFORM) {
            boolean left = mode == 1 || mode == 3;
            float ry = tf.rotation().y(), rz = tf.rotation().z();
            if (left) { ry = -ry; rz = -rz; }
            matrices.translate((left ? -1 : 1) * tf.translation().x(), tf.translation().y(), tf.translation().z());
            matrices.mulPose(new Quaternionf().rotationXYZ(
                    tf.rotation().x() * DisplayHitboxes.DEG,
                    ry * DisplayHitboxes.DEG,
                    rz * DisplayHitboxes.DEG
            ));
            matrices.scale(tf.scale().x(), tf.scale().y(), tf.scale().z());
        }

        matrices.translate(-0.5f, -0.5f, -0.5f);
    }
}