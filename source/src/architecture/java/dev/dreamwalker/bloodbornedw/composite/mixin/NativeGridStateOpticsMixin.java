package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.NativeGridBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bank's native states may mix opaque and transparent cells. Keep the
 * optical flag per state, including when vanilla initializes its shape cache. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class NativeGridStateOpticsMixin {
    @Inject(method = "isOpaque", at = @At("HEAD"), cancellable = true)
    private void dw$nativeGridOpaque(CallbackInfoReturnable<Boolean> result) {
        BlockState state = (BlockState) (Object) this;
        if (state.getBlock() instanceof NativeGridBlock block) result.setReturnValue(block.cell(state).optics().opaque());
    }
}
