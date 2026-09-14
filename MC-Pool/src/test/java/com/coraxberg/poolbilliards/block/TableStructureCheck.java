package com.coraxberg.poolbilliards.block;

import com.coraxberg.poolbilliards.block.BilliardsTableBlock.Layout;



import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.Set;

/** Pure production geometry checks: never starts a Minecraft client, server or world. */
public final class TableStructureCheck {
    public static void main(String[] args) {


        BlockPos origin = new BlockPos(37, 80, -21);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            Set<BlockPos> positions = new HashSet<>();
            for (int i = 0; i < Layout.FOOTPRINT.length; i++) {
                int[] local = Layout.FOOTPRINT[i];
                BlockPos position = origin.add(Layout.offset(local[0], local[1], facing));
                require(positions.add(position), "Duplicate segment for " + facing);
                if (i == 7) continue;
                require(Layout.mainPos(position, facing, i).equals(origin), "Wrong owner");
                require(Layout.matches(facing, i, facing, i), "Own part rejected");
                require(!Layout.matches(facing, i, facing, (i + 1) % 15), "Foreign index accepted");
                require(!Layout.matches(facing, i, facing.getOpposite(), i), "Foreign facing accepted");
                BlockPos neighbor = origin.add(6, 0, 6);
                require(!Layout.mainPos(position, facing, i).equals(neighbor), "Neighbor accepted");
            }
            require(positions.size() == 15, "Incomplete footprint");
        }
        System.out.println("Table structure checks passed: all 4 facings, 56 part owners and foreign-segment rejection.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

