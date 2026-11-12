package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.IntProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ImportedArtworkBlock.class)
abstract class DistinctFormArtBankMixin {
    @Inject(method = "bankProperty", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamwalker$readFixedBank(BlockState state, CallbackInfoReturnable<IntProperty> cir) {
        IntProperty bank = DistinctForms.artBank(state.getBlock());
        if (bank != null) cir.setReturnValue(bank);
    }
}
