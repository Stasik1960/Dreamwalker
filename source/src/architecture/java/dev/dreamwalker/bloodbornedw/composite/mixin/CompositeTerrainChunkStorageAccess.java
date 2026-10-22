package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BuiltChunkStorage.class)
public interface CompositeTerrainChunkStorageAccess {
    @Accessor("chunks") ChunkBuilder.BuiltChunk[] terrainChunks();
}
