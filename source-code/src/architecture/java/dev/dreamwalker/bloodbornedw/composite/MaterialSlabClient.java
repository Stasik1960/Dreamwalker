package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

/** Retain the transparent gaps around the original decorative stones. The
 * complete opaque paving slab beneath them remains ordinary vanilla geometry. */
public final class MaterialSlabClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        for(var slab:MaterialSlabs.decoratedBlocks())BlockRenderLayerMap.INSTANCE.putBlock(slab,RenderLayer.getCutout());
    }
}
