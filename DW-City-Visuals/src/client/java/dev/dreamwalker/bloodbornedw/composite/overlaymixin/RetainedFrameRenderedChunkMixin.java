package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_6849", remap = false)
public abstract class RetainedFrameRenderedChunkMixin implements RetainedFrameRenderSnapshot {
    @Unique private NativeVisualData dw$frameSnapshot = NativeVisualData.EMPTY;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void dw$captureFrames(WorldChunk chunk, CallbackInfo ci) {
        dw$frameSnapshot = RetainedFrames.data(chunk);
    }
    @Override public NativeVisualData dw$retainedFrameData() { return dw$frameSnapshot; }
}


