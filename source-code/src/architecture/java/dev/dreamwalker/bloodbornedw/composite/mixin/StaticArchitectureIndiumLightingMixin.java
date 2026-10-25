package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitecturePrelitContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="link.infra.indium.renderer.render.AbstractBlockRenderContext",remap=false)
public abstract class StaticArchitectureIndiumLightingMixin implements StaticArchitecturePrelitContext {
    @Unique private boolean dw$prelit;
    @Override public boolean staticArchitecturePrelit(){return dw$prelit;}
    @Override public void staticArchitecturePrelit(boolean value){dw$prelit=value;}
    @Inject(method="shadeQuad",at=@At("HEAD"),cancellable=true,remap=false)
    private void dw$keepSourceLight(CallbackInfo ci){if(dw$prelit)ci.cancel();}
}
