package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** A single installing item. Partial original source cells remain exact hidden
 * compatibility states in the converted world; creative pick always returns
 * the whole tree, without copying source privilege or source UUIDs. */
public final class SourceOriginalTreeBlock extends CompositeRootBlock implements NewStateStorage {
    public SourceOriginalTreeBlock(CompositeSpec spec){super(spec);}
    @Override public ItemStack art(BlockState state,NbtCompound payload){return super.art(state.with(VARIANT,0),new NbtCompound());}
}
