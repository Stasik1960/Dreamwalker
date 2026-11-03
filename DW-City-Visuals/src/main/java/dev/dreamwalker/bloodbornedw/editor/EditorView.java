package dev.dreamwalker.bloodbornedw.editor;

import net.minecraft.util.math.BlockPos;

/** Optional immutable render snapshot for per-position city appearance. */
public interface EditorView {
    /** Unknown implementations must preserve lookup; immutable snapshots may prove emptiness. */
    default boolean dw$hasEditorCells() { return true; }
    int dw$editorStyle(BlockPos pos);
}
