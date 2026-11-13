package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.building.BbModelClickScope;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.item.DreamWalkerMining", remap = false)
abstract class BbModelMiningGateMixin {
    @Inject(method = "permits", at = @At("HEAD"), cancellable = true)
    private static void dw$allowModelToolClick(PlayerEntity player, World world, BlockPos pos, BlockState state,
                                               CallbackInfoReturnable<Boolean> result) {
        if (BbModelClickScope.accept(player, world, pos)) result.setReturnValue(true);
    }
}
