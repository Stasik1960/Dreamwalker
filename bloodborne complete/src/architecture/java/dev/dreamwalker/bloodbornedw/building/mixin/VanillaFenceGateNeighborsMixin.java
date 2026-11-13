package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FenceGateBlock.class)
public abstract class VanillaFenceGateNeighborsMixin {
    @Shadow private boolean isWall(BlockState state) {
        throw new AssertionError("Untransformed shadow");
    }

    @Inject(method = "isWall", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$gateMatchesImportedWall(BlockState neighbor, CallbackInfoReturnable<Boolean> result) {
        if (neighbor.getBlock() instanceof PrototypeWallBlock && PrototypeWallBlock.diagonalPost(neighbor)) {
            result.setReturnValue(false);
        } else if (ImportedFenceAccess.dreamwalker$wallLike(neighbor)) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void dreamwalker$ignoreIsolatedWallsOnPlacement(ItemPlacementContext context,
            CallbackInfoReturnable<BlockState> result) {
        BlockState state = result.getReturnValue();
        if (state != null && state.get(Properties.IN_WALL)) {
            result.setReturnValue(dreamwalker$updateWall(state, context.getWorld(), context.getBlockPos()));
        }
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
    private void dreamwalker$ignoreIsolatedWallsOnUpdate(BlockState state, Direction direction, BlockState neighbor,
            WorldAccess world, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> result) {
        BlockState next = result.getReturnValue();
        if (next.contains(Properties.IN_WALL) && next.get(Properties.IN_WALL)
                && direction.getAxis() == state.get(Properties.HORIZONTAL_FACING).rotateYClockwise().getAxis()) {
            result.setReturnValue(dreamwalker$updateWall(next, world, pos));
        }
    }

    @Unique
    private BlockState dreamwalker$updateWall(BlockState state, WorldAccess world, BlockPos pos) {
        Direction side = state.get(Properties.HORIZONTAL_FACING).rotateYClockwise();
        for (BlockPos neighborPos : new BlockPos[]{pos.offset(side), pos.offset(side.getOpposite())}) {
            BlockState neighbor = world.getBlockState(neighborPos);
            if (BuildingNeighbors.acceptsFenceNeighbor(world, neighborPos, neighbor, state.getBlock()) && isWall(neighbor)) {
                return state;
            }
        }
        return state.with(Properties.IN_WALL, false);
    }
}
