package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the existing Indigo task's region without a second world lookup or
 * a retained region/world cache. The optional implementation keeps its own
 * normal prepare/release lifecycle. */
@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.ChunkRenderInfo",remap=false)
public interface StaticArchitectureIndigoRegionAccess {
    @Accessor(value="blockView",remap=false)
    BlockRenderView staticArchitectureRegion();
}
