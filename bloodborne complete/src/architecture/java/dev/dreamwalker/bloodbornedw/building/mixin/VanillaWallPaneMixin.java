package dev.dreamwalker.bloodbornedw.building.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WallBlock.class, priority = 1100)
public abstract class VanillaWallPaneMixin {
    @Inject(method = "shouldConnectTo", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$connectImportedPane(BlockState neighbor, boolean solid, Direction face,
            CallbackInfoReturnable<Boolean> result) {
        if (ImportedFenceAccess.dreamwalker$matches(neighbor)
                && !ImportedFenceAccess.dreamwalker$wallLike(neighbor)
                && !ImportedFenceAccess.dreamwalker$fenceLike(neighbor)) result.setReturnValue(true);
    }
}
