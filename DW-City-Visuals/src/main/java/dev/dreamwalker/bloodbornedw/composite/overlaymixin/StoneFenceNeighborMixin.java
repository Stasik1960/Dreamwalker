package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeRootBlock.class, remap = false)
public abstract class StoneFenceNeighborMixin {
    @Inject(method = "method_9559", at = @At("RETURN"), remap = false)
    private void dw$fenceNeighbor(BlockState state, Direction side, BlockState neighbor, WorldAccess world,
            BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        if ((side.getAxis().isHorizontal() || side == Direction.UP) && AutoStoneFence.matches(state) && world instanceof ServerWorld server)
            AutoStoneFence.enqueue(server, pos);
    }
}
