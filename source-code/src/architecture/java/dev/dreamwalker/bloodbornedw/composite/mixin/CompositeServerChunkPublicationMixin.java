package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.wrapoperation.*;

/** Each vanilla attachment pass publishes one complete immutable revision.
 * The finally blocks balance even failed NBT/attachment callbacks. No FULL
 * lookup is made while the chunk's own future is still being completed. */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class CompositeServerChunkPublicationMixin {
    @WrapOperation(method="method_17227",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;loadEntities()V"),require=1)
    private void dreamwalker$loadEntities(WorldChunk chunk,Operation<Void> original){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());try(var decode=CompositeData.openDecodeScope()){original.call(chunk);}finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
    @WrapOperation(method="method_17227",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;updateAllBlockEntities()V"),require=1)
    private void dreamwalker$attachTickers(WorldChunk chunk,Operation<Void> original){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());try(var decode=CompositeData.openDecodeScope()){original.call(chunk);}finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
    @WrapOperation(method="method_18711",at=@At(value="INVOKE",target="Lnet/minecraft/world/chunk/WorldChunk;runPostProcessing()V"),require=1)
    private void dreamwalker$postProcess(WorldChunk chunk,Operation<Void> original){
        CompositeShapeSnapshots.beginBatch(chunk.getWorld());try(var decode=CompositeData.openDecodeScope()){original.call(chunk);}finally{CompositeShapeSnapshots.endBatch(chunk.getWorld());}
    }
}
