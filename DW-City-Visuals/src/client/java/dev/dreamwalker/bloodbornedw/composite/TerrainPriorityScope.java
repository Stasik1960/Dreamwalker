package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.PriorityBlockContextAccess;
import java.util.concurrent.ConcurrentHashMap;

/** Worker-local context for the Sodium encoder, also used by Indium. */
public final class TerrainPriorityScope {
    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::new);
    private static final ConcurrentHashMap<Object, Boolean> SOLID = new ConcurrentHashMap<>();

    private static final class State {
        float offset;
        Object lastMaterial;
        boolean lastSolid;
        final float[] vertices = new float[12];
    }

    private TerrainPriorityScope() {}

    public static float begin(Object raw) {
        State current = CURRENT.get();
        float previous = current.offset;
        current.offset = 0;
        if (raw instanceof PriorityBlockContextAccess context) {
            var state = context.dw$priorityState();
            if (state != null) {
                current.offset = SurfacePriority.offset(context.dw$priorityPos(), state);
            }
        }
        return previous;
    }

    public static void end(float previous) {
        CURRENT.get().offset = previous;
    }

    public static float offset(Object material) {
        State current = CURRENT.get();
        if (current.offset == 0) return 0;
        if (current.lastMaterial != material) {
            current.lastMaterial = material;
            current.lastSolid = SOLID.computeIfAbsent(material, TerrainPriorityScope::solid);
        }
        return current.lastSolid ? current.offset : 0;
    }

    public static float[] vertexScratch() {
        return CURRENT.get().vertices;
    }

    private static boolean solid(Object material) {
        try {
            Object pass = material.getClass().getField("pass").get(material);
            return !(boolean) pass.getClass().getMethod("isReverseOrder").invoke(pass);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }
}