package com.yharnam.casino;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public final class ChipCabinetBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = SlotMachineBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = SlotMachineBlock.HALF;

    ChipCabinetBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.SOUTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos upper = context.getBlockPos().up();
        World world = context.getWorld();
        return world.isInBuildLimit(upper) && world.getBlockState(upper).canReplace(context)
                ? getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()) : null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        world.setBlockState(pos.up(),state.with(HALF,DoubleBlockHalf.UPPER),Block.NOTIFY_ALL);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.get(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)
                && (!neighbor.isOf(this) || neighbor.get(HALF) == half)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state,direction,neighbor,world,pos,neighborPos);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new ChipCabinetEntity(pos,state) : null;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        BlockPos base = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
        if (!world.isClient && world.getBlockEntity(base) instanceof ChipCabinetEntity cabinet) {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId,inventory,viewer) -> new ChipCabinetMenu(syncId,inventory,cabinet),getName()));
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && state.get(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.down();
            if (world.getBlockState(lower).isOf(this)) {
                world.breakBlock(lower,!player.isCreative(),player);
            }
        }
        super.onBreak(world,pos,state,player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof ChipCabinetEntity cabinet) {
            ItemScatterer.spawn(world,pos,cabinet);
        }
        super.onStateReplaced(state,world,pos,newState,moved);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING,rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return rotate(state,mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        double top = state.get(HALF) == DoubleBlockHalf.LOWER ? 16 : 14.3;
        return switch (state.get(FACING)) {
            case SOUTH -> createCuboidShape(1,0,3.1,15,top,12.33);
            case WEST -> createCuboidShape(3.67,0,1,12.9,top,15);
            case NORTH -> createCuboidShape(1,0,3.67,15,top,12.9);
            case EAST -> createCuboidShape(3.1,0,1,12.33,top,15);
            default -> throw new IllegalStateException("Expected horizontal cabinet facing");
        };
    }
}
