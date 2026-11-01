package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The material cutoff of zero otherwise lets fully transparent pixels write depth. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class SodiumTranslucentShaderMixin {
    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dw$zeroAlpha(Identifier id, CallbackInfoReturnable<String> cir) {
        if (!id.toString().equals("sodium:blocks/block_layer_opaque.fsh")) return;
        String source = cir.getReturnValue();
        String anchor = "    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);";
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, first + anchor.length()) >= 0)
            throw new IllegalStateException("Unsupported Sodium terrain fragment shader");
        cir.setReturnValue(source.replace(anchor, anchor + "\n#ifdef DW_TRANSLUCENT_PASS\n"
                + "    if (diffuseColor.a <= 0.0) discard;\n#endif"));
    }
}
