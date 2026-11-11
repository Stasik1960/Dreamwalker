package dev.dreamwalker.brickreuse;
import java.util.Map;
import net.minecraft.class_1087;
public final class BrickReuseSnapshot {
    public final java.lang.ref.WeakReference<Map<?,?>> cache;
    public final Object stateFactory;
    public final java.util.List<?> parts;
    public final int partCount;
    public final Object[] keys, children;
    public final Object rotation;
    public final boolean uvLock;
    public final class_1087 model;
    public BrickReuseSnapshot(Map<?,?> cache, Object stateFactory, java.util.List<?> parts, Object[] keys, Object[] children, Object rotation, boolean uvLock, class_1087 model) {
        this.cache = new java.lang.ref.WeakReference<>(cache); this.stateFactory = stateFactory; this.parts = parts; this.partCount = parts.size(); this.keys = keys; this.children = children; this.rotation = rotation; this.uvLock = uvLock; this.model = model;
    }
}



