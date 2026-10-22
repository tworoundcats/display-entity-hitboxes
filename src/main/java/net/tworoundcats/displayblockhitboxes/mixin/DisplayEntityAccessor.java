package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.class)
public interface DisplayEntityAccessor {
    @Accessor("DATA_TRANSLATION_ID")
    static EntityDataAccessor<Vector3fc> getTranslation() { throw new AssertionError(); }

    @Accessor("DATA_SCALE_ID")
    static EntityDataAccessor<Vector3fc> getScale() { throw new AssertionError(); }

    @Accessor("DATA_LEFT_ROTATION_ID")
    static EntityDataAccessor<Quaternionfc> getLeftRotation() { throw new AssertionError(); }

    @Accessor("DATA_RIGHT_ROTATION_ID")
    static EntityDataAccessor<Quaternionfc> getRightRotation() { throw new AssertionError(); }
}