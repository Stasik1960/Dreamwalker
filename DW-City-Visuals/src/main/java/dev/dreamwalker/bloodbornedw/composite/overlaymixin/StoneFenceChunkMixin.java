package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class StoneFenceChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void dw$fencePlaced(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        BlockState before = cir.getReturnValue();
        if (before == null || (!AutoStoneFence.matches(before) && !AutoStoneFence.matches(state))) return;
        WorldChunk chunk = (WorldChunk)(Object)this;
        if (chunk.getWorld() instanceof ServerWorld world)
            AutoStoneFence.changed(world, pos, before, chunk.getBlockState(pos));
    }
}
