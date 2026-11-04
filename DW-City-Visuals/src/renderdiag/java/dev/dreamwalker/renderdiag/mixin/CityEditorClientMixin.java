package dev.dreamwalker.renderdiag.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.editor.CityEditorClient;
import dev.dreamwalker.renderdiag.RenderDiag;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.editor.CityEditorClient", remap = false)
abstract class CityEditorClientMixin {
    @WrapOperation(method = "applyPending", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/NativeVisuals;data(Lnet/minecraft/class_2791;Ldev/dreamwalker/bloodbornedw/composite/NativeVisualData;)V"), remap = false)
    private static void update(Chunk chunk, NativeVisualData data, Operation<Void> original,
                               MinecraftClient client) {
        original.call(chunk, data);
        if (!RenderDiag.ENABLED) return;
        int size = ((RetainedFrameData) (Object) data).dw$editor().size();
        (size == 0 ? RenderDiag.updateEmpty : RenderDiag.updateFull).increment();
    }
}
