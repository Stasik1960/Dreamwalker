package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock$CompactItem", remap = false)
public abstract class FenceCompactPlacementMixin {
    @WrapOperation(method = "method_7709", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2680;method_26194(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;"))
    private VoxelShape dw$compactFence(BlockState state, BlockView view, BlockPos pos, ShapeContext context,
            Operation<VoxelShape> original) {
        VoxelShape shape = original.call(state, view, pos, context);
        return FencePlacement.isFence(state) ? FencePlacement.cap(shape) : shape;
    }
}
