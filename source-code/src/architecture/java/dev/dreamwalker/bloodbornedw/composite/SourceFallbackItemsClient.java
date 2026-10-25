package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;

/** Four items encode the exact saved state, not a guessed vanilla material. */
public final class SourceFallbackItemsClient {
    private SourceFallbackItemsClient(){}
    public static void initialize(){
        for(var block:SourceFallbackCarrierV1271.banks())BuiltinItemRendererRegistry.INSTANCE.register(block,(stack,mode,matrices,consumers,light,overlay)->{
            var state=SourceFallbackCarrierV1271.itemState(stack);if(state==null)return;
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state,matrices,consumers,light,overlay);
        });
    }
}
