package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.PlacementRotation;
import dev.dreamwalker.bloodbornedw.composite.ThinWindowRootBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Quantize the player facing before imported offsets and surface alignment. */
@Mixin(value = CompositeItem.class, remap = false)
public abstract class CompositePlacementRotationMixin {
    @Redirect(method = "placeInternal", at = @At(value = "INVOKE", target = "Ljava/lang/Math;round(F)I", remap = false), remap = false, require = 2)
    private int dw$rotation(float steps, ItemPlacementContext context) {
        return PlacementRotation.steps(steps, context);
    }

    @ModifyArg(method = "placeInternal", index = 1, at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeSurfacePlacement;align(Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;Lnet/minecraft/class_2680;Lnet/minecraft/class_2487;Lnet/minecraft/class_1750;)Ldev/dreamwalker/bloodbornedw/composite/CompositeSurfacePlacement$Placement;"))
    private BlockState dw$facePlayer(CompositeSpec spec, BlockState state, NbtCompound payload, ItemPlacementContext context) {
        if (state.getBlock() instanceof ThinWindowRootBlock && context.getSide().getAxis().isHorizontal()) return state;
        int rotation = PlacementRotation.fitModel(state.get(CompositeRootBlock.ROTATION), state);
        return state.with(CompositeRootBlock.ROTATION, rotation);
    }
}
