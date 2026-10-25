package dev.dreamwalker.bloodbornedw.composite;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
/** Per-resource alpha classification; geometry/catalog identities stay frozen. */
final class NativeMaterialLayers {
 private static final Map<String,String> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 static void reload(){CACHE.clear();}
 static String layer(NativeGridCatalog.Material m){if(!m.layer().equals("translucent"))return m.layer();return CACHE.computeIfAbsent(m.sprite(),NativeMaterialLayers::classify);}
 private static String classify(String sprite){
  var id=new Identifier(sprite);var resource=MinecraftClient.getInstance().getResourceManager().getResource(new Identifier(id.getNamespace(),"textures/"+id.getPath()+".png"));
  if(resource.isEmpty())return "translucent";
  try(var stream=resource.get().getInputStream();var image=NativeImage.read(stream)){
   // Include every animation frame. Soft alpha always retains real blending.
   for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int alpha=image.getColor(x,y)>>>24;if(alpha!=0&&alpha!=255)return "translucent";}
   return "cutout";
  }catch(java.io.IOException failure){return "translucent";}
 }
}
