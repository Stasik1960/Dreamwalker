package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StudioStaticShapes;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StaticArchitectureBlock.class)
public abstract class StudioStaticBlockMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void dw$authoredPlayerShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
            CallbackInfoReturnable<VoxelShape> result) {
        if (!(context instanceof EntityShapeContext entity) || !(entity.getEntity() instanceof PlayerEntity)) return;
        var shape = StudioStaticShapes.of(StaticArchitectureCatalog.recipe(state)).players();
        if (shape != null) result.setReturnValue(shape);
    }
}
