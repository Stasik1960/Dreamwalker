package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Invalidate the static owner light mesh on real sky/block-light section updates. */
@Mixin(ClientChunkManager.class)
public abstract class CompositeMeshLightMixin {
    @Inject(method="onLightUpdate",at=@At("HEAD"))
    private void dreamwalker$light(LightType type,ChunkSectionPos section,CallbackInfo ci){CompositeWorldMesh.sectionChanged(section.asLong());}
}
