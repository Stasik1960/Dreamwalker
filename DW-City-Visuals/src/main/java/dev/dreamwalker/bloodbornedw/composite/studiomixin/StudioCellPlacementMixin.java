package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeCellBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.StudioFootprints;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompositeCellBlock.class)
public abstract class StudioCellPlacementMixin {
    @Inject(method = "canReplace", at = @At("RETURN"), cancellable = true)
    private void dw$onlyReserveAuthoredBlockVolumes(BlockState state, ItemPlacementContext context,
            CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) return;
        var entries = CompositeRuntime.contributions(context.getWorld(), context.getBlockPos());
        if (entries.isEmpty()) return;
        for (var entry : entries) {
            var geometry = StudioFootprints.dynamic(entry.rootData());
            if (geometry == null || geometry.blocks() == null || !entry.shape().collision().isEmpty()) return;
        }
        result.setReturnValue(true);
    }
}
