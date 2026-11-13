package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.CreativeOnlyUtilities;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowableFluid.class)
abstract class UtilityFluidFillMixin {
    @Inject(method = "canFill", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$keepUtilityDuringFluidFlow(BlockView world, BlockPos pos, BlockState state, Fluid fluid,
                                                       CallbackInfoReturnable<Boolean> result) {
        if (CreativeOnlyUtilities.includes(state)) result.setReturnValue(false);
    }
}
