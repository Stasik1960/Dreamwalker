package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep an otherwise empty section in the ordinary vanilla build pipeline. */
@Mixin(ChunkRendererRegionBuilder.class)
public abstract class RetainedFrameVanillaRegionGateMixin {
    @Redirect(method = "method_39970", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2818;method_12228(II)Z", remap = false), remap = false)
    private static boolean dw$frameSection(WorldChunk chunk, int minY, int maxY) {
        if (!chunk.areSectionsEmptyBetween(minY, maxY)) return false;
        NativeVisualData data = RetainedFrames.data(chunk);
        for (int y = minY >> 4; y <= maxY >> 4; ++y)
            if (RetainedFrames.hasSection(data, y)) return false;
        return true;
    }
}


