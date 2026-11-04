package dev.dreamwalker.editorqa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.editor.CityEditorClient;
import dev.dreamwalker.bloodbornedw.editor.EditorCells;
import dev.dreamwalker.editorqa.EditorRenderProbe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes editor appearance installation only in the disposable QA mod. */
@Mixin(value = CityEditorClient.class, remap = false)
public abstract class EditorUpdateProbeMixin {
    private static boolean relevant(MinecraftClient client, Chunk chunk) {
        if (!EditorRenderProbe.enabled() || client.world == null) return false;
        BlockPos target = EditorRenderProbe.target();
        return chunk.getPos().x == (target.getX() >> 4)
                && chunk.getPos().z == (target.getZ() >> 4);
    }

    private static int style(NativeVisualData data) {
        return ((RetainedFrameData) (Object) data).dw$editor().style(EditorRenderProbe.target());
    }

    private static void log(String stage, MinecraftClient client, String detail) {
        try {
            EditorRenderProbe.record(stage, EditorRenderProbe.target(), EditorRenderProbe.liveStyle(),
                    client.world, "generation=" + EditorRenderProbe.generation() + " " + detail);
        } catch (RuntimeException ignored) { }
    }

    @WrapOperation(method = "applyPending", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/NativeVisuals;data(Lnet/minecraft/class_2791;Ldev/dreamwalker/bloodbornedw/composite/NativeVisualData;)V"), remap = false)
    private static void install(Chunk chunk, NativeVisualData data, Operation<Void> original,
                               MinecraftClient client) {
        boolean observe = relevant(client, chunk);
        if (observe) {
            NativeVisualData previous = NativeVisuals.data(chunk);
            log("update_install_before", client, "installedStyle=" + style(previous)
                    + " incomingStyle=" + style(data));
        }
        try {
            original.call(chunk, data);
            if (observe) {
                NativeVisualData installed = NativeVisuals.data(chunk);
                log("update_install_success", client, "installedStyle=" + style(installed)
                        + " incomingStyle=" + style(data));
            }
        } catch (RuntimeException error) {
            if (observe) log("update_install_exception", client, error.toString());
            throw error;
        }
    }
}
