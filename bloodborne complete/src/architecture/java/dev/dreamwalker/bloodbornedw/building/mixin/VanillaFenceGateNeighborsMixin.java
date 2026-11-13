package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FenceGateBlock.class)
public abstract class VanillaFenceGateNeighborsMixin {
    @Inject(method = "isWall", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$gateMatchesImportedWall(BlockState neighbor, CallbackInfoReturnable<Boolean> result) {
        if (neighbor.getBlock() instanceof PrototypeWallBlock && PrototypeWallBlock.diagonalPost(neighbor)) {
            result.setReturnValue(false);
        } else if (ImportedFenceAccess.dreamwalker$wallLike(neighbor)) {
            result.setReturnValue(true);
        }
    }
}
