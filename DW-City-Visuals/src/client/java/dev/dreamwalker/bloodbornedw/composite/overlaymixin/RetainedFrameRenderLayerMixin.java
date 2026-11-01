package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SourceFallbackCarrierClientV1271.class, remap = false)
public abstract class RetainedFrameRenderLayerMixin {
    @Inject(method = "artworkLayer", at = @At("HEAD"), cancellable = true)
    private static void dw$cutout(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {
        if (RetainedFrameState.matches(state)) { cir.setReturnValue(RenderLayer.getCutout()); return; }
        if (!(state.getBlock() instanceof SourceFallbackCarrierV1271)
                || !SourceFallbackCarrierV1271.MULTIPART_ID.equals(Registries.BLOCK.getId(state.getBlock()))) return;
        int index = state.get(SourceFallbackCarrierV1271.SOURCE)
                + 128 * state.get(SourceFallbackCarrierV1271.SOURCE_BANK);
        if (index == 65 || index == 70 || index == 72 || index == 73 || index == 74)
            cir.setReturnValue(RenderLayer.getCutout());
    }
}
