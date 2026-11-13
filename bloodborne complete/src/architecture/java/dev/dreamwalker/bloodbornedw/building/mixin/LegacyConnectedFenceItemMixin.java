package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.ConnectedFences;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class LegacyConnectedFenceItemMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$placeConnectedFence(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = ConnectedFences.placeLegacy(context);
        if (result != ActionResult.PASS) cir.setReturnValue(result);
    }
}
