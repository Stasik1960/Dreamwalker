package dev.dreamwalker.bloodborneblocks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Pure resolver checks: no client model bake, world, or resource reload required. */
public final class LogicalVisualModelsChecks {
 private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void fails(Runnable action,String text){try{action.run();throw new AssertionError("accepted "+text);}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  ModularMeshData.Mesh builtin=new ModularMeshData.Mesh(java.util.List.of(new ModularMeshData.Polygon("minecraft:block/stone",new float[15])));
  Map<String,JsonObject> models=Map.of(
   "parent",json("{\"bloodborne_mesh\":\"mesh\",\"bloodborne_texture_slots\":{\"minecraft:block/stone\":\"#slot\"},\"textures\":{\"slot\":\"minecraft:block/stone\"},\"parent\":\"minecraft:block/block\"}"),
   "child",json("{\"parent\":\"parent\",\"textures\":{\"slot\":\"minecraft:block/gold_block\"}}"),
   "minecraft:block/block",json("{\"textures\":{\"particle\":\"minecraft:block/stone\"}}"),
   "vanilla",json("{\"elements\":[]}"));
  LogicalVisualModels first=new LogicalVisualModels(models::get,Map.of("mesh",builtin));
  Optional<ModularMeshData.Mesh> resolved=first.resolve("child");check(resolved.isPresent()&&resolved.get().polygons.get(0).texture.equals("minecraft:block/gold_block"),"child texture override inherits builtin parent");
  check(first.resolve("vanilla").isEmpty(),"standard elements override custom resolver");
  LogicalVisualModels fresh=new LogicalVisualModels(models::get,Map.of("mesh",builtin));check(fresh.resolve("child").isPresent(),"fresh resolver has no stale reload cache");
  Map<String,JsonObject> custom=Map.of(
   "parent",json("{\"bloodborne_polygons\":[{\"texture\":\"#slot\",\"vertices\":[[0,0,0,0,0],[1,0,0,16,0],[0,1,0,0,16]]}],\"textures\":{\"slot\":\"minecraft:block/stone\"}}"),
   "child",json("{\"parent\":\"parent\",\"bloodborne_polygons\":[{\"texture\":\"#slot\",\"vertices\":[[0,0,0,0,0],[1,0,0,16,0],[0,1,0,0,16]]}],\"textures\":{\"slot\":\"minecraft:block/gold_block\"}}"),
   "inherited",json("{\"parent\":\"parent\",\"bloodborne_polygons\":[{\"texture\":\"#slot\",\"vertices\":[[0,0,0,0,0],[1,0,0,16,0],[0,1,0,0,16]]}]}"),
   "normal",json("{\"elements\":[],\"parent\":\"missing-custom-parent\"}"));
  Optional<ModularMeshData.Mesh> polygons=new LogicalVisualModels(custom::get,Map.of()).resolve("child");check(polygons.isPresent()&&polygons.get().polygons.get(0).texture.equals("minecraft:block/gold_block"),"nearest custom geometry wins while inherited textures resolve");
  Optional<ModularMeshData.Mesh> inherited=new LogicalVisualModels(custom::get,Map.of()).resolve("inherited");check(inherited.isPresent()&&inherited.get().polygons.get(0).texture.equals("minecraft:block/stone"),"custom child polygon inherits parent texture slot");
  check(new LogicalVisualModels(custom::get,Map.of()).resolve("normal").isEmpty(),"normal elements do not read custom ancestors");
  fails(()->new LogicalVisualModels(Map.of("a",json("{\"bloodborne_mesh\":\"mesh\",\"parent\":\"b\"}"),"b",json("{\"parent\":\"a\"}"))::get,Map.of("mesh",builtin)).resolve("a"),"parent cycle after geometry");
  fails(()->new LogicalVisualModels(path->null,Map.of()).resolve("missing"),"missing model");
  fails(()->new LogicalVisualModels(Map.of("x",json("{\"bloodborne_mesh\":\"mesh\",\"parent\":\"missing\"}"))::get,Map.of("mesh",builtin)).resolve("x"),"missing parent after geometry");
  fails(()->new LogicalVisualModels(Map.of("x",json("{\"bloodborne_mesh\":\"mesh\",\"bloodborne_texture_slots\":{\"minecraft:block/stone\":\"#missing\"}}"))::get,Map.of("mesh",builtin)).resolve("x"),"unresolved texture slot");
 fails(()->new LogicalVisualModels(Map.of("x",json("{\"bloodborne_polygons\":[{\"texture\":\"minecraft:block/gold_block\",\"vertices\":[[0,0,0,0,0],[1,0,0,16,0],[0,1,0,0,16]]}],\"textures\":{\"slot\":\"minecraft:block/stone\"}}"))::get,Map.of()).resolve("x"),"undeclared direct custom texture");
  String starterKit=args.length==0?System.getProperty("bloodborne.altStarterKitZip"):args[0];if(starterKit!=null)checkStarterKit(Path.of(starterKit));
 }

