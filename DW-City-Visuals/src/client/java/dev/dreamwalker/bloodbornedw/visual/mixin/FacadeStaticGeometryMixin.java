package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.StaticArchitectureGeometry", remap=false)
abstract class FacadeStaticGeometryMixin {
    @Inject(method="visible",at=@At("RETURN"),cancellable=true,remap=false)
    private static void dw$facades(StaticArchitectureCatalog.Pose pose,int index,BlockRenderView world,BlockPos root,CallbackInfoReturnable<BakedModel> cir) {
        var partModel=pose.parts().get(index).model();
        cir.setReturnValue(CityFacadesClient.visibleModel(cir.getReturnValue(),root,pose.state(),world,
                CityFacadesClient.contextualPart(partModel),partModel));
    }
}

