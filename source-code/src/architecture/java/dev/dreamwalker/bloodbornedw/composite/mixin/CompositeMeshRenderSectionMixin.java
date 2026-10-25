package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also covers chunk managers that override onLightUpdate without calling super. */
@Mixin(WorldRenderer.class)
public abstract class CompositeMeshRenderSectionMixin {
    @Inject(method="scheduleBlockRender(III)V",at=@At("HEAD"))
    private void dw$sectionRedraw(int x,int y,int z,CallbackInfo ci){
        CompositeWorldMesh.sectionChanged(ChunkSectionPos.asLong(x,y,z));
        dev.dreamwalker.bloodbornedw.composite.CompositeTerrainUpdates.section(x,y,z);
    }
}
