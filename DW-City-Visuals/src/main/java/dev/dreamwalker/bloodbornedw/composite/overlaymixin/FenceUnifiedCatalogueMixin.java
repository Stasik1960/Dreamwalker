package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import java.util.ArrayList;
import java.util.List;
import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UnifiedCreativeCatalogue.class, remap = false)
public abstract class FenceUnifiedCatalogueMixin {
    @Inject(method = "canonicalItems", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$includeFenceStyles(CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> items = new ArrayList<>(cir.getReturnValue());
        for (ItemStack candidate : FenceCatalogue.specialItems())
            if (items.stream().noneMatch(existing -> ItemStack.areEqual(existing, candidate))) items.add(candidate);
        cir.setReturnValue(List.copyOf(items));
    }
}
