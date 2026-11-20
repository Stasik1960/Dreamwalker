package com.yharnam.casino;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Shapes in model pixels, rotated from the south-facing cabinet. */
final class SlotCabinetGeometry {
    private static final VoxelShape[][] COLLISION = new VoxelShape[4][2];
    private static final VoxelShape[][] OUTLINE = new VoxelShape[4][2];
    private static final VoxelShape[] CREST_TIP = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            int index = direction.getHorizontal();
            VoxelShape plinth = box(direction, 0, 0, 2.4, 16, 4, 12.5);
            VoxelShape lowerBody = box(direction, .8, 4, 2.2, 15.2, 16, 12.8);
            VoxelShape tray = box(direction, 3.6, 8.2, 11.5, 12.4, 9.9, 15.6);
            COLLISION[index][0] = VoxelShapes.union(plinth, lowerBody, tray);
            OUTLINE[index][0] = COLLISION[index][0];

            // The upper half is relative to the block one metre above the floor.
            VoxelShape upperBody = box(direction, 1.2, 0, 2.3, 14.8, 12.8, 13.3);
            VoxelShape deck = box(direction, 1.9, 3.6, 11.8, 14.1, 5.5, 15.6);
            VoxelShape gable = box(direction, 1.8, 12.8, 2.9, 14.2, 16.7, 13.2);
            VoxelShape crest = box(direction, 4, 16.7, 3.5, 12, 20.05, 13);
            VoxelShape lever = box(direction, 14.5, 5.5, 6.5, 16, 13.4, 9.1);
            COLLISION[index][1] = VoxelShapes.union(upperBody, deck, gable, crest);
            OUTLINE[index][1] = VoxelShapes.union(COLLISION[index][1], lever);
            CREST_TIP[index] = VoxelShapes.union(gable, crest).offset(0, -1, 0);
        }
    }

    private SlotCabinetGeometry() {}

    static VoxelShape collision(BlockState state) {
        return COLLISION[state.get(SlotMachineBlock.FACING).getHorizontal()][half(state)];
    }

    static VoxelShape outline(BlockState state) {
        return OUTLINE[state.get(SlotMachineBlock.FACING).getHorizontal()][half(state)];
    }

    static boolean hasRoom(ItemPlacementContext context) {
        World world = context.getWorld();
        BlockPos base = context.getBlockPos();
        BlockPos tip = base.up(2);
        if (!world.isInBuildLimit(tip) || !world.getBlockState(base.up()).canReplace(context)) return false;
        int facing = context.getHorizontalPlayerFacing().getOpposite().getHorizontal();
        VoxelShape overhead = world.getBlockState(tip).getOutlineShape(world, tip);
        return !VoxelShapes.matchesAnywhere(CREST_TIP[facing], overhead, BooleanBiFunction.AND);
    }

    private static int half(BlockState state) {
        return state.get(SlotMachineBlock.HALF) == DoubleBlockHalf.LOWER ? 0 : 1;
    }

    private static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
            case WEST -> Block.createCuboidShape(16-z2, y1, x1, 16-z1, y2, x2);
            case NORTH -> Block.createCuboidShape(16-x2, y1, 16-z2, 16-x1, y2, 16-z1);
            case EAST -> Block.createCuboidShape(z1, y1, 16-x2, z2, y2, 16-x1);
            default -> throw new IllegalArgumentException("Expected a horizontal facing");
        };
    }
}
