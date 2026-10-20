package net.tworoundcats.displayblockhitboxes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.component.ComponentMap;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.concurrent.TimeUnit;

public final class ItemModelBounds {
    private record Key(Item item, ComponentMap components) {}

    private static final Cache<Key, VoxelShape> CACHE = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    private ItemModelBounds() {}

    public static VoxelShape getShape(World world, ItemStack stack) {
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

    private static VoxelShape computeShape(World world, ItemStack stack) {
        BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModel(stack, world, null, 0);
        VoxelShape shape = VoxelShapes.empty();

        if (!model.isBuiltin()) {
            Random random = Random.create(42L);
            Flat flat = model.hasDepth() ? null : new Flat();
            for (Direction dir : Direction.values()) {
                shape = addQuads(shape, model, dir, random, flat);
            }
            shape = addQuads(shape, model, null, random, flat);

            if (flat != null && !Float.isNaN(flat.x0)) {
                shape = addFlat(shape, flat);
            }
        }

        if (shape.isEmpty()) {
            shape = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        }
        return shape.simplify();
    }

    // Opaque pixels of the texture become solid boxes, which assumes a 16x16 texture, like vanilla items
    private static VoxelShape addFlat(VoxelShape shape, Flat f) {
        float zMin = f.zMin, zMax = f.zMax;
        if (zMax - zMin < 0.001f) { zMin -= 1.0f / 32.0f; zMax += 1.0f / 32.0f; }

        if (f.sprite == null) {
            return VoxelShapes.union(shape, VoxelShapes.cuboid(f.x0, f.y0, zMin, f.x1, f.y1, zMax));
        }

        int sw = f.sprite.getWidth(), sh = f.sprite.getHeight();
        int res = DisplayHitboxConfig.get().itemTextureResolution;
        final int nx = res == 0 ? sw : res, ny = res == 0 ? sh : res;
        float w = f.x1 - f.x0, h = f.y1 - f.y0;
        for (int py = 0; py < ny; py++) {
            int start = -1;
            for (int px = 0; px <= nx; px++) {
                boolean solid = px < nx && !f.sprite.isPixelTransparent(0, Math.min(sw - 1, px * sw / nx), Math.min(sh - 1, py * sh / ny));
                if (solid && start < 0) {
                    start = px;
                } else if (!solid && start >= 0) {
                    shape = VoxelShapes.union(shape, VoxelShapes.cuboid(
                            f.x0 + w * start / nx, f.y1 - h * (py + 1) / ny, zMin,
                            f.x0 + w * px / nx, f.y1 - h * py / ny, zMax));
                    start = -1;
                }
            }
        }
        return shape;
    }

    private static VoxelShape addQuads(VoxelShape shape, BakedModel model, Direction dir, Random random, Flat flat) {
        for (BakedQuad quad : model.getQuads(null, dir, random)) {
            int[] data = quad.getVertexData();
            int stride = data.length / 4;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            for (int j = 0; j < 4; j++) {
                float x = Float.intBitsToFloat(data[j * stride]);
                float y = Float.intBitsToFloat(data[j * stride + 1]);
                float z = Float.intBitsToFloat(data[j * stride + 2]);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }

            if (flat != null && (maxZ - minZ) < 0.001f && (maxX - minX) * (maxY - minY) > 0.25f) {
                flat.zMin = Math.min(flat.zMin, minZ);
                flat.zMax = Math.max(flat.zMax, maxZ);
                if (Float.isNaN(flat.x0)) { flat.x0 = minX; flat.y0 = minY; flat.x1 = maxX; flat.y1 = maxY; }
                if (quad.getFace() == Direction.SOUTH) {
                    flat.sprite = quad.getSprite().getContents();
                }
                continue;
            }

            if (maxX - minX < 0.001f) { minX -= 0.005f; maxX += 0.005f; }
            if (maxY - minY < 0.001f) { minY -= 0.005f; maxY += 0.005f; }
            if (maxZ - minZ < 0.001f) { minZ -= 0.005f; maxZ += 0.005f; }

            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return shape;
    }

    public static Matrix4f getInternalTransform(World world, ItemStack stack, int mode) {
        BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModel(stack, world, null, 0);
        ModelTransformationMode[] modes = ModelTransformationMode.values();
        ModelTransformationMode tm = mode >= 0 && mode < modes.length ? modes[mode] : ModelTransformationMode.NONE;

        Matrix4f m = new Matrix4f().rotateY((float) Math.PI);
        Transformation tf = model.getTransformation().getTransformation(tm);
        if (tf != Transformation.IDENTITY) {
            boolean left = mode == 1 || mode == 3;
            float ry = tf.rotation.y(), rz = tf.rotation.z();
            if (left) { ry = -ry; rz = -rz; }
            m.translate((left ? -1 : 1) * tf.translation.x(), tf.translation.y(), tf.translation.z());
            m.rotateXYZ(tf.rotation.x() * DisplayHitboxes.DEG, ry * DisplayHitboxes.DEG, rz * DisplayHitboxes.DEG);
            m.scale(tf.scale.x(), tf.scale.y(), tf.scale.z());
        }

        m.translate(-0.5f, -0.5f, -0.5f);
        return m;
    }

    public static void applyInternalTransform(MatrixStack matrices, World world, ItemStack stack, int mode) {
        BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModel(stack, world, null, 0);
        ModelTransformationMode[] modes = ModelTransformationMode.values();
        ModelTransformationMode tm = mode >= 0 && mode < modes.length ? modes[mode] : ModelTransformationMode.NONE;

        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation((float) Math.PI));
        Transformation tf = model.getTransformation().getTransformation(tm);
        if (tf != Transformation.IDENTITY) {
            boolean left = mode == 1 || mode == 3;
            float ry = tf.rotation.y(), rz = tf.rotation.z();
            if (left) { ry = -ry; rz = -rz; }
            matrices.translate((left ? -1 : 1) * tf.translation.x(), tf.translation.y(), tf.translation.z());
            matrices.multiply(new Quaternionf().rotationXYZ(
                    tf.rotation.x() * DisplayHitboxes.DEG,
                    ry * DisplayHitboxes.DEG,
                    rz * DisplayHitboxes.DEG
            ));
            matrices.scale(tf.scale.x(), tf.scale.y(), tf.scale.z());
        }

        matrices.translate(-0.5f, -0.5f, -0.5f);
    }
}