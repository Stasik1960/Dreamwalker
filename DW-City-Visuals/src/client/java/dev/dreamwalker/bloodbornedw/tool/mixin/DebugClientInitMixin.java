package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.tool.DebugAdjustmentsClient;
import dev.dreamwalker.bloodbornedw.visual.CityVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CityVisualClient.class, remap = false)
abstract class DebugClientInitMixin {
    @Inject(method = "onInitializeClient", at = @At("TAIL"))
    private void dw$debugNetworking(CallbackInfo ci) {
        DebugAdjustmentsClient.initialize();
    }
}
