package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticGrassMasonryRenderView;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native brightness caching keys positions without the source view's root.
 * This view alone computes fresh values; native cache data/lifecycle is untouched. */
@Mixin(targets="net.minecraft.client.render.block.BlockModelRenderer$BrightnessCache")
public abstract class StaticGrassMasonryBrightnessMixin {
    @Inject(method="getFloat",at=@At("HEAD"),cancellable=true)
    private void dw$staticGrassFreshAo(BlockState state,BlockRenderView world,BlockPos pos,CallbackInfoReturnable<Float> result){
        if(world instanceof StaticGrassMasonryRenderView)result.setReturnValue(state.getAmbientOcclusionLightLevel(world,pos));
    }
    @Inject(method="getInt",at=@At("HEAD"),cancellable=true)
    private void dw$staticGrassFreshLight(BlockState state,BlockRenderView world,BlockPos pos,CallbackInfoReturnable<Integer> result){
        if(world instanceof StaticGrassMasonryRenderView)result.setReturnValue(WorldRenderer.getLightmapCoordinates(world,state,pos));
    }
}
