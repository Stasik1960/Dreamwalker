package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRenderView;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A position alone cannot key differently projected source-part views.
 * Evaluate only this view freshly; native cache contents/lifecycle stay intact. */
@Mixin(targets="net.minecraft.client.render.block.BlockModelRenderer$BrightnessCache")
public abstract class StaticArchitectureBrightnessMixin {
    @Inject(method="getFloat",at=@At("HEAD"),cancellable=true)
    private void dw$staticArchitectureAo(BlockState state,BlockRenderView world,BlockPos pos,CallbackInfoReturnable<Float> result){if(world instanceof StaticArchitectureRenderView)result.setReturnValue(state.getAmbientOcclusionLightLevel(world,pos));}
    @Inject(method="getInt",at=@At("HEAD"),cancellable=true)
    private void dw$staticArchitectureLight(BlockState state,BlockRenderView world,BlockPos pos,CallbackInfoReturnable<Integer> result){if(world instanceof StaticArchitectureRenderView)result.setReturnValue(WorldRenderer.getLightmapCoordinates(world,state,pos));}
}
