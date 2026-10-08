package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the real vanilla save boundary; callbacks do not write to the world. */
@Mixin(MinecraftServer.class)
public abstract class DiagnosticsSaveMixin {
    @Inject(method="save",at=@At("HEAD"))
    private void dreamwalker$saveBegin(boolean suppressLogs,boolean flush,boolean force,CallbackInfoReturnable<Boolean> cir){DwDiagnostics.saved((MinecraftServer)(Object)this,"begin",flush,force);}
    @Inject(method="save",at=@At("RETURN"))
    private void dreamwalker$saveEnd(boolean suppressLogs,boolean flush,boolean force,CallbackInfoReturnable<Boolean> cir){DwDiagnostics.saved((MinecraftServer)(Object)this,"end; nativeResult="+cir.getReturnValue(),flush,force);}
}
