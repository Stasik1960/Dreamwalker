package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Additive per-owner shapes preserve the actual foreign block and actual foreign BE. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class CompositeShapeMixin {
    /** Static native states cache this overload and otherwise bypass the contextual overlay. */
    @Inject(method="getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;",at=@At("HEAD"),cancellable=true)
    private void bloodborne$cachedCollision(BlockView world,BlockPos pos,CallbackInfoReturnable<VoxelShape> result){
        BlockState state=(BlockState)(Object)this;
        if(!CompositeRuntime.overlay(state,world,pos,true).isEmpty()||!CompositeRuntime.overlay(state,world,pos,false).isEmpty())result.setReturnValue(state.getCollisionShape(world,pos,ShapeContext.absent()));
    }
    @Inject(method="getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",at=@At("HEAD"),cancellable=true)
    private void bloodborne$collision(BlockView world,BlockPos pos,ShapeContext context,CallbackInfoReturnable<VoxelShape> result){
        BlockState state=(BlockState)(Object)this;VoxelShape extra=CompositeRuntime.overlay(state,world,pos,true,context);
        if(!extra.isEmpty()||!CompositeRuntime.overlay(state,world,pos,false).isEmpty())result.setReturnValue(VoxelShapes.union(CompositeRuntime.nativeCollision(state,world,pos,context),extra));
    }
    @Inject(method="getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",at=@At("RETURN"),cancellable=true)
    private void bloodborne$selection(BlockView world,BlockPos pos,ShapeContext context,CallbackInfoReturnable<VoxelShape> result){VoxelShape extra=CompositeRuntime.overlay((BlockState)(Object)this,world,pos,false);if(!extra.isEmpty())result.setReturnValue(VoxelShapes.union(result.getReturnValue(),extra));}
}
