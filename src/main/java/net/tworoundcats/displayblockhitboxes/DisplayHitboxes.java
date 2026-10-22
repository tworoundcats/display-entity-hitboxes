package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.tworoundcats.displayblockhitboxes.mixin.BlockDisplayEntityInvoker;
import net.tworoundcats.displayblockhitboxes.mixin.DisplayEntityAccessor;
import net.tworoundcats.displayblockhitboxes.mixin.ItemDisplayEntityAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.util.math.MathHelper;

public final class DisplayHitboxes {
    private static final boolean CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    public static final float DEG = 0.017453292F;
    private static final double MIN_THICKNESS = 0.05;

    public record ShapeData(VoxelShape shape, Matrix4f transform) {}

    private DisplayHitboxes() {}

    public static boolean isTargetable(Entity e) {
        DisplayHitboxConfig c = DisplayHitboxConfig.get();
        if (e instanceof DisplayEntity.BlockDisplayEntity) return c.blockDisplayHitboxes;
        if (e instanceof DisplayEntity.ItemDisplayEntity) return c.itemDisplayHitboxes;
        return false;
    }

    public static boolean isTargetableType(net.minecraft.entity.EntityType<?> type) {
        DisplayHitboxConfig c = DisplayHitboxConfig.get();
        if (type == net.minecraft.entity.EntityType.BLOCK_DISPLAY) return c.blockDisplayHitboxes;
        if (type == net.minecraft.entity.EntityType.ITEM_DISPLAY) return c.itemDisplayHitboxes;
        return false;
    }

    public static boolean hasCollision(Entity e) {
        if (!isTargetable(e)) return false;
        return !e.getCommandTags().contains("deh_no_collision");
    }

