package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.ChunkRenderInfo;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Indigo's default terrain path bypasses BlockModelRenderer even for vanilla
 * adapters. Keep only these new native cells on their exact existing source
 * renderer, writing directly to Indigo's normally initialized native buffer. */
@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext",remap=false)
public abstract class StaticArchitectureIndigoTerrainMixin {
    @Shadow @Final private ChunkRenderInfo chunkInfo;

    @Inject(method="tessellateBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private void dw$staticArchitectureIndigo(BlockState state,BlockPos pos,BakedModel model,MatrixStack matrices,CallbackInfo callback){
        if(!(state.getBlock() instanceof StaticArchitectureBlock))return;
        if(StaticArchitectureCatalog.recipe(state).resident()>=0){
            var region=((StaticArchitectureIndigoRegionAccess)(Object)chunkInfo).staticArchitectureRegion();
            if(region==null)throw new IllegalStateException("Static architecture Indigo task has no prepared native region");
            // At HEAD the supplied matrix already contains the vanilla
            // section-local block translation; Indigo has added no model
            // offset, tint or AO/light processing. Static cells have no
            // native model offset. The source renderer applies its own exact
            // reviewed transforms, source tint, native AO and packed lights.
            StaticArchitectureClient.render(MinecraftClient.getInstance().getBlockRenderManager().getModelRenderer(),
                region,state,pos,matrices,chunkInfo.getInitializedBuffer(RenderLayer.getCutout()),OverlayTexture.DEFAULT_UV);
        }
        // Helpers emit no geometry. No already shaded source vertices enter
        // Indigo's second lighting/tint pass, and no model is emitted twice.
        callback.cancel();
    }
}
