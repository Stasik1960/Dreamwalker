package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;
import net.minecraft.util.math.BlockPos;

public interface RetainedFrameVanillaSnapshot {
    Map<Integer, Integer> dw$frameSection(BlockPos origin);
}
