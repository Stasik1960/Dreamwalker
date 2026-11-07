package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import java.util.List;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlacementPhysics.class, remap = false)
public abstract class FencePlacementPhysicsMixin {
    private static final String CONFLICT = "conflict(Lnet/minecraft/class_1937;Ljava/util/List;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;Lnet/minecraft/class_1297;Lnet/minecraft/class_2338;)Ljava/lang/String;";

    @WrapOperation(method = CONFLICT, at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;nativeCollision(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;"))
    private static VoxelShape dw$nativeFence(BlockView view, BlockPos pos, ShapeContext context, Operation<VoxelShape> original) {
        return FencePlacement.nativeShape((World)view, pos, original.call(view, pos, context));
    }

    @WrapOperation(method = CONFLICT, at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;physicalParts(Ldev/dreamwalker/bloodbornedw/composite/CompositeData$Contribution;Lnet/minecraft/class_2338;)Ljava/util/List;"))
    private static List<ObjectGeometry.Box> dw$ownedFence(CompositeData.Contribution entry, BlockPos cell,
            Operation<List<ObjectGeometry.Box>> original, World world, List<Box> proposed,
            ObjectInstance.Owner excludeOwner, Entity excludeEntity, BlockPos replacingNative) {
        return FencePlacement.existingParts(world, entry, cell, original.call(entry, cell));
    }

    @WrapOperation(method = CONFLICT, at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornedw/architecture/PlacementPhysics;physicalBoxes(Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance;)Ljava/util/List;"))
    private static List<Box> dw$previousFence(ObjectInstance object, Operation<List<Box>> original,
            World world, List<Box> proposed, ObjectInstance.Owner excludeOwner, Entity excludeEntity, BlockPos replacingNative) {
        return FencePlacement.proposedBoxes(world, object, original.call(object));
    }
}
