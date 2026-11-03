package dev.dreamwalker.cornerqa.mixin;

import dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import dev.dreamwalker.bloodbornedw.visual.CornerTargetSamplerDiagnostic;
import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** QA-only observation of the actual sampler; does not alter the result. */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight",remap=false)
public abstract class CornerLightingProbeMixin {
    @Inject(method="sample",at=@At("RETURN"),remap=false)
    private void dw$record(BakedQuad quad,StaticLightingViewAccess view,int[] lights,CallbackInfoReturnable<Boolean> ci){
        CornerTargetSamplerDiagnostic.capture((CornerSurfaceLight)(Object)this,quad,view,lights,ci.getReturnValueZ(),false);
    }
    @Inject(method="sampleQualified",at=@At("RETURN"),remap=false)
    private void dw$recordChild(BakedQuad quad,StaticLightingViewAccess view,int[] lights,CallbackInfoReturnable<Boolean> ci){
        CornerTargetSamplerDiagnostic.capture((CornerSurfaceLight)(Object)this,quad,view,lights,ci.getReturnValueZ(),true);
    }
}
