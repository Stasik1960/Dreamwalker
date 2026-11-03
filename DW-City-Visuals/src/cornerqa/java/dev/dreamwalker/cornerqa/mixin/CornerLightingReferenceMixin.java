package dev.dreamwalker.cornerqa.mixin;
import dev.dreamwalker.bloodbornedw.visual.CornerLightingQa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight",remap=false)
public abstract class CornerLightingReferenceMixin {
 @Inject(method="sample",at=@At("HEAD"),cancellable=true,remap=false)
 private void dw$reference(net.minecraft.client.render.model.BakedQuad quad, dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess view, int[] nativeLights, CallbackInfoReturnable<Boolean> ci){if(!CornerLightingQa.corrected() && (Object)view instanceof dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRenderView)ci.setReturnValue(false);}
}
