package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.SurfaceDepthShaders;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gl.GlImportProcessor;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderProgram.class)
public abstract class CompositePriorityShaderMixin {
    @WrapOperation(method = "method_34579", remap = false, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/class_281;method_1283(Lnet/minecraft/class_281$class_282;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lnet/minecraft/class_5913;)Lnet/minecraft/class_281;", remap = false))
    private static ShaderStage dw$depthPriority(ShaderStage.Type type, String name, InputStream source,
                                                String pack, GlImportProcessor imports, Operation<ShaderStage> original)
            throws IOException {
        if (type != ShaderStage.Type.VERTEX || !name.equals("rendertype_cutout")) {
            return original.call(type, name, source, pack, imports);
        }
        String text = new String(source.readAllBytes(), StandardCharsets.UTF_8);
        byte[] patched = SurfaceDepthShaders.composite(text).getBytes(StandardCharsets.UTF_8);
        return original.call(type, name, new ByteArrayInputStream(patched), pack, imports);
    }
}
