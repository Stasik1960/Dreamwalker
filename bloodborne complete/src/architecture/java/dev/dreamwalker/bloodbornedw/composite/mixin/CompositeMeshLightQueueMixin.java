package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Public network entry also covers engines that replace vanilla LightStorage. */
@Mixin(value=LightingProvider.class,priority=1100)
public abstract class CompositeMeshLightQueueMixin {
    @Unique private boolean dw$clientLight;
    @Inject(method="<init>",at=@At("RETURN"))
    private void dw$clientProvider(ChunkProvider provider,boolean block,boolean sky,CallbackInfo ci){dw$clientLight=provider instanceof ClientChunkManager;}
    @Inject(method="enqueueSectionData",at=@At("HEAD"))
    private void dw$incomingLight(LightType type,ChunkSectionPos section,ChunkNibbleArray data,CallbackInfo ci){
        if(dw$clientLight)CompositeWorldMesh.sectionChanged(section.asLong());
        // Vanilla's separate publication hook stamps again after queued arrays
        // become visible. Replacement engines may publish directly in this call.
    }
}
