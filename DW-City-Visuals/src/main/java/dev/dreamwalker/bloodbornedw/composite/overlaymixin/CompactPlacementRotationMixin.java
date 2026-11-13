package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import dev.dreamwalker.bloodbornedw.composite.PlacementRotation;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native/owned decision and the native placement state must use the same yaw. */
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock$CompactItem", remap = false)
public abstract class CompactPlacementRotationMixin {
    @Redirect(method = {
        "method_7712(Lnet/minecraft/class_1750;)Lnet/minecraft/class_1269;",
        "method_7707(Lnet/minecraft/class_1750;)Lnet/minecraft/class_2680;"
    }, at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(F)I", remap = false), remap = false, require = 2)
    private int dw$rotation(float steps, ItemPlacementContext context) {
        // These connected walls are native grid geometry and have no oblique placement.
        return FenceCatalogue.automaticWallItem(context.getStack()) ? 0 : PlacementRotation.steps(steps, context);
    }

    @ModifyExpressionValue(method = "method_7707", at = @At(value = "INVOKE", target = "Ljava/lang/Math;floorMod(II)I"))
    private int dw$fitCompactModel(int rotation, @Local BlockState state) {
        return PlacementRotation.fitModel(rotation, state);
    }
}
