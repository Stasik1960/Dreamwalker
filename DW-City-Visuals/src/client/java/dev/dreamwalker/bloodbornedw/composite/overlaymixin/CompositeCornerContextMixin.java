package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.CornerLightingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Both asynchronous prepared meshes and synchronous fallback use this renderer call. */
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh",remap=false)
public abstract class CompositeCornerContextMixin {
 @ModifyArgs(method={"bakePrepared","bake"},at=@At(value="INVOKE",target="Lnet/minecraft/class_778;method_3374(Lnet/minecraft/class_1920;Lnet/minecraft/class_1087;Lnet/minecraft/class_2680;Lnet/minecraft/class_2338;Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;ZLnet/minecraft/class_5819;JI)V",remap=false),remap=false)
 private static void dw$cornerContext(Args args){
  Object consumer=args.get(5),view=args.get(0);
  if(consumer instanceof CornerLightingContext context)context.dw$setCornerContext(view instanceof CompositeLightingViewAccess access?access:null);
 }
}
