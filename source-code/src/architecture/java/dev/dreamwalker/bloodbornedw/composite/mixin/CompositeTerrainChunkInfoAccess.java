package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets="net.minecraft.client.render.WorldRenderer$ChunkInfo")
public interface CompositeTerrainChunkInfoAccess {
    @Accessor("chunk") ChunkBuilder.BuiltChunk terrainChunk();
}
