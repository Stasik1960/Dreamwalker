package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.NoCollideDepthAccessor;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.function.Supplier;
import net.minecraft.block.BlockState;

public final class CompositeOverlapEdit {
    public static final String OPEN_WITH_OVERLAP = "OpenWithBlockOverlap";
    private static final ThreadLocal<Boolean> TRANSITION = new ThreadLocal<>();

    private CompositeOverlapEdit() {}

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof CompositeRootBlock block && block.spec.openable
                && (block.spec.functionalDoorPhysics || block.spec.id.getPath().endsWith("_door"));
    }

    public static boolean isShutter(BlockState state) {
        return state.getBlock() instanceof CompositeRootBlock block
                && block.spec.id.getPath().equals("source_shutter_window");
    }

    public static boolean inTransition() { return Boolean.TRUE.equals(TRANSITION.get()); }

    public static <T> T run(Owner owner, Supplier<T> transaction) {
        var depth = NoCollideDepthAccessor.dw$depth();
        int previous = depth.get();
        boolean wasTransition = inTransition();
        TRANSITION.set(true);
        depth.set(previous + 1);
        try {
            return NoCollidePlacement.forOwner(owner.instanceId(), transaction);
        } finally {
            if (previous == 0) depth.remove();
            else depth.set(previous);
            if (!wasTransition) TRANSITION.remove();
        }
    }
}
