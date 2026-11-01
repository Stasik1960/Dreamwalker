package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.ImportedStairBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedStairShapes;
import dev.dreamwalker.bloodbornedw.composite.PlacementRotation;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve the stair's facing convention and connectivity logic. */
@Mixin(value = ImportedStairBlock.class, remap = false)
public abstract class ImportedStairPlacementRotationMixin {
    @Redirect(method = "placementState", at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/composite/ImportedStairShapes;rotation(Lnet/minecraft/class_2350;)I", remap = false), remap = false)
    private static int dw$rotation(Direction direction, BlockState state, ItemPlacementContext context) {
        int original = ImportedStairShapes.rotation(direction);
        if (!PlacementRotation.diagonal(context)) return original;
        float yaw = context.getPlayerYaw();
        // Existing direction mapping may have a fixed 180-degree offset.
        int cardinal = Math.floorMod(Math.round(yaw / 90.0F), 4) * 2;
        return Math.floorMod(PlacementRotation.yaw(yaw, context) + original - cardinal, 8);
    }
}