 /** Validates the distributable ALT pack against the logical BASE mesh data. */
 private static void checkStarterKit(Path archive){
  check(Files.isRegularFile(archive),"ALT starter-kit ZIP exists: "+archive);
  try(ZipFile zip=new ZipFile(archive.toFile())){
   Map<String,JsonObject> packed=new HashMap<>(),exported=new TreeMap<>();Set<String> textures=new HashSet<>();
   Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){
    ZipEntry entry=entries.nextElement();String name=entry.getName();
    if(name.matches("assets/[^/]+/models/.+\\.json")){String path=modelPath(name);JsonObject model=read(zip,entry);packed.put(path,model);if(path.contains("/alt/"))exported.put(path,model);}
    if(name.matches("assets/[^/]+/textures/.+\\.png"))textures.add(name);
   }
   check(!exported.isEmpty(),"ALT starter kit exports models");
   Map<String,String> sourceMeshes=altMeshes();Map<String,ModularMeshData.Mesh> builtin=ModularMeshData.loadLogicalAndValidate();
   check(exported.keySet().equals(sourceMeshes.keySet()),"ALT starter kit exports exactly every declared ALT model");
   Function<String,JsonObject> reader=path->{JsonObject model=packed.get(path);return model!=null?model:classpathModel(path);};
   LogicalVisualModels resolver=new LogicalVisualModels(reader,builtin);
   for(var entry:exported.entrySet()){
    String meshId=sourceMeshes.get(entry.getKey());check(meshId!=null,"exported ALT model has BASE mesh mapping: "+entry.getKey());
    Optional<ModularMeshData.Mesh> resolved=resolver.resolve(entry.getKey());check(resolved.isPresent(),"exported ALT model resolves custom geometry: "+entry.getKey());
    compare(entry.getKey(),builtin.get(meshId),resolved.get());
    for(ModularMeshData.Polygon polygon:resolved.get().polygons)check(textureExists(polygon.texture,textures),"exported texture exists: "+entry.getKey()+" -> "+polygon.texture);
   }
   System.out.println("ALT starter-kit PASS: "+exported.size()+" models, "+textures.size()+" textures");
  }catch(IOException exception){throw new IllegalStateException("Cannot read ALT starter kit "+archive,exception);}
 }

 private static Map<String,String> altMeshes(){
  JsonObject root=classpathJson("bloodborne_blocks/logical/definitions.json");check(root!=null,"logical definitions resource exists");Map<String,String> result=new HashMap<>();
  for(var value:root.getAsJsonArray("blocks")){
   JsonObject block=value.getAsJsonObject(),visual=block.getAsJsonObject("visual_models"),models=block.getAsJsonObject("models");
   if(visual==null||models==null)continue;
   for(var state:visual.entrySet())if(state.getValue().getAsString().contains("/alt/")){
    String mesh=models.get(state.getKey()).getAsString();String previous=result.put(state.getValue().getAsString(),mesh);
    check(previous==null||previous.equals(mesh),"ALT path maps to one BASE mesh: "+state.getValue().getAsString());
   }
  }
  return result;
 }

 private static void compare(String path,ModularMeshData.Mesh source,ModularMeshData.Mesh actual){
  check(source!=null,"BASE mesh exists for "+path);check(source.polygons.size()==actual.polygons.size(),"ALT polygon count preserves BASE mesh: "+path);
  for(int polygon=0;polygon<source.polygons.size();polygon++){
   float[] expected=source.polygons.get(polygon).vertices,got=actual.polygons.get(polygon).vertices;
   check(expected.length==got.length,"ALT vertex count preserves BASE mesh: "+path+"["+polygon+"]");
   for(int index=0;index<expected.length;index++)check(Math.abs(expected[index]-got[index])<=.00001f,"ALT XYZUV preserves BASE mesh: "+path+"["+polygon+"]["+index+"]");
  }
 }

 private static boolean textureExists(String texture,Set<String> exported){
  int colon=texture.indexOf(':');String namespace=colon<0?"minecraft":texture.substring(0,colon),path=colon<0?texture:texture.substring(colon+1);
  String resource="assets/"+namespace+"/textures/"+path+".png";
  if(exported.contains(resource))return true;
  return LogicalVisualModelsChecks.class.getClassLoader().getResource(resource)!=null;
 }

 private static String modelPath(String name){
  int namespaceStart="assets/".length(),namespaceEnd=name.indexOf('/',namespaceStart);String namespace=name.substring(namespaceStart,namespaceEnd);
  String model=name.substring(("assets/"+namespace+"/models/").length(),name.length()-5);return namespace+":"+model;
 }
 private static JsonObject classpathModel(String path){
  int colon=path.indexOf(':');String namespace=colon<0?"minecraft":path.substring(0,colon),model=colon<0?path:path.substring(colon+1);
  return classpathJson("assets/"+namespace+"/models/"+model+".json");
 }
 private static JsonObject classpathJson(String resource){
  try(InputStream stream=LogicalVisualModelsChecks.class.getClassLoader().getResourceAsStream(resource)){
   if(stream==null)return null;return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
  }catch(IOException exception){throw new IllegalStateException("Cannot read classpath resource "+resource,exception);}
 }
 private static JsonObject read(ZipFile zip,ZipEntry entry)throws IOException{
  try(InputStream stream=zip.getInputStream(entry)){return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}
 }
}
