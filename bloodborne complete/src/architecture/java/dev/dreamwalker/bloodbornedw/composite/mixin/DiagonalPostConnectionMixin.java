package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inherited by modded vanilla-style walls/fences as well; no global neighbor suppression. */
@Mixin(WallBlock.class)
public abstract class DiagonalPostConnectionMixin {
    @Inject(method="shouldConnectTo",at=@At("HEAD"),cancellable=true)
    private void standalonePost(BlockState neighbor,boolean face,Direction direction,CallbackInfoReturnable<Boolean> cir) {
        if(neighbor.getBlock() instanceof PrototypeWallBlock&&PrototypeWallBlock.diagonalPost(neighbor))cir.setReturnValue(false);
    }
}
