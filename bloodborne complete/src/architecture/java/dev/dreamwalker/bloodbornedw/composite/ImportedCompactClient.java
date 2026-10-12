package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.MinecraftClient;

/** Compact mesh uses the same v15 biome grass palette as the owner renderer. */
@Environment(EnvType.CLIENT)
public final class ImportedCompactClient {
    private ImportedCompactClient() {}
    public static void initialize() {
        for(var block:CompositeArchitecture.blocks())if(block instanceof ImportedCompactBlock)
            ColorProviderRegistry.BLOCK.register((state,world,pos,tint)->RemainingSourceTint.color(block,MinecraftClient.getInstance(),pos),block);
    }
}
