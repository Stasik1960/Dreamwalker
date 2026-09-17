package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import java.util.*;

public final class BloodborneClient implements ClientModInitializer {
 public void onInitializeClient(){
  EntityRendererRegistry.register(BloodborneBlocks.SEAT_ENTITY,EmptyEntityRenderer::new);
  if(BloodborneBlocks.DATA.compat_layers!=null)BloodborneBlocks.DATA.compat_layers.forEach((name,layer)->{
   var id=new Identifier(name);if(net.minecraft.registry.Registries.BLOCK.containsId(id)){
    var block=net.minecraft.registry.Registries.BLOCK.get(id);
    if(net.minecraft.client.render.RenderLayers.getBlockLayer(block.getDefaultState())!=RenderLayer.getTranslucent())BlockRenderLayerMap.INSTANCE.putBlock(block,RenderLayer.getCutout());
   }
  });
  for(ArchitectureBlock block:BloodborneBlocks.BLOCKS.values()){
   String layer=block.definition.layer;BlockRenderLayerMap.INSTANCE.putBlock(block,layer.equals("translucent")?RenderLayer.getTranslucent():layer.equals("cutout")?RenderLayer.getCutout():RenderLayer.getSolid());
   ColorProviderRegistry.BLOCK.register((state,view,pos,index)->MinecraftClient.getInstance().getBlockColors().getColor(block.original(state),view,pos,index),block);
   ColorProviderRegistry.ITEM.register((stack,index)->{var provider=ColorProviderRegistry.ITEM.get(block.definition.sourceBlock.asItem());return provider==null?-1:provider.getColor(new ItemStack(block.definition.sourceBlock),index);},block.asItem());
  }
  ModelLoadingPlugin.register(context->context.modifyModelAfterBake().register((model,bake)->{
   Identifier id=bake.id();if(!(id instanceof ModelIdentifier modelId)||!id.getNamespace().equals(BloodborneBlocks.ID))return model;
   ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id.getPath());if(block==null)return model;
   net.minecraft.client.render.model.BakedModel result=model;
   if(block.definition.emissive){
    List<EmissiveModel.Pair>pairs=new ArrayList<>();
    for(var e:BloodborneBlocks.DATA.emissive_textures.entrySet()){
     Sprite base=bake.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,new Identifier(e.getKey())));
     Sprite glow=bake.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,new Identifier(e.getValue())));
     if(!base.getContents().getId().getPath().equals("missingno")&&!glow.getContents().getId().getPath().equals("missingno"))pairs.add(new EmissiveModel.Pair(base,glow));
    }
    result=new EmissiveModel(result,pairs);
   }
   double[] offset=GeometryRuntime.renderOffset(id.getPath(),modelId.getVariant());
   if(offset!=null&&(offset[0]!=0||offset[1]!=0||offset[2]!=0))result=new TranslatedBakedModel(result,offset);
   return result;
  }));
 }
}
