package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.dreamwalker.bloodbornedw.composite.ArtificialWaterSurface;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(FluidRenderer.class)
abstract class VanillaArtificialWaterSurfaceMixin {
    private static final String REVERSE_TOP = "Lnet/minecraft/fluid/FluidState;canFlowTo(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Z";

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = REVERSE_TOP))
    private boolean dw$recordReverseSurface(FluidState fluid, BlockView world, BlockPos above,
            Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos,
            @Share("artificialReverseSurface") LocalBooleanRef reverseSurface) {
        boolean drawn = original.call(fluid, world, above);
        if (drawn && ArtificialWaterSurface.isArtificial(world, pos)) reverseSurface.set(true);
        return drawn;
    }

    // Slot 25 controls the bottom quad and is also read earlier for the side skirt height.
    @ModifyVariable(method = "render", at = @At("LOAD"), index = 25,
            slice = @Slice(from = @At(value = "INVOKE", target = REVERSE_TOP)))
    private boolean dw$drawSingleUnderside(boolean drawBottom,
            @Share("artificialReverseSurface") LocalBooleanRef reverseSurface) {
        return drawBottom && !reverseSurface.get();
    }
}
