package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.editor.EditorRenderScope;

import java.util.Map;
import net.minecraft.util.math.ChunkSectionPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class RetainedFrameSodiumTaskMixin {
    @Unique private Map<Integer, Integer> dw$frames = Map.of();
    @Unique private ChunkSectionPos dw$origin;
    @Unique private dev.dreamwalker.bloodbornedw.editor.EditorView dw$editorView;
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void dw$capture(@Coerce Object render, @Coerce Object context, int buildTime, CallbackInfo ci) {
        RetainedFrameSodiumSnapshot snapshot = (RetainedFrameSodiumSnapshot) context;
        dw$frames = snapshot.dw$frameSection(); dw$origin = snapshot.dw$frameOrigin();
        dw$editorView = snapshot;
    }
    @Inject(method = "execute", at = @At("HEAD"), remap = false)
    private void dw$beginEditorScope(@Coerce Object build, @Coerce Object token, CallbackInfoReturnable<?> ci) { EditorRenderScope.begin(dw$editorView); }
    @Inject(method = "execute", at = @At("RETURN"), remap = false)
    private void dw$endEditorScope(@Coerce Object build, @Coerce Object token, CallbackInfoReturnable<?> ci) { EditorRenderScope.end(); }
    @Inject(method = "execute", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
        target = "Lme/jellysquid/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses;ALL:[Lme/jellysquid/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;", remap = false), remap = false)
    private void dw$render(@Coerce Object build, @Coerce Object token, CallbackInfoReturnable<?> ci) {
        if (!dw$frames.isEmpty()) RetainedFrameSodiumEmitter.render(build, dw$origin, dw$frames);
    }
}


