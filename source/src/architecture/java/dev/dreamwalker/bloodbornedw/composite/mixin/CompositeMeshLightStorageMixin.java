package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Network nibble replacements publish without native onLightUpdate callbacks. */
@Mixin(LightStorage.class)
public abstract class CompositeMeshLightStorageMixin {
    @Shadow @Final protected LongSet dirtySections;
    @Shadow @Final protected ChunkProvider chunkProvider;

    @Inject(method = "notifyChanges", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/chunk/light/LightStorage;uncachedStorage:Lnet/minecraft/world/chunk/ChunkToNibbleArrayMap;",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void dw$publishedNetworkLight(CallbackInfo ci) {
        // This class also serves the integrated server. Only client publication
        // changes renderer dependencies; the newly published arrays are ready.
        if (!(chunkProvider instanceof ClientChunkManager)) return;
        var sections = dirtySections.iterator();
        while (sections.hasNext()) CompositeWorldMesh.sectionChanged(sections.nextLong());
    }
}
