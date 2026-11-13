package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.CreativeOnlyUtilities;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.item.DreamWalkerMining", remap = false)
abstract class UtilityMiningMixin {
    @Inject(method = "permits", at = @At("RETURN"), cancellable = true)
    private static void dreamwalker$denyUtilityMining(PlayerEntity player, World world, BlockPos pos,
                                                      BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ() && CreativeOnlyUtilities.blocksReplacement(state, player)
                && !player.getMainHandStack().isOf(BbToolService.ITEM)) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "bypassAdventureRestriction", at = @At("RETURN"), cancellable = true)
    private static void dreamwalker$keepAdventureRestrictions(PlayerEntity player, World world, BlockPos pos,
            GameMode mode, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ() && CreativeOnlyUtilities.includes(world.getBlockState(pos))) {
            result.setReturnValue(false);
        }
    }
}
