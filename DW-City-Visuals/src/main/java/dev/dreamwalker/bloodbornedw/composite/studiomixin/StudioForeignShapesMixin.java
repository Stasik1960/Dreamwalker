package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.composite.StudioOverhangs;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class StudioForeignShapesMixin {
    @WrapMethod(method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape dw$addStudioPlayerOverhang(BlockView world, BlockPos cell, ShapeContext context,
            Operation<VoxelShape> original) {
        var shape = original.call(world, cell, context);
        if (context instanceof EntityShapeContext entity && entity.getEntity() instanceof PlayerEntity)
            return StudioOverhangs.augment(world, cell, shape, StudioOverhangs.Mode.PLAYERS);
        return shape;
    }

    @WrapMethod(method = "getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;")
    private VoxelShape dw$addStudioOutline(BlockView world, BlockPos cell, ShapeContext context,
            Operation<VoxelShape> original) {
        return StudioOverhangs.augment(world, cell, original.call(world, cell, context), StudioOverhangs.Mode.OUTLINE);
    }
}
