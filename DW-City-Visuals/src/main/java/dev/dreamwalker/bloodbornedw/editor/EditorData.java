package dev.dreamwalker.bloodbornedw.editor;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;

/** Shared appearance lookup. -1 means use the automatic city rule; 0 is the
 * explicitly placed neutral material; 1..4 are palette entries. */
public final class EditorData {
    private EditorData() {}

    public static int style(BlockRenderView view, BlockPos pos) {
        if (view instanceof EditorView snapshot) return snapshot.dw$editorStyle(pos);
        if (view instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            int pending=CityEditorServer.pendingStyle(serverWorld,pos);if(pending>=-1)return pending;
        }
        if (view instanceof World world) {
            var chunk = world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
            if (chunk != null) return ((RetainedFrameData)(Object)NativeVisuals.data(chunk)).dw$editor().style(pos);
        }
        return -1;
    }
}
