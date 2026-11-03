package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;
import dev.dreamwalker.bloodbornedw.editor.EditorCells;

/** Immutable retained-frame attachment on a NativeVisualData snapshot. */
public interface RetainedFrameData {
    Map<Long, Integer> dw$frames();
    void dw$installFrames(Map<Long, Integer> frames);
    NativeVisualData dw$withFrames(Map<Long, Integer> frames);
    EditorCells dw$editor();
    void dw$installEditor(EditorCells cells);
    NativeVisualData dw$withEditor(EditorCells cells);
}

