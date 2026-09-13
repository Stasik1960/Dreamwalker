package com.coraxberg.poolbilliards.block;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.net.PoolPackets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BilliardsTableBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // Визуальная зона примерно 3x2 блока. Основной блок хранит BlockEntity,
    // а невидимые части вокруг него дают нормальную коллизию по всему столу.
    public static final int[][] FOOTPRINT = new int[][]{{-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 13, 16);

    public BilliardsTableBlock(Settings settings) {
        super(settings);
        this.setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BilliardsTableBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world.isClient) return;
        Direction facing = state.get(FACING);
        for (int i = 0; i < FOOTPRINT.length; i++) {
            int lx = FOOTPRINT[i][0];
            int lz = FOOTPRINT[i][1];
            if (lx == 0 && lz == 0) continue;
            BlockPos partPos = pos.add(rotateOffset(lx, lz, facing));
            if (world.getBlockState(partPos).isAir()) {
                world.setBlockState(partPos, PoolBilliardsMod.BILLIARDS_TABLE_PART.getDefaultState()
                        .with(BilliardsTablePartBlock.FACING, facing)
                        .with(BilliardsTablePartBlock.PART, i), Block.NOTIFY_ALL);
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient) {
            Direction facing = state.get(FACING);
            for (int i = 0; i < FOOTPRINT.length; i++) {
                int lx = FOOTPRINT[i][0];
                int lz = FOOTPRINT[i][1];
                if (lx == 0 && lz == 0) continue;
                BlockPos partPos = pos.add(rotateOffset(lx, lz, facing));
                BlockState partState = world.getBlockState(partPos);
                if (partState.isOf(PoolBilliardsMod.BILLIARDS_TABLE_PART)) {
                    world.setBlockState(partPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public static BlockPos rotateOffset(int localX, int localZ, Direction facing) {
        return switch (facing) {
            case SOUTH -> new BlockPos(-localX, 0, -localZ);
            case EAST -> new BlockPos(-localZ, 0, localX);
            case WEST -> new BlockPos(localZ, 0, -localX);
            default -> new BlockPos(localX, 0, localZ);
        };
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof BilliardsTableBlockEntity be) {
            be.join(serverPlayer);
            PoolPackets.sendOpen(serverPlayer, pos);
            PoolPackets.sendState(serverPlayer, pos, be.getGame());
        }
        return ActionResult.CONSUME;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type == PoolBilliardsMod.BILLIARDS_TABLE_ENTITY) {
            return (tickWorld, tickPos, tickState, blockEntity) -> BilliardsTableBlockEntity.tick(tickWorld, tickPos, tickState, (BilliardsTableBlockEntity) blockEntity);
        }
        return null;
    }
}
