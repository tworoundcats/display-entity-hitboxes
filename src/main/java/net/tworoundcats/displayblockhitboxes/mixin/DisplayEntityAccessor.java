package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.decoration.DisplayEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
    @Accessor("TRANSLATION")
    static TrackedData<Vector3f> getTranslation() { throw new AssertionError(); }

    @Accessor("SCALE")
    static TrackedData<Vector3f> getScale() { throw new AssertionError(); }

    @Accessor("LEFT_ROTATION")
    static TrackedData<Quaternionf> getLeftRotation() { throw new AssertionError(); }

    @Accessor("RIGHT_ROTATION")
    static TrackedData<Quaternionf> getRightRotation() { throw new AssertionError(); }
}
