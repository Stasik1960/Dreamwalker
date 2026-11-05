package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRuntime;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse orphan cleanup when another block already replaced part of a Creative break target. */
@Mixin(value = StaticArchitectureRuntime.class, remap = false)
abstract class StaticCreativeBreakMixin {
    @Shadow(remap = false)
    private static TransactionCore.Result remove(ServerWorld world, BlockPos root,
            StaticArchitectureCatalog.Pose pose, boolean tolerateMissing) {
        throw new AssertionError();
    }

    @Inject(method = "breakAt(Lnet/minecraft/class_3218;Lnet/minecraft/class_2338;Lnet/minecraft/class_1657;Z)"
            + "Ldev/dreamwalker/bloodbornedw/runtime/TransactionCore$Result;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$removeRemainingCreativeParts(ServerWorld world, BlockPos hit, PlayerEntity player,
            boolean drop, CallbackInfoReturnable<TransactionCore.Result> cir) {
        if (player == null || !player.isCreative() || drop) return;
        var result = cir.getReturnValue();
        if (result.outcome() != TransactionCore.Outcome.REJECTED || result.reason() == null
                || !result.reason().startsWith("static_component_cell_replaced:")) return;

        var target = StaticArchitectureRuntime.target(world, hit, player);
        if (target == null) return;
        var root = target.root();
        var chunk = world.getChunkManager().getWorldChunk(root.getX() >> 4, root.getZ() >> 4);
        if (chunk == null || !StaticArchitectureCatalog.matchesRoot(chunk.getBlockState(root), target.pose())) return;

        cir.setReturnValue(remove(world, root, target.pose(), true));
    }
}
