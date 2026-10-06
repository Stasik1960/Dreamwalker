package dev.dreamwalker.bloodbornedw.mixin;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Block-entity support sets contain vanilla source blocks, never their DW carriers. */
@Mixin(BlockEntityType.class)
abstract class BlockEntityTypeMixin {
    @ModifyVariable(method = "supports", at = @At("HEAD"), argsOnly = true)
    private BlockState bloodborneDw$sourceCarrierState(BlockState state) {
        return DwBlocks.sourceState(state);
    }
}
