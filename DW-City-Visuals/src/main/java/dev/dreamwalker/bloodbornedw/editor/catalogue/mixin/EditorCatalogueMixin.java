package dev.dreamwalker.bloodbornedw.editor.catalogue.mixin;
import dev.dreamwalker.bloodbornedw.editor.catalogue.EditorCatalogue;

import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UnifiedCreativeCatalogue.class, remap = false, priority = 900)
public abstract class EditorCatalogueMixin {
    @Inject(method = "canonicalItems", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$completeCatalogue(CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> items = new ArrayList<>(cir.getReturnValue());
        EditorCatalogue.fill(items);
        cir.setReturnValue(List.copyOf(items));
    }
}
