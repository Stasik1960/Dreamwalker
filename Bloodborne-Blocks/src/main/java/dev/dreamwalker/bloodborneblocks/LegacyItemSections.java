package dev.dreamwalker.bloodborneblocks;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Old inventory stacks place one editable section; saved world IDs stay readable. */
final class LegacyItemSections {
 private record Rule(Map<String,String> defaults,Map<String,String> states) {}
 private static Map<String,Rule> rules=Map.of();
 private LegacyItemSections() {}

 static void load(){
  try(InputStream stream=LegacyItemSections.class.getResourceAsStream("/bloodborne_blocks/v2/migration.json")){
   if(stream==null)throw new IOException("Missing item migration rules");
   JsonObject data=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
   Map<String,Rule> loaded=new HashMap<>();
   for(var entry:data.entrySet()){
    JsonObject spec=entry.getValue().getAsJsonObject();Map<String,String> defaults=new TreeMap<>(),states=new HashMap<>();
    spec.getAsJsonObject("default").entrySet().forEach(p->defaults.put(p.getKey(),p.getValue().getAsString()));
    for(var state:spec.getAsJsonObject("states").entrySet()){
     JsonElement value=state.getValue();
     // A keep marker retains real native stairs/slabs and large interactive props.
     if(value.isJsonArray())states.put(state.getKey(),representative(value.getAsJsonArray()));
     else states.put(state.getKey(),entry.getKey());
    }
    loaded.put(entry.getKey(),new Rule(Map.copyOf(defaults),Map.copyOf(states)));
   }
   rules=Map.copyOf(loaded);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load legacy item sections",e);}
 }

 static String representative(JsonArray pieces){
  String result="";int distance=Integer.MAX_VALUE;
  for(JsonElement element:pieces){
   JsonObject part=element.getAsJsonObject();JsonArray offset=part.getAsJsonArray("offset");
   int d=0;for(JsonElement coordinate:offset)d+=Math.abs(coordinate.getAsInt());
   if(d<distance){distance=d;result=part.get("id").getAsString();}
  }
  return result;
 }

 static String targetId(String legacy,Map<String,String> supplied){
  Rule rule=rules.get(legacy);if(rule==null)return legacy;
  Map<String,String> values=new TreeMap<>(rule.defaults);
  supplied.forEach((key,value)->{if(values.containsKey(key))values.put(key,value);});
  String key=key(values),fallback=key(rule.defaults);
  return rule.states.getOrDefault(key,rule.states.getOrDefault(fallback,legacy));
 }

 static ArchitectureBlock target(ArchitectureBlock block,ItemStack stack){
  if(block.definition.modular)return block;
  Map<String,String> properties=new HashMap<>();NbtCompound tag=stack.getSubNbt("BlockStateTag");
  if(tag!=null)for(String key:tag.getKeys())properties.put(key,tag.getString(key));
  String id=targetId(block.definition.id,properties);
  if(id.isEmpty())return null;
  ArchitectureBlock target=BloodborneBlocks.BLOCKS.get(id);
  if(target==null)throw new IllegalStateException("Unknown migrated item "+id);
  return target;
 }

 private static String key(Map<String,String> values){
  List<String> parts=new ArrayList<>();new TreeMap<>(values).forEach((k,v)->parts.add(k+"="+v));return String.join(",",parts);
 }
}
