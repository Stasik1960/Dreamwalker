package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.util.math.ChunkSectionPos;

/** Package-local bridge for editor invalidation of composite mesh work. */
public final class EditorMeshUpdates {
    private EditorMeshUpdates() {}

    public static void changed(CompositeBlockEntity owner) {
        CompositeMeshJobs.cancel(owner);
        CompositeWorldMesh.sectionChanged(ChunkSectionPos.from(owner.getPos()).asLong());
    }

    public static long stamp(long section) {
        return CompositeWorldMesh.sectionStamp(section);
    }
}
