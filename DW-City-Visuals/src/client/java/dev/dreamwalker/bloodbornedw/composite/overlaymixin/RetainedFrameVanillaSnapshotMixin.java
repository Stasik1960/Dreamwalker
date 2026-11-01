package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import java.util.Map;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkRendererRegion.class)
public abstract class RetainedFrameVanillaSnapshotMixin implements RetainedFrameVanillaSnapshot {
    @Unique private Object[][] dw$chunks;
    @Unique private int dw$offsetX, dw$offsetZ;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dw$capture(CallbackInfo ci) {
        try {
            dw$chunks = (Object[][])RetainedFrameVanillaRegionFields.CHUNKS.get(this);
            dw$offsetX = RetainedFrameVanillaRegionFields.X.getInt(this); dw$offsetZ = RetainedFrameVanillaRegionFields.Z.getInt(this);
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot read region snapshot", failure); }
    }
    @Override public Map<Integer, Integer> dw$frameSection(BlockPos origin) {
        int x = (origin.getX() >> 4) - dw$offsetX, z = (origin.getZ() >> 4) - dw$offsetZ;
        if (x < 0 || z < 0 || x >= dw$chunks.length || z >= dw$chunks[x].length) return Map.of();
        NativeVisualData data = ((RetainedFrameRenderSnapshot)dw$chunks[x][z]).dw$retainedFrameData();
        return ((RetainedFrameData)(Object)data).dw$frames().isEmpty() ? Map.of() : RetainedFrames.section(data, origin.getY() >> 4);
    }
}


