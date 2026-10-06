package dev.dreamwalker.bloodbornedw.block;

/** Constructor-scoped flag consumed by the Block state-manager mixin. */
public final class DwCarrierState {
    private static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> false);

    private DwCarrierState() {}

    public static <T> T create(java.util.function.Supplier<T> factory) {
        if (BUILDING.get()) throw new IllegalStateException("Nested DW carrier construction");
        BUILDING.set(true);
        try { return factory.get(); }
        finally { BUILDING.remove(); }
    }

    public static boolean isBuilding() { return BUILDING.get(); }
}
