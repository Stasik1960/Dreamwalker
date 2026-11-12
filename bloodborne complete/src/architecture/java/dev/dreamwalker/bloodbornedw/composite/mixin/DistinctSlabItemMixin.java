package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.MaterialSlabs$Slab")
abstract class DistinctSlabItemMixin {
    @Inject(method = {"getPickStack", "method_9574"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void dreamwalker$pickExactSlab(BlockView world, BlockPos pos, BlockState state, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack form = DistinctForms.slabPick(state);
        if (!form.isEmpty()) cir.setReturnValue(form);
    }

    @Inject(method = {"getDroppedStacks", "method_9560"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void dreamwalker$dropOneFixedSlab(BlockState state, LootContextParameterSet.Builder builder,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        if (DistinctForms.fixed(state.getBlock())) cir.setReturnValue(List.of(new ItemStack((Block) (Object) this)));
    }
}
