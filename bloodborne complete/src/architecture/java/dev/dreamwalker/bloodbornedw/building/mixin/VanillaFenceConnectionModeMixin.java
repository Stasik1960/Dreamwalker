package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FenceBlock.class, PaneBlock.class})
abstract class VanillaFenceConnectionModeMixin {
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void dreamwalker$checkPlacementConnections(ItemPlacementContext context,
            CallbackInfoReturnable<BlockState> result) {
        var state = result.getReturnValue();
        if (state == null) return;
        var world = context.getWorld();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            var property = switch (direction) {
                case NORTH -> Properties.NORTH;
                case EAST -> Properties.EAST;
                case SOUTH -> Properties.SOUTH;
                case WEST -> Properties.WEST;
                default -> throw new IllegalArgumentException("Expected a horizontal connection");
            };
            if (!state.get(property)) continue;
            BlockPos neighborPos = context.getBlockPos().offset(direction);
            var neighbor = world.getBlockState(neighborPos);
            if (!BuildingNeighbors.acceptsFenceNeighbor(world, neighborPos, neighbor, state.getBlock())) {
                state = state.with(property, false);
            }
        }
        result.setReturnValue(state);
    }

    @WrapMethod(method = "getStateForNeighborUpdate")
    private BlockState dreamwalker$checkNeighborConnection(BlockState state, Direction direction, BlockState neighbor,
            WorldAccess world, BlockPos pos, BlockPos neighborPos, Operation<BlockState> original) {
        if (direction.getAxis().isHorizontal()
                && !BuildingNeighbors.acceptsFenceNeighbor(world, neighborPos, neighbor, state.getBlock())) {
            neighbor = Blocks.AIR.getDefaultState();
        }
        return original.call(state, direction, neighbor, world, pos, neighborPos);
    }
}
