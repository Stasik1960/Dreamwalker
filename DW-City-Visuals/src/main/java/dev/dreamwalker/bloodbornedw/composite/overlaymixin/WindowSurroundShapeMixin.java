package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.util.List;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Saved dynamic carriers can still hold the geometry of the removed wall. */
@Mixin(value = CompositeRuntime.class, remap = false)
public abstract class WindowSurroundShapeMixin {
    @Unique
    private static void dw$omitRemovedWall(CompositeData.Contribution part, CallbackInfoReturnable<List<Box>> result) {
        if (part.owner().registryId().equals("bloodborne_dw:source_shutter_window_surround")) {
            result.setReturnValue(List.of());
        }
    }

    @Inject(method = "physicalParts", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$omitWallCollision(CompositeData.Contribution part, BlockPos cell,
            CallbackInfoReturnable<List<Box>> result) {
        dw$omitRemovedWall(part, result);
    }

    @Inject(method = "contextSelection", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$omitWallOutline(BlockView world, CompositeData.Contribution part, BlockPos cell,
            ShapeContext context, CallbackInfoReturnable<List<Box>> result) {
        dw$omitRemovedWall(part, result);
    }
}
