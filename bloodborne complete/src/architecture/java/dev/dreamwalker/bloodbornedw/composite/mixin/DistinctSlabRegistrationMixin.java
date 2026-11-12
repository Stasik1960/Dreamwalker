package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import java.util.List;
import net.minecraft.block.SlabBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// MaterialSlabs is supplied by the published distribution's city overlay.
@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.MaterialSlabs", remap = false)
abstract class DistinctSlabRegistrationMixin {
    @Shadow @Final private static List<SlabBlock> BLOCKS;

    @Inject(method = "initialize", at = @At("RETURN"))
    private static void dreamwalker$registerSlabForms(CallbackInfo ci) {
        DistinctForms.registerSlabs(BLOCKS);
    }
}
