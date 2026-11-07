package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallItem;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PrototypeWallItem.class, remap = false)
public abstract class FenceWallPlacementMixin {
    @WrapOperation(method = "method_7707", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/class_2680;method_26220(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)Lnet/minecraft/class_265;"))
    private VoxelShape dw$wallFence(BlockState state, BlockView view, BlockPos pos, Operation<VoxelShape> original) {
        VoxelShape shape = original.call(state, view, pos);
        return FencePlacement.isFence(state) ? FencePlacement.cap(shape) : shape;
    }
}
