package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.ImportedSourceLightingV127;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The root state's existing open bit selects a torch-bright lit variant. */
@Mixin(value = ImportedSourceLightingV127.class, remap = false)
abstract class HoldLanternLightingMixin {
    @Inject(method = "settings", at = @At("RETURN"), remap = false)
    private static void dw$holdLanternLight(CompositeSpec spec, AbstractBlock.Settings settings,
                                             CallbackInfoReturnable<AbstractBlock.Settings> result) {
        if (spec.id.toString().equals("bloodborne_dw:source_hold_lanterns")) {
            result.getReturnValue().luminance(state -> state.get(CompositeRootBlock.OPEN) ? 14 : 0);
        }
    }
}
