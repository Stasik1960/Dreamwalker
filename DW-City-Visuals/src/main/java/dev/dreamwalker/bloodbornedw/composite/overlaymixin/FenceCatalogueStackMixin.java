package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImportedTypeAliases.class, remap = false)
public abstract class FenceCatalogueStackMixin {
    @Inject(method = "canonicalStack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$canonicalFence(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack next = FenceCatalogue.canonical(stack);
        if (next != stack) cir.setReturnValue(next);
    }
}
