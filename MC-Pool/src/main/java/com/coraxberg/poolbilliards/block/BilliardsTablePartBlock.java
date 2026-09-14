package com.coraxberg.poolbilliards.block;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.net.PoolPackets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class BilliardsTablePartBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 14);
    private static final VoxelShape COLLISION = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

    public BilliardsTablePartBlock(Settings settings) {
        super(settings);
        this.setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return COLLISION;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        return COLLISION;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // Only the main block has loot. Remove it before the ordinary part break
        // so survival drops one table and creative drops none.
        if (!world.isClient) {
            BlockPos mainPos = getMainPos(pos, state);
            if (hasMatchingMain(world, mainPos, state)) {
                world.breakBlock(mainPos, !player.isCreative(), player);
            }
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            BlockPos mainPos = getMainPos(pos, state);
            if (hasMatchingMain(world, mainPos, state)) {
                // Covers explosions and replacement as well as player breaks.
                // The main is already air when its cleanup removes the other
                // parts, so their callbacks cannot recurse or produce loot.
                world.breakBlock(mainPos, true);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    private static boolean hasMatchingMain(World world, BlockPos mainPos, BlockState partState) {
        BlockState mainState = world.getBlockState(mainPos);
        return mainState.isOf(PoolBilliardsMod.BILLIARDS_TABLE)
                && mainState.get(BilliardsTableBlock.FACING) == partState.get(FACING);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos mainPos = getMainPos(pos, state);
        if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(mainPos) instanceof BilliardsTableBlockEntity be) {
            be.join(serverPlayer);
            PoolPackets.sendOpen(serverPlayer, mainPos);
            PoolPackets.sendState(serverPlayer, mainPos, be.getGame());
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    public static BlockPos getMainPos(BlockPos partPos, BlockState state) {
        Direction facing = state.get(FACING);
        int part = state.get(PART);
        return BilliardsTableBlock.Layout.mainPos(partPos, facing, part);
    }
}
