package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import dev.dreamwalker.bloodbornedw.building.CreativeOnlyUtilities;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DwUtilityBlocks.class, remap = false)
abstract class UtilityCarrierReplacementMixin {
    @Inject(method = "replaceCarrier", at = @At("HEAD"), cancellable = true)
    private static void dreamwalker$keepUtilityDuringCarrierReplacement(ServerWorld world, BlockPos pos, BlockState next,
            PlayerEntity player, CallbackInfoReturnable<Boolean> result) {
        if (player == null || player.isCreative() || !world.isChunkLoaded(pos)) return;
        BlockState current = world.getBlockState(pos);
        if (!current.isOf(next.getBlock()) && CreativeOnlyUtilities.includes(current)) result.setReturnValue(false);
    }

    @Inject(method = "collisionSwitch", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dreamwalker$allowWhiteLightCollisionSwitch(BlockState state,
                                                                  CallbackInfoReturnable<Boolean> result) {
        if (CreativeOnlyUtilities.isWhiteLight(state)) result.setReturnValue(true);
    }
}
