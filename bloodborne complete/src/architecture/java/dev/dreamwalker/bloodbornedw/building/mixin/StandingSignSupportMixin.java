package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignBlock.class)
public abstract class StandingSignSupportMixin {
    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$standOnCarrier(BlockState state, WorldView world, BlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        BlockPos support = pos.down();
        if (BuildingNeighbors.dreamWalker(world.getBlockState(support))) {
            result.setReturnValue(BuildingNeighbors.signSupport(world, support, Direction.UP));
        }
    }
}
