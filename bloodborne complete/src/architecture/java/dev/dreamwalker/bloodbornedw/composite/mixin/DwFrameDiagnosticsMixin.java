package dev.dreamwalker.bloodbornedw.composite.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Actual presentation interval; includes waiting/VSync and does not time GPU execution. */
@Mixin(value=RenderSystem.class,remap=false)
public abstract class DwFrameDiagnosticsMixin {
    @Inject(method="flipFrame",at=@At("HEAD"),remap=false)
    private static void dw$present(long window,CallbackInfo callback){DwClientDiagnostics.frame();}
}
