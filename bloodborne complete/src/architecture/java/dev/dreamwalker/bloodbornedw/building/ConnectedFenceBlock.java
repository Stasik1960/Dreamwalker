package dev.dreamwalker.bloodbornedw.building;

import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

class ConnectedFenceBlock extends FenceBlock {
    private final VoxelShape[] placementShapes;
    private final VoxelShape[] playerShapes;

    ConnectedFenceBlock(Settings settings, boolean isolatedRail) {
        super(settings);
        placementShapes = createShapes(2, 2, 16, 0, 16);
        playerShapes = createShapes(2, 2, 24, 0, 24);
        if (isolatedRail) {
            placementShapes[0] = createCuboidShape(6, 0, 4, 10, 16, 12);
            playerShapes[0] = createCuboidShape(6, 0, 4, 10, 24, 12);
        }
    }

    protected boolean connectsTo(BlockState neighbor) {
        return !neighbor.isAir() && !(neighbor.getBlock() instanceof FluidBlock);
    }

    protected BlockState withNeighbors(BlockState state, BlockView world, BlockPos pos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            state = state.with(FACING_PROPERTIES.get(direction), connectsTo(world.getBlockState(pos.offset(direction))));
        }
        return state;
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        BlockState state = getDefaultState().with(WATERLOGGED, context.getWorld().getFluidState(pos).isOf(Fluids.WATER));
        return withNeighbors(state, context.getWorld(), pos);
    }

    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
            WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        var property = FACING_PROPERTIES.get(direction);
        return property == null ? state : state.with(property, connectsTo(neighbor));
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return placementShapes[getShapeIndex(state)];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        boolean player = context instanceof EntityShapeContext entity && entity.getEntity() instanceof PlayerEntity;
        return (player ? playerShapes : placementShapes)[getShapeIndex(state)];
    }

    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }
}
