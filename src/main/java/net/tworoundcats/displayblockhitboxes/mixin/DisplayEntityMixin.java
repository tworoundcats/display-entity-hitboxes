package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DisplayEntity.class)
public abstract class DisplayEntityMixin extends Entity {

    public DisplayEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    // Allows the player to target and click the display entity
    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private void makeInteractable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof DisplayEntity.BlockDisplayEntity) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void pushEntitiesFromBlockDisplays(CallbackInfo ci) {
        if (!((Object) this instanceof DisplayEntity.BlockDisplayEntity blockDisplay)) return;

        Vector3f s = this.getDataTracker().get(DisplayEntityAccessor.getScale());

        // Failsafe against NaN matrix inversions if scaled to 0
        if (Math.abs(s.x()) < 1e-5 || Math.abs(s.y()) < 1e-5 || Math.abs(s.z()) < 1e-5) return;

        double maxScale = Math.max(s.x(), Math.max(s.y(), s.z()));

        // Broad phase search: Only perform math if entities are physically nearby
        Box bounds = new Box(
            this.getX() - maxScale - 1, this.getY() - maxScale - 1, this.getZ() - maxScale - 1,
            this.getX() + maxScale + 1, this.getY() + maxScale + 1, this.getZ() + maxScale + 1
        );

        List<Entity> entities = this.getWorld().getOtherEntities(this, bounds,
            e -> !e.isSpectator() && !(e instanceof DisplayEntity));

        if (entities.isEmpty()) return;

        // Obtain block's collision boxes (supports Slabs, Stairs, and custom blocks)
        BlockState state = blockDisplay.getBlockState();
        VoxelShape shape = state.getCollisionShape(this.getWorld(), this.getBlockPos());
        if (shape.isEmpty()) return;
        List<Box> blockBoxes = shape.getBoundingBoxes();

        Vector3f t = this.getDataTracker().get(DisplayEntityAccessor.getTranslation());
        Quaternionf lr = this.getDataTracker().get(DisplayEntityAccessor.getLeftRotation());
        Quaternionf rr = this.getDataTracker().get(DisplayEntityAccessor.getRightRotation());

        // Construct the transformation matrix exactly as the renderer does
        Matrix4f worldMatrix = new Matrix4f();
        worldMatrix.translate((float) this.getX(), (float) this.getY(), (float) this.getZ());
        worldMatrix.translate(t);
        worldMatrix.rotate(lr);
        worldMatrix.scale(s);
        worldMatrix.rotate(rr);

        Matrix4f invWorld = new Matrix4f(worldMatrix);
        invWorld.invert();

        for (Entity entity : entities) {
            Box eBox = entity.getBoundingBox();
            Vec3d[] corners = new Vec3d[]{
                new Vec3d(eBox.minX, eBox.minY, eBox.minZ), new Vec3d(eBox.maxX, eBox.minY, eBox.minZ),
                new Vec3d(eBox.minX, eBox.maxY, eBox.minZ), new Vec3d(eBox.maxX, eBox.maxY, eBox.minZ),
                new Vec3d(eBox.minX, eBox.minY, eBox.maxZ), new Vec3d(eBox.maxX, eBox.minY, eBox.maxZ),
                new Vec3d(eBox.minX, eBox.maxY, eBox.maxZ), new Vec3d(eBox.maxX, eBox.maxY, eBox.maxZ)
            };

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            // Map the entity's hitbox into the block's local OBB space
            for (Vec3d corner : corners) {
                Vector4f localP = new Vector4f((float) corner.x, (float) corner.y, (float) corner.z, 1.0f);
                invWorld.transform(localP);
                minX = Math.min(minX, localP.x()); maxX = Math.max(maxX, localP.x());
                minY = Math.min(minY, localP.y()); maxY = Math.max(maxY, localP.y());
                minZ = Math.min(minZ, localP.z()); maxZ = Math.max(maxZ, localP.z());
            }

            boolean modified = false;
            double totalPushX = 0, totalPushY = 0, totalPushZ = 0;

            for (Box bBox : blockBoxes) {
                if (maxX <= bBox.minX || minX >= bBox.maxX ||
                    maxY <= bBox.minY || minY >= bBox.maxY ||
                    maxZ <= bBox.minZ || minZ >= bBox.maxZ) {
                    continue;
                }

                // Calculate minimum translation vectors (penetration depths)
                double px1 = bBox.maxX - minX; double px2 = maxX - bBox.minX;
                double py1 = bBox.maxY - minY; double py2 = maxY - bBox.minY;
                double pz1 = bBox.maxZ - minZ; double pz2 = maxZ - bBox.minZ;

                double minPen = px1;
                Vector3f localPush = new Vector3f((float) px1, 0, 0);

                if (px2 < minPen) { minPen = px2; localPush.set((float) -px2, 0, 0); }
                if (py1 < minPen) { minPen = py1; localPush.set(0, (float) py1, 0); }
                if (py2 < minPen) { minPen = py2; localPush.set(0, (float) -py2, 0); }
                if (pz1 < minPen) { minPen = pz1; localPush.set(0, 0, (float) pz1); }
                if (pz2 < minPen) { minPen = pz2; localPush.set(0, 0, (float) -pz2); }

                minX += localPush.x(); maxX += localPush.x();
                minY += localPush.y(); maxY += localPush.y();
                minZ += localPush.z(); maxZ += localPush.z();

                totalPushX += localPush.x();
                totalPushY += localPush.y();
                totalPushZ += localPush.z();
                modified = true;
            }

            // Entity intersected, resolve physically
            if (modified) {
                // w=0.0f ensures we only apply rotation & scale, not translation
                Vector4f worldPush4 = new Vector4f((float) totalPushX, (float) totalPushY, (float) totalPushZ, 0.0f);
                worldMatrix.transform(worldPush4);
                Vector3f worldPush = new Vector3f(worldPush4.x(), worldPush4.y(), worldPush4.z());

                entity.setPosition(entity.getX() + worldPush.x(), entity.getY() + worldPush.y(), entity.getZ() + worldPush.z());

                Vec3d velocity = entity.getVelocity();

                // Emulate solid block gravity cancelation / landing
                if (worldPush.y() > 0.0001f) {
                    entity.setVelocity(velocity.x, Math.max(0, velocity.y), velocity.z);
                    entity.setOnGround(true);
                    entity.fallDistance = 0;
                }
            }
        }
    }
}
