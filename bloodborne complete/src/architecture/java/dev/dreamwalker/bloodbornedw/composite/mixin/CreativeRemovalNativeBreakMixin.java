package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.ForcedBlockRemoval;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.NativePlayerBreak", remap = false)
abstract class CreativeRemovalNativeBreakMixin {
    @Inject(method = {
            "remove(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;)Ljava/lang/Boolean;",
            "remove(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;)Ljava/lang/Boolean;"
    }, at = @At("RETURN"), cancellable = true, remap = false)
    private static void dreamwalker$clearLayersAfterBreakChecks(ServerWorld world, BlockPos pos,
                                                               CallbackInfoReturnable<Boolean> result) {
        var player = ForcedBlockRemoval.creativeOrphanActor(world, pos);
        Boolean removed = result.getReturnValue();
        if (player != null && ForcedBlockRemoval.hasDreamWalkerData(world, pos)
                && (Boolean.TRUE.equals(removed) || Boolean.FALSE.equals(removed) && ForcedBlockRemoval.isOrphanHelper(world, pos))) {
            result.setReturnValue(ForcedBlockRemoval.clearAllAt(world, pos, player));
        }
    }
}
