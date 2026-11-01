package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.BinaryQuadAlpha;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureGeometry;
import java.util.function.Function;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla and FRAPI share this exact source-quad dispatcher. */
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient$QuadLayerConsumer", remap = false)
public abstract class StaticQuadLayerMixin {
    @Shadow @Final private Function<RenderLayer, VertexConsumer> factory;
    @Shadow @Final private boolean translucent;
    @Shadow private VertexConsumer cutout;
    @Inject(method = "method_22920", at = @At("HEAD"), cancellable = true, remap = false)
    private void dw$binaryLayer(MatrixStack.Entry matrix, BakedQuad quad, float[] brightness,
            float red, float green, float blue, int[] lights, int overlay, boolean useColor, CallbackInfo ci) {
        if (!translucent || StaticArchitectureGeometry.opaqueQuad(quad) || !BinaryQuadAlpha.binary(quad)) return;
        if (cutout == null) cutout = factory.apply(RenderLayer.getCutout());
        cutout.quad(matrix, quad, brightness, red, green, blue, lights, overlay, useColor);
        ci.cancel();
    }
}
