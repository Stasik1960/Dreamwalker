package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.composite.PlacementRotation;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the original floor, wall and corner support checks for both yaw sets. */
@Mixin(value = PrototypeLadderBlock.class, remap = false)
public abstract class LadderPlacementRotationMixin {
    @Redirect(method = "method_9605(Lnet/minecraft/class_1750;)Lnet/minecraft/class_2680;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/class_3532;method_15375(F)I", remap = false), remap = false, require = 3)
    private int dw$rotation(float biasedSteps, ItemPlacementContext context) {
        return PlacementRotation.biasedSteps(biasedSteps, context);
    }
}
