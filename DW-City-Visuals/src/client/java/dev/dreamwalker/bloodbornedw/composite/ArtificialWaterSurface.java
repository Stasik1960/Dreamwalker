package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public final class ArtificialWaterSurface {
    private ArtificialWaterSurface() {}

    public static boolean isArtificial(BlockView world, BlockPos pos) {
        return world.getBlockState(pos).isOf(DwUtilityBlocks.WATER)
                || (world instanceof NativeVisualView view && view.nativeForcedWater(pos));
    }
}
