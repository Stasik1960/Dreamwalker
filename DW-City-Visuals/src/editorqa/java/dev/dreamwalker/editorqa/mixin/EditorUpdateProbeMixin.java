package dev.dreamwalker.editorqa.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.editor.CityEditorClient;
import dev.dreamwalker.bloodbornedw.editor.EditorCells;
import dev.dreamwalker.editorqa.EditorRenderProbe;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the actual UPDATE path only in the disposable QA mod. */
@Mixin(value = CityEditorClient.class, remap = false)
public abstract class EditorUpdateProbeMixin {
    private static final String RUN = "lambda$onInitializeClient$2(Lnet/minecraft/class_310;Lnet/minecraft/class_634;Lnet/minecraft/class_2960;IILnet/minecraft/class_2487;)V";

    private static boolean relevant(MinecraftClient client, Identifier dimension, int x, int z) {
        if (!EditorRenderProbe.enabled()) return false;
        BlockPos target = EditorRenderProbe.target();
        return x == (target.getX() >> 4) && z == (target.getZ() >> 4)
            && client.world != null && client.world.getRegistryKey().getValue().equals(dimension);
    }

    private static void log(String stage, MinecraftClient client, String detail) {
        try {
            EditorRenderProbe.record(stage, EditorRenderProbe.target(), EditorRenderProbe.liveStyle(),
                client.world, "generation=" + EditorRenderProbe.generation() + " " + detail);
        } catch (RuntimeException ignored) { }
    }

    private static int style(EditorCells cells) {
        return cells.style(EditorRenderProbe.target());
    }

    @Inject(method = "lambda$onInitializeClient$3(Lnet/minecraft/class_310;Lnet/minecraft/class_634;Lnet/minecraft/class_2540;Lnet/fabricmc/fabric/api/networking/v1/PacketSender;)V", at = @At("HEAD"), remap = false)
    private static void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender, CallbackInfo ci) {
        if (EditorRenderProbe.enabled()) log("update_receive", client, "bytes=" + buf.readableBytes());
    }

    @Inject(method = RUN, at = @At("HEAD"), remap = false)
    private static void run(MinecraftClient client, ClientPlayNetworkHandler handler, Identifier dimension, int x, int z, NbtCompound packet, CallbackInfo ci) {
        if (!EditorRenderProbe.enabled()) return;
        BlockPos target = EditorRenderProbe.target();
        if (x != (target.getX() >> 4) || z != (target.getZ() >> 4)) return;
        log("update_run", client, "dimension=" + dimension + " worldDimension="
            + (client.world == null ? "null" : client.world.getRegistryKey().getValue())
            + " handlerMatch=" + (client.getNetworkHandler() == handler) + " worldPresent=" + (client.world != null));
    }

    @WrapOperation(method = RUN, at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/editor/EditorCells;readPacked(Lnet/minecraft/class_2487;Lnet/minecraft/class_1923;II)Ldev/dreamwalker/bloodbornedw/editor/EditorCells;"), remap = false)
    private static EditorCells read(NbtCompound nbt, ChunkPos pos, int bottom, int top, Operation<EditorCells> original, MinecraftClient client, ClientPlayNetworkHandler handler, Identifier dimension, int x, int z, NbtCompound packet) {
        boolean observe = relevant(client, dimension, x, z);
        if (observe) log("update_read_before", client, "bottom=" + bottom + " top=" + top);
        try {
            EditorCells result = original.call(nbt, pos, bottom, top);
            if (observe) log("update_read_success", client, "size=" + result.size() + " newStyle=" + style(result));
            return result;
        } catch (RuntimeException e) { if (observe) log("update_read_exception", client, e.toString()); throw e; }
    }

    @WrapOperation(method = RUN, at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/editor/CityEditorClient;changedSections(Ldev/dreamwalker/bloodbornedw/editor/EditorCells;Ldev/dreamwalker/bloodbornedw/editor/EditorCells;)Ljava/util/Set;"), remap = false)
    private static Set<?> changed(EditorCells before, EditorCells after, Operation<Set<?>> original, MinecraftClient client, ClientPlayNetworkHandler handler, Identifier dimension, int x, int z, NbtCompound packet) {
        boolean observe = relevant(client, dimension, x, z);
        if (observe) log("update_changed_before", client, "oldStyle=" + style(before) + " newStyle=" + style(after));
        try {
            Set<?> result = original.call(before, after);
            if (observe) log("update_changed_success", client, "sections=" + result);
            return result;
        } catch (RuntimeException e) { if (observe) log("update_changed_exception", client, e.toString()); throw e; }
    }

    @WrapOperation(method = RUN, at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/composite/RetainedFrameData;dw$withEditor(Ldev/dreamwalker/bloodbornedw/editor/EditorCells;)Ldev/dreamwalker/bloodbornedw/composite/NativeVisualData;"), remap = false)
    private static NativeVisualData replacement(RetainedFrameData data, EditorCells cells, Operation<NativeVisualData> original, MinecraftClient client, ClientPlayNetworkHandler handler, Identifier dimension, int x, int z, NbtCompound packet) {
        boolean observe = relevant(client, dimension, x, z);
        if (observe) log("update_replacement_before", client, "newStyle=" + style(cells));
        try {
            NativeVisualData result = original.call(data, cells);
            if (observe) log("update_replacement_success", client, "same=" + ((Object) result == data));
            return result;
        } catch (RuntimeException e) { if (observe) log("update_replacement_exception", client, e.toString()); throw e; }
    }

    private static String installed(Chunk chunk) {
        try { return "installedStyle=" + style(((RetainedFrameData)(Object)NativeVisuals.data(chunk)).dw$editor()); }
        catch (RuntimeException e) { return "installedStyleError=" + e; }
    }

    @WrapOperation(method = RUN, at = @At(value = "INVOKE", target = "Ldev/dreamwalker/bloodbornedw/composite/NativeVisuals;data(Lnet/minecraft/class_2791;Ldev/dreamwalker/bloodbornedw/composite/NativeVisualData;)V"), remap = false)
    private static void install(Chunk chunk, NativeVisualData data, Operation<Void> original, MinecraftClient client, ClientPlayNetworkHandler handler, Identifier dimension, int x, int z, NbtCompound packet) {
        boolean observe = relevant(client, dimension, x, z);
        if (observe) log("update_install_before", client, installed(chunk));
        try {
            original.call(chunk, data);
            if (observe) log("update_install_success", client, installed(chunk));
        } catch (RuntimeException e) { if (observe) log("update_install_exception", client, e.toString()); throw e; }
    }

    @WrapOperation(method = RUN, at = @At(value = "INVOKE", target = "Lnet/minecraft/class_761;method_18146(IIIIII)V"), remap = false)
    private static void schedule(WorldRenderer renderer, int x1, int y1, int z1, int x2, int y2, int z2, Operation<Void> original, MinecraftClient client, ClientPlayNetworkHandler handler, Identifier dimension, int x, int z, NbtCompound packet) {
        boolean observe = relevant(client, dimension, x, z);
        String bounds = x1 + "," + y1 + "," + z1 + ".." + x2 + "," + y2 + "," + z2;
        if (observe) log("update_schedule_before", client, bounds);
        try {
            original.call(renderer, x1, y1, z1, x2, y2, z2);
            if (observe) log("update_schedule_success", client, bounds);
        } catch (RuntimeException e) { if (observe) log("update_schedule_exception", client, bounds + " " + e); throw e; }
    }
}
