package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkStates;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ImportedArtworkStates.class)
abstract class DistinctFormCyclingMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dreamwalker$keepForm(CompositeRootBlock block, BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (DistinctForms.fixed(block)) cir.setReturnValue(ActionResult.PASS);
    }
}
