package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.registry.Registries;
import net.minecraft.block.BlockState;

/** The original lime stained glass carrier renders in the translucent layer. */
@Environment(EnvType.CLIENT)
public final class SourceFallbackCarrierClientV1271 {
    private SourceFallbackCarrierClientV1271() {}
    public static void initialize(){
        BlockRenderLayerMap.INSTANCE.putBlock(Registries.BLOCK.get(SourceFallbackCarrierV1271.TRANSLUCENT_ID),RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Registries.BLOCK.get(SourceFallbackCarrierV1271.MULTIPART_ID),RenderLayer.getTranslucent());
    }
    /** Resource-only UV/PNG audit of the finite saved palette identified these
     * 24 applications sampling transparent texels. The default solid shader
     * writes those texels as black/depth. All other compatibility states retain
     * their established layer, including the already translucent applications. */
    public static RenderLayer artworkLayer(BlockState state){
        if(!(state.getBlock() instanceof SourceFallbackCarrierV1271))return null;
        int index=state.get(SourceFallbackCarrierV1271.SOURCE)+128*state.get(SourceFallbackCarrierV1271.SOURCE_BANK);
        var id=Registries.BLOCK.getId(state.getBlock());
        boolean cutout=id.equals(SourceFallbackCarrierV1271.ID)?switch(index){
            case 1,88,101,102,105,106,107,109,114,133->true;default->false;
        }:id.equals(SourceFallbackCarrierV1271.SOLID_MULTIPART_ID)&&switch(index){
            case 108,128,129,130,131,132,144,145,149,150,151,152,154,155->true;default->false;
        };
        return cutout?RenderLayer.getCutout():null;
    }
}
