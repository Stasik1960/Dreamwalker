package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock;
import dev.dreamwalker.bloodbornedw.composite.WallArtBanks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImportedArtworkBlock.class, remap = false)
abstract class WallArtBudgetMixin {
    @Inject(method = "isBankedPath", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$smallWallHasBank(String path, CallbackInfoReturnable<Boolean> callback) {
        if (WallArtBanks.smallWall(path)) callback.setReturnValue(true);
    }

    @Inject(method = "variantBudget", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$wallVariantBudget(String path, CallbackInfoReturnable<Integer> callback) {
        int budget = WallArtBanks.variantBudget(path);
        if (budget != 0) callback.setReturnValue(budget);
    }
}
