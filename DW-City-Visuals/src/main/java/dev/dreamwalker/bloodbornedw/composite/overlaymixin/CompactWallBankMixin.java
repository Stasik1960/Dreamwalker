package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock;
import dev.dreamwalker.bloodbornedw.composite.WallArtBanks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImportedCompactBlock.class, remap = false)
abstract class CompactWallBankMixin {
    @ModifyConstant(method = "load", constant = @Constant(intValue = 479), remap = false)
    private static int dw$largestCompactPose(int original) {
        return (WallArtBanks.BRICK + 1) * 16 - 1;
    }

    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dw$wallPoseBank(CompositeSpec spec, CallbackInfoReturnable<ImportedCompactBlock> callback) {
        String path = spec.id.getPath();
        if (path.equals(WallArtBanks.STONE_BRICK_PATH)) {
            callback.setReturnValue(new WallArtBanks.StoneBrick(spec));
        } else if (WallArtBanks.smallWall(path)) {
            callback.setReturnValue(new WallArtBanks.Small(spec));
        }
    }
}
