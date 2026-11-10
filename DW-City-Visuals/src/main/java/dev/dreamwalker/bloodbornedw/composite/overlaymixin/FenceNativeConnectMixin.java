package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mixed artwork IDs are not tagged wholesale: test the selected pose instead. */
@Mixin(FenceBlock.class)
public abstract class FenceNativeConnectMixin {
    @Inject(method = "canConnect", at = @At("HEAD"), cancellable = true)
    private void dw$connectReviewedFence(BlockState neighbor, boolean solid, Direction direction,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (AutoStoneFence.matches(neighbor))
            cir.setReturnValue(!neighbor.isIn(BlockTags.WALLS)
                && !((FenceBlock)(Object)this).getDefaultState().isIn(BlockTags.WOODEN_FENCES));
    }
}
