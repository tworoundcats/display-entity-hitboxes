package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityStepMixin {

    @Inject(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"), cancellable = true)
    private void dbh$classicStep(Vec3d movement, CallbackInfoReturnable<Vec3d> cir) {
        if (!DisplayHitboxConfig.get().accurateHitboxes) return;

        Entity self = (Entity) (Object) this;
        float step = self.getStepHeight();
        if (step <= 0.0f) return;

        World world = self.getEntityWorld();
        Box box = self.getBoundingBox();
        Box area = box.stretch(movement.x, step, movement.z).expand(0.0, 0.0, 0.0);
        if (!nearDisplay(world, self, area)) return;

        List<VoxelShape> list = world.getEntityCollisions(self, box.stretch(movement));
        Vec3d vec = movement.lengthSquared() == 0.0 ? movement : Entity.adjustMovementForCollisions(self, movement, box, world, list);

        boolean hitX = movement.x != vec.x;
        boolean hitY = movement.y != vec.y;
        boolean hitZ = movement.z != vec.z;
        boolean landing = hitY && movement.y < 0.0;
        if (!((landing || self.isOnGround()) && (hitX || hitZ))) return;

        Box box2 = landing ? box.offset(0.0, vec.y, 0.0) : box;
        Box box3 = box2.stretch(movement.x, step, movement.z);
        if (!landing) box3 = box3.stretch(0.0, -1.0E-5, 0.0);
        List<VoxelShape> list2 = world.getEntityCollisions(self, box3);

        Vec3d up = Entity.adjustMovementForCollisions(self, new Vec3d(movement.x, step, movement.z), box2, world, list2);
        Vec3d lift = Entity.adjustMovementForCollisions(self, new Vec3d(0.0, step, 0.0), box2.stretch(movement.x, 0.0, movement.z), world, list2);
        if (lift.y < step) {
            Vec3d across = Entity.adjustMovementForCollisions(self, new Vec3d(movement.x, 0.0, movement.z), box2.offset(lift), world, list2).add(lift);
            if (across.horizontalLengthSquared() > up.horizontalLengthSquared()) up = across;
        }

        if (up.horizontalLengthSquared() > vec.horizontalLengthSquared()) {
            Vec3d down = Entity.adjustMovementForCollisions(self, new Vec3d(0.0, -up.y + movement.y, 0.0), box2.offset(up), world, list2);
            double d = box.minY - box2.minY;
            cir.setReturnValue(up.add(down).add(0.0, -d, 0.0));
        }
    }

    private static boolean nearDisplay(World world, Entity self, Box area) {
        for (DisplayEntity d : DisplayHitboxes.displays(world)) {
            if (d != self && DisplayHitboxes.hasCollision(d) && DisplayHitboxes.mayIntersect(d, area)) return true;
        }
        return false;
    }
}