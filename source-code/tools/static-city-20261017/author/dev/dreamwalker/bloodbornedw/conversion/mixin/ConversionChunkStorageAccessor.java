package dev.dreamwalker.bloodbornedw.conversion.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Queue;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only converter diagnostics; no collection mutation or future scheduling. */
@Mixin(ThreadedAnvilChunkStorage.class)
public interface ConversionChunkStorageAccessor {
    @Accessor("currentChunkHolders")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> dw$currentChunkHolders();

    @Accessor("chunkHolders")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> dw$readableChunkHolders();

    @Accessor("chunksToUnload")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> dw$chunksToUnload();

    @Accessor("unloadTaskQueue")
    Queue<Runnable> dw$unloadTaskQueue();
}
