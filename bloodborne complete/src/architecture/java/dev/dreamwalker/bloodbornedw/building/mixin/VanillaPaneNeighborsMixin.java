package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PaneBlock.class)
public abstract class VanillaPaneNeighborsMixin {
    @Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$connectImportedWallOrPane(BlockState neighbor, boolean solid,
            CallbackInfoReturnable<Boolean> result) {
        if (neighbor.getBlock() instanceof PrototypeWallBlock && PrototypeWallBlock.diagonalPost(neighbor)) return;
        if (ImportedFenceAccess.dreamwalker$wallLike(neighbor)
                || ImportedFenceAccess.dreamwalker$matches(neighbor) && !ImportedFenceAccess.dreamwalker$fenceLike(neighbor)) {
            result.setReturnValue(true);
        }
    }
}
