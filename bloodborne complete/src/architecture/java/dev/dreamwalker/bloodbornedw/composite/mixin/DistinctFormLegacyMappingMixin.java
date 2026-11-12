package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases;
import java.util.Optional;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ImportedTypeAliases.class)
abstract class DistinctFormLegacyMappingMixin {
    @Inject(target = @Desc(value = "mapping", args = BlockState.class, ret = Optional.class),
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamwalker$skipLegacyArtMigration(BlockState state, CallbackInfoReturnable<Optional<?>> cir) {
        if (DistinctForms.fixed(state.getBlock())) cir.setReturnValue(Optional.empty());
    }
}
