package dev.dreamwalker.bloodbornedw.editor;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

/** Thread-bound immutable Sodium task snapshot; never queries a live world. */
public final class EditorRenderScope {
    private static final ThreadLocal<EditorView> CURRENT = new ThreadLocal<>();
    private static final EditorView EMPTY = new EditorView() {
        @Override public boolean dw$hasEditorCells() { return false; }
        @Override public int dw$editorStyle(BlockPos pos) { return -1; }
    };
    private EditorRenderScope() {}
    public static void begin(EditorView view) { CURRENT.set(view != null && !view.dw$hasEditorCells() ? EMPTY : view); }
    public static void end() { CURRENT.remove(); }
    public static boolean canResolveStyle(BlockRenderView view) {
        return view instanceof EditorView || view instanceof net.minecraft.world.World || CURRENT.get() != null;
    }

    public static int style(BlockRenderView view, BlockPos pos) {
        if (view instanceof EditorView snapshot) return snapshot.dw$hasEditorCells() ? snapshot.dw$editorStyle(pos) : -1;
        EditorView snapshot = CURRENT.get();
        if (snapshot == EMPTY) return -1;
        return snapshot == null ? EditorData.style(view, pos) : snapshot.dw$editorStyle(pos);
    }
}
