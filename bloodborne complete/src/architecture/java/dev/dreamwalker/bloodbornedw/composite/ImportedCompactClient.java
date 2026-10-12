package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;

/** Compact mesh uses the same v15 biome grass palette as the owner renderer. */
@Environment(EnvType.CLIENT)
public final class ImportedCompactClient {
    private ImportedCompactClient() {}
    public static void initialize() {
        for(var block:CompositeArchitecture.blocks())if(block instanceof ImportedCompactBlock) {
            BlockRenderLayerMap.INSTANCE.putBlock(block,block.spec.translucent?RenderLayer.getTranslucent():RenderLayer.getCutout());
            ColorProviderRegistry.BLOCK.register((state,world,pos,tint)->RemainingSourceTint.color(block,MinecraftClient.getInstance(),pos),block);
        }
    }
}
