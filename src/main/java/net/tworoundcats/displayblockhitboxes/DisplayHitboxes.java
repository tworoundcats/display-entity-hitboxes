package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

public final class DisplayHitboxes {
    private static final boolean CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    public static final float DEG = 0.017453292F;
    private static final double MIN_THICKNESS = 0.05;

    public record ShapeData(VoxelShape shape, Matrix4f transform) {
    }

    private DisplayHitboxes() {
    }

    public static boolean isTargetable(Entity e) {
        DisplayHitboxConfig c = DisplayHitboxConfig.get();
        if (e instanceof Display.BlockDisplay) return c.blockDisplayHitboxes;
        if (e instanceof Display.ItemDisplay) return c.itemDisplayHitboxes;
        return false;
    }

    public static boolean isTargetableType(net.minecraft.world.entity.EntityType<?> type) {
        DisplayHitboxConfig c = DisplayHitboxConfig.get();
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        if (id.equals("block_display")) return c.blockDisplayHitboxes;
        if (id.equals("item_display")) return c.itemDisplayHitboxes;
        return false;
    }

    public static boolean hasCollision(Entity e) {
        final Matrix4f shapeTransform;

        if (!isTargetable(e)) return false;
        return !e.entityTags().contains("deh_no_collision");
    }
    private static final java.util.Map<Level, java.util.Set<Display>> REGISTRY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static void track(Entity e, Level w) {
        if (e instanceof Display d) {
            REGISTRY.computeIfAbsent(w, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(d);
        }
    }

    public static void untrack(Entity e, Level w) {
        if (e instanceof Display d) {
            java.util.Set<Display> set = REGISTRY.get(w);
            if (set != null) set.remove(d);
        }
    }

    public static java.util.Collection<Display> displays(Level w) {
        java.util.Set<Display> set = REGISTRY.get(w);
        return set == null ? List.of() : set;
    }

    public static boolean mayIntersect(Display d, AABB q) {
        Vector3f t = new Vector3f(d.getEntityData().get(DisplayEntityAccessor.getTranslation()));
        Vector3f s = new Vector3f(d.getEntityData().get(DisplayEntityAccessor.getScale()));
        double r = t.length() + Math.max(Math.abs(s.x()), Math.max(Math.abs(s.y()), Math.abs(s.z()))) * 2.5 + 1.0;

        double dx = Math.max(0.0, Math.abs(d.getX() - (q.minX + q.maxX) / 2) - (q.maxX - q.minX) / 2);
        double dy = Math.max(0.0, Math.abs(d.getY() - (q.minY + q.maxY) / 2) - (q.maxY - q.minY) / 2);
        double dz = Math.max(0.0, Math.abs(d.getZ() - (q.minZ + q.maxZ) / 2) - (q.maxZ - q.minZ) / 2);
        if (dx * dx + dy * dy + dz * dz > r * r) return false;

        AABB b = entryFor(d).bounds;
        if (b == null) return false;
        double x = d.getX(), y = d.getY(), z = d.getZ();
        return b.minX + x < q.maxX && b.maxX + x > q.minX
                && b.minY + y < q.maxY && b.maxY + y > q.minY
                && b.minZ + z < q.maxZ && b.maxZ + z > q.minZ;
    }

    public static void addShapes(com.google.common.collect.ImmutableList.Builder<VoxelShape> builder, Display display, AABB box) {
        double dx = display.getX(), dy = display.getY(), dz = display.getZ();
        AABB rel = box.move(-dx, -dy, -dz);
        for (AABB b : getCollisionBoxes(display)) {
            if (b.intersects(rel)) builder.add(Shapes.create(b.move(dx, dy, dz)));
        }
    }

    public static int sampleLight(Display display) {
        Level world = display.level();
        boolean isBlock = display instanceof Display.BlockDisplay;
        Vector3f c = isBlock ? new Vector3f(0.5f, 0.5f, 0.5f) : new Vector3f(0f, 0f, 0f);
        getBaseTransform(display).transformPosition(c);

        int cx = Mth.floor(display.getX() + c.x());
        int cy = Mth.floor(display.getY() + c.y());
        int cz = Mth.floor(display.getZ() + c.z());

        int block = 0, sky = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    p.set(cx + dx, cy + dy, cz + dz);
                    block = Math.max(block, world.getBrightness(LightLayer.BLOCK, p));
                    sky = Math.max(sky, world.getBrightness(LightLayer.SKY, p));
                }
            }
        }
        return LightCoordsUtil.pack(block, sky);
    }

    public static VoxelShape removeModelOffset(BlockState state, VoxelShape shape) {
        Vec3 o = state.getOffset(BlockPos.ZERO);
        if (o.x == 0.0 && o.y == 0.0 && o.z == 0.0) return shape;
        return shape.move(-o.x, -o.y, -o.z);
    }

    public static Matrix4f getBaseTransform(Display display) {
        Vector3f t = new Vector3f(display.getEntityData().get(DisplayEntityAccessor.getTranslation()));
        Vector3f s = new Vector3f(display.getEntityData().get(DisplayEntityAccessor.getScale()));
        Quaternionf lr = new Quaternionf(display.getEntityData().get(DisplayEntityAccessor.getLeftRotation()));
        Quaternionf rr = new Quaternionf(display.getEntityData().get(DisplayEntityAccessor.getRightRotation()));

        Quaternionf orient = new Quaternionf().rotationYXZ(-DEG * display.getYRot(), DEG * display.getXRot(), 0.0f);
        return new Matrix4f().rotate(orient).translate(t).rotate(lr).scale(s).rotate(rr);
    }

    public static ShapeData getAccurateShape(Display display, boolean forPicking) {
        Matrix4f m = getBaseTransform(display);

        if (display instanceof Display.BlockDisplay blockDisplay) {
            BlockState state = ((BlockDisplayEntityInvoker) blockDisplay).invokeGetBlockState();
            if (state == null || state.isAir()) return null;

            VoxelShape shape;
            if (CLIENT) {
                shape = BlockModelBounds.getShape(state);
                if (!DisplayHitboxConfig.get().accurateHitboxes && !shape.isEmpty()) {
                    shape = Shapes.create(shape.bounds());
                }
            } else {
                shape = forPicking
                        ? state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        : state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (shape.isEmpty() && forPicking) {
                    shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                }
                shape = removeModelOffset(state, shape);
            }

            if (shape.isEmpty()) {
                return forPicking ? new ShapeData(Shapes.box(0, 0, 0, 1, 1, 1), m) : null;
            }

            return new ShapeData(shape, m);

        } else if (display instanceof Display.ItemDisplay itemDisplay) {
            ItemStack stack = null;
            try {
                net.minecraft.world.entity.SlotAccess ref = itemDisplay.getSlot(0);
                if (ref != null) stack = ref.get();
            } catch (Exception ignored) {
            }

            if (stack == null || stack.isEmpty()) {
                return new ShapeData(Shapes.box(-0.25, -0.25, -0.25, 0.25, 0.25, 0.25), m);
            }

            if (CLIENT) {
                int mode = itemDisplay.getEntityData().get(ItemDisplayEntityAccessor.getItemDisplay());
                VoxelShape shape = ItemModelBounds.getShape(display.level(), stack);
                Matrix4f internal = ItemModelBounds.getInternalTransform(display.level(), stack, mode);
                m.mul(internal);
                return new ShapeData(shape, m);
            } else {
                return new ShapeData(Shapes.box(-0.5, -0.5, -1.0 / 32.0, 0.5, 0.5, 1.0 / 32.0), m);
            }
        }
        return null;
    }

    private static ItemStack stackOf(Display.ItemDisplay itemDisplay) {
        try {
            net.minecraft.world.entity.SlotAccess ref = itemDisplay.getSlot(0);
            if (ref != null) return ref.get();
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static boolean sameContent(Object a, Object b) {
        if (a instanceof ItemStack x && b instanceof ItemStack y) {
            return ItemStack.isSameItemSameComponents(x, y);
        }
        return Objects.equals(a, b);
    }

    private static final class CollisionEntry {
        final Matrix4f base;
        final Object content;
        final int mode;
        final boolean accurate;
        final List<AABB> boxes;
        final AABB bounds;
        final Matrix4f shapeTransform;
        int checkedAge;
        volatile boolean pickReady;
        volatile ShapeData pick;
        volatile Matrix4f pickInverse;

        CollisionEntry(Matrix4f base, Object content, int mode, boolean accurate, List<AABB> boxes, AABB bounds, int age, Matrix4f shapeTransform) {
            this.base = base;
            this.content = content;
            this.mode = mode;
            this.accurate = accurate;
            this.boxes = boxes;
            this.bounds = bounds;
            this.checkedAge = age;
            this.shapeTransform = shapeTransform;
        }

        ShapeData pickData(Display d) {
            if (!pickReady) {
                ShapeData p = getAccurateShape(d, true);
                Matrix4f inv = null;
                if (p != null && Math.abs(p.transform().determinant()) > 1e-6) {
                    inv = new Matrix4f(p.transform()).invert();
                }
                pick = p;
                pickInverse = inv;
                pickReady = true;
            }
            return pick;
        }
    }

    private static final Cache<Display, CollisionEntry> COLLISION_CACHE =
            CacheBuilder.newBuilder().weakKeys().build();

    private static CollisionEntry entryFor(Display display) {
        boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;
        CollisionEntry cached = COLLISION_CACHE.getIfPresent(display);
        if (cached != null && cached.accurate == accurate && cached.checkedAge == display.tickCount) return cached;

        Matrix4f base = getBaseTransform(display);
        Object content = null;
        int mode = 0;
        if (display instanceof Display.BlockDisplay blockDisplay) {
            content = ((BlockDisplayEntityInvoker) blockDisplay).invokeGetBlockState();
        } else if (display instanceof Display.ItemDisplay itemDisplay) {
            content = stackOf(itemDisplay);
            mode = itemDisplay.getEntityData().get(ItemDisplayEntityAccessor.getItemDisplay());
        }

        if (cached != null && cached.accurate == accurate && cached.mode == mode
                && cached.base.equals(base) && sameContent(cached.content, content)) {
            cached.checkedAge = display.tickCount;
            return cached;
        }

        List<AABB> boxes = List.of();
        AABB bounds = null;
        Matrix4f shapeTransform = null;
        ShapeData data = getAccurateShape(display, false);
        if (data != null && !data.shape().isEmpty()) {
            shapeTransform = new Matrix4f(data.transform());
            List<AABB> out = new ArrayList<>();
            if (accurate) {
                ObbBoxes.decompose(data.shape().toAabbs(), data.transform(), out);
            } else {
                AABB w = computeBox(display, false);
                if (w != null) out.add(w.move(-display.getX(), -display.getY(), -display.getZ()));
            }
            boxes = List.copyOf(out);
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (AABB b : boxes) {
                minX = Math.min(minX, b.minX);
                minY = Math.min(minY, b.minY);
                minZ = Math.min(minZ, b.minZ);
                maxX = Math.max(maxX, b.maxX);
                maxY = Math.max(maxY, b.maxY);
                maxZ = Math.max(maxZ, b.maxZ);
            }
            if (!boxes.isEmpty()) bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        Object stored = content instanceof ItemStack s ? s.copy() : content;
        CollisionEntry entry = new CollisionEntry(base, stored, mode, accurate, boxes, bounds, display.tickCount, shapeTransform);
        COLLISION_CACHE.put(display, entry);
        return entry;
    }

    public static final class Motion {
        long tick = Long.MIN_VALUE;
        Vec3 pos = Vec3.ZERO, prevPos = Vec3.ZERO;
        Matrix4f shape, prevInverse;
        boolean moving;
    }

    private static final Cache<Display, Motion> MOTION = CacheBuilder.newBuilder().weakKeys().build();

    public static void clearCache() {
        COLLISION_CACHE.invalidateAll();
        MOTION.invalidateAll();
    }

    public static Motion motion(Display d, long tick) {
        Motion m = MOTION.getIfPresent(d);
        if (m == null) {
            m = new Motion();
            MOTION.put(d, m);
        }
        if (m.tick == tick) return m;

        Matrix4f cur = entryFor(d).shapeTransform;
        Matrix4f prevShape = m.shape;
        Vec3 prevPos = m.pos;
        boolean consecutive = m.tick == tick - 1 && prevShape != null && cur != null;

        m.prevInverse = null;
        m.moving = false;
        if (consecutive && Math.abs(prevShape.determinant()) > 1.0e-6) {
            Vec3 pos = d.position();
            m.moving = !(prevPos.equals(pos) && prevShape.equals(cur));
            if (m.moving) m.prevInverse = new Matrix4f(prevShape).invert();
        }
        m.prevPos = prevPos;
        m.shape = cur;
        m.pos = d.position();
        m.tick = tick;
        return m;
    }

    public static Vec3 surfaceVelocity(Motion m, Vec3 q) {
        if (!m.moving || m.prevInverse == null || m.shape == null) return Vec3.ZERO;
        Vector3f p = new Vector3f((float) (q.x - m.prevPos.x), (float) (q.y - m.prevPos.y), (float) (q.z - m.prevPos.z));
        m.prevInverse.transformPosition(p);
        m.shape.transformPosition(p);
        return new Vec3(p.x + m.pos.x - q.x, p.y + m.pos.y - q.y, p.z + m.pos.z - q.z);
    }

    public static List<AABB> getCollisionBoxes(Display display) {
        return entryFor(display).boxes;
    }

    public static AABB transformBox(AABB local, Matrix4f m, double dx, double dy, double dz) {
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
        return new AABB(dx + minX, dy + minY, dz + minZ, dx + maxX, dy + maxY, dz + maxZ);
    }

    public static AABB computeBox(Display display, boolean forPicking) {
        if (display.level() == null) return null;

        if (!forPicking && DisplayHitboxConfig.get().accurateHitboxes) {
            AABB b = entryFor(display).bounds;
            if (b == null) return null;
            return b.move(display.getX(), display.getY(), display.getZ());
        }

        ShapeData data = getAccurateShape(display, forPicking);
        if (data == null || data.shape().isEmpty()) return null;

        AABB local = data.shape().bounds();
        AABB transformed = transformBox(local, data.transform(), display.getX(), display.getY(), display.getZ());

        double px = Math.max(0, (MIN_THICKNESS - (transformed.maxX - transformed.minX)) / 2);
        double py = Math.max(0, (MIN_THICKNESS - (transformed.maxY - transformed.minY)) / 2);
        double pz = Math.max(0, (MIN_THICKNESS - (transformed.maxZ - transformed.minZ)) / 2);

        return transformed.inflate(px, py, pz);
    }

    public static Optional<Vec3> raycast(Display display, Vec3 eye, Vec3 end) {
        boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;

        if (accurate) {
            CollisionEntry entry = entryFor(display);
            ShapeData data = entry.pickData(display);
            Matrix4f inv = entry.pickInverse;
            if (data != null && !data.shape().isEmpty() && inv != null) {
                Matrix4f m = data.transform();
                double dx = display.getX(), dy = display.getY(), dz = display.getZ();

                Vector3f localEye = new Vector3f((float) (eye.x - dx), (float) (eye.y - dy), (float) (eye.z - dz));
                inv.transformPosition(localEye);
                Vector3f localEnd = new Vector3f((float) (end.x - dx), (float) (end.y - dy), (float) (end.z - dz));
                inv.transformPosition(localEnd);

                Vec3 le = new Vec3(localEye.x, localEye.y, localEye.z);
                Vec3 ln = new Vec3(localEnd.x, localEnd.y, localEnd.z);

                Vec3 bestHit = null;
                double bestDist = Double.MAX_VALUE;
                for (AABB box : data.shape().toAabbs()) {
                    Optional<Vec3> hit = box.clip(le, ln);
                    if (hit.isPresent()) {
                        double dist = hit.get().distanceToSqr(le);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestHit = hit.get();
                        }
                    }
                }

                if (bestHit != null) {
                    Vector3f hitPos = new Vector3f((float) bestHit.x, (float) bestHit.y, (float) bestHit.z);
                    m.transformPosition(hitPos);
                    return Optional.of(new Vec3(hitPos.x() + dx, hitPos.y() + dy, hitPos.z() + dz));
                }
                return Optional.empty();
            }
        }

        AABB box = computeBox(display, true);
        if (box == null) return Optional.empty();
        return box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
    }
}