package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.BinaryQuadAlpha;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StaticArchitectureGeometry.class, remap = false)
public abstract class QuadAlphaReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private static void dw$clearAlpha(CallbackInfo ci) { BinaryQuadAlpha.clear(); dev.dreamwalker.bloodbornedw.composite.ShutterGlassLayers.clear(); }
}
