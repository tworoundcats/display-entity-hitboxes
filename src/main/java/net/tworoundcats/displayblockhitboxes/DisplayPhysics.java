package net.tworoundcats.displayblockhitboxes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

public final class DisplayPhysics {
    private static final Map<Entity, Vec3d> CARRY = new WeakHashMap<>();

    private DisplayPhysics() {}

    public static void tickClient(MinecraftClient mc) {
        World world = mc.world;
        Entity player = mc.player;
        if (world == null || player == null || mc.isPaused()) return;

        DisplayHitboxConfig config = DisplayHitboxConfig.get();
        if (!config.displayPhysics || !config.accurateHitboxes) return;
        if (player.isSpectator() || player.hasVehicle()) return;

        apply(world, player, world.getTime());
    }

    private static void apply(World world, Entity e, long tick) {
        DisplayHitboxConfig cfg = DisplayHitboxConfig.get();
        final double MARGIN = cfg.physicsContactMargin;
        final double MAX_SPEED = cfg.physicsMaxSpeed;
        final double MAX_PUSH = cfg.physicsMaxPush;
        final double STAND = cfg.physicsStandTolerance;
        Box eb = e.getBoundingBox();
        Box probe = eb.expand(MARGIN);

        Vec3d carry = Vec3d.ZERO;
        Vec3d impulse = Vec3d.ZERO;
        boolean carried = false;
        double depX = 0, depY = 0, depZ = 0;

        for (DisplayEntity d : DisplayHitboxes.displays(world)) {
            if (!DisplayHitboxes.hasCollision(d) || !DisplayHitboxes.mayIntersect(d, probe)) continue;

            DisplayHitboxes.Motion m = DisplayHitboxes.motion(d, tick);
            double ox = d.getX(), oy = d.getY(), oz = d.getZ();

            for (Box local : DisplayHitboxes.getCollisionBoxes(d)) {
                Box b = local.offset(ox, oy, oz);
                if (!b.intersects(probe)) continue;

                Vec3d q = new Vec3d(
                        (Math.max(probe.minX, b.minX) + Math.min(probe.maxX, b.maxX)) / 2,
                        (Math.max(probe.minY, b.minY) + Math.min(probe.maxY, b.maxY)) / 2,
                        (Math.max(probe.minZ, b.minZ) + Math.min(probe.maxZ, b.maxZ)) / 2);
                Vec3d v = DisplayHitboxes.surfaceVelocity(m, q);
                if (v.length() > MAX_SPEED) continue;

                boolean standing = eb.minY >= b.maxY - STAND && eb.minY <= b.maxY + MARGIN + 0.01;
                if (standing) {
                    if (!carried || v.lengthSquared() > carry.lengthSquared()) carry = v;
                    carried = true;
                    continue;
                }

                if (!b.intersects(eb)) continue;

                // push out along the shortest route and carry over its speed
                double px = Math.min(eb.maxX, b.maxX) - Math.max(eb.minX, b.minX);
                double py = Math.min(eb.maxY, b.maxY) - Math.max(eb.minY, b.minY);
                double pz = Math.min(eb.maxZ, b.maxZ) - Math.max(eb.minZ, b.minZ);
                if (Math.min(px, Math.min(py, pz)) < 1.0e-4) continue;

                if (px <= py && px <= pz) {
                    double s = (eb.minX + eb.maxX) >= (b.minX + b.maxX) ? px : -px;
                    if (Math.abs(s) > Math.abs(depX)) depX = s;
                } else if (py <= pz) {
                    double s = (eb.minY + eb.maxY) >= (b.minY + b.maxY) ? py : -py;
                    if (Math.abs(s) > Math.abs(depY)) depY = s;
                } else {
                    double s = (eb.minZ + eb.maxZ) >= (b.minZ + b.maxZ) ? pz : -pz;
                    if (Math.abs(s) > Math.abs(depZ)) depZ = s;
                }
                if (v.lengthSquared() > impulse.lengthSquared()) impulse = v;
            }
        }

        Vec3d move = new Vec3d(
                Math.max(-MAX_PUSH, Math.min(MAX_PUSH, depX)),
                Math.max(-MAX_PUSH, Math.min(MAX_PUSH, depY)),
                Math.max(-MAX_PUSH, Math.min(MAX_PUSH, depZ)));
        if (carried) move = move.add(carry);
        if (move.lengthSquared() > 1.0e-10) e.move(MovementType.SELF, move);

        if (impulse.lengthSquared() > 1.0e-8) {
            Vec3d vel = e.getVelocity();
            Vec3d n = impulse.normalize();
            double need = impulse.length() - vel.dotProduct(n);
            if (need > 0) e.setVelocity(vel.add(n.multiply(need)));
        }

        // momentum that just keep the platform's speed once when you leave it
        if (carried) {
            CARRY.put(e, carry);
        } else {
            Vec3d stored = CARRY.remove(e);
            if (cfg.physicsKeepMomentum && stored != null && !e.isOnGround()) {
                e.setVelocity(e.getVelocity().add(stored.x, Math.max(0, stored.y), stored.z));
            }
        }
    }
}