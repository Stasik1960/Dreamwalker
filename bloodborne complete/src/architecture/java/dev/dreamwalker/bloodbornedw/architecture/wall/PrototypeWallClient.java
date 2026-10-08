package dev.dreamwalker.bloodbornedw.architecture.wall;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;

@Environment(EnvType.CLIENT)
public final class PrototypeWallClient {
    private static boolean initialized;
    private PrototypeWallClient(){}
    public static void initializeClient(){
        if(initialized)return;
        PrototypeWallModels.initialize();
        for(var block:PrototypeWallArchitecture.blocks()){
            BlockRenderLayerMap.INSTANCE.putBlock(block,RenderLayer.getSolid());
            FabricModelPredicateProviderRegistry.register(block.asItem(),PrototypeWallArchitecture.id("wall_material"),(stack,world,entity,seed)->PrototypeWallItem.material(stack)/7.0F);
            FabricModelPredicateProviderRegistry.register(block.asItem(),PrototypeWallArchitecture.id("wall_course"),(stack,world,entity,seed)->PrototypeWallItem.course(stack)==PrototypeWallBlock.Course.TALL?1F:0F);
            FabricModelPredicateProviderRegistry.register(block.asItem(),PrototypeWallArchitecture.id("wall_profile"),(stack,world,entity,seed)->PrototypeWallItem.profile(stack)==PrototypeWallBlock.Profile.ALT?1F:0F);
        }
        initialized=true;
    }
}
