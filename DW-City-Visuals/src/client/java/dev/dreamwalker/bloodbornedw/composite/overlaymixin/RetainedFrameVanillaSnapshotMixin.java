package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.editor.EditorView;
import java.util.Map;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkRendererRegion.class)
public abstract class RetainedFrameVanillaSnapshotMixin implements RetainedFrameVanillaSnapshot, EditorView {
    @Unique private Object[][] dw$chunks;
    @Unique private int dw$offsetX, dw$offsetZ;
    @Unique private boolean dw$hasEditorCells;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dw$capture(CallbackInfo ci) {
        try {
            dw$chunks = (Object[][])RetainedFrameVanillaRegionFields.CHUNKS.get(this);
            dw$offsetX = RetainedFrameVanillaRegionFields.X.getInt(this); dw$offsetZ = RetainedFrameVanillaRegionFields.Z.getInt(this);
            for (Object[] column : dw$chunks) for (Object chunk : column) {
                if (chunk instanceof RetainedFrameRenderSnapshot snapshot && ((RetainedFrameData)(Object)snapshot.dw$retainedFrameData()).dw$editor().size() != 0) dw$hasEditorCells = true;
            }
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot read region snapshot", failure); }
    }
    @Override public Map<Integer, Integer> dw$frameSection(BlockPos origin) {
        int x = (origin.getX() >> 4) - dw$offsetX, z = (origin.getZ() >> 4) - dw$offsetZ;
        if (x < 0 || z < 0 || x >= dw$chunks.length || z >= dw$chunks[x].length) return Map.of();
        NativeVisualData data = ((RetainedFrameRenderSnapshot)dw$chunks[x][z]).dw$retainedFrameData();
        return ((RetainedFrameData)(Object)data).dw$frames().isEmpty() ? Map.of() : RetainedFrames.section(data, origin.getY() >> 4);
    }
    @Override public boolean dw$hasEditorCells() { return dw$hasEditorCells; }
    @Override public int dw$editorStyle(BlockPos pos) {
        if (!dw$hasEditorCells) return -1;
        int x = (pos.getX() >> 4) - dw$offsetX, z = (pos.getZ() >> 4) - dw$offsetZ;
        if (x < 0 || z < 0 || x >= dw$chunks.length || z >= dw$chunks[x].length) return -1;
        Object chunk = dw$chunks[x][z];
        if (!(chunk instanceof RetainedFrameRenderSnapshot snapshot)) return -1;
        NativeVisualData data = snapshot.dw$retainedFrameData();
        return ((RetainedFrameData)(Object)data).dw$editor().style(pos);
    }
}


