package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CompositeRuntime.class, remap = false)
public abstract class FenceRuntimePlacementMixin {
    @WrapOperation(method = "lambda$checks$8", at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/architecture/PlacementPhysics;physicalBoxes(Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance;)Ljava/util/List;"))
    private static List<Box> dw$proposedFence(ObjectInstance object, Operation<List<Box>> original, PlayerEntity player,
            ServerWorld world, CompositeRootBlock block, ObjectInstance requested, ObjectInstance proposed,
            TransactionCore.ReadView reads) {
        return FencePlacement.proposedBoxes(world, object, original.call(object));
    }

    @WrapOperation(method = "topHeight", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2680;method_26220(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;)Lnet/minecraft/class_265;"))
    private static VoxelShape dw$nativeSupport(BlockState state, BlockView view, BlockPos pos, Operation<VoxelShape> original) {
        VoxelShape shape = original.call(state, view, pos);
        return FencePlacement.isFence(state) ? FencePlacement.cap(shape) : shape;
    }

    @WrapOperation(method = "topHeight", at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/composite/ImportedPhysicalShapes;physical(Ldev/dreamwalker/bloodbornedw/composite/CompositeData$Contribution;Lnet/minecraft/class_2338;)Lnet/minecraft/class_265;"))
    private static VoxelShape dw$ownedSupport(CompositeData.Contribution entry, BlockPos pos, Operation<VoxelShape> original) {
        return FencePlacement.overFence(entry, pos) ? VoxelShapes.empty() : original.call(entry, pos);
    }
}
