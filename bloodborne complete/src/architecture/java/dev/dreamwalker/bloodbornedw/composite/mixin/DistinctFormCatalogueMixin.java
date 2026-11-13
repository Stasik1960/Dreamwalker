package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.building.ConnectedFences;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import java.util.List;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UnifiedCreativeCatalogue.class)
abstract class DistinctFormCatalogueMixin {
    @Inject(method = "canonicalItems", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dreamwalker$offerFormItems(CallbackInfoReturnable<List<ItemStack>> cir) {
        cir.setReturnValue(ConnectedFences.creative(DistinctForms.catalogue(cir.getReturnValue())));
    }
}