    private static final java.util.Map<World, java.util.Set<DisplayEntity>> REGISTRY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void track(Entity e, World w) {
        if (e instanceof DisplayEntity d) {
            REGISTRY.computeIfAbsent(w, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(d);
        }
    }

    public static void untrack(Entity e, World w) {
        if (e instanceof DisplayEntity d) {
            java.util.Set<DisplayEntity> set = REGISTRY.get(w);
            if (set != null) set.remove(d);
        }
    }

    public static java.util.Collection<DisplayEntity> displays(World w) {
        java.util.Set<DisplayEntity> set = REGISTRY.get(w);
        return set == null ? List.of() : set;
    }

    public static boolean mayIntersect(DisplayEntity d, Box q) {
        Vector3f t = d.getDataTracker().get(DisplayEntityAccessor.getTranslation());
        Vector3f s = d.getDataTracker().get(DisplayEntityAccessor.getScale());
        double r = t.length() + Math.max(Math.abs(s.x()), Math.max(Math.abs(s.y()), Math.abs(s.z()))) * 2.5 + 1.0;

        double dx = Math.max(0.0, Math.abs(d.getX() - (q.minX + q.maxX) / 2) - (q.maxX - q.minX) / 2);
        double dy = Math.max(0.0, Math.abs(d.getY() - (q.minY + q.maxY) / 2) - (q.maxY - q.minY) / 2);
        double dz = Math.max(0.0, Math.abs(d.getZ() - (q.minZ + q.maxZ) / 2) - (q.maxZ - q.minZ) / 2);
        if (dx * dx + dy * dy + dz * dz > r * r) return false;

        DisplayCache cache = getCache(d);
        cache.update(d);

        Box b = cache.worldBounds;
        if (b == null) return false;
        double x = d.getX(), y = d.getY(), z = d.getZ();
        return b.minX + x < q.maxX && b.maxX + x > q.minX
                && b.minY + y < q.maxY && b.maxY + y > q.minY
                && b.minZ + z < q.maxZ && b.maxZ + z > q.minZ;
    }

    public static void addShapes(com.google.common.collect.ImmutableList.Builder<VoxelShape> builder, DisplayEntity display, Box box) {
        double dx = display.getX(), dy = display.getY(), dz = display.getZ();
        Box rel = box.offset(-dx, -dy, -dz);
        for (Box b : getCollisionBoxes(display)) {
            if (b.intersects(rel)) builder.add(VoxelShapes.cuboid(b.offset(dx, dy, dz)));
        }
    }

    public static int sampleLight(DisplayEntity display) {
        DisplayCache cache = getCache(display);
        if (cache.lightAge == display.age && cache.lightValue != -1) {
            return cache.lightValue;
        }

        World world = display.getEntityWorld();
        boolean isBlock = display instanceof DisplayEntity.BlockDisplayEntity;
        Vector3f c = isBlock ? new Vector3f(0.5f, 0.5f, 0.5f) : new Vector3f(0f, 0f, 0f);
        getBaseTransform(display).transformPosition(c);

        int cx = MathHelper.floor(display.getX() + c.x());
        int cy = MathHelper.floor(display.getY() + c.y());
        int cz = MathHelper.floor(display.getZ() + c.z());

        int block = 0, sky = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(cx + dx, cy + dy, cz + dz);
                    block = Math.max(block, world.getLightLevel(LightType.BLOCK, p));
                    sky = Math.max(sky, world.getLightLevel(LightType.SKY, p));
                }
            }
        }

        cache.lightValue = LightmapTextureManager.pack(block, sky);
        cache.lightAge = display.age;
        return cache.lightValue;
    }

    public static VoxelShape removeModelOffset(BlockState state, VoxelShape shape) {
        Vec3d o = state.getModelOffset(BlockPos.ORIGIN);
        if (o.x == 0.0 && o.y == 0.0 && o.z == 0.0) return shape;
        return shape.offset(-o.x, -o.y, -o.z);
    }

    public static Matrix4f getBaseTransform(DisplayEntity display) {
        Vector3f t = display.getDataTracker().get(DisplayEntityAccessor.getTranslation());
        Vector3f s = display.getDataTracker().get(DisplayEntityAccessor.getScale());
        Quaternionf lr = display.getDataTracker().get(DisplayEntityAccessor.getLeftRotation());
        Quaternionf rr = display.getDataTracker().get(DisplayEntityAccessor.getRightRotation());

        Quaternionf orient = new Quaternionf().rotationYXZ(-DEG * display.getYaw(), DEG * display.getPitch(), 0.0f);
        return new Matrix4f().rotate(orient).translate(t).rotate(lr).scale(s).rotate(rr);
    }

    public static ShapeData getAccurateShape(DisplayEntity display, boolean forPicking) {
        return computeAccurateShape(display, getBaseTransform(display), forPicking);
    }

    private static ShapeData computeAccurateShape(DisplayEntity display, Matrix4f m, boolean forPicking) {
        if (display instanceof DisplayEntity.BlockDisplayEntity blockDisplay) {
            BlockState state = ((BlockDisplayEntityInvoker) blockDisplay).invokeGetBlockState();
            if (state == null || state.isAir()) return null;

            VoxelShape shape;
            if (CLIENT && DisplayHitboxConfig.get().accurateHitboxes) {
                shape = BlockModelBounds.getShape(state);
            } else {
                shape = forPicking
                        ? state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)
                        : state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                if (shape.isEmpty() && forPicking) {
                    shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                }
                shape = removeModelOffset(state, shape);
            }

            if (shape.isEmpty()) {
                return forPicking ? new ShapeData(VoxelShapes.cuboid(0, 0, 0, 1, 1, 1), m) : null;
            }

            return new ShapeData(shape, m);

        } else if (display instanceof DisplayEntity.ItemDisplayEntity itemDisplay) {
            ItemStack stack = stackOf(itemDisplay);

            if (stack == null || stack.isEmpty()) {
                return new ShapeData(VoxelShapes.cuboid(-0.25, -0.25, -0.25, 0.25, 0.25, 0.25), m);
            }

            if (CLIENT) {
                int mode = itemDisplay.getDataTracker().get(ItemDisplayEntityAccessor.getItemDisplay());
                VoxelShape shape = ItemModelBounds.getShape(display.getEntityWorld(), stack);
                Matrix4f internal = ItemModelBounds.getInternalTransform(display.getEntityWorld(), stack, mode);
                Matrix4f combined = new Matrix4f(m).mul(internal);
                return new ShapeData(shape, combined);
            } else {
                return new ShapeData(VoxelShapes.cuboid(-0.5, -0.5, -1.0 / 32.0, 0.5, 0.5, 1.0 / 32.0), m);
            }
        }
        return null;
    }

    private static ItemStack stackOf(DisplayEntity.ItemDisplayEntity itemDisplay) {
        try {
            return itemDisplay.getDataTracker().get(ItemDisplayEntityAccessor.getItem());
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static boolean sameContent(Object a, Object b) {
        if (a instanceof ItemStack x && b instanceof ItemStack y) {
            return ItemStack.areItemsAndComponentsEqual(x, y);
        }
        return Objects.equals(a, b);
    }

    private static final Cache<DisplayEntity, DisplayCache> CACHE = CacheBuilder.newBuilder().weakKeys().build();

    private static DisplayCache getCache(DisplayEntity d) {
        try {
            return CACHE.get(d, DisplayCache::new);
        } catch (Exception e) {
            DisplayCache c = CACHE.getIfPresent(d);
            if (c == null) {
                c = new DisplayCache();
                CACHE.put(d, c);
            }
            return c;
        }
    }

    private static class DisplayCache {
        long lastTick = -1;
        Matrix4f lastTransform = new Matrix4f();
        Object lastContent = null;
        boolean lastAccurate = false;

        ShapeData shapeData;
        Box worldBounds;
        List<Box> slicedBoxes;
        Matrix4f inverse;

        int lightAge = -1;
        int lightValue = -1;

        void update(DisplayEntity display) {
            long tick = display.getEntityWorld().getTime();
            if (tick == lastTick) return;
            lastTick = tick;

            boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;
            Object content = null;
            if (display instanceof DisplayEntity.BlockDisplayEntity bd) {
                content = ((BlockDisplayEntityInvoker) bd).invokeGetBlockState();
            } else if (display instanceof DisplayEntity.ItemDisplayEntity id) {
                content = stackOf(id);
            }

            Matrix4f transform = getBaseTransform(display);
            boolean contentChanged = !sameContent(lastContent, content) || lastAccurate != accurate;
            boolean transformChanged = contentChanged || !lastTransform.equals(transform);

            if (transformChanged || shapeData == null) {
                lastTransform.set(transform);
                lastContent = content instanceof ItemStack s ? s.copy() : content;
                lastAccurate = accurate;

                shapeData = computeAccurateShape(display, transform, false);

                if (shapeData != null && !shapeData.shape().isEmpty()) {
                    worldBounds = transformBox(shapeData.shape().getBoundingBox(), transform, 0, 0, 0);
                } else {
                    worldBounds = null;
                }

                slicedBoxes = null;
                inverse = null;
            }
        }

        List<Box> getSlicedBoxes(DisplayEntity display) {
            update(display);
            if (slicedBoxes == null) {
                if (shapeData == null || shapeData.shape().isEmpty()) {
                    slicedBoxes = List.of();
                } else {
                    List<Box> out = new ArrayList<>();
                    ObbBoxes.decompose(shapeData.shape().getBoundingBoxes(), shapeData.transform(), out);
                    slicedBoxes = out;
                }
            }
            return slicedBoxes;
        }

        Matrix4f getInverse(ShapeData pickData) {
            if (inverse == null) {
                if (pickData != null && Math.abs(pickData.transform().determinant()) > 1e-6) {
                    inverse = new Matrix4f(pickData.transform()).invert();
                } else {
                    inverse = new Matrix4f();
                }
            }
            return inverse;
        }
    }

    public static final class Motion {
        long tick = Long.MIN_VALUE;
        Vec3d pos = Vec3d.ZERO, prevPos = Vec3d.ZERO;
        Matrix4f shape, prevInverse;
        boolean moving;
    }

    private static final Cache<DisplayEntity, Motion> MOTION = CacheBuilder.newBuilder().weakKeys().build();

    public static void clearCache() {
        CACHE.invalidateAll();
        MOTION.invalidateAll();
    }

    public static Motion motion(DisplayEntity d, long tick) {
        Motion m = MOTION.getIfPresent(d);
        if (m == null) {
            m = new Motion();
            MOTION.put(d, m);
        }
        if (m.tick == tick) return m;

        DisplayCache cache = getCache(d);
        cache.update(d);
        Matrix4f cur = cache.shapeData != null ? cache.shapeData.transform() : null;

        Matrix4f prevShape = m.shape;
        Vec3d prevPos = m.pos;
        boolean consecutive = m.tick == tick - 1 && prevShape != null && cur != null;

        m.prevInverse = null;
        m.moving = false;
        if (consecutive && Math.abs(prevShape.determinant()) > 1.0e-6) {
            Vec3d pos = d.getEntityPos();
            m.moving = !(prevPos.equals(pos) && prevShape.equals(cur));
            if (m.moving) m.prevInverse = new Matrix4f(prevShape).invert();
        }
        m.prevPos = prevPos;
        m.shape = cur != null ? new Matrix4f(cur) : null;
        m.pos = d.getEntityPos();
        m.tick = tick;
        return m;
    }

    public static Vec3d surfaceVelocity(Motion m, Vec3d q) {
        if (!m.moving || m.prevInverse == null || m.shape == null) return Vec3d.ZERO;
        Vector3f p = new Vector3f((float) (q.x - m.prevPos.x), (float) (q.y - m.prevPos.y), (float) (q.z - m.prevPos.z));
        m.prevInverse.transformPosition(p);
        m.shape.transformPosition(p);
        return new Vec3d(p.x + m.pos.x - q.x, p.y + m.pos.y - q.y, p.z + m.pos.z - q.z);
    }

    public static List<Box> getCollisionBoxes(DisplayEntity display) {
        DisplayCache cache = getCache(display);
        return cache.getSlicedBoxes(display);
    }

    public static Box transformBox(Box local, Matrix4f m, double dx, double dy, double dz) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector4f v = new Vector4f(
                    (float) ((i & 1) == 0 ? local.minX : local.maxX),
                    (float) ((i & 2) == 0 ? local.minY : local.maxY),
                    (float) ((i & 4) == 0 ? local.minZ : local.maxZ),
                    1.0f);
            m.transform(v);
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y());
            maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z());
            maxZ = Math.max(maxZ, v.z());
        }
        return new Box(dx + minX, dy + minY, dz + minZ, dx + maxX, dy + maxY, dz + maxZ);
    }

    public static Box computeBox(DisplayEntity display, boolean forPicking) {
        if (display.getEntityWorld() == null) return null;

        if (!forPicking && DisplayHitboxConfig.get().accurateHitboxes) {
            DisplayCache cache = getCache(display);
            cache.update(display);

            Box b = cache.worldBounds;
            if (b == null) return null;
            return b.offset(display.getX(), display.getY(), display.getZ());
        }

        ShapeData data = getAccurateShape(display, forPicking);
        if (data == null || data.shape().isEmpty()) return null;

        Box local = data.shape().getBoundingBox();
        Box transformed = transformBox(local, data.transform(), display.getX(), display.getY(), display.getZ());

        double px = Math.max(0, (MIN_THICKNESS - (transformed.maxX - transformed.minX)) / 2);
        double py = Math.max(0, (MIN_THICKNESS - (transformed.maxY - transformed.minY)) / 2);
        double pz = Math.max(0, (MIN_THICKNESS - (transformed.maxZ - transformed.minZ)) / 2);

        return transformed.expand(px, py, pz);
    }

    public static Optional<Vec3d> raycast(DisplayEntity display, Vec3d eye, Vec3d end) {
        boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;

        if (accurate) {
            ShapeData data = getAccurateShape(display, true);
            if (data != null && !data.shape().isEmpty()) {
                DisplayCache cache = getCache(display);
                Matrix4f inv = cache.getInverse(data);

                Matrix4f m = data.transform();
                double dx = display.getX(), dy = display.getY(), dz = display.getZ();

                Vector3f localEye = new Vector3f((float) (eye.x - dx), (float) (eye.y - dy), (float) (eye.z - dz));
                inv.transformPosition(localEye);
                Vector3f localEnd = new Vector3f((float) (end.x - dx), (float) (end.y - dy), (float) (end.z - dz));
                inv.transformPosition(localEnd);

                Vec3d le = new Vec3d(localEye.x, localEye.y, localEye.z);
                Vec3d ln = new Vec3d(localEnd.x, localEnd.y, localEnd.z);

                Vec3d bestHit = null;
                double bestDist = Double.MAX_VALUE;
                for (Box box : data.shape().getBoundingBoxes()) {
                    Optional<Vec3d> hit = box.raycast(le, ln);
                    if (hit.isPresent()) {
                        double dist = hit.get().squaredDistanceTo(le);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestHit = hit.get();
                        }
                    }
                }

                if (bestHit != null) {
                    Vector3f hitPos = new Vector3f((float) bestHit.x, (float) bestHit.y, (float) bestHit.z);
                    m.transformPosition(hitPos);
                    return Optional.of(new Vec3d(hitPos.x() + dx, hitPos.y() + dy, hitPos.z() + dz));
                }
                return Optional.empty();
            }
        }

        Box box = computeBox(display, true);
        if (box == null) return Optional.empty();
        return box.contains(eye) ? Optional.of(eye) : box.raycast(eye, end);
    }
}