package dev.dreamwalker.bloodbornedw.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

final class ConnectedFenceColumnBlock extends ConnectedFenceBlock {
    private static final VoxelShape LOWER = VoxelShapes.union(
            column(4, 0, 2), column(5, 2, 4), column(4, 4, 6), column(5, 6, 9), column(6, 9, 16));
    private static final VoxelShape UPPER = VoxelShapes.union(column(6, 0, 5), column(5, 5, 13), column(3, 13, 16));

    ConnectedFenceColumnBlock(Settings settings) {
        super(settings, false);
        setDefaultState(getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
    }

    private static VoxelShape column(int inset, int bottom, int top) {
        return createCuboidShape(inset, bottom, inset, 16 - inset, top, 16 - inset);
    }

    private static boolean upper(BlockState state) {
        return state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(Properties.DOUBLE_BLOCK_HALF);
    }

    @Override protected boolean canJoinNeighbor(BlockState neighbor) {
        return neighbor.getBlock() instanceof ConnectedFenceBlock && !(neighbor.getBlock() instanceof ConnectedFenceColumnBlock);
    }

    @Override protected BlockState withNeighbors(BlockState state, BlockView world, BlockPos pos) {
        BlockPos other = upper(state) ? pos.down() : pos.up();
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos first = pos.offset(side);
            BlockPos second = other.offset(side);
            boolean connected = connectsTo(state, world, first, world.getBlockState(first))
                    || connectsTo(state, world, second, world.getBlockState(second));
            state = state.with(FACING_PROPERTIES.get(side), connected);
        }
        return state;
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        World world = context.getWorld();
        BlockPos upperPos = context.getBlockPos().up();
        if (world.isOutOfHeightLimit(upperPos) || !world.getBlockState(upperPos).canReplace(context)) return null;
        if (!world.canPlace(upperState(world, upperPos), upperPos, ShapeContext.absent())) return null;
        return super.getPlacementState(context);
    }

    private BlockState upperState(WorldView world, BlockPos pos) {
        BlockState state = getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                .with(WATERLOGGED, world.getFluidState(pos).isOf(Fluids.WATER));
        return withNeighbors(state, world, pos);
    }

    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        world.setBlockState(pos.up(), upperState(world, pos.up()), Block.NOTIFY_ALL);
    }

    @Override public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (!upper(state)) return true;
        BlockState base = world.getBlockState(pos.down());
        return base.isOf(this) && !upper(base);
    }

    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
            WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == (upper(state) ? Direction.DOWN : Direction.UP)
                && (!neighbor.isOf(this) || upper(neighbor) == upper(state))) {
            return (state.get(WATERLOGGED) ? Blocks.WATER : Blocks.AIR).getDefaultState();
        }
        state = super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
        return withNeighbors(state, world, pos);
    }

    @Override boolean toggleWall(ServerWorld world, BlockPos pos, BlockState state, Direction.Axis axis) {
        BlockPos other = upper(state) ? pos.down() : pos.up();
        BlockState partner = world.getBlockState(other);
        if (!partner.isOf(this) || upper(partner) == upper(state)) return false;
        if (!super.toggleWall(world, pos, state, axis)) return false;
        WallAxis selected = world.getBlockState(pos).get(WALL_AXIS);
        world.setBlockState(other, withNeighbors(partner.with(WALL_AXIS, selected), world, other), Block.NOTIFY_ALL);
        return true;
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = upper(state) ? UPPER : LOWER;
        if (hasArm(state, Direction.NORTH)) shape = VoxelShapes.union(shape, createCuboidShape(7, 0, 0, 9, 16, 7));
        if (hasArm(state, Direction.EAST)) shape = VoxelShapes.union(shape, createCuboidShape(9, 0, 7, 16, 16, 9));
        if (hasArm(state, Direction.SOUTH)) shape = VoxelShapes.union(shape, createCuboidShape(7, 0, 9, 9, 16, 16));
        if (hasArm(state, Direction.WEST)) shape = VoxelShapes.union(shape, createCuboidShape(0, 0, 7, 7, 16, 9));
        return shape;
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }
}
