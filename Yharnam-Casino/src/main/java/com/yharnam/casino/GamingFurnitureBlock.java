package com.yharnam.casino;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;

/** One anchor owns the game. The other occupied cells forward hits from each side of the furniture. */
public final class GamingFurnitureBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 8);
    public static final int ANCHOR = 1;
    private final boolean wheel;
    private final boolean blackjack;
    public GamingFurnitureBlock(Settings settings, boolean wheel, boolean blackjack) {
        super(settings);
        this.wheel = wheel;
        this.blackjack = blackjack;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH).with(PART, ANCHOR));
    }
    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, java.util.List<net.minecraft.text.Text> lines,
        net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, lines, context);
        lines.add(net.minecraft.text.Text.translatable(
            wheel ? "item.yharnam_casino.wheel_help" : "item.yharnam_casino.table_help"));
    }
    public boolean wheel() {
        return wheel;
    }
    public int parts() {
        return wheel ? 9 : 6;
    }
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        b.add(FACING, PART);
    }
    public BlockPos cell(BlockPos origin, Direction facing, int part) {
        BlockPos col = origin.offset(facing.rotateYCounterclockwise(), part % 3 - 1);
        return wheel ? col.up(part / 3) : col.offset(facing, part / 3);
    }
    public BlockPos anchor(BlockPos pos, BlockState state) {
        BlockPos delta = cell(BlockPos.ORIGIN, state.get(FACING), state.get(PART));
        return pos.subtract(delta);
    }
    @Override
    public BlockState getPlacementState(ItemPlacementContext c) {
        Direction f = c.getHorizontalPlayerFacing().getOpposite();
        for (int i = 0; i < parts(); i++) {
            BlockPos p = cell(c.getBlockPos(), f, i);
            if (!c.getWorld().isInBuildLimit(p) || !c.getWorld().getWorldBorder().contains(p)
                || !c.getWorld().getBlockState(p).canReplace(c)
                || c.getPlayer() != null && !c.getWorld().canPlayerModifyAt(c.getPlayer(), p))
                return null;
        }
        return getDefaultState().with(FACING, f);
    }
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack item) {
        if (!world.isClient && state.get(PART) == ANCHOR) {
            for (int i = 0; i < parts(); i++)
                if (i != ANCHOR)
                    world.setBlockState(cell(pos, state.get(FACING), i), state.with(PART, i), Block.NOTIFY_ALL);
        }
    }
    private boolean same(World world, BlockPos pos, BlockState state, int part) {
        BlockState other = world.getBlockState(pos);
        return other.isOf(this) && other.get(PART) == part && other.get(FACING) == state.get(FACING);
    }
    @Override
    public void onBreak(World world, BlockPos p, BlockState state, PlayerEntity player) {
        if (!world.isClient && state.get(PART) != ANCHOR) {
            BlockPos base = anchor(p, state);
            if (same(world, base, state, ANCHOR))
                world.breakBlock(base, !player.isCreative(), player);
        }
        super.onBreak(world, p, state, player);
    }
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos p, BlockState next, boolean moved) {
        if (world instanceof ServerWorld server && !next.isOf(this)) {
            BlockPos base = anchor(p, state);
            if (state.get(PART) == ANCHOR) {
                if (world.getBlockEntity(p) instanceof CardTableEntity table)
                    table.onRemoved(server);
                for (int i = 0; i < parts(); i++) {
                    BlockPos cell = cell(base, state.get(FACING), i);
                    if (i != ANCHOR && same(world, cell, state, i))
                        world.removeBlock(cell, false);
                }
            } else if (same(world, base, state, ANCHOR))
                world.breakBlock(base, true);
        }
        super.onStateReplaced(state, world, p, next, moved);
    }
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        if (state.get(PART) != ANCHOR)
            return null;
        return wheel ? new FortuneWheelEntity(pos, state) : new CardTableEntity(pos, state);
    }
    @Override
    public void scheduledTick(
        BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        if (world.getBlockEntity(pos) instanceof FortuneWheelEntity rotor)
            rotor.advanceClock(world);
    }
    // X points to the dealer's right and Z towards the players in the default (south) orientation.
    public static double seatX(boolean blackjack, int i) {
        if (blackjack)
            return .90 * Math.cos(Math.toRadians(201 + 23 * i));
        return switch (i) {
            case 0, 5 -> -1.02;
            case 1 -> -.50;
            case 2 -> .50;
            default -> 1.02;
        };
    }
    public static double seatZ(boolean blackjack, int i) {
        if (blackjack)
            return -.23 - .68 * Math.sin(Math.toRadians(201 + 23 * i));
        return switch (i) {
            case 0, 3 -> .24;
            case 1, 2 -> .53;
            default -> -.28;
        };
    }
    public static int target(boolean blackjack, double x, double z) {
        if (z < -.16 && Math.abs(x) < (blackjack ? .82 : .45) || blackjack && z < -.13 && x > .52)
            return -1;
        int nearest = -2;
        double distance = .32 * .32;
        for (int i = 0; i < (blackjack ? 7 : 6); i++) {
            double dx = x - seatX(blackjack, i), dz = z - seatZ(blackjack, i);
            if (dx * dx + dz * dz < distance) {
                nearest = i;
                distance = dx * dx + dz * dz;
            }
        }
        return nearest;
    }
    @Override
    public ActionResult onUse(
        BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND)
            return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity server) {
            BlockPos base = anchor(pos, state);
            BlockEntity be = world.getBlockEntity(base);
            if (be instanceof FortuneWheelEntity rotor)
                rotor.use(server);
            if (be instanceof CardTableEntity table) {
                Direction f = state.get(FACING), right = f.rotateYCounterclockwise();
                Vec3d d = hit.getPos().subtract(Vec3d.ofCenter(base));
                double x = d.x * right.getOffsetX() + d.z * right.getOffsetZ(),
                       z = d.x * f.getOffsetX() + d.z * f.getOffsetZ() - .5;
                table.interact(server, hand, target(blackjack, x, z));
            }
        }
        return ActionResult.success(world.isClient);
    }
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        int part = state.get(PART);
        if (part >= parts())
            return net.minecraft.util.shape.VoxelShapes.empty();
        int col = part % 3 - 1, row = part / 3;
        double width = wheel ? (row == 0 ? .60 : .86) : blackjack ? 1.3125 : 1.375;
        double xmin = Math.max(0, .5 - width - col), xmax = Math.min(1, .5 + width - col);
        double zmin = wheel ? .11 : Math.max(0, 1 - (blackjack ? .58 : .75) - row);
        double zmax = wheel ? .79 : Math.min(1, 1 + (blackjack ? .80 : .75) - row);
        double h = wheel ? Math.min(1, 2.385 - row) : .91;
        return switch (state.get(FACING)) {
            case SOUTH -> Block.createCuboidShape(xmin * 16, 0, zmin * 16, xmax * 16, h * 16, zmax * 16);
            case NORTH ->
                Block.createCuboidShape((1 - xmax) * 16, 0, (1 - zmax) * 16, (1 - xmin) * 16, h * 16, (1 - zmin) * 16);
            case WEST -> Block.createCuboidShape((1 - zmax) * 16, 0, xmin * 16, (1 - zmin) * 16, h * 16, xmax * 16);
            case EAST -> Block.createCuboidShape(zmin * 16, 0, (1 - xmax) * 16, zmax * 16, h * 16, (1 - xmin) * 16);
            default -> throw new IllegalStateException();
        };
    }
}
