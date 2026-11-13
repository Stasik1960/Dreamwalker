package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallSignBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallSignBlock.class)
public abstract class WallSignSupportMixin {
    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$hangOnCarrier(BlockState state, WorldView world, BlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        Direction face = state.get(WallSignBlock.FACING);
        BlockPos support = pos.offset(face.getOpposite());
        if (BuildingNeighbors.dreamWalker(world.getBlockState(support))) {
            result.setReturnValue(BuildingNeighbors.signSupport(world, support, face));
        }
    }
}
