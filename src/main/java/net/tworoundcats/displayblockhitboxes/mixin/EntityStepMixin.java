package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityStepMixin {

    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void dbh$classicStep(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (!DisplayHitboxConfig.get().accurateHitboxes) return;

        Entity self = (Entity) (Object) this;
        float step = self.maxUpStep();
        if (step <= 0.0f) return;

        Level world = self.level();
        AABB box = self.getBoundingBox();
        AABB area = box.expandTowards(movement.x, step, movement.z).inflate(0.0, 0.0, 0.0);
        if (!nearDisplay(world, self, area)) return;

        List<VoxelShape> list = world.getEntityCollisions(self, box.expandTowards(movement));
        Vec3 vec = movement.lengthSqr() == 0.0 ? movement : Entity.collideBoundingBox(self, movement, box, world, list);

        boolean hitX = movement.x != vec.x;
        boolean hitY = movement.y != vec.y;
        boolean hitZ = movement.z != vec.z;
        boolean landing = hitY && movement.y < 0.0;
        if (!((landing || self.onGround()) && (hitX || hitZ))) return;

        AABB box2 = landing ? box.move(0.0, vec.y, 0.0) : box;
        AABB box3 = box2.expandTowards(movement.x, step, movement.z);
        if (!landing) box3 = box3.expandTowards(0.0, -1.0E-5, 0.0);
        List<VoxelShape> list2 = world.getEntityCollisions(self, box3);

        Vec3 up = Entity.collideBoundingBox(self, new Vec3(movement.x, step, movement.z), box2, world, list2);
        Vec3 lift = Entity.collideBoundingBox(self, new Vec3(0.0, step, 0.0), box2.expandTowards(movement.x, 0.0, movement.z), world, list2);
        if (lift.y < step) {
            Vec3 across = Entity.collideBoundingBox(self, new Vec3(movement.x, 0.0, movement.z), box2.move(lift), world, list2).add(lift);
            if (across.horizontalDistanceSqr() > up.horizontalDistanceSqr()) up = across;
        }

        if (up.horizontalDistanceSqr() > vec.horizontalDistanceSqr()) {
            Vec3 down = Entity.collideBoundingBox(self, new Vec3(0.0, -up.y + movement.y, 0.0), box2.move(up), world, list2);
            double d = box.minY - box2.minY;
            cir.setReturnValue(up.add(down).add(0.0, -d, 0.0));
        }
    }

    private static boolean nearDisplay(Level world, Entity self, AABB area) {
        for (Display d : DisplayHitboxes.displays(world)) {
            if (d != self && DisplayHitboxes.hasCollision(d) && DisplayHitboxes.mayIntersect(d, area)) return true;
        }
        return false;
    }
}