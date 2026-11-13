package dev.dreamwalker.bloodbornedw.building;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

public final class ConnectedFencesClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        for (var item : ConnectedFences.items()) {
            BlockRenderLayerMap.INSTANCE.putBlock(item.getBlock(), RenderLayer.getCutout());
        }
    }
}
