package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.architecture.RemainingArchitecture;
import java.util.Arrays;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The frozen collision cohort still references these historical descriptors. */
@Mixin(value = RemainingArchitecture.class, remap = false)
public abstract class RetiredShutterRegistrationMixin {
    @Unique private static final Set<String> DW_RETIRED_SHUTTERS = Set.of(
            "source_spruce_trapdoor_bottom", "source_spruce_trapdoor_open");

    @Inject(method = "paths", at = @At("RETURN"), cancellable = true)
    private static void dw$omitRetiredShutters(String[] existing, CallbackInfoReturnable<String[]> result) {
        result.setReturnValue(Arrays.stream(result.getReturnValue())
                .filter(path -> !DW_RETIRED_SHUTTERS.contains(path)).toArray(String[]::new));
    }
}
