package com.yharnam.casino;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/** A decorative, inert two-block-tall cabinet. It has no block entity or interaction. */
public final class BrokenSlotMachineBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape[][] COLLISION = new VoxelShape[4][2];
    private static final VoxelShape[][] OUTLINE = new VoxelShape[4][2];

    static {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            int index = direction.getHorizontal();
            VoxelShape base = box(direction, 1, 0, 1.5, 15, 3, 15.5);
            VoxelShape body = box(direction, 2, 3, 2.5, 14, 16, 12.9);
            VoxelShape tray = box(direction, 4, 3, 12.5, 12, 5.8, 15.5);
            VoxelShape deck = box(direction, 2.6, 11.9, 12.2, 13.4, 13.4, 15.1);
            VoxelShape shoulders = box(direction, 2, 0, 2.7, 14, 8.4, 13.3);
            VoxelShape gable = box(direction, 3.2, 8.4, 3.2, 12.8, 13.1, 12.8);
            VoxelShape peak = box(direction, 5, 13.1, 4.3, 11, 15.7, 11.5);
            COLLISION[index][0] = VoxelShapes.union(base, body, tray, deck);
            COLLISION[index][1] = VoxelShapes.union(shoulders, gable, peak);
            OUTLINE[index][0] = VoxelShapes.union(COLLISION[index][0],
                    box(direction, 13.3, 12.5, 6.5, 15.75, 16, 9.8));
            OUTLINE[index][1] = VoxelShapes.union(COLLISION[index][1],
                    box(direction, 13.3, 0, 6.5, 15.75, 5.6, 9.8));
        }
    }

    private final SlotStyle style;

    public BrokenSlotMachineBlock(SlotStyle style, Settings settings) {
        super(settings);
        this.style = style;
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.SOUTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    public SlotStyle style() {
        return style;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        World world = context.getWorld();
        if (pos.getY() >= world.getTopY() - 1 || !world.isInBuildLimit(pos.up())
                || !world.getBlockState(pos.up()).canReplace(context)) {
            return null;
        }
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.get(HALF);
        Direction otherSide = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == otherSide && (!neighbor.isOf(this) || neighbor.get(HALF) == half)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && state.get(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos base = pos.down();
            BlockState lower = world.getBlockState(base);
            if (lower.isOf(this) && lower.get(HALF) == DoubleBlockHalf.LOWER) {
                world.breakBlock(base, !player.isCreative() && player.canHarvest(lower), player);
            }
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state, mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE[state.get(FACING).getHorizontal()][state.get(HALF) == DoubleBlockHalf.LOWER ? 0 : 1];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISION[state.get(FACING).getHorizontal()][state.get(HALF) == DoubleBlockHalf.LOWER ? 0 : 1];
    }

    private static VoxelShape box(Direction facing, double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> createCuboidShape(x1,y1,z1,x2,y2,z2);
            case WEST -> createCuboidShape(16-z2,y1,x1,16-z1,y2,x2);
            case NORTH -> createCuboidShape(16-x2,y1,16-z2,16-x1,y2,16-z1);
            case EAST -> createCuboidShape(z1,y1,16-x2,z2,y2,16-x1);
            default -> throw new IllegalArgumentException("Expected a horizontal facing");
        };
    }
}
