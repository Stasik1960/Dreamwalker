package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures the actual server tick, including Fabric END listeners. */
@Mixin(MinecraftServer.class)
public abstract class DiagnosticsTickMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void dreamwalker$diagnosticTickStart(BooleanSupplier shouldKeepTicking,CallbackInfo ci){DwDiagnostics.serverTickStart((MinecraftServer)(Object)this);}
    @Inject(method="tick",at=@At("RETURN"))
    private void dreamwalker$diagnosticTickEnd(BooleanSupplier shouldKeepTicking,CallbackInfo ci){DwDiagnostics.serverTickEnd((MinecraftServer)(Object)this);}
}
