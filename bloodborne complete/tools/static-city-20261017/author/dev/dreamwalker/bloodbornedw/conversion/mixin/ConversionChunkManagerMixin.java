package dev.dreamwalker.bloodbornedw.conversion.mixin;

import com.mojang.datafixers.util.Either;
import dev.dreamwalker.bloodbornedw.conversion.FullMapConversionAuthor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.WrapperProtoChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Counts native references without loading chunks, awaiting futures or clearing maps. */
@Mixin(ServerChunkManager.class)
public abstract class ConversionChunkManagerMixin implements FullMapConversionAuthor.ChunkControl {
    @Shadow @Final public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;
    @Shadow @Final Thread serverThread;
    @Shadow @Final private Chunk[] chunkCache;

    @Override
    public FullMapConversionAuthor.ChunkCounts dw$chunkCounts() {
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException("Conversion chunk diagnostics require the server thread");
        }
        ConversionChunkStorageAccessor storage = (ConversionChunkStorageAccessor) threadedAnvilChunkStorage;
        Set<ChunkHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        holders.addAll(storage.dw$currentChunkHolders().values());
        holders.addAll(storage.dw$readableChunkHolders().values());
        holders.addAll(storage.dw$chunksToUnload().values());
        Set<WorldChunk> worldChunks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Chunk cached : chunkCache) dw$inspectChunk(cached, worldChunks);
        List<ChunkStatus> statuses = ChunkStatus.createOrderedList();
        int pending = 0;
        for (ChunkHolder holder : holders) {
            boolean unfinished = dw$inspectFuture(holder.getSavingFuture(), worldChunks);
            for (ChunkStatus status : statuses) {
                unfinished |= dw$inspectFuture(holder.getFutureFor(status), worldChunks);
            }
            unfinished |= dw$inspectFuture(holder.getAccessibleFuture(), worldChunks);
            unfinished |= dw$inspectFuture(holder.getTickingFuture(), worldChunks);
            unfinished |= dw$inspectFuture(holder.getEntityTickingFuture(), worldChunks);
            if (unfinished) pending++;
        }
        return new FullMapConversionAuthor.ChunkCounts(worldChunks.size(),
                storage.dw$currentChunkHolders().size(), storage.dw$readableChunkHolders().size(),
                pending, storage.dw$chunksToUnload().size(), storage.dw$unloadTaskQueue().size());
    }

    @Unique
    private static boolean dw$inspectFuture(CompletableFuture<?> future, Set<WorldChunk> chunks) {
        if (future == null) throw new IllegalStateException("Missing native holder future");
        if (future.isCancelled() || future.isCompletedExceptionally()) {
            throw new IllegalStateException("Failed native holder future prevents a clean drain");
        }
        if (!future.isDone()) return true;
        Object value = future.getNow(null);
        if (value instanceof Either<?, ?> either) value = either.left().orElse(null);
        dw$inspectChunk(value, chunks);
        return false;
    }

    @Unique
    private static void dw$inspectChunk(Object value, Set<WorldChunk> chunks) {
        if (value instanceof WorldChunk chunk) chunks.add(chunk);
        else if (value instanceof WrapperProtoChunk wrapper) chunks.add(wrapper.getWrappedChunk());
    }
}
