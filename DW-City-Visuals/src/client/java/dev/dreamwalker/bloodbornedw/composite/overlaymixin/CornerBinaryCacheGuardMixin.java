package dev.dreamwalker.bloodbornedw.composite.overlaymixin;
import dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScope;
import dev.dreamwalker.bloodbornedw.composite.BinaryQuadAlpha;
import net.minecraft.client.render.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BinaryQuadAlpha.class,remap=false)
public abstract class CornerBinaryCacheGuardMixin {
 @Inject(method="binary",at=@At("HEAD"),cancellable=true,remap=false)
 private static void dw$parentQuad(BakedQuad quad,CallbackInfoReturnable<Boolean> ci){
  Object parent=CornerQuadParentScope.parentFor(quad);
  if(parent instanceof BakedQuad original&&original!=quad)ci.setReturnValue(BinaryQuadAlpha.binary(original));
 }
}
