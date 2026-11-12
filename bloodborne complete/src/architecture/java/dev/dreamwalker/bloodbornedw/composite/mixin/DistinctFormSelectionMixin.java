package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.ImportedSelectionShapes;
import dev.dreamwalker.bloodbornedw.composite.ImportedLegacyOutline;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({ImportedSelectionShapes.class, ImportedLegacyOutline.class})
abstract class DistinctFormSelectionMixin {
    @Redirect(method = {"parts", "selection"}, at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"), remap = false)
    private static boolean dreamwalker$matchesOwner(String specId, Object registry, CompositeData.Contribution entry, BlockPos cell) {
        return entry.rootData().blockId().equals(registry);
    }
}
