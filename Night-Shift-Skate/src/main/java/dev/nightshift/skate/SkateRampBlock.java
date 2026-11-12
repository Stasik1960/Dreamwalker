package dev.nightshift.skate;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** The FACING value points uphill. A fine collision staircase supports entities without a board. */
public final class SkateRampBlock extends HorizontalFacingBlock {
    private static final int STRIPS = 16;
    private static final double TOE_THICKNESS = 1.0 / 32.0;

    private final double rise;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public SkateRampBlock(double rise, Settings settings) {
        super(settings);
        if (rise <= 0.0 || rise > 1.0) {
            throw new IllegalArgumentException("Ramp rise must be within (0, 1]");
        }
        this.rise = rise;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
        for (Direction direction : Direction.Type.HORIZONTAL) {
            shapes.put(direction, buildShape(direction));
        }
    }

    public double rise() {
        return rise;
    }

    /** Returns the world-space Y of the continuous ramp surface at the supplied X/Z. */
    public double heightAt(BlockState state, BlockPos pos, double x, double z) {
        double localX = clamp(x - pos.getX());
        double localZ = clamp(z - pos.getZ());
        double progress = switch (state.get(FACING)) {
            case NORTH -> 1.0 - localZ;
            case SOUTH -> localZ;
            case EAST -> localX;
            case WEST -> 1.0 - localX;
            default -> 0.0;
        };
        return pos.getY() + Math.max(TOE_THICKNESS, rise * progress);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapes.get(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapes.get(state.get(FACING));
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    private VoxelShape buildShape(Direction uphill) {
        VoxelShape shape = VoxelShapes.empty();
        for (int strip = 0; strip < STRIPS; strip++) {
            double start = strip / (double) STRIPS;
            double end = (strip + 1) / (double) STRIPS;
            double height = strip == 0 ? TOE_THICKNESS : rise * strip / STRIPS;
            VoxelShape tread = switch (uphill) {
                case NORTH -> createCuboidShape(0, 0, 16 * (1.0 - end), 16, 16 * height, 16 * (1.0 - start));
                case SOUTH -> createCuboidShape(0, 0, 16 * start, 16, 16 * height, 16 * end);
                case EAST -> createCuboidShape(16 * start, 0, 0, 16 * end, 16 * height, 16);
                case WEST -> createCuboidShape(16 * (1.0 - end), 0, 0, 16 * (1.0 - start), 16 * height, 16);
                default -> VoxelShapes.empty();
            };
            shape = VoxelShapes.union(shape, tread);
        }
        return shape.simplify();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
