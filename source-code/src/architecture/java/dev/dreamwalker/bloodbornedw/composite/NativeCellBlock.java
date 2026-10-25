package dev.dreamwalker.bloodbornedw.composite;
import net.minecraft.block.BlockState;
/** Shared physical state contract; whole-object identities remain separate. */
public interface NativeCellBlock { NativeGridCatalog.Cell cell(BlockState state); }
