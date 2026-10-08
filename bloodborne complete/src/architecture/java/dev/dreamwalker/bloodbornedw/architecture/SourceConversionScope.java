package dev.dreamwalker.bloodbornedw.architecture;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Non-serialized privilege scoped to explicit initial source instances, never item NBT or a type. */
public final class SourceConversionScope {
    private static final ThreadLocal<Set<UUID>> INITIAL = ThreadLocal.withInitial(Set::of);
    private SourceConversionScope() {}
    public static boolean initialInstance(UUID uuid) { return uuid != null && INITIAL.get().contains(uuid); }
    public static <T> T initialInstances(Set<UUID> uuids, Supplier<T> operation) {
        Set<UUID> before = INITIAL.get();
        if (!before.isEmpty()) throw new IllegalStateException("Nested source conversion privilege is not supported");
        INITIAL.set(Set.copyOf(uuids));
        try { return operation.get(); }
        finally { INITIAL.set(before); }
    }
}
