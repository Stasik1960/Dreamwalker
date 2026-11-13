package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SideShapeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BuildingNeighborFacesMixin {
    @Inject(method = "isSideSolid", at = @At("RETURN"), cancellable = true)
    private void dreamwalker$fullCarrierFace(BlockView world, BlockPos pos, Direction face, SideShapeType type,
            CallbackInfoReturnable<Boolean> result) {
        if (type != SideShapeType.FULL || result.getReturnValueZ()) return;
        BlockState state = (BlockState) (Object) this;
        if (BuildingNeighbors.dreamWalker(state) && BuildingNeighbors.fullFace(state, world, pos, face)) {
            result.setReturnValue(true);
        }
    }
}
