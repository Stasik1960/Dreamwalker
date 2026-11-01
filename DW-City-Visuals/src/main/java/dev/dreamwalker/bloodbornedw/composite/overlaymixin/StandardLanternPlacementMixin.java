package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Canonical item mounting is chosen by the clicked surface, never saved item art. */
@Mixin(value = CompositeItem.class, remap = false)
public abstract class StandardLanternPlacementMixin {
    // Explicit production descriptor: mod method names are stable; Minecraft types are intermediary.
    @Redirect(method = "placeInternal", remap = false, at = @At(value = "INVOKE", remap = false, target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeSurfacePlacement;align(Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;Lnet/minecraft/class_2680;Lnet/minecraft/class_2487;Lnet/minecraft/class_1750;)Ldev/dreamwalker/bloodbornedw/composite/CompositeSurfacePlacement$Placement;"))
    @org.spongepowered.asm.mixin.injection.Coerce
    private Object dw$mount(CompositeSpec spec, BlockState state, NbtCompound payload, ItemPlacementContext context) {
        return StandardLanternMount.align(spec, state, payload, context);
    }
}
