package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import java.util.List;
import dev.dreamwalker.bloodbornedw.architecture.RemainingArchitecture;
import dev.dreamwalker.bloodbornedw.composite.FenceCatalogue;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RemainingArchitecture.class, remap = false)
public abstract class FenceCatalogueCreativeMixin {
    @Inject(method = "creativeVariants", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$hideFenceParts(ItemStack stack, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (FenceCatalogue.hidden(stack)) cir.setReturnValue(List.of());
    }
    @Inject(method = "creativeVariants", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$compactFenceItem(ItemStack stack, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!cir.getReturnValue().isEmpty())
            cir.setReturnValue(cir.getReturnValue().stream().filter(item -> !FenceCatalogue.generatedWallPose(item)).map(FenceCatalogue::compact).toList());
    }
}
