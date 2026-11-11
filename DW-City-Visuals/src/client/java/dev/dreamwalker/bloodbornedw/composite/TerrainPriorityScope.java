package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.PriorityBlockContextAccess;
import java.util.concurrent.ConcurrentHashMap;

/** Worker-local context for the Sodium encoder, also used by Indium. */
public final class TerrainPriorityScope {
    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::new);
    private static final ConcurrentHashMap<Object, Boolean> SOLID = new ConcurrentHashMap<>();

    private static final class State {
        int rank;
        Object lastMaterial;
        boolean lastSolid;
    }

    private TerrainPriorityScope() {}

    public static int begin(Object raw) {
        State current = CURRENT.get();
        int previous = current.rank;
        current.rank = 0;
        if (raw instanceof PriorityBlockContextAccess context) {
            var state = context.dw$priorityState();
            if (state != null) {
                current.rank = SurfacePriority.rank(context.dw$priorityPos(), state);
            }
        }
        return previous;
    }

    public static void end(int previous) {
        CURRENT.get().rank = previous;
    }

    public static int rank(Object material) {
        State current = CURRENT.get();
        if (current.rank == 0) return 0;
        if (current.lastMaterial != material) {
            current.lastMaterial = material;
            current.lastSolid = SOLID.computeIfAbsent(material, TerrainPriorityScope::solid);
        }
        return current.lastSolid ? current.rank : 0;
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