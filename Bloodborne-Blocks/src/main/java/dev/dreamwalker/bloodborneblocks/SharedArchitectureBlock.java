package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Root block that can carry guest helper bindings without allocating them by default. */
final class SharedArchitectureBlock extends ArchitectureBlock implements BlockEntityProvider {
 SharedArchitectureBlock(BloodborneBlocks.Definition definition){super(definition);}
 @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return null;}
}
