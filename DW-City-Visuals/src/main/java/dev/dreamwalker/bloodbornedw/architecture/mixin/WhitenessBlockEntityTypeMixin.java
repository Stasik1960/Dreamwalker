package dev.dreamwalker.bloodbornedw.architecture.mixin;

import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Extends the composite cell type once during block registration. */
@Mixin(BlockEntityType.class)
public interface WhitenessBlockEntityTypeMixin {
    @Accessor("blocks")
    Set<Block> dw$whitenessGetSupportedBlocks();

    @Mutable
    @Accessor("blocks")
    void dw$whitenessSetSupportedBlocks(Set<Block> blocks);
}
