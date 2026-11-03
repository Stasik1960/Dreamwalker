package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;
import dev.dreamwalker.bloodbornedw.editor.worldedit.AppearanceTag;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge", remap = false)
abstract class LogicalReadMixin {
    @Inject(method = "logicalRead", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$capture(com.sk89q.worldedit.world.World adapter, BlockVector3 vector, BaseBlock nativeBlock, CallbackInfoReturnable<BaseBlock> cir) {
        cir.setReturnValue(AppearanceTag.capture(WorldEditBridge.nativeWorld(adapter), new BlockPos(vector.getX(), vector.getY(), vector.getZ()), cir.getReturnValue()));
    }
}
