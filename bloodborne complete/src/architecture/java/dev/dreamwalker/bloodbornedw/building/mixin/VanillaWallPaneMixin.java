package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WallBlock.class)
public abstract class VanillaWallPaneMixin {
    @WrapMethod(method = "shouldConnectTo")
    private boolean dreamwalker$connectImportedPane(BlockState neighbor, boolean solid, Direction face,
            Operation<Boolean> original) {
        // The distribution's wall mixin also handles imported families and can return early.
        return ImportedFenceAccess.dreamwalker$matches(neighbor)
                && !ImportedFenceAccess.dreamwalker$wallLike(neighbor)
                && !ImportedFenceAccess.dreamwalker$fenceLike(neighbor)
                || original.call(neighbor, solid, face);
    }

    @WrapOperation(method = "getPlacementState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/block/WallBlock;shouldConnectTo(Lnet/minecraft/block/BlockState;ZLnet/minecraft/util/math/Direction;)Z"))
    private boolean dreamwalker$checkPlacementConnection(WallBlock wall, BlockState neighbor, boolean solid,
            Direction face, Operation<Boolean> original, ItemPlacementContext context) {
        BlockPos neighborPos = context.getBlockPos().offset(face.getOpposite());
        return BuildingNeighbors.acceptsFenceNeighbor(context.getWorld(), neighborPos, neighbor, wall)
                && original.call(wall, neighbor, solid, face);
    }

    @WrapMethod(method = "getStateForNeighborUpdate")
    private BlockState dreamwalker$checkWallConnection(BlockState state, Direction direction, BlockState neighbor,
            WorldAccess world, BlockPos pos, BlockPos neighborPos, Operation<BlockState> original) {
        if (direction.getAxis().isHorizontal()) {
            // Imported walls use a vanilla state to calculate the shape of their selected arms.
            var source = state.getBlock();
            if (state.isOf(Blocks.COBBLESTONE_WALL)
                    && ImportedFenceAccess.dreamwalker$matches(world.getBlockState(pos))) {
                return original.call(state, direction, neighbor, world, pos, neighborPos);
            }
            if ((!neighbor.isOf(source) && BuildingNeighbors.sameIdFence(world, pos, state))
                    || !BuildingNeighbors.acceptsFenceNeighbor(world, neighborPos, neighbor, source)) {
                neighbor = Blocks.AIR.getDefaultState();
            }
        }
        return original.call(state, direction, neighbor, world, pos, neighborPos);
    }
}
