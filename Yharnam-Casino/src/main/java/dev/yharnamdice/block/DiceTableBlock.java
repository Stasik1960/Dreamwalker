package dev.yharnamdice.block;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.game.TableSessions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class DiceTableBlock extends BlockWithEntity {
    public enum Part implements StringIdentifiable {
        WEST, EAST;
        @Override public String asString() { return this == WEST ? "west" : "east"; }
    }

    public static final EnumProperty<Part> PART = EnumProperty.of("part", Part.class);
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][2];

    static {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            for (Part part : Part.values()) {
                VoxelShape shape = rotate(0, 12.1, 0.62, 16, 14.9, 15.38, direction);
                double x = part == Part.WEST ? 3.31 : 12.69;
                for (double z : new double[]{2.67, 13.33}) {
                    shape = VoxelShapes.union(shape, rotate(x - 1.27, 0, z - 1.22, x + 1.27, 12.1, z + 1.22, direction));
                }
                SHAPES[direction.getHorizontal()][part.ordinal()] = shape;
            }
        }
    }

    public DiceTableBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST).with(PART, Part.WEST));
    }

    private static VoxelShape rotate(double x0, double y0, double z0, double x1, double y1, double z1, Direction direction) {
        return switch (direction) {
            case EAST -> createCuboidShape(x0, y0, z0, x1, y1, z1);
            case SOUTH -> createCuboidShape(16-z1, y0, x0, 16-z0, y1, x1);
            case WEST -> createCuboidShape(16-x1, y0, 16-z1, 16-x0, y1, 16-z0);
            default -> createCuboidShape(z0, y0, 16-x1, z1, y1, 16-x0);
        };
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING, PART);
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Direction direction = context.getHorizontalPlayerFacing().rotateYClockwise();
        BlockPos other = context.getBlockPos().offset(direction);
        World world = context.getWorld();
        if (!world.getWorldBorder().contains(other) || !world.getBlockState(other).canReplace(context)) return null;
        if (!world.getOtherEntities(null, new net.minecraft.util.math.Box(other).contract(.035)).isEmpty()) return null;
        return getDefaultState().with(Properties.HORIZONTAL_FACING, direction);
    }

    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) world.setBlockState(pos.offset(state.get(Properties.HORIZONTAL_FACING)), state.with(PART, Part.EAST), Block.NOTIFY_ALL);
    }

    public static BlockPos anchor(BlockPos pos, BlockState state) {
        return state.get(PART) == Part.WEST ? pos : pos.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite());
    }

    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == Part.WEST ? new DiceTableEntity(pos, state) : null;
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return SHAPES[state.get(Properties.HORIZONTAL_FACING).getHorizontal()][state.get(PART).ordinal()];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(Properties.HORIZONTAL_FACING, rotation.rotate(state.get(Properties.HORIZONTAL_FACING)));
    }

    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state, mirror.getRotation(state.get(Properties.HORIZONTAL_FACING)));
    }

    @Override public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world instanceof ServerWorld server && player instanceof ServerPlayerEntity user) {
            BlockPos anchor = anchor(pos, state);
            if (server.getBlockEntity(anchor) instanceof DiceTableEntity table) TableSessions.interact(user, table);
        }
        return ActionResult.success(world.isClient);
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.isOf(next.getBlock()) && world instanceof ServerWorld server) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            boolean west = state.get(PART) == Part.WEST;
            BlockPos anchor = anchor(pos, state);
            if (west) {
                TableSessions.destroy(server, anchor);
                if (server.getBlockEntity(pos) instanceof DiceTableEntity table && !table.disk().isEmpty()) {
                    Block.dropStack(world, pos, table.disk().copy());
                }
            }
            BlockPos companion = west ? pos.offset(facing) : anchor;
            BlockState other = world.getBlockState(companion);
            if (other.isOf(this) && other.get(Properties.HORIZONTAL_FACING) == facing && other.get(PART) != state.get(PART)) {
                world.breakBlock(companion, !west);
            }
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }
}
