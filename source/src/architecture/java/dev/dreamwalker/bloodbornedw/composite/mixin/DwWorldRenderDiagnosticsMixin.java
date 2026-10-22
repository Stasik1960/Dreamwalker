package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Sampled CPU preparation wall elapsed and actual thread CPU; no GPU duration inference. */
@Mixin(WorldRenderer.class)
public abstract class DwWorldRenderDiagnosticsMixin {
    @Unique private long dw$wallStart,dw$cpuStart;
    @Inject(method="render",at=@At("HEAD"))
    private void dw$before(CallbackInfo callback){dw$wallStart=DwClientDiagnostics.shouldSample("client-process",null,"world-render")?System.nanoTime():0;if(dw$wallStart!=0)dw$cpuStart=DwClientDiagnostics.threadCpuNs();}
    @Inject(method="render",at=@At("RETURN"))
    private void dw$after(CallbackInfo callback){if(dw$wallStart==0)return;DwClientDiagnostics.measured("world-render.wallElapsed.notGPU",System.nanoTime()-dw$wallStart);long cpu=DwClientDiagnostics.threadCpuNs();if(cpu>=0&&dw$cpuStart>=0)DwClientDiagnostics.measured("world-render.threadCPU.notGPU",cpu-dw$cpuStart);dw$wallStart=0;}
}
