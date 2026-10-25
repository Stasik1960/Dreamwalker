package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderLayers.class)
public abstract class NativeGridRenderLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void dw$nativeGridLayer(BlockState state, CallbackInfoReturnable<RenderLayer> result) {
        if (state.getBlock() instanceof NativeCellBlock) result.setReturnValue(NativeGridClient.layer(state));
    }
}
