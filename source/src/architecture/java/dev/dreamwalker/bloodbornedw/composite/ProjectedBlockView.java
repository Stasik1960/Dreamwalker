package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A model-local view of an existing world. Publication lookups use the real
 * world and its projected cell; the common runtime has no client dependency. */
public interface ProjectedBlockView extends BlockView {
    World backingWorld();
    BlockPos worldPosition(BlockPos localPosition);
}
