package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
public abstract class RetainedFrameChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void dw$changed(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        BlockState before = cir.getReturnValue();
        if (before == null) return;
        WorldChunk chunk = (WorldChunk)(Object)this;
        if (!RetainedFrameState.matches(before) && RetainedFrames.all(RetainedFrames.data(chunk)).isEmpty()) return;
        BlockState after = chunk.getBlockState(pos);
        if (!before.equals(after)) RetainedFramePlacement.changed(chunk, pos, before, after);
    }
}

