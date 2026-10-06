package dev.dreamwalker.bloodbornedw.mixin;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets vanilla neighbor and block-entity checks recognize an equivalent DW carrier. */
@Mixin(AbstractBlock.AbstractBlockState.class)
abstract class BlockStateIsOfMixin {
    @Inject(method = "isOf", at = @At("HEAD"), cancellable = true)
    private void bloodborneDw$matchesSource(Block expected, CallbackInfoReturnable<Boolean> callback) {
        Block actual = ((BlockState) (Object) this).getBlock();
        if (DwBlocks.isCarrierOf(actual, expected)) callback.setReturnValue(true);
    }
}
