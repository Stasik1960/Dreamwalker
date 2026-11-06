package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.NoCollideDepthAccessor;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.function.Supplier;

public final class CompositeOverlapEdit {
    private CompositeOverlapEdit() {}

    public static <T> T run(Owner owner, Supplier<T> transaction) {
        var depth = NoCollideDepthAccessor.dw$depth();
        int previous = depth.get();
        depth.set(previous + 1);
        try {
            return NoCollidePlacement.forOwner(owner.instanceId(), transaction);
        } finally {
            if (previous == 0) depth.remove();
            else depth.set(previous);
        }
    }
}
