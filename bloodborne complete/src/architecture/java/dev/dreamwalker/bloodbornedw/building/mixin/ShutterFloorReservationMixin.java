package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.building.ShutterFloorPlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import java.util.List;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompositeRuntime.class, remap = false)
abstract class ShutterFloorReservationMixin {
    @WrapOperation(method = "lambda$checks$8", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/architecture/PlacementPhysics;physicalBoxes(Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance;)Ljava/util/List;"))
    private static List<Box> dreamwalker$keepShutterPlacementAboveFloor(ObjectInstance object,
            Operation<List<Box>> original) {
        return ShutterFloorPlacement.placementBoxes(object, original.call(object));
    }
}
