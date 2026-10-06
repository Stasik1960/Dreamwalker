package dev.dreamwalker.bloodbornedw.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Block.class)
public interface BlockDefaultStateAccessor {
    @Invoker("setDefaultState") void bloodborneDw$setDefaultState(BlockState state);
}
