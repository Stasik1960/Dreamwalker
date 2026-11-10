package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CoplanarQuads;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh$Recorder", remap = false)
public abstract class CompositeCoplanarMixin {
    @Unique private final CoplanarQuads dw$coplanar = new CoplanarQuads();

    @Inject(method = "method_22920", at = @At("HEAD"), cancellable = true, remap = false)
    private void dw$keepFirstOpaqueFace(MatrixStack.Entry matrix, BakedQuad quad, float[] brightness,
            float red, float green, float blue, int[] lights, int overlay, boolean useColor, CallbackInfo ci) {
        if (dw$coplanar.skip(matrix.getPositionMatrix(), quad)) ci.cancel();
    }
}
