package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
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
        if (result.outcome() != TransactionCore.Outcome.REJECTED || result.reason() == null) return;

        String reason = result.reason();
        if (!reason.startsWith("static_component_cell_replaced:")
                && !reason.startsWith("static_component_recipe_mismatch:")
                && !reason.equals("static_root_missing")
                && !reason.equals("no_static_part_at_hit")) return;

        var target = StaticArchitectureRuntime.target(world, hit, player);
        if (target == null && reason.equals("no_static_part_at_hit")) {
            var state = world.getBlockState(hit);
            if (StaticArchitectureCatalog.contains(state)
                    && StaticArchitectureCatalog.recipe(state).fragments().size() == 1) {
                target = StaticArchitectureRuntime.target(world, hit, null);
            }
        }
        if (target == null) return;

        // The cleanup transaction only removes the matching fragment of this
        // pose at each cell. It retains the normal protection and loaded-chunk checks.
        var cleanup = remove(world, target.root(), target.pose(), true);
        if (cleanup.outcome() == TransactionCore.Outcome.COMMITTED) {
            DreamwalkerBb.LOG.debug("Creative architecture cleanup at {} (root {}): {}",
                    hit, target.root(), reason);
        }
        cir.setReturnValue(cleanup);
    }
}
