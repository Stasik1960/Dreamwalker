package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.building.ShutterFloorPlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompositeItem.class, remap = false)
abstract class ShutterFloorPlacementMixin {
    @WrapOperation(method = "placeInternal", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/ImportedSourceLightingV127;manualPlacementPayload(Ldev/dreamwalker/bloodbornedw/composite/CompositeSpec;Lnet/minecraft/class_2680;Lnet/minecraft/class_2487;)Lnet/minecraft/class_2487;"))
    private NbtCompound dreamwalker$centerShutterOnFloor(CompositeSpec spec, BlockState state, NbtCompound payload,
            Operation<NbtCompound> original, ItemPlacementContext context) {
        return ShutterFloorPlacement.forPlacement(spec, context.getSide(), original.call(spec, state, payload));
    }
}
