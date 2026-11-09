package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.StudioOverhangs;
import dev.dreamwalker.bloodbornedw.composite.StudioPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class StudioOrdinaryPlacementMixin {
    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true)
    private void dw$checkReviewedReservation(ItemPlacementContext context, BlockState state,
            CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() || NoCollidePlacement.ignoringBlocks()) return;
        var world = context.getWorld();
        var pos = context.getBlockPos();
        var volume = CompositeRuntime.nativeCollision(state, world, pos, ShapeContext.absent());
        if (volume.isEmpty()) return;
        var overhang = StudioOverhangs.augment(world, pos, VoxelShapes.empty(), StudioOverhangs.Mode.BLOCKS);
        if (VoxelShapes.matchesAnywhere(volume, overhang, BooleanBiFunction.AND)
                || volume.getBoundingBoxes().stream().anyMatch(box -> StudioPlacement.rpBlocks(world, box.offset(pos), null)))
            result.setReturnValue(false);
    }
}
