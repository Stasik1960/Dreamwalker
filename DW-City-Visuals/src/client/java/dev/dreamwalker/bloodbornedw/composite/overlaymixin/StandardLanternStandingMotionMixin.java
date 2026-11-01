package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Do not reuse a former hanging lamp's transient motion at a standing lamp's position. */
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.LanternMotionClient", remap = false)
public abstract class StandardLanternStandingMotionMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$standing(CompositeBlockEntity entity, float delta, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int overlay, CallbackInfoReturnable<Boolean> cir) {
        var state = entity.getCachedState();
        if (Registries.BLOCK.getId(state.getBlock()).toString().equals("bloodborne_dw:source_standard_lantern")
                && state.contains(CompositeRootBlock.VARIANT) && state.get(CompositeRootBlock.VARIANT) == 1) cir.setReturnValue(false);
    }
}
