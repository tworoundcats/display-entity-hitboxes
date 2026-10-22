package net.tworoundcats.displayblockhitboxes;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.IdentityHashMap;
import java.util.Collections;

public final class DisplayPhysics {
    private static final Map<Entity, Vec3> CARRY = new WeakHashMap<>();

    private DisplayPhysics() {}

    public static void tickClient(Minecraft mc) {
        Level world = mc.level;
        if (world == null || mc.isPaused()) return;

        DisplayHitboxConfig config = DisplayHitboxConfig.get();
        if (!config.displayPhysics || !config.accurateHitboxes) return;

        long tick = world.getGameTime();
        final double MARGIN = config.physicsContactMargin;
        final double STAND = config.physicsStandTolerance;
        final double MAX_SPEED_SQ = config.physicsMaxSpeed * config.physicsMaxSpeed;

        Set<Entity> carriedThisTick = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Display d : DisplayHitboxes.displays(world)) {
            if (!DisplayHitboxes.hasCollision(d)) continue;

            DisplayHitboxes.Motion m = DisplayHitboxes.motion(d, tick);
            if (!m.moving) continue;

            AABB worldBounds = DisplayHitboxes.computeBox(d, false);
            if (worldBounds == null) continue;

            AABB probe = worldBounds.inflate(MARGIN);
            List<Entity> nearby = world.getEntities(d, probe, e -> !e.isSpectator() && !e.isPassenger() && !(e instanceof Display));

            if (nearby.isEmpty()) continue;

            List<AABB> collisionBoxes = DisplayHitboxes.getCollisionBoxes(d);
            double ox = d.getX(), oy = d.getY(), oz = d.getZ();

            for (Entity e : nearby) {
                if (carriedThisTick.contains(e)) continue;

                AABB eb = e.getBoundingBox();
                boolean standing = false;
                Vec3 carry = Vec3.ZERO;

                for (AABB local : collisionBoxes) {
                    AABB b = local.move(ox, oy, oz);

                    if (eb.minX <= b.maxX + MARGIN && eb.maxX >= b.minX - MARGIN &&
                            eb.minZ <= b.maxZ + MARGIN && eb.maxZ >= b.minZ - MARGIN) {

                        if (eb.minY >= b.maxY - STAND && eb.minY <= b.maxY + MARGIN + 0.01) {
                            Vec3 q = new Vec3(
                                    (Math.max(eb.minX, b.minX) + Math.min(eb.maxX, b.maxX)) / 2,
                                    b.maxY,
                                    (Math.max(eb.minZ, b.minZ) + Math.min(eb.maxZ, b.maxZ)) / 2);

                            Vec3 v = DisplayHitboxes.surfaceVelocity(m, q);
                            if (v.lengthSqr() <= MAX_SPEED_SQ && v.lengthSqr() > carry.lengthSqr()) {
                                carry = v;
                                standing = true;
                            }
                        }
                    }
                }

                if (standing && carry.lengthSqr() > 1.0e-10) {
                    e.move(MoverType.SELF, carry);
                    CARRY.put(e, carry);
                    carriedThisTick.add(e);
                }
            }
        }

        Iterator<Map.Entry<Entity, Vec3>> it = CARRY.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Vec3> entry = it.next();
            Entity e = entry.getKey();
            if (!carriedThisTick.contains(e)) {
                if (config.physicsKeepMomentum && e.isAlive() && !e.onGround()) {
                    Vec3 stored = entry.getValue();
                    e.setDeltaMovement(e.getDeltaMovement().add(stored.x, Math.max(0, stored.y), stored.z));
                }
                it.remove();
            }
        }
    }
}