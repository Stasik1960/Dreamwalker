package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.WallArtBanks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = {
        "dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock$Expanded",
        "dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock$Brick"
}, remap = false)
abstract class BrickWallBankMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 29), remap = false)
    private static int dw$brickPoseBank(int original) {
        return WallArtBanks.BRICK;
    }
}
