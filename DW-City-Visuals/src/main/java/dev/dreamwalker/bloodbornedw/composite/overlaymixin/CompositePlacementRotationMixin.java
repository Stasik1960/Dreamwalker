package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import dev.dreamwalker.bloodbornedw.composite.PlacementRotation;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Quantize the player facing before imported offsets and surface alignment. */
@Mixin(value = CompositeItem.class, remap = false)
public abstract class CompositePlacementRotationMixin {
    @Redirect(method = "placeInternal", at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(F)I", remap = false), remap = false, require = 2)
    private int dw$rotation(float steps, ItemPlacementContext context) {
        return PlacementRotation.steps(steps, context);
    }
}
