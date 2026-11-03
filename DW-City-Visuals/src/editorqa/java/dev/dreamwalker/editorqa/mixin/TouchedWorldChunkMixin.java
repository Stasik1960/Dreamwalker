package dev.dreamwalker.editorqa.mixin;

import dev.dreamwalker.editorqa.TouchedCells;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldChunk.class)
abstract class TouchedWorldChunkMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void dwqa$before(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        TouchedCells.touch(((WorldChunk)(Object)this).getWorld(), pos);
    }
}
