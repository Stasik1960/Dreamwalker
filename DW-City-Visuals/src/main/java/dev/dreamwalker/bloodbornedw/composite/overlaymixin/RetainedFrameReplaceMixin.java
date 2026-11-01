package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SourceFallbackCarrierV1271.class, remap = false)
public abstract class RetainedFrameReplaceMixin extends Block {
    protected RetainedFrameReplaceMixin(Settings settings) { super(settings); }
    @Override public boolean canReplace(BlockState state, ItemPlacementContext context) {
        return RetainedFrameState.matches(state) && context.getStack().getItem() instanceof BlockItem || super.canReplace(state, context);
    }
}
