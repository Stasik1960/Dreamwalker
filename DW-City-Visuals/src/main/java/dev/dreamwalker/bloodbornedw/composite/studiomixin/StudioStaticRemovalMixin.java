package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRuntime;
import dev.dreamwalker.bloodbornedw.composite.StudioStaticRemoval;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StaticArchitectureRuntime.class, remap = false)
public abstract class StudioStaticRemovalMixin {
    @Inject(method = "remove(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;Ldev/dreamwalker/bloodbornedw/composite/StaticArchitectureCatalog$Pose;Z)Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore$Result;",
            at = @At("HEAD"), cancellable = true)
    private static void dw$preserveForeignGuests(ServerWorld world, BlockPos root, StaticArchitectureCatalog.Pose pose,
            boolean cleanup, CallbackInfoReturnable<TransactionCore.Result> result) {
        var removal = StudioStaticRemoval.withAuthoredMembers(world, root, pose, cleanup);
        if (removal != null) result.setReturnValue(removal);
    }
}
