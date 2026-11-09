package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.composite.CompositeOverlapEdit;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlacementPhysics.class, remap = false)
public abstract class ForcedPlacementPhysicsMixin {
    @Inject(method = "conflict(Lnet/minecraft/class_1937;Ljava/util/List;Ldev/dreamwalker/bloodbornedw/runtime/ObjectInstance$Owner;Lnet/minecraft/class_1297;Lnet/minecraft/class_2338;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true)
    private static void dw$permitBlockIntersections(World world, List<Box> proposed, Owner excludedOwner,
            Entity excludedEntity, BlockPos replacing, CallbackInfoReturnable<String> result) {
        if (NoCollidePlacement.ignoringBlocks() && !CompositeOverlapEdit.inTransition()) result.setReturnValue(null);
    }

    @Inject(method = "entityConflict(Lnet/minecraft/class_1937;Ljava/util/List;Lnet/minecraft/class_1297;ZLjava/util/List;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true)
    private static void dw$permitEntityIntersections(World world, List<Box> proposed, Entity excluded,
            boolean initialSource, List<Box> previous, CallbackInfoReturnable<String> result) {
        if (NoCollidePlacement.ignoringBlocks() && !CompositeOverlapEdit.inTransition()) result.setReturnValue(null);
    }
}
