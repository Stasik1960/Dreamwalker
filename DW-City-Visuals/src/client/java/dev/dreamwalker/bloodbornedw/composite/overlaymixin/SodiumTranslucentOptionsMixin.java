package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import java.lang.reflect.InvocationTargetException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extend Sodium's existing option builder only for its reverse translucent pass. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions", remap = false)
public abstract class SodiumTranslucentOptionsMixin {
    @Redirect(method = "constants", at = @At(value = "INVOKE",
        target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderConstants$Builder;add(Ljava/lang/String;)V",
        ordinal = 1, remap = false), remap = false)
    private void dw$translucentFlag(@Coerce Object builder, String original) {
        try {
            var add = builder.getClass().getMethod("add", String.class);
            add.invoke(builder, original);
            Object pass = getClass().getMethod("pass").invoke(this);
            if ((Boolean)pass.getClass().getMethod("isReverseOrder").invoke(pass))
                add.invoke(builder, "DW_TRANSLUCENT_PASS");
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause() : failure;
            throw new IllegalStateException("Unable to configure Sodium translucent shader", cause);
        }
    }
}
