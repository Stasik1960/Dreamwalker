package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.SodiumCompatibility;
import dev.dreamwalker.bloodbornedw.composite.SodiumShaderPatch;
import dev.dreamwalker.bloodbornedw.composite.SurfaceDepthShaders;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The material cutoff of zero otherwise lets fully transparent pixels write depth. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumTranslucentShaderMixin {
    @Unique private static final AtomicBoolean dw$warnedUnverified = new AtomicBoolean();

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$zeroAlpha(Identifier id, CallbackInfoReturnable<String> cir) {
        if (id.toString().equals("sodium:blocks/block_layer_opaque.vsh")) {
            cir.setReturnValue(SurfaceDepthShaders.sodium(cir.getReturnValue()));
            return;
        }
        if (!id.toString().equals("sodium:blocks/block_layer_opaque.fsh")) return;
        String versions = SodiumCompatibility.installed();
        if (!SodiumCompatibility.verifiedClient() && dw$warnedUnverified.compareAndSet(false, true)) {
            LoggerFactory.getLogger("DreamWalker").warn(
                    "Unverified graphics mod versions: {}. Validating the translucent shader structure; "
                    + "the verified baseline is Sodium 0.5.13, Indium 1.0.36 and Iris 1.7.6 for Minecraft 1.20.1.", versions);
        }
        cir.setReturnValue(SodiumShaderPatch.apply(cir.getReturnValue(), versions));
    }
}
