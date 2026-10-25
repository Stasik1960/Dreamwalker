package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitecturePrelitContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.dreamwalker.bloodbornedw.composite.NativeAoDiagnostics;
import net.minecraft.util.math.MathHelper;
@Pseudo
@Mixin(targets="link.infra.indium.renderer.render.AbstractBlockRenderContext",remap=false)
public abstract class StaticArchitectureIndiumLightingMixin implements StaticArchitecturePrelitContext {
    @Unique private boolean dw$prelit;
    @Override public boolean staticArchitecturePrelit(){return dw$prelit;}
    @Override public void staticArchitecturePrelit(boolean value){dw$prelit=value;}
    @Inject(method="shadeQuad",at=@At("HEAD"),cancellable=true,remap=false)
    private void dw$keepSourceLight(CallbackInfo ci){if(dw$prelit)ci.cancel();}
    /** Indium's AO also extrapolates on preserved native whole faces. Its
     * multiplyRGB packs into bytes, so bound the normalized AO factor before
     * that conversion. Valid AO values and emissive lighting are unchanged. */
    @WrapOperation(method="shadeQuad",at=@At(value="INVOKE",target="Llink/infra/indium/renderer/helper/ColorHelper;multiplyRGB(IF)I"),remap=false)
    private int dw$saturateAo(int color,float value,Operation<Integer> original){
        float safe=MathHelper.clamp(value,0,1);NativeAoDiagnostics.observe(color,value,safe);
        return original.call(color,safe);
    }
}
