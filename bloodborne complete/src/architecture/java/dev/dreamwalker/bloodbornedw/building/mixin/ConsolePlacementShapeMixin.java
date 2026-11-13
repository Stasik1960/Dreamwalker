package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeRuntime.class, remap = false)
abstract class ConsolePlacementShapeMixin {
    @Unique private static final String DW_CONSOLE_ID = "bloodborne_dw:source_quartz_stairs_outer_1";
    @Unique private static final List<Box> DW_CONSOLE_CELL = List.of(new Box(0, 0, 0, 1, 1, 1));

    @WrapOperation(
            method = "instance(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Ljava/util/UUID;Lnet/minecraft/class_2487;)Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance;",
            at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeSourceShift;footprint(Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;Lnet/minecraft/class_2680;Lnet/minecraft/class_2487;)Ljava/util/Map;"))
    private static Map<Cell, Footprint> dreamwalker$reserveOneConsoleCell(CompositeSpec spec, BlockState state,
            NbtCompound payload, Operation<Map<Cell, Footprint>> original) {
        if (spec.id.toString().equals(DW_CONSOLE_ID)) {
            return Map.of(Cell.ORIGIN, new Footprint(DW_CONSOLE_CELL, DW_CONSOLE_CELL));
        }
        return original.call(spec, state, payload);
    }

    @Inject(method = "physicalParts", at = @At("HEAD"), cancellable = true)
    private static void dreamwalker$replaceCachedConsoleCollision(CompositeData.Contribution entry, BlockPos cell,
            CallbackInfoReturnable<List<Box>> result) {
        if (!entry.owner().registryId().equals(DW_CONSOLE_ID)) return;
        // Worlds saved with the old decorative reservation still carry its
        // outside-cell boxes. Keep only the owner's real grid cell occupied.
        result.setReturnValue(CompositeData.cell(cell).equals(entry.owner().root()) ? DW_CONSOLE_CELL : List.of());
    }
}
