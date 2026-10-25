package dev.dreamwalker.bloodbornedw.composite;
import net.minecraft.util.math.BlockPos;
/** Immutable metadata paired with a chunk builder's block-state snapshot. */
public interface NativeVisualView { int nativeVisualIndex(BlockPos pos); }
