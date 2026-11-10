package dev.dreamwalker.bloodbornedw.editor.catalogue.mixin;

import dev.dreamwalker.bloodbornedw.editor.catalogue.EditorCatalogue;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornedw.tool.DebugToolModes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UnifiedCreativeCatalogue.class, remap = false)
public abstract class EditorCatalogueMixin {
    @Inject(method = "initialize", at = @At("RETURN"), remap = false)
    private static void dw$registerTabs(CallbackInfo ci) { EditorCatalogue.registerTabs(); }

    @Redirect(method = "lambda$initialize$1", at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/debug/UnifiedCreativeCatalogue;canonicalItems()Ljava/util/List;"), remap = false)
    private static List<ItemStack> dw$buildingTab() { return EditorCatalogue.categoryItems(EditorCatalogue.Category.BUILDING); }

    // Normalize the original list before the pinned lantern repair's cancellable RETURN.
    // No competing RETURN callback or thread-local intermediate list is needed.
    @Redirect(method = "canonicalItems", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"), remap = false)
    private static List<ItemStack> dw$completeOriginalCatalogue(Stream<ItemStack> stream) {
        List<ItemStack> items = new ArrayList<>(stream.toList());
        EditorCatalogue.fill(items);
        for (var stack : DebugToolModes.extraItems()) {
            if (items.stream().noneMatch(item -> item.isOf(stack.getItem()))) items.add(stack);
        }
        return List.copyOf(items);
    }
}
