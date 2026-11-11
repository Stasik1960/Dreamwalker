package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.WallArtBanks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = {
        "dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock$ExpandedDeepslate",
        "dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock$Deepslate"
}, remap = false)
abstract class DeepslateWallBankMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 9), remap = false)
    private static int dw$deepslatePoseBank(int original) {
        return WallArtBanks.DEEPSLATE;
    }
}
