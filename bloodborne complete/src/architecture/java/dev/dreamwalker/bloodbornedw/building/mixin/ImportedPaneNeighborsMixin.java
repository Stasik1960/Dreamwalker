package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.AutoStoneFence", remap = false)
public abstract class ImportedPaneNeighborsMixin {
    @Unique
    private static final String DW_FENCE_KIND =
            "Ldev/dreamwalker/bloodbornedw/composite/AutoStoneFence;fenceLike(Lnet/minecraft/class_2680;)Z";

    @WrapOperation(method = "reconnect", at = @At(value = "INVOKE", target = DW_FENCE_KIND,
            ordinal = 0, remap = false), remap = false)
    private static boolean dreamwalker$routePane(BlockState state, Operation<Boolean> fence) {
        // Share the neighbor loop with fences, but give panes their own predicate below.
        return fence.call(state) || ImportedFenceAccess.dreamwalker$matches(state)
                && !ImportedFenceAccess.dreamwalker$wallLike(state);
    }

    @WrapOperation(method = "reconnect", at = @At(value = "INVOKE", target = DW_FENCE_KIND,
            ordinal = 1, remap = false), remap = false)
    private static boolean dreamwalker$matchSameConnectionType(BlockState neighbor, Operation<Boolean> fence,
            ServerWorld world, BlockPos pos, BlockState state) {
        if (ImportedFenceAccess.dreamwalker$fenceLike(state)) return fence.call(neighbor);
        return ImportedFenceAccess.dreamwalker$matches(neighbor)
                && !ImportedFenceAccess.dreamwalker$wallLike(neighbor) && !fence.call(neighbor);
    }

    @WrapOperation(method = "reconnect", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2354;method_10184(Lnet/minecraft/class_2680;ZLnet/minecraft/class_2350;)Z",
            ordinal = 0, remap = false), remap = false)
    private static boolean dreamwalker$useVanillaPaneRules(FenceBlock fenceBlock, BlockState neighbor,
            boolean solid, Direction side, Operation<Boolean> fence, ServerWorld world, BlockPos pos, BlockState state) {
        if (ImportedFenceAccess.dreamwalker$fenceLike(state)) return fence.call(fenceBlock, neighbor, solid, side);
        return ((PaneBlock) Blocks.GLASS_PANE).connectsTo(neighbor, solid);
    }
}
