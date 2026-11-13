package dev.dreamwalker.bloodbornedw.building;

import dev.dreamwalker.bloodbornedw.composite.BuildingNeighbors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.server.world.ServerWorld;

class ConnectedFenceBlock extends FenceBlock {
    enum WallAxis implements StringIdentifiable {
        NONE, X, Z, SAME_ID;

        @Override public String asString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final EnumProperty<WallAxis> WALL_AXIS = EnumProperty.of("wall_axis", WallAxis.class);
    private final VoxelShape[] placementShapes;
    private final VoxelShape[] playerShapes;

    ConnectedFenceBlock(Settings settings, boolean isolatedRail) {
        super(settings);
        setDefaultState(getDefaultState().with(WALL_AXIS, WallAxis.NONE));
        placementShapes = createShapes(2, 2, 16, 0, 16);
        playerShapes = createShapes(2, 2, 24, 0, 24);
        if (isolatedRail) {
            placementShapes[0] = createCuboidShape(6, 0, 4, 10, 16, 12);
            playerShapes[0] = createCuboidShape(6, 0, 4, 10, 24, 12);
        }
    }

    protected boolean canJoinNeighbor(BlockState neighbor) {
        return !neighbor.isAir() && !(neighbor.getBlock() instanceof FluidBlock);
    }

    protected final boolean connectsTo(BlockState state, BlockView world, BlockPos neighborPos, BlockState neighbor) {
        if (sameIdOnly(state)) return neighbor.isOf(this);
        if (neighbor.getBlock() instanceof ConnectedFenceBlock fence && fence.sameIdOnly(neighbor)) {
            return neighbor.isOf(this);
        }
        return BuildingNeighbors.acceptsFenceNeighbor(world, neighborPos, neighbor, this) && canJoinNeighbor(neighbor);
    }

    final boolean sameIdOnly(BlockState state) {
        return state.get(WALL_AXIS) == WallAxis.SAME_ID;
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(WALL_AXIS);
    }

    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        var current = state.get(WALL_AXIS);
        state = super.rotate(state, rotation);
        if ((current == WallAxis.X || current == WallAxis.Z)
                && (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90)) {
            state = state.with(WALL_AXIS, current == WallAxis.X ? WallAxis.Z : WallAxis.X);
        }
        return state;
    }

    protected boolean hasArm(BlockState state, Direction direction) {
        return switch (state.get(WALL_AXIS)) {
            case NONE, SAME_ID -> state.get(FACING_PROPERTIES.get(direction));
            case X -> direction.getAxis() == Direction.Axis.X;
            case Z -> direction.getAxis() == Direction.Axis.Z;
        };
    }

    boolean toggleWall(ServerWorld world, BlockPos pos, BlockState state, Direction.Axis axis) {
        WallAxis next = switch (state.get(WALL_AXIS)) {
            case NONE -> axis == Direction.Axis.X ? WallAxis.X : WallAxis.Z;
            case X, Z -> WallAxis.SAME_ID;
            case SAME_ID -> WallAxis.NONE;
        };
        return world.setBlockState(pos, withNeighbors(state.with(WALL_AXIS, next), world, pos), Block.NOTIFY_ALL);
    }

    private int activeShape(BlockState state) {
        return switch (state.get(WALL_AXIS)) {
            case NONE, SAME_ID -> getShapeIndex(state);
            case X -> getShapeIndex(getDefaultState().with(EAST, true).with(WEST, true));
            case Z -> getShapeIndex(getDefaultState().with(NORTH, true).with(SOUTH, true));
        };
    }

    protected BlockState withNeighbors(BlockState state, BlockView world, BlockPos pos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            state = state.with(FACING_PROPERTIES.get(direction), connectsTo(state, world, neighbor, world.getBlockState(neighbor)));
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
        return property == null ? state : state.with(property, connectsTo(state, world, neighborPos, neighbor));
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return placementShapes[activeShape(state)];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        boolean player = context instanceof EntityShapeContext entity && entity.getEntity() instanceof PlayerEntity;
        return (player ? playerShapes : placementShapes)[activeShape(state)];
    }

    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }
}
