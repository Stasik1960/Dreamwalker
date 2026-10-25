package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Contextual optical union only at an existing BE with NativeGridBase. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class NativeGridBridgeStateMixin {
    private static NativeGridCatalog.Optics optics(BlockView world,BlockPos pos){int index=NativeGridBridge.base(world,pos);return index<0?null:NativeGridCatalog.cell(index).optics();}
    @Inject(method="getRenderType",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeRenderType(CallbackInfoReturnable<BlockRenderType> result){
        if(NativeGridBridge.carrier((BlockState)(Object)this))result.setReturnValue(BlockRenderType.MODEL);
    }
    @Inject(method="getOpacity",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeOpacity(BlockView world,BlockPos pos,CallbackInfoReturnable<Integer> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(Math.max(result.getReturnValue(),base.opacity()));}
    @Inject(method="getAmbientOcclusionLightLevel",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeAo(BlockView world,BlockPos pos,CallbackInfoReturnable<Float> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(Math.min(result.getReturnValue(),base.ambientOcclusion()));}
    @Inject(method="isTransparent",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeTransparent(BlockView world,BlockPos pos,CallbackInfoReturnable<Boolean> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(result.getReturnValue()&&base.transparent());}
    @Inject(method="isOpaqueFullCube",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeFullCube(BlockView world,BlockPos pos,CallbackInfoReturnable<Boolean> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(result.getReturnValue()||base.opaqueFullCube());}
    @Inject(method="isSolidBlock",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeSolid(BlockView world,BlockPos pos,CallbackInfoReturnable<Boolean> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(result.getReturnValue()||base.solidBlock());}
    @Inject(method="shouldSuffocate",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeSuffocate(BlockView world,BlockPos pos,CallbackInfoReturnable<Boolean> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(result.getReturnValue()||base.suffocates());}
    @Inject(method="shouldBlockVision",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeVision(BlockView world,BlockPos pos,CallbackInfoReturnable<Boolean> result){var base=optics(world,pos);if(base!=null)result.setReturnValue(result.getReturnValue()||base.blockVision());}
    @Inject(method="getCullingShape",at=@At("RETURN"),cancellable=true)
    private void dw$bridgeCulling(BlockView world,BlockPos pos,CallbackInfoReturnable<VoxelShape> result){int index=NativeGridBridge.base(world,pos);if(index>=0)result.setReturnValue(VoxelShapes.union(result.getReturnValue(),NativeGridCatalog.cell(index).culling()));}
}
