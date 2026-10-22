package net.tworoundcats.displayblockhitboxes.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayEntityInvoker {
    @Invoker("getBlockState")
    BlockState invokeGetBlockState();
}