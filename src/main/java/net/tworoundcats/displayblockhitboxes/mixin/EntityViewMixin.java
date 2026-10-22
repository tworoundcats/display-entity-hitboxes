package net.tworoundcats.displayblockhitboxes.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

@Mixin(EntityGetter.class)
public interface EntityViewMixin {

    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    default void dbh$accurateEntityCollisions(Entity entity, AABB box, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (!DisplayHitboxConfig.get().accurateHitboxes) return;
        if (box.getSize() < 1.0E-7) return;

        EntityGetter view = (EntityGetter) this;
        Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        AABB query = box.inflate(1.0E-7);
        List<Entity> list = view.getEntities(entity, query, predicate);

        // displays that vanilla's lookup, missed box far from the origin of the display
        List<Display> extra = new ArrayList<>();
        if (view instanceof Level world) {
            Set<Entity> inList = Collections.newSetFromMap(new IdentityHashMap<>());
            inList.addAll(list);
            for (Display d : DisplayHitboxes.displays(world)) {
                if (d == entity || inList.contains(d)) continue;
                if (!DisplayHitboxes.hasCollision(d) || !predicate.test(d)) continue;
                if (DisplayHitboxes.mayIntersect(d, query)) extra.add(d);
            }
        }

        if (list.isEmpty() && extra.isEmpty()) return;

        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builder();
        for (Entity other : list) {
            if (other instanceof Display display && DisplayHitboxes.hasCollision(display)) {
                DisplayHitboxes.addShapes(builder, display, box);
                continue;
            }
            builder.add(Shapes.create(other.getBoundingBox()));
        }
        for (Display d : extra) DisplayHitboxes.addShapes(builder, d, box);

        cir.setReturnValue(builder.build());
    }
}