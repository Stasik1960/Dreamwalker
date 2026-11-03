package dev.dreamwalker.renderdiag.mixin;
import dev.dreamwalker.renderdiag.RenderDiag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="dev.dreamwalker.bloodbornedw.visual.FacadeModel",remap=false)
abstract class FacadeModelMixin {
    @Unique private boolean dwdiag$counted;
    @Inject(method="<init>",at=@At("RETURN"),remap=false) private void constructed(CallbackInfo ci) { if(RenderDiag.ENABLED&&!dwdiag$counted) { dwdiag$counted=true; RenderDiag.facade.increment(); } }
}
