package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlabBlock.class)
abstract class DistinctSlabPlacementMixin {
    @Inject(method = "canReplace", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$fixedSlabsDoNotMerge(BlockState state, ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
        if (DistinctForms.fixed(state.getBlock())) cir.setReturnValue(false);
    }
}
