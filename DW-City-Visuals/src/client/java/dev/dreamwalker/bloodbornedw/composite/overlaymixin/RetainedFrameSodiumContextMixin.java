package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.Map;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/** Reuses the native immutable snapshot after the context is fully constructed. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext", priority = 900, remap = false)
public abstract class RetainedFrameSodiumContextMixin implements RetainedFrameSodiumSnapshot {
    @Unique private Map<Integer, Integer> dw$frames = Map.of();
    @Unique private ChunkSectionPos dw$origin;
    @Unique private volatile NativeVisualSodiumSnapshot dw$editorSnapshot;
    @Unique private boolean dw$hasEditorCells;
    // The meshing-task constructor calls frameSection before publishing the task.
    // Constructor RETURN callbacks from different mixins have no safe dependency
    // order: copying there can retain the native mixin's initial EMPTY forever.
    @Unique private void dw$ensureSnapshot() {
        if (dw$editorSnapshot != null) return;
        synchronized (this) {
            if (dw$editorSnapshot != null) return;
            dw$captureSnapshot();
        }
    }
    @Unique private void dw$captureSnapshot() {
        NativeVisualSodiumSnapshot nativeSnapshot = ((NativeVisualSodiumSnapshot.Context) this).nativeVisualSnapshot();
        RetainedFrameSodiumDataAccessor access = (RetainedFrameSodiumDataAccessor)(Object)nativeSnapshot;
        for (NativeVisualData section : access.dw$frameSections()) {
            if (section != null && ((RetainedFrameData)(Object)section).dw$editor().size() != 0) { dw$hasEditorCells = true; break; }
        }
        NativeVisualData data = access.dw$frameSections()[13];
        if (data != null && !((RetainedFrameData)(Object)data).dw$frames().isEmpty()) {
            int sectionY = access.dw$frameY() + 1;
            dw$frames = RetainedFrames.section(data, sectionY);
            if (!dw$frames.isEmpty()) dw$origin = ChunkSectionPos.from(access.dw$frameX() + 1, sectionY, access.dw$frameZ() + 1);
        }
        // Publish all derived fields together; workers retain these exact references.
        dw$editorSnapshot = nativeSnapshot;
    }
    @Override public Map<Integer, Integer> dw$frameSection() { dw$ensureSnapshot(); return dw$frames; }
    @Override public ChunkSectionPos dw$frameOrigin() { dw$ensureSnapshot(); return dw$origin; }
    @Override public boolean dw$hasEditorCells() { dw$ensureSnapshot(); return dw$hasEditorCells; }
    @Override public int dw$editorStyle(net.minecraft.util.math.BlockPos pos) {
        dw$ensureSnapshot();
        if (!dw$hasEditorCells) return -1;
        RetainedFrameSodiumDataAccessor access = (RetainedFrameSodiumDataAccessor)(Object)dw$editorSnapshot;
        int x=(pos.getX()>>4)-access.dw$frameX(), y=(pos.getY()>>4)-access.dw$frameY(), z=(pos.getZ()>>4)-access.dw$frameZ();
        if(x<0||x>2||y<0||y>2||z<0||z>2)return -1;
        NativeVisualData data=access.dw$frameSections()[x+z*3+y*9];
        return data==null?-1:((RetainedFrameData)(Object)data).dw$editor().style(pos);
    }
}


