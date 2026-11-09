package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.ImportedSelectionShapes;
import dev.dreamwalker.bloodbornedw.composite.StudioFootprints;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImportedSelectionShapes.class, remap = false)
public abstract class StudioImportedOutlineMixin {
    @Inject(method = "parts", at = @At("HEAD"), cancellable = true)
    private static void dw$useAuthoredOutline(CompositeData.Contribution contribution, BlockPos cell,
            CallbackInfoReturnable<Optional<List<Box>>> result) {
        var layout = StudioFootprints.dynamic(contribution.rootData());
        if (layout != null && layout.outline() != null) result.setReturnValue(Optional.of(contribution.shape().selection()));
    }
}
