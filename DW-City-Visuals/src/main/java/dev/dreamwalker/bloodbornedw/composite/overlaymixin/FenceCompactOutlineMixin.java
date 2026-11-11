package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import dev.dreamwalker.bloodbornedw.composite.FencePlacement;
import dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The invisible upper half of a fence collider is not a selectable surface. */
@Mixin(value = ImportedCompactBlock.class, remap = false)
public abstract class FenceCompactOutlineMixin {
    @Unique private static final Map<VoxelShape, VoxelShape> dw$fenceOutlines = new ConcurrentHashMap<>();
    @Inject(method = "method_9549", at = @At("RETURN"), cancellable = true, remap = false)
    private void dw$fenceMovement(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                  CallbackInfoReturnable<VoxelShape> cir) {
        if (!ImportedCompactBlock.compact(state)) return;
        BlockState wall = AutoStoneFence.canonicalWall(state);
        if (wall != null) cir.setReturnValue(wall.getCollisionShape(world, pos, context));
        else if (AutoStoneFence.fenceLike(state)) cir.setReturnValue(FencePlacement.movement(cir.getReturnValue()));
    }

    @Inject(method = "method_9530", at = @At("RETURN"), cancellable = true, remap = false)
    private void dw$clipFenceOutline(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                      CallbackInfoReturnable<VoxelShape> cir) {
        if (!ImportedCompactBlock.compact(state) || !AutoStoneFence.matches(state)) return;
        BlockState wall = AutoStoneFence.canonicalWall(state);
        if (wall != null) { cir.setReturnValue(wall.getOutlineShape(world, pos, context)); return; }
        VoxelShape shape = cir.getReturnValue();
        VoxelShape clipped = dw$fenceOutlines.get(shape);
        if (clipped == null) {
            clipped = VoxelShapes.combineAndSimplify(shape, VoxelShapes.fullCube(), BooleanBiFunction.AND);
            if (dw$fenceOutlines.size() < 1024) {
                VoxelShape existing = dw$fenceOutlines.putIfAbsent(shape, clipped);
                if (existing != null) clipped = existing;
            }
        }
        cir.setReturnValue(clipped);
    }
}
