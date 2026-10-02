package dev.dreamwalker.bloodborneblocks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Map;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Per-reload client palette; absent resource packs retain the authored appearance. */
final class AutumnAppearance {
 private static final Identifier PALETTE=new Identifier(BloodborneBlocks.ID,"autumn/palette.json");
 private static final Identifier LEAVES=new Identifier(BloodborneBlocks.ID,"block/autumn/leaves");
 private static final Map<String,Integer> REQUIRED=Map.of("stone",0xFFEEDB,"roof",0xCCD5DF,"metal",0xDDD5C9,"wood",0xF2D1A1,"foliage",0xFFD281);
 private final Map<String,Integer> colors;
 private final boolean foliage;
 private final Identifier leafTexture;
 private AutumnAppearance(Map<String,Integer> colors,boolean foliage,Identifier leafTexture){this.colors=Map.copyOf(colors);this.foliage=foliage;this.leafTexture=leafTexture;}
 static AutumnAppearance load(ResourceManager manager){
  try{
   var resource=manager.getResource(PALETTE);if(resource.isEmpty())return identity();
   try(var reader=resource.get().getReader()){
    JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();if(!root.has("schemaVersion")||root.get("schemaVersion").getAsInt()!=1||!root.has("colors")||!root.has("foliage")||!root.has("leaf_texture"))throw new IllegalStateException("Invalid autumn palette schema");
    JsonObject values=root.getAsJsonObject("colors");java.util.Map<String,Integer> colors=new java.util.LinkedHashMap<>();for(String category:REQUIRED.keySet()){if(!values.has(category))throw new IllegalStateException("Missing autumn color "+category);colors.put(category,color(values.get(category).getAsString()));}
    Identifier leaves=Identifier.tryParse(root.get("leaf_texture").getAsString());if(leaves==null)throw new IllegalStateException("Invalid autumn leaf texture");return new AutumnAppearance(colors,root.get("foliage").getAsBoolean(),leaves);
   }
  }catch(IOException|RuntimeException error){throw error instanceof IllegalStateException failure?failure:new IllegalStateException("Cannot read autumn palette",error);}
 }
 static AutumnAppearance identity(){return new AutumnAppearance(Map.of("stone",0xFFFFFF,"roof",0xFFFFFF,"metal",0xFFFFFF,"wood",0xFFFFFF,"foliage",0xFFFFFF),false,LEAVES);}
 int color(BloodborneBlocks.Definition definition){return colors.get(category(definition));}
 boolean foliage(BloodborneBlocks.Definition definition){return foliage&&"foliage".equals(category(definition));}
 Identifier leafTexture(){return leafTexture;}
 static String category(BloodborneBlocks.Definition definition){
  String semantic=definition.semantic==null?"":definition.semantic;
  return switch(semantic){case "tree","bush","plant"->"foliage";case "roof"->"roof";case "fence","metal"->"metal";case "wood"->"wood";case "masonry","column","trim"->"stone";default->"stone";};
 }
 private static int color(String value){
  if(value==null||!value.matches("#[0-9A-Fa-f]{6}"))throw new IllegalStateException("Invalid autumn color "+value);return Integer.parseInt(value.substring(1),16);
 }
}
