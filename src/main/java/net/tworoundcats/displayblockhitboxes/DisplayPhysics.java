package net.tworoundcats.displayblockhitboxes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.IdentityHashMap;
import java.util.Collections;

public final class DisplayPhysics {
    private static final Map<Entity, Vec3d> CARRY = new WeakHashMap<>();

    private DisplayPhysics() {}

    public static void tickClient(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null || mc.isPaused()) return;

        DisplayHitboxConfig config = DisplayHitboxConfig.get();
        if (!config.displayPhysics || !config.accurateHitboxes) return;

        long tick = world.getTime();
        final double MARGIN = config.physicsContactMargin;
        final double STAND = config.physicsStandTolerance;
        final double MAX_SPEED_SQ = config.physicsMaxSpeed * config.physicsMaxSpeed;

        Set<Entity> carriedThisTick = Collections.newSetFromMap(new IdentityHashMap<>());

        for (DisplayEntity d : DisplayHitboxes.displays(world)) {
            if (!DisplayHitboxes.hasCollision(d)) continue;

            DisplayHitboxes.Motion m = DisplayHitboxes.motion(d, tick);
            if (!m.moving) continue;

            Box worldBounds = DisplayHitboxes.computeBox(d, false);
            if (worldBounds == null) continue;

            Box probe = worldBounds.expand(MARGIN);
            List<Entity> nearby = world.getOtherEntities(d, probe, e -> !e.isSpectator() && !e.hasVehicle() && !(e instanceof DisplayEntity));

            if (nearby.isEmpty()) continue;

            List<Box> collisionBoxes = DisplayHitboxes.getCollisionBoxes(d);
            double ox = d.getX(), oy = d.getY(), oz = d.getZ();

            for (Entity e : nearby) {
                if (carriedThisTick.contains(e)) continue;

                Box eb = e.getBoundingBox();
                boolean standing = false;
                Vec3d carry = Vec3d.ZERO;

                for (Box local : collisionBoxes) {
                    Box b = local.offset(ox, oy, oz);

                    if (eb.minX <= b.maxX + MARGIN && eb.maxX >= b.minX - MARGIN &&
                            eb.minZ <= b.maxZ + MARGIN && eb.maxZ >= b.minZ - MARGIN) {

                        if (eb.minY >= b.maxY - STAND && eb.minY <= b.maxY + MARGIN + 0.01) {
                            Vec3d q = new Vec3d(
                                    (Math.max(eb.minX, b.minX) + Math.min(eb.maxX, b.maxX)) / 2,
                                    b.maxY,
                                    (Math.max(eb.minZ, b.minZ) + Math.min(eb.maxZ, b.maxZ)) / 2);

                            Vec3d v = DisplayHitboxes.surfaceVelocity(m, q);
                            if (v.lengthSquared() <= MAX_SPEED_SQ && v.lengthSquared() > carry.lengthSquared()) {
                                carry = v;
                                standing = true;
                            }
                        }
                    }
                }

                if (standing && carry.lengthSquared() > 1.0e-10) {
                    e.move(MovementType.SELF, carry);
                    CARRY.put(e, carry);
                    carriedThisTick.add(e);
                }
            }
        }

        Iterator<Map.Entry<Entity, Vec3d>> it = CARRY.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Vec3d> entry = it.next();
            Entity e = entry.getKey();
            if (!carriedThisTick.contains(e)) {
                if (config.physicsKeepMomentum && e.isAlive() && !e.isOnGround()) {
                    Vec3d stored = entry.getValue();
                    e.setVelocity(e.getVelocity().add(stored.x, Math.max(0, stored.y), stored.z));
                }
                it.remove();
            }
        }
    }
}