package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient;
import dev.dreamwalker.bloodbornedw.composite.CompositeTerrain;
import dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierClientV1271;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Select the source CompositeSpec layer for each finite static recipe. */
@Mixin(RenderLayers.class)
public abstract class StaticArchitectureRenderLayersMixin {
    @Inject(method="getBlockLayer",at=@At("HEAD"),cancellable=true)
    private static void dw$staticArchitectureLayer(BlockState state,CallbackInfoReturnable<RenderLayer> callback){
        if(state.getBlock() instanceof StaticArchitectureBlock||CompositeTerrain.eligible(state))callback.setReturnValue(StaticArchitectureClient.layer(state));
        else{var artwork=SourceFallbackCarrierClientV1271.artworkLayer(state);if(artwork!=null)callback.setReturnValue(artwork);}
    }
}
