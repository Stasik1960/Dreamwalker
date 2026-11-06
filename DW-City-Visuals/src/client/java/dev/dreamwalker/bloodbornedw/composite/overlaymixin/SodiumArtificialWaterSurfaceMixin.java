package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.dreamwalker.bloodbornedw.composite.ArtificialWaterSurface;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer", remap = false)
abstract class SodiumArtificialWaterSurfaceMixin {
    private static final String REVERSE_TOP = "Lnet/minecraft/class_3610;method_15756(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)Z";

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = REVERSE_TOP))
    private boolean dw$recordReverseSurface(FluidState fluid, BlockView world, BlockPos above,
            Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) BlockPos pos,
            @Share("artificialReverseSurface") LocalBooleanRef reverseSurface) {
        boolean drawn = original.call(fluid, world, above);
        if (drawn && ArtificialWaterSurface.isArtificial(world, pos)) reverseSurface.set(true);
        return drawn;
    }

    @ModifyVariable(method = "render", at = @At("LOAD"), name = "sfDown",
            slice = @Slice(from = @At(value = "INVOKE", target = REVERSE_TOP)))
    private boolean dw$drawSingleUnderside(boolean bottomOccluded,
            @Share("artificialReverseSurface") LocalBooleanRef reverseSurface) {
        return bottomOccluded || reverseSurface.get();
    }
}
