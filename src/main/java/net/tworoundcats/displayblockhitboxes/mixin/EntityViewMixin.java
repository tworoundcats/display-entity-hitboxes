package net.tworoundcats.displayblockhitboxes.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(EntityView.class)
public interface EntityViewMixin {

    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    default void dbh$accurateEntityCollisions(Entity entity, Box box, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (!DisplayHitboxConfig.get().accurateHitboxes) return;
        if (box.getAverageSideLength() < 1.0E-7) return;

        EntityView view = (EntityView) this;
        Predicate<Entity> predicate = entity == null ? EntityPredicates.CAN_COLLIDE : EntityPredicates.EXCEPT_SPECTATOR.and(entity::collidesWith);
        Box query = box.expand(1.0E-7);
        List<Entity> list = view.getOtherEntities(entity, query, predicate);

        // displays that vanilla's lookup, missed box far from the origin of the display
        List<DisplayEntity> extra = new ArrayList<>();
        if (view instanceof World world) {
            Set<Entity> inList = Collections.newSetFromMap(new IdentityHashMap<>());
            inList.addAll(list);
            for (DisplayEntity d : DisplayHitboxes.displays(world)) {
                if (d == entity || inList.contains(d)) continue;
                if (!DisplayHitboxes.hasCollision(d) || !predicate.test(d)) continue;
                if (DisplayHitboxes.mayIntersect(d, query)) extra.add(d);
            }
        }

        if (list.isEmpty() && extra.isEmpty()) return;

        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builder();
        for (Entity other : list) {
            if (other instanceof DisplayEntity display && DisplayHitboxes.hasCollision(display)) {
                DisplayHitboxes.addShapes(builder, display, box);
                continue;
            }
            builder.add(VoxelShapes.cuboid(other.getBoundingBox()));
        }
        for (DisplayEntity d : extra) DisplayHitboxes.addShapes(builder, d, box);

        cir.setReturnValue(builder.build());
    }
}