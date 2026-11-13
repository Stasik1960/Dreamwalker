package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import dev.dreamwalker.bloodbornedw.building.ConnectedFences;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompositeRootBlock.class)
abstract class DistinctFormPickMixin {
    @Inject(method = "art", at = @At("RETURN"), cancellable = true, remap = false)
    private void dreamwalker$pickFormItem(BlockState state, NbtCompound payload, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack connectedFence = ConnectedFences.forState(state);
        if (!connectedFence.isEmpty()) {
            cir.setReturnValue(connectedFence);
            return;
        }
        ItemStack original = cir.getReturnValue();
        ItemStack form = DistinctForms.forArt(original);
        if (form != original) cir.setReturnValue(form);
    }
}
