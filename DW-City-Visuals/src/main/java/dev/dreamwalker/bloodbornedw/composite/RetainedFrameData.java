package dev.dreamwalker.bloodbornedw.composite;

import java.util.Map;

/** Immutable retained-frame attachment on a NativeVisualData snapshot. */
public interface RetainedFrameData {
    Map<Long, Integer> dw$frames();
    void dw$installFrames(Map<Long, Integer> frames);
    NativeVisualData dw$withFrames(Map<Long, Integer> frames);
}

