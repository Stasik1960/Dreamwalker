package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.NativeGridClient",remap=false)
abstract class FacadeNativeTerrainMixin {
    @Inject(method="terrainModel",at=@At("RETURN"),cancellable=true,remap=false)
    private static void dw$facades(BlockRenderView world,BlockState state,BlockPos pos,BakedModel fallback,CallbackInfoReturnable<BakedModel> cir) {
        cir.setReturnValue(CityFacadesClient.terrainModel(cir.getReturnValue(),pos,state,world));
    }
}
