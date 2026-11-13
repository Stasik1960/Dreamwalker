package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrototypeWallBlock.class)
abstract class PrototypeWallSupportMixin {
    @Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$allowMaterialZeroWithoutFloor(BlockState state, WorldView world, BlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        // Material zero is the other retained fence alongside the main wall and diagonal post.
        if (PrototypeWallBlock.material(state) == 0) result.setReturnValue(true);
    }
}
