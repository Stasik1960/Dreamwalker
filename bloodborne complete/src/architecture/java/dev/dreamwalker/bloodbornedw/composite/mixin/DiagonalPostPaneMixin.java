package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PaneBlock.class)
public abstract class DiagonalPostPaneMixin {
    @Inject(method="connectsTo",at=@At("HEAD"),cancellable=true)
    private void standalonePost(BlockState neighbor,boolean face,CallbackInfoReturnable<Boolean> cir) {
        if(neighbor.getBlock() instanceof PrototypeWallBlock&&PrototypeWallBlock.diagonalPost(neighbor))cir.setReturnValue(false);
    }
}
