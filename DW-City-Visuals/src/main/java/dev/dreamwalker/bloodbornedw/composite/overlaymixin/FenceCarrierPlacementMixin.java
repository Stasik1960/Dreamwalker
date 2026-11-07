package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeCellBlock.class, remap = false)
public abstract class FenceCarrierPlacementMixin {
    @Inject(method = "method_9616", at = @At("RETURN"), cancellable = true)
    private void dw$placeAboveFence(BlockState state, ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        var world = context.getWorld();
        var pos = context.getBlockPos();
        var entries = CompositeRuntime.contributions(world, pos);
        if (!entries.isEmpty() && entries.stream().allMatch(entry ->
                FencePlacement.overFence(entry, pos) || DebugAdjustments.blocksDisabled(world, entry.owner())
                || entry.owner().registryId().equals("bloodborne_dw:prototype_roof")
                && !entry.owner().root().equals(CompositeData.cell(pos)))) {
            cir.setReturnValue(true);
        }
    }
}
