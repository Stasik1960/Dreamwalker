package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.building.ConnectedFences;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import java.util.List;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(UnifiedCreativeCatalogue.class)
abstract class DistinctFormCatalogueMixin {
    @WrapMethod(method = "canonicalItems", remap = false)
    private static List<ItemStack> dreamwalker$offerFormItems(Operation<List<ItemStack>> original) {
        return ConnectedFences.creative(DistinctForms.catalogue(original.call()));
    }
}
