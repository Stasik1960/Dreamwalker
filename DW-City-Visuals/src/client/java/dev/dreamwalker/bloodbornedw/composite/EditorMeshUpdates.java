package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/** Package-local bridge for editor invalidation of composite mesh work. */
public final class EditorMeshUpdates {
    private EditorMeshUpdates() {}

    public static void changed(CompositeBlockEntity owner) {
        CompositeMeshJobs.cancel(owner);
        changedAt(owner.getPos());
    }

    public static void changedAt(BlockPos pos) {
        CompositeWorldMesh.sectionChanged(ChunkSectionPos.from(pos).asLong());
    }

    public static long stamp(long section) {
        return CompositeWorldMesh.sectionStamp(section);
    }
}
