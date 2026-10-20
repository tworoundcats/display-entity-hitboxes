package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.BlockDisplayEntity.class)
public interface BlockDisplayEntityInvoker {
    @Invoker("getBlockState")
    BlockState invokeGetBlockState();
}