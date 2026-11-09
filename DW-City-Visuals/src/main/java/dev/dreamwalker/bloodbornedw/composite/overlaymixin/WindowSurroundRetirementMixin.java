package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeItem.class, remap = false)
public abstract class WindowSurroundRetirementMixin {
    @Unique private static final Identifier DW_REMOVED_SURROUND = new Identifier("bloodborne_dw", "source_shutter_window_surround");

    @Inject(method = "placeInternal", at = @At("HEAD"), cancellable = true, remap = false)
    private void dw$rejectSurroundPlacement(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> result) {
        if (DW_REMOVED_SURROUND.equals(Registries.ITEM.getId(context.getStack().getItem()))) {
            result.setReturnValue(ActionResult.FAIL);
        }
    }
}
