package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AxeItem.class)
public abstract class SelectionAxeItemMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$disableWoodenAxeToolUse(ItemUsageContext context, CallbackInfoReturnable<ActionResult> callback) {
        if (context.getStack().isOf(Items.WOODEN_AXE)) callback.setReturnValue(ActionResult.PASS);
    }
}
