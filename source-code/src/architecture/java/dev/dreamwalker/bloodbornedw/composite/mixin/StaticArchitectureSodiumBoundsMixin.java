package dev.dreamwalker.bloodbornedw.composite.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Source vertices are allowed throughout Sodium's packed [-8,24) cube.
 * Conservatively admit that complete cube in normal and shadow frusta. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller",remap=false)
public abstract class StaticArchitectureSodiumBoundsMixin {
    @ModifyConstant(method="isWithinFrustum",constant=@Constant(floatValue=9.125F),remap=false)
    private static float dw$sourceBounds(float original){return 16.125F;}
}
