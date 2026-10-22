package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Each vanilla attachment pass publishes one complete immutable revision.
 * The finally blocks balance even failed NBT/attachment callbacks. No FULL
 * lookup is made while the chunk's own future is still being completed. */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class CompositeServerChunkPublicationMixin {
    @Redirect(method="method_17227",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;loadEntities()V"),require=1)
    private void dreamwalker$loadEntities(WorldChunk chunk){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());try(var decode=CompositeData.openDecodeScope()){chunk.loadEntities();}finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
    @Redirect(method="method_17227",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;updateAllBlockEntities()V"),require=1)
    private void dreamwalker$attachTickers(WorldChunk chunk){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());try(var decode=CompositeData.openDecodeScope()){chunk.updateAllBlockEntities();}finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
    @Redirect(method="method_18711",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;runPostProcessing()V"),require=1)
    private void dreamwalker$postProcess(WorldChunk chunk){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());try(var decode=CompositeData.openDecodeScope()){chunk.runPostProcessing();}finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
}
