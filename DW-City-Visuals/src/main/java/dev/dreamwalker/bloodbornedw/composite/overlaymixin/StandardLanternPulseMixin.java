package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LanternMotion.class, remap = false)
public abstract class StandardLanternPulseMixin {
    @Inject(method = "pulse", at = @At("HEAD"), cancellable = true)
    private static void dw$standingDoesNotSway(ServerWorld world, BlockPos root, CallbackInfoReturnable<Boolean> cir) {
        if (world.getBlockEntity(root) instanceof CompositeBlockEntity entity && entity.resident() != null && entity.resident().registryId().equals("bloodborne_dw:source_standard_lantern")) {
            var state = world.getBlockState(root);
            if (state.contains(CompositeRootBlock.VARIANT) && state.get(CompositeRootBlock.VARIANT) == 1) cir.setReturnValue(false);
        }
    }
}
