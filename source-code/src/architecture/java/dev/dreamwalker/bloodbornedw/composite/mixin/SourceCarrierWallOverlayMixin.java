package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native source lighting while allowing eyes inside retained empty geometry. */
@Mixin(InGameOverlayRenderer.class)
public abstract class SourceCarrierWallOverlayMixin {
    @Redirect(method = "getInWallBlockState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockState;shouldBlockVision(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Z"))
    private static boolean dw$sourceCarrierWallOverlay(BlockState state, BlockView view, BlockPos pos) {
        return !SourceFallbackCarrierV1271.suppressesNativeWallOverlay(state)
                && state.shouldBlockVision(view, pos);
    }
}
