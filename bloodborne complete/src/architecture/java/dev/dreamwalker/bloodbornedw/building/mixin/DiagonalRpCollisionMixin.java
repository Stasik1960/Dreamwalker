package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.DiagonalRpCollision;
import java.util.List;
import net.minecraft.util.math.Box;
import org.joml.Matrix4d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry", remap = false)
abstract class DiagonalRpCollisionMixin {
    // build() uses boxes() for players, placement reservations, then outline.
    // Keep the existing outline and seat targeting volumes.
    @WrapOperation(method = "build", at = {
            @At(value = "INVOKE", ordinal = 0, target =
                    "Ldev/dreamwalker/bloodbornedw/composite/StudioRpGeometry;boxes(Ljava/util/List;Lorg/joml/Matrix4d;)Ljava/util/List;"),
            @At(value = "INVOKE", ordinal = 1, target =
                    "Ldev/dreamwalker/bloodbornedw/composite/StudioRpGeometry;boxes(Ljava/util/List;Lorg/joml/Matrix4d;)Ljava/util/List;")
    }, require = 2, allow = 2)
    private static List<Box> dw$authoredDiagonalBoxes(List<Box> local, Matrix4d transform,
            Operation<List<Box>> original) {
        return DiagonalRpCollision.supports(transform)
                ? DiagonalRpCollision.boxes(local, transform) : original.call(local, transform);
    }
}
