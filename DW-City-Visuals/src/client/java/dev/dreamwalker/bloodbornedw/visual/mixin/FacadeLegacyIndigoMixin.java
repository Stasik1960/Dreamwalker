package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.composite.mixin.StaticArchitectureIndigoRegionAccess;
import dev.dreamwalker.bloodbornedw.visual.CityFacadesClient;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.ChunkRenderInfo;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Position-aware facade resolution for Indigo's non-vanilla terrain branch. */
@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext",remap=false)
abstract class FacadeLegacyIndigoMixin {
    @Shadow @Final private ChunkRenderInfo chunkInfo;

    @ModifyVariable(method="tessellateBlock",at=@At("HEAD"),argsOnly=true,remap=false)
    private BakedModel dw$facades(BakedModel model,BlockState state,BlockPos pos,BakedModel original,MatrixStack matrices) {
        BlockRenderView region=((StaticArchitectureIndigoRegionAccess)chunkInfo).staticArchitectureRegion();
        return region==null?model:CityFacadesClient.terrainModel(model,pos,state,region);
    }
}
