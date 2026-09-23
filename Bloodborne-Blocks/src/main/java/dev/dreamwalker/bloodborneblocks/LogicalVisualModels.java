package dev.dreamwalker.bloodborneblocks;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;

/** Resource-generation-scoped resolver. Appearance never participates in gameplay. */
final class LogicalVisualModels {
 private final Function<String,JsonObject> reader;
 private final Map<String,ModularMeshData.Mesh> builtin;
 private final Map<String,Optional<ModularMeshData.Mesh>> cache=new HashMap<>();
 LogicalVisualModels(Function<String,JsonObject> reader,Map<String,ModularMeshData.Mesh> builtin){this.reader=reader;this.builtin=builtin;}

 /** Empty means a normal vanilla JSON elements model, not a missing model. */
 Optional<ModularMeshData.Mesh> resolve(String path){return cache.computeIfAbsent(path,this::read);}
 private Optional<ModularMeshData.Mesh> read(String path){
  List<JsonObject> chain=new ArrayList<>();Set<String> visited=new HashSet<>();String current=path;JsonObject geometry=null;
  for(int depth=0;current!=null;depth++){
   if(depth>=32||!visited.add(current))throw new IllegalArgumentException("Cyclic/deep visual model parent: "+path);
   JsonObject object=reader.apply(current);if(object==null)throw new IllegalArgumentException("Missing visual model: "+current);
   chain.add(object);
   // A closest normal-elements model is vanilla-owned: do not inspect a
   // possible custom ancestor. A closest custom model, however, may inherit
   // texture slots from any of its parents.
   if(geometry==null&&(object.has("elements")||object.has("bloodborne_mesh")||object.has("bloodborne_polygons"))){
    geometry=object;if(object.has("elements"))return Optional.empty();
   }
   current=object.has("parent")?object.get("parent").getAsString():null;
  }
  Map<String,String> textures=new HashMap<>();
  for(int i=chain.size()-1;i>=0;i--){JsonObject object=chain.get(i);if(object.has("textures"))object.getAsJsonObject("textures").entrySet().forEach(e->textures.put(e.getKey(),e.getValue().getAsString()));}
  if(geometry==null)return Optional.empty();
  if(geometry.has("bloodborne_polygons")){
   JsonArray polygons=geometry.getAsJsonArray("bloodborne_polygons");if(polygons.size()>65536)throw new IllegalArgumentException("Visual polygon budget exceeded");
   List<ModularMeshData.Polygon> result=new ArrayList<>();
   for(JsonElement value:polygons){
    JsonObject polygon=value.getAsJsonObject();String texture=texture(polygon.get("texture").getAsString(),textures);
    if(!declared(texture,textures))throw new IllegalArgumentException("Undeclared custom visual texture "+texture);
    JsonArray vertices=polygon.getAsJsonArray("vertices");if(vertices.size()<3||vertices.size()>64)throw new IllegalArgumentException("Invalid visual polygon");
    float[] packed=new float[vertices.size()*5];int index=0;
    for(JsonElement vertex:vertices){JsonArray tuple=vertex.getAsJsonArray();if(tuple.size()!=5)throw new IllegalArgumentException("Visual vertex must contain xyzuv");
     for(int i=0;i<5;i++){double n=tuple.get(i).getAsDouble();if(!Double.isFinite(n)||(i<3?Math.abs(n)>64:n<0||n>16))throw new IllegalArgumentException("Out-of-range visual vertex");packed[index++]=(float)n;}}
    result.add(new ModularMeshData.Polygon(texture,packed));
   }
   return Optional.of(new ModularMeshData.Mesh(result));
  }
  if(!geometry.has("bloodborne_mesh"))return Optional.empty();
  String meshId=geometry.get("bloodborne_mesh").getAsString();ModularMeshData.Mesh mesh=builtin.get(meshId);
  if(mesh==null)throw new IllegalArgumentException("Unknown built-in visual mesh: "+meshId);
  JsonObject mapping=geometry.has("bloodborne_texture_slots")?geometry.getAsJsonObject("bloodborne_texture_slots"):new JsonObject();
  List<ModularMeshData.Polygon> result=new ArrayList<>();boolean changed=false;
  for(ModularMeshData.Polygon polygon:mesh.polygons){String target=mapping.has(polygon.texture)?texture(mapping.get(polygon.texture).getAsString(),textures):polygon.texture;
   changed|=!target.equals(polygon.texture);result.add(new ModularMeshData.Polygon(target,polygon.vertices));}
  return Optional.of(changed?new ModularMeshData.Mesh(result):mesh);
 }
 private static String texture(String value,Map<String,String> slots){
  Set<String> seen=new HashSet<>();while(value.startsWith("#")){if(!seen.add(value))throw new IllegalArgumentException("Cyclic visual texture slot");String next=slots.get(value.substring(1));if(next==null)throw new IllegalArgumentException("Unresolved visual texture "+value);value=next;}
  if(net.minecraft.util.Identifier.tryParse(value)==null)throw new IllegalArgumentException("Invalid visual texture "+value);return value;
 }
 private static boolean declared(String texture,Map<String,String> slots){
  for(String value:slots.values())if(texture(value,slots).equals(texture))return true;
  return false;
 }
}
