package dev.dreamwalker.bloodbornedw.composite;
/** The table belongs to the chunk; no global coordinate registry retains it. */
public interface NativeVisualChunk {
    NativeVisualData nativeVisualData();
    void nativeVisualData(NativeVisualData data);
}
