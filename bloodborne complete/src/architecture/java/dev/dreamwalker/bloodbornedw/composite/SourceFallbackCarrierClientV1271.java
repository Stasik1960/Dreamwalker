package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.registry.Registries;

/** The original lime stained glass carrier renders in the translucent layer. */
@Environment(EnvType.CLIENT)
public final class SourceFallbackCarrierClientV1271 {
    private SourceFallbackCarrierClientV1271() {}
    public static void initialize(){
        BlockRenderLayerMap.INSTANCE.putBlock(Registries.BLOCK.get(SourceFallbackCarrierV1271.TRANSLUCENT_ID),RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Registries.BLOCK.get(SourceFallbackCarrierV1271.MULTIPART_ID),RenderLayer.getTranslucent());
    }
}
