package net.tworoundcats.displayblockhitboxes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import java.util.concurrent.TimeUnit;

public final class BlockModelBounds {
    private static final int N = DisplayHitboxConfig.get().blockModelResolution;

    private static final Cache<BlockState, VoxelShape> CACHE = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    private BlockModelBounds() {}

    public static VoxelShape getShape(BlockState state) {
        try {
            return CACHE.get(state, () -> computeShape(state));
        } catch (Exception e) {
            return computeShape(state);
        }
    }

    private static VoxelShape computeShape(BlockState state) {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        VoxelShape shape = VoxelShapes.empty();
        boolean[] grid = new boolean[N * N * N];

        Random random = Random.create(42L);
        for (Direction dir : Direction.values()) {
            shape = addQuads(shape, grid, state, model, dir, random);
        }
        shape = addQuads(shape, grid, state, model, null, random);
        shape = addCells(shape, grid);

        if (shape.isEmpty()) {
            return DisplayHitboxes.removeModelOffset(state, state.getOutlineShape(net.minecraft.world.EmptyBlockView.INSTANCE, net.minecraft.util.math.BlockPos.ORIGIN));
        }
        return shape.simplify();
    }

    private static VoxelShape addQuads(VoxelShape shape, boolean[] grid, BlockState state, BakedModel model, Direction dir, Random random) {
        for (BakedQuad quad : model.getQuads(state, dir, random)) {
            int[] data = quad.getVertexData();
            int stride = data.length / 4;
            float[] x = new float[4], y = new float[4], z = new float[4];
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            for (int j = 0; j < 4; j++) {
                x[j] = Float.intBitsToFloat(data[j * stride]);
                y[j] = Float.intBitsToFloat(data[j * stride + 1]);
                z[j] = Float.intBitsToFloat(data[j * stride + 2]);
                minX = Math.min(minX, x[j]); maxX = Math.max(maxX, x[j]);
                minY = Math.min(minY, y[j]); maxY = Math.max(maxY, y[j]);
                minZ = Math.min(minZ, z[j]); maxZ = Math.max(maxZ, z[j]);
            }

            // Skips any decorative 0.1 pixel thick displays like bamboo leaves
            if (minX < -0.1f || minY < -0.1f || minZ < -0.1f || maxX > 1.1f || maxY > 1.1f || maxZ > 1.1f) continue;

            float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;


            boolean aligned = Math.min(dx, Math.min(dy, dz)) < 0.002f;
            if (aligned) {
                float rect = Math.max(dx * dy, Math.max(dy * dz, dx * dz));
                aligned = rect < 1.0e-6f || quadArea(x, y, z) >= rect * 0.97f;
            }

            if (!aligned) {
                rasterize(grid, quad, x, y, z);
                continue;
            }

            if (dx < 0.001f) { minX -= 0.005f; maxX += 0.005f; }
            if (dy < 0.001f) { minY -= 0.005f; maxY += 0.005f; }
            if (dz < 0.001f) { minZ -= 0.005f; maxZ += 0.005f; }

            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return shape;
    }

    private static float quadArea(float[] x, float[] y, float[] z) {
        float ax = x[2] - x[0], ay = y[2] - y[0], az = z[2] - z[0];
        float bx = x[3] - x[1], by = y[3] - y[1], bz = z[3] - z[1];
        float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        return 0.5f * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    private static float dist(float[] x, float[] y, float[] z, int a, int b) {
        float dx = x[a] - x[b], dy = y[a] - y[b], dz = z[a] - z[b];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void rasterize(boolean[] g, BakedQuad quad, float[] x, float[] y, float[] z) {
        int[] data = quad.getVertexData();
        int stride = data.length / 4;
        float[] tu = new float[4], tv = new float[4];
        for (int j = 0; j < 4; j++) {
            tu[j] = Float.intBitsToFloat(data[j * stride + 4]);
            tv[j] = Float.intBitsToFloat(data[j * stride + 5]);
        }

        Sprite sprite = quad.getSprite();
        SpriteContents contents = sprite == null ? null : sprite.getContents();
        float minU = 0, maxU = 0, minV = 0, maxV = 0;
        if (sprite != null) {
            minU = sprite.getMinU(); maxU = sprite.getMaxU();
            minV = sprite.getMinV(); maxV = sprite.getMaxV();
        }
        boolean alpha = contents != null && maxU > minU && maxV > minV;

        float lenU = Math.max(dist(x, y, z, 0, 1), dist(x, y, z, 3, 2));
        float lenV = Math.max(dist(x, y, z, 0, 3), dist(x, y, z, 1, 2));
        int nu = Math.min(N * 6, Math.max(1, (int) Math.ceil(lenU * N * 4)));
        int nv = Math.min(N * 6, Math.max(1, (int) Math.ceil(lenV * N * 4)));
        for (int i = 0; i <= nu; i++) {
            float u = (float) i / nu;
            for (int j = 0; j <= nv; j++) {
                float v = (float) j / nv;

                // skip transparent texture pixels, so only the visible crystal is solid
                if (alpha) {
                    float pu = (1 - v) * ((1 - u) * tu[0] + u * tu[1]) + v * ((1 - u) * tu[3] + u * tu[2]);
                    float pv = (1 - v) * ((1 - u) * tv[0] + u * tv[1]) + v * ((1 - u) * tv[3] + u * tv[2]);
                    int tx = (int) ((pu - minU) / (maxU - minU) * contents.getWidth());
                    int ty = (int) ((pv - minV) / (maxV - minV) * contents.getHeight());
                    tx = Math.max(0, Math.min(contents.getWidth() - 1, tx));
                    ty = Math.max(0, Math.min(contents.getHeight() - 1, ty));
                    if (contents.isPixelTransparent(0, tx, ty)) continue;
                }

                float px = (1 - v) * ((1 - u) * x[0] + u * x[1]) + v * ((1 - u) * x[3] + u * x[2]);
                float py = (1 - v) * ((1 - u) * y[0] + u * y[1]) + v * ((1 - u) * y[3] + u * y[2]);
                float pz = (1 - v) * ((1 - u) * z[0] + u * z[1]) + v * ((1 - u) * z[3] + u * z[2]);
                int cx = clamp((int) Math.floor(px * N)), cy = clamp((int) Math.floor(py * N)), cz = clamp((int) Math.floor(pz * N));
                g[(cy * N + cz) * N + cx] = true;
            }
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, N - 1);
    }

    private static VoxelShape addCells(VoxelShape shape, boolean[] g) {
        boolean[] used = new boolean[g.length];
        for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) for (int x = 0; x < N; x++) {
            if (!free(g, used, x, y, z)) continue;
            int x2 = x;
            while (x2 + 1 < N && free(g, used, x2 + 1, y, z)) x2++;
            int z2 = z;
            while (z2 + 1 < N && rowFree(g, used, x, x2, y, z2 + 1)) z2++;
            int y2 = y;
            while (y2 + 1 < N && layerFree(g, used, x, x2, y2 + 1, z, z2)) y2++;
            for (int yy = y; yy <= y2; yy++) for (int zz = z; zz <= z2; zz++) for (int xx = x; xx <= x2; xx++) {
                used[(yy * N + zz) * N + xx] = true;
            }
            shape = VoxelShapes.union(shape, VoxelShapes.cuboid(
                    (double) x / N, (double) y / N, (double) z / N,
                    (double) (x2 + 1) / N, (double) (y2 + 1) / N, (double) (z2 + 1) / N));
        }
        return shape;
    }

    private static boolean free(boolean[] g, boolean[] used, int x, int y, int z) {
        int i = (y * N + z) * N + x;
        return g[i] && !used[i];
    }

    private static boolean rowFree(boolean[] g, boolean[] used, int x1, int x2, int y, int z) {
        for (int x = x1; x <= x2; x++) if (!free(g, used, x, y, z)) return false;
        return true;
    }

    private static boolean layerFree(boolean[] g, boolean[] used, int x1, int x2, int y, int z1, int z2) {
        for (int z = z1; z <= z2; z++) if (!rowFree(g, used, x1, x2, y, z)) return false;
        return true;
    }
}