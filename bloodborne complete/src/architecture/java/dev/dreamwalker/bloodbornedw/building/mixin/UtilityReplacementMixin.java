package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.CreativeOnlyUtilities;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
abstract class UtilityReplacementMixin {
    @Inject(method = "canReplace", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$keepUtilityDuringPlacement(ItemPlacementContext context,
                                                       CallbackInfoReturnable<Boolean> result) {
        if (CreativeOnlyUtilities.blocksReplacement((BlockState) (Object) this, context.getPlayer())) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "canBucketPlace", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$keepUtilityWhenEmptyingBucket(Fluid fluid, CallbackInfoReturnable<Boolean> result) {
        if (CreativeOnlyUtilities.includes((BlockState) (Object) this)) result.setReturnValue(false);
    }
}
