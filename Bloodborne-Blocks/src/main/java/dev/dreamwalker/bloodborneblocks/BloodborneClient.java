package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
 static int facingTurns(String variant){
  for(String entry:variant.split(",")){
   int separator=entry.indexOf('=');
   if(separator>0&&entry.substring(0,separator).equals("facing"))return switch(entry.substring(separator+1)){
    case "east"->1;case "south"->2;case "west"->3;default->0;
   };
  }
  return 0;
 }
 public void onInitializeClient(){
  Map<String,ModularMeshData.Mesh> logicalMeshes=ModularMeshData.loadLogicalAndValidate();
  Map<String,ModularMeshData.Mesh> allMeshes=new HashMap<>(logicalMeshes);
  ModularMeshData.loadCityIfPresent().forEach((key,value)->{if(allMeshes.putIfAbsent(key,value)!=null)throw new IllegalStateException("Duplicate logical/city mesh "+key);});
  EntityRendererRegistry.register(BloodborneBlocks.SEAT_ENTITY,EmptyEntityRenderer::new);
  for(ArchitectureBlock block:BloodborneBlocks.allBlocks()){
   String layer=block.definition.layer;BlockRenderLayerMap.INSTANCE.putBlock(block,layer.equals("translucent")?RenderLayer.getTranslucent():layer.equals("cutout")?RenderLayer.getCutout():RenderLayer.getSolid());
   ColorProviderRegistry.BLOCK.register((state,view,pos,index)->MinecraftClient.getInstance().getBlockColors().getColor(block.original(state),view,pos,index),block);
   ColorProviderRegistry.ITEM.register((stack,index)->{var provider=ColorProviderRegistry.ITEM.get(block.definition.sourceBlock.asItem());return provider==null?-1:provider.getColor(new ItemStack(block.definition.sourceBlock),index);},block.asItem());
  }
  PreparableModelLoadingPlugin.register((manager,executor)->CompletableFuture.supplyAsync(()->{
   Map<String,JsonObject> resources=new HashMap<>();
   // Read from this reload's ResourceManager, never the previous client manager.
   LogicalVisualModels visuals=new LogicalVisualModels(path->resources.computeIfAbsent(path,name->{
    Identifier model=new Identifier(name);Identifier file=new Identifier(model.getNamespace(),"models/"+model.getPath()+".json");
    try(var reader=manager.getResource(file).orElseThrow(()->new IllegalArgumentException("Missing model "+file)).getReader()){
     return JsonParser.parseReader(reader).getAsJsonObject();
    }catch(java.io.IOException e){throw new IllegalStateException("Cannot read visual model "+file,e);}
   }),logicalMeshes);
   for(ArchitectureBlock block:BloodborneBlocks.allBlocks())if(block.definition.visual_models!=null)
    for(String path:new HashSet<>(block.definition.visual_models.values()))visuals.resolve(path);
   return visuals;
  },executor),(visuals,context)->{
   // Scoped to one model-loader generation, so a resource reload never reuses stale sprites.
   Map<String,ModularBakedModel.Parts> modularQuads=new HashMap<>();
   Map<String,Map<String,net.minecraft.client.render.model.BakedModel>> cityItemVariants=new ConcurrentHashMap<>();
   ModularBakedModel.QuadPool sharedFaces=new ModularBakedModel.QuadPool();
   context.modifyModelAfterBake().register((model,bake)->{
   Identifier id=bake.id();if(!(id instanceof ModelIdentifier modelId)||!id.getNamespace().equals(BloodborneBlocks.ID))return model;
   ArchitectureBlock block=BloodborneBlocks.registeredBlock(id.getPath());
   // Item models parent their selected default mesh so their JSON display transform can fit a large object.
   if(block==null&&id.getPath().startsWith("block/logical/")){
    String meshKey=id.getPath().substring("block/logical/".length());ModularMeshData.Mesh mesh=allMeshes.get(meshKey);
    if(mesh==null)return model;String cacheKey="logical-item:"+meshKey;
    return ModularBakedModel.withDelegate(modularQuads.computeIfAbsent(cacheKey,key->ModularBakedModel.bake(mesh,0,bake.textureGetter(),sharedFaces,false)),model);
   }
   if(block==null)return model;
   net.minecraft.client.render.model.BakedModel result=model;
   if(block.definition.models!=null){
   String inventoryKey=BloodborneBlocks.key(BloodborneBlocks.applyPlacementProperties(block.definition,block.getDefaultState()));
   String meshKey=block.definition.models.get(modelId.getVariant());
   if(meshKey==null&&modelId.getVariant().equals("inventory"))meshKey=block.definition.models.get(inventoryKey);
   if(meshKey==null)throw new IllegalStateException("Missing logical mesh state "+block.definition.id+"["+modelId.getVariant()+"]");
   ModularMeshData.Mesh mesh=allMeshes.get(meshKey);if(mesh==null)throw new IllegalStateException("Missing logical/city mesh "+meshKey+" for "+block.definition.id);
    // Logical meshes are generated in their complete state orientation; do not rotate them again.
    String visualPath=block.definition.visual_models==null?null:block.definition.visual_models.get(modelId.getVariant().equals("inventory")?inventoryKey:modelId.getVariant());
    Optional<ModularMeshData.Mesh> appearance=visualPath==null?Optional.of(mesh):visuals.resolve(visualPath);
    // A resource-pack model with normal elements keeps Minecraft's baked model.
    // Only our explicit mesh/polygon JSON extension uses the static quad adapter.
    if(appearance.isPresent()){
     String cacheKey=visualPath==null?"logical:"+meshKey:"visual:"+visualPath;
     result=ModularBakedModel.withDelegate(modularQuads.computeIfAbsent(cacheKey,key->ModularBakedModel.bake(appearance.get(),0,bake.textureGetter(),sharedFaces,block.definition.city_compat&&!block.definition.whole_owner)),result);
    }
   }
   if(block.definition.emissive){
    List<EmissiveModel.Pair>pairs=new ArrayList<>();
    Map<String,String> emissiveTextures=(block.definition.city_compat?BloodborneBlocks.CITY_DATA:BloodborneBlocks.DATA).emissive_textures;
    if(emissiveTextures==null)emissiveTextures=Map.of();
    for(var e:emissiveTextures.entrySet()){
     Sprite base=bake.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,new Identifier(e.getKey())));
     Sprite glow=bake.textureGetter().apply(new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,new Identifier(e.getValue())));
     if(!base.getContents().getId().getPath().equals("missingno")&&!glow.getContents().getId().getPath().equals("missingno"))pairs.add(new EmissiveModel.Pair(base,glow));
    }
    result=new EmissiveModel(result,pairs);
   }
   double[] offset=GeometryRuntime.renderOffset(id.getPath(),modelId.getVariant());
   if(offset!=null&&(offset[0]!=0||offset[1]!=0||offset[2]!=0))result=new TranslatedBakedModel(result,offset);
   if(block.definition.city_compat&&!block.definition.whole_owner&&block.definition.models!=null){
    Map<String,net.minecraft.client.render.model.BakedModel> variants=cityItemVariants.computeIfAbsent(block.definition.id,key->new ConcurrentHashMap<>());
    if(modelId.getVariant().equals("inventory"))result=new CityVariantItemModel(result,block.definition,variants);
    else variants.put(modelId.getVariant(),result);
   }
   return result;
  });});
 }
}
