package dev.dreamwalker.cornerqa.mixin;

import dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLight;
import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import dev.dreamwalker.bloodbornedw.visual.CornerLightingQa;
import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** CornerLight1 reference had no recorded-composite correction. */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeCornerLighting",remap=false)
public abstract class CompositeCornerReferenceMixin {
 @Inject(method="sample",at=@At("HEAD"),cancellable=true,remap=false)
 private static void dw$reference(CornerSurfaceLight sampler,BakedQuad quad,StaticLightingViewAccess view,int[] light,CallbackInfoReturnable<Boolean> ci){
  if(!CornerLightingQa.corrected())ci.setReturnValue(false);
 }
}
