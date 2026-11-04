package dev.dreamwalker.bloodbornedw.editor.mixin;

import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WrapperProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class EditorChunkSaveMixin {
    @Inject(method = "serialize", at = @At("HEAD"))
    private static void dw$saveQueuedAppearance(ServerWorld world, Chunk chunk, CallbackInfoReturnable<NbtCompound> cir) {
        if (chunk instanceof WrapperProtoChunk wrapper) chunk = wrapper.getWrappedChunk();
        if (chunk instanceof WorldChunk full) CityEditorServer.flushBeforeSave(full);
    }
}
