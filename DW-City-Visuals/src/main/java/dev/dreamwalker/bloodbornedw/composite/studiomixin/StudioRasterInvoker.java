package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = CompositeSpec.class, remap = false)
public interface StudioRasterInvoker {
    @Invoker("distribute")
    static void dw$raster(CompositeSpec.Bounds box, int rotation, double mount,
            Map<Cell, List<Box>> destination, int resolution) {
        throw new AssertionError();
    }
}
