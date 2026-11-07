package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeRuntime.class, remap = false)
abstract class CompositePlayerDebugCollisionMixin {
    @WrapOperation(method = "cellShape(Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;ZLnet/minecraft/class_2680;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;",
            at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/composite/CompositeRuntime;physicalParts(Ldev/dreamwalker/bloodbornedw/composite/CompositeData$Contribution;Lnet/minecraft/class_2338;)Ljava/util/List;"))
    private static List<ObjectGeometry.Box> dw$playerParts(CompositeData.Contribution entry, BlockPos cell,
            Operation<List<ObjectGeometry.Box>> original, BlockView view, BlockPos pos, boolean physical,
            BlockState state, ShapeContext context) {
        if (view instanceof World world && context instanceof EntityShapeContext entity
                && entity.getEntity() instanceof PlayerEntity && DebugAdjustments.playersDisabled(world, entry.owner())) {
            return List.of();
        }
        return original.call(entry, cell);
    }

    @Inject(method = "nativeCollision(Lnet/minecraft/class_2680;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;",
            at = @At("HEAD"), cancellable = true)
    private static void dw$nativePlayerCollision(BlockState state, BlockView view, BlockPos pos,
            ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (view instanceof World world && context instanceof EntityShapeContext entity
                && entity.getEntity() instanceof PlayerEntity && DebugAdjustments.playersDisabled(world, pos)) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
