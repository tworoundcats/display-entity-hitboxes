package net.tworoundcats.displayblockhitboxes;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DisplayPhysics {
    private static final Map<Entity, Vec3> CARRY = new WeakHashMap<>();

    private DisplayPhysics() {}

    public static void tickClient(Minecraft mc) {
        Level world = mc.level;
        Entity player = mc.player;
        if (world == null || player == null || mc.isPaused()) return;

        DisplayHitboxConfig config = DisplayHitboxConfig.get();
        if (!config.displayPhysics || !config.accurateHitboxes) return;
        if (player.isSpectator() || player.isPassenger()) return;

        apply(world, player, world.getGameTime());
    }

    private static void apply(Level world, Entity e, long tick) {
        DisplayHitboxConfig cfg = DisplayHitboxConfig.get();
        final double MARGIN = cfg.physicsContactMargin;
        final double MAX_SPEED = cfg.physicsMaxSpeed;
        final double MAX_PUSH = cfg.physicsMaxPush;
        final double STAND = cfg.physicsStandTolerance;
        AABB eb = e.getBoundingBox();
        AABB probe = eb.inflate(MARGIN);

        Vec3 carry = Vec3.ZERO;
        Vec3 impulse = Vec3.ZERO;
        boolean carried = false;
        double depX = 0, depY = 0, depZ = 0;

        for (Display d : DisplayHitboxes.displays(world)) {
            if (!DisplayHitboxes.hasCollision(d) || !DisplayHitboxes.mayIntersect(d, probe)) continue;

            DisplayHitboxes.Motion m = DisplayHitboxes.motion(d, tick);
            if (!m.moving) continue;
            double ox = d.getX(), oy = d.getY(), oz = d.getZ();

            for (AABB local : DisplayHitboxes.getCollisionBoxes(d)) {
                AABB b = local.move(ox, oy, oz);
                if (!b.intersects(probe)) continue;

                Vec3 q = new Vec3(
                        (Math.max(probe.minX, b.minX) + Math.min(probe.maxX, b.maxX)) / 2,
                        (Math.max(probe.minY, b.minY) + Math.min(probe.maxY, b.maxY)) / 2,
                        (Math.max(probe.minZ, b.minZ) + Math.min(probe.maxZ, b.maxZ)) / 2);
                Vec3 v = DisplayHitboxes.surfaceVelocity(m, q);
                if (v.length() > MAX_SPEED) continue;

                boolean standing = eb.minY >= b.maxY - STAND && eb.minY <= b.maxY + MARGIN + 0.01;
                if (standing) {
                    if (!carried || v.lengthSqr() > carry.lengthSqr()) carry = v;
                    carried = true;
                    continue;
                }

                if (!b.intersects(eb)) continue;

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
                if (v.lengthSqr() > impulse.lengthSqr()) impulse = v;
            }
        }

        Vec3 move = new Vec3(
                Math.max(-MAX_PUSH, Math.min(MAX_PUSH, depX)),
                Math.max(-MAX_PUSH, Math.min(MAX_PUSH, depY)),
                Math.max(-MAX_PUSH, Math.min(MAX_PUSH, depZ)));
        if (carried) move = move.add(carry);
        if (move.lengthSqr() > 1.0e-10) e.move(MoverType.SELF, move);

        if (impulse.lengthSqr() > 1.0e-8) {
            Vec3 vel = e.getDeltaMovement();
            Vec3 n = impulse.normalize();
            double need = impulse.length() - vel.dot(n);
            if (need > 0) e.setDeltaMovement(vel.add(n.scale(need)));
        }

        if (carried) {
            CARRY.put(e, carry);
        } else {
            Vec3 stored = CARRY.remove(e);
            if (cfg.physicsKeepMomentum && stored != null && !e.onGround()) {
                e.setDeltaMovement(e.getDeltaMovement().add(stored.x, Math.max(0, stored.y), stored.z));
            }
        }
    }
}