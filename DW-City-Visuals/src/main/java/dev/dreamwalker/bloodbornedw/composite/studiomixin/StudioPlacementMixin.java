package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StudioStaticShapes;
import dev.dreamwalker.bloodbornedw.composite.StudioOverhangs;
import dev.dreamwalker.bloodbornedw.composite.StudioPlacement;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlacementPhysics.class, remap = false)
public abstract class StudioPlacementMixin {
    @Inject(method = "conflict(Lnet/minecraft/class_1937;Ljava/util/List;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;Lnet/minecraft/class_1297;Lnet/minecraft/class_2338;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true)
    private static void dw$checkAuthoredRpReservations(World world, List<Box> proposed, Owner excludedOwner,
            Entity excludedEntity, BlockPos replacing, CallbackInfoReturnable<String> result) {
        if (result.getReturnValue() != null) return;
        for (var box : proposed) if (StudioPlacement.rpBlocks(world, box, excludedEntity)) {
            result.setReturnValue("solid_studio_rp_overlap");
            return;
        }
    }

    @WrapOperation(method = "conflict(Lnet/minecraft/class_1937;Ljava/util/List;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;Lnet/minecraft/class_1297;Lnet/minecraft/class_2338;)Ljava/lang/String;",
            at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;nativeCollision(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;"))
    private static VoxelShape dw$authoredStaticReservation(BlockView world, BlockPos cell, ShapeContext context,
            Operation<VoxelShape> original) {
        var state = world.getBlockState(cell);
        if (StaticArchitectureCatalog.contains(state)) {
            var shape = StudioStaticShapes.of(StaticArchitectureCatalog.recipe(state)).blocks();
            if (shape != null) return StudioOverhangs.augment(world, cell, shape, StudioOverhangs.Mode.BLOCKS);
        }
        return StudioOverhangs.augment(world, cell, original.call(world, cell, context), StudioOverhangs.Mode.BLOCKS);
    }
}
