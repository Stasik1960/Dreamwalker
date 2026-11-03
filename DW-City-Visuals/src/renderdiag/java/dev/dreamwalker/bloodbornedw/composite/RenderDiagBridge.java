package dev.dreamwalker.bloodbornedw.composite;
public final class RenderDiagBridge {
    private RenderDiagBridge() {}
    public static long stamp(long section) { return CompositeWorldMesh.sectionStamp(section); }
}
