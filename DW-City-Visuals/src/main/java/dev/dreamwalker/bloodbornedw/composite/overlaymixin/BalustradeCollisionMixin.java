package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Saved owner footprints may predate the reviewed balustrade collision data. */
@Mixin(value = CompositeRuntime.class, remap = false)
public abstract class BalustradeCollisionMixin {
    @Unique private static final Map<BlockData, Map<Cell, List<Box>>> dw$balustrades = new WeakHashMap<>();

    @Inject(method = "physicalParts", at = @At("HEAD"), cancellable = true)
    private static void dw$balustrade(CompositeData.Contribution entry, BlockPos cell, CallbackInfoReturnable<List<Box>> cir) {
        if (!entry.owner().registryId().equals("bloodborne_dw:source_assembly_brown_stained_glass_pane")) return;
        BlockData data = entry.rootData();
        Map<Cell, List<Box>> shapes;
        synchronized (dw$balustrades) { shapes = dw$balustrades.get(data); }
        if (shapes == null) {
            var state = CompositeData.state(data);
            if (!(state.getBlock() instanceof CompositeRootBlock block)) return;
            var footprint = CompositeSourceShift.footprint(block.spec, state, CompositeData.nbt(data.blockEntityNbt()));
            Map<Cell, List<Box>> generated = new HashMap<>();
            footprint.forEach((position, shape) -> {
                if (!shape.collision().isEmpty()) generated.put(position, shape.collision());
            });
            Map<Cell, List<Box>> ready = Map.copyOf(generated);
            synchronized (dw$balustrades) { shapes = dw$balustrades.computeIfAbsent(data, ignored -> ready); }
        }
        cir.setReturnValue(shapes.getOrDefault(CompositeData.cell(cell).subtract(entry.owner().root()), List.of()));
    }
}
