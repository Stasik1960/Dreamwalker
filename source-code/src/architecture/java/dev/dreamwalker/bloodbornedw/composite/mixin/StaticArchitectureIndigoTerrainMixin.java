package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient;
import dev.dreamwalker.bloodbornedw.composite.CompositeTerrain;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.ChunkRenderInfo;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Indigo's default terrain path bypasses BlockModelRenderer even for vanilla
 * adapters. Keep only these new native cells on their exact existing source
 * renderer, writing directly to Indigo's normally initialized native buffer. */
@Pseudo
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.TerrainRenderContext",remap=false)
public abstract class StaticArchitectureIndigoTerrainMixin {
    @Shadow @Final private ChunkRenderInfo chunkInfo;

    @ModifyVariable(method="tessellateBlock",at=@At("HEAD"),argsOnly=true,remap=false)
    private BakedModel dw$resolveNativeVisual(BakedModel model,BlockState state,BlockPos pos,BakedModel original,MatrixStack matrices){
        if(!(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.NativePhysicsBlock))return model;
        var region=((StaticArchitectureIndigoRegionAccess)(Object)chunkInfo).staticArchitectureRegion();
        if(region==null)throw new IllegalStateException("Native visual has no prepared Indigo region");
        return dev.dreamwalker.bloodbornedw.composite.NativeGridClient.terrainModel(region,state,pos,model);
    }

    @Inject(method="tessellateBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private void dw$staticArchitectureIndigo(BlockState state,BlockPos pos,BakedModel model,MatrixStack matrices,CallbackInfo callback){
        var bridgeRegion=((StaticArchitectureIndigoRegionAccess)(Object)chunkInfo).staticArchitectureRegion();
        if(bridgeRegion!=null&&dev.dreamwalker.bloodbornedw.composite.NativeGridBridge.renderBase(bridgeRegion.getBlockEntityRenderData(pos))>=0)return;
        if(!(state.getBlock() instanceof StaticArchitectureBlock)&&!CompositeTerrain.eligible(state))return;
        if(CompositeTerrain.at(state,pos)||state.getBlock() instanceof StaticArchitectureBlock&&StaticArchitectureCatalog.recipe(state).resident()>=0){
            var region=((StaticArchitectureIndigoRegionAccess)(Object)chunkInfo).staticArchitectureRegion();
            if(region==null)throw new IllegalStateException("Static architecture Indigo task has no prepared native region");
            // At HEAD the supplied matrix already contains the vanilla
            // section-local block translation; Indigo has added no model
            // offset, tint or AO/light processing. Static cells have no
            // native model offset. The source renderer applies its own exact
            // reviewed transforms, source tint, native AO and packed lights.
            StaticArchitectureClient.renderLayered(MinecraftClient.getInstance().getBlockRenderManager().getModelRenderer(),
                region,state,pos,matrices,chunkInfo::getInitializedBuffer,OverlayTexture.DEFAULT_UV);
        }
        // Helpers emit no geometry. No already shaded source vertices enter
        // Indigo's second lighting/tint pass, and no model is emitted twice.
        callback.cancel();
    }
}
