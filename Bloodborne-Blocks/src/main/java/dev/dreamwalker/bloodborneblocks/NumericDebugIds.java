package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Immutable, display-only aliases. They are never registry or persistent-data IDs. */
final class NumericDebugIds {
 private static final String PATH="/bloodborne_blocks/debug-ids.json";
 private static Map<String,String> ids=Map.of();
 private static Map<String,String> paths=Map.of();
 private NumericDebugIds() {}

 private static final class Data {int schemaVersion;Map<String,String> ids;Collection<String> retiredIds;}

 static void loadAndValidate(Collection<String> registeredIds){
  Data data;
  try(InputStream stream=NumericDebugIds.class.getResourceAsStream(PATH)){
   if(stream==null)throw new IOException("Missing numeric debug IDs");data=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
  }catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load numeric debug IDs",e);}
  if(data==null||data.schemaVersion!=1||data.ids==null||data.retiredIds==null)throw new IllegalStateException("Invalid numeric debug ID schema");
  Set<String> expected=Set.copyOf(registeredIds),seen=new HashSet<>();Map<String,String> reverse=new HashMap<>();
  if(!data.ids.keySet().equals(expected))throw new IllegalStateException("Numeric debug ID membership mismatch");
  for(var entry:data.ids.entrySet()){
   String value=entry.getValue();
   if(value==null||!value.matches("[0-9]{5}")||"00000".equals(value)||!seen.add(value))throw new IllegalStateException("Invalid or duplicate numeric debug ID "+entry.getKey()+"="+value);
   reverse.put(value,entry.getKey());
  }
  for(String retired:data.retiredIds)if(retired==null||!retired.matches("[0-9]{5}")||"00000".equals(retired)||!seen.add(retired))throw new IllegalStateException("Invalid or reused retired numeric debug ID "+retired);
  ids=Collections.unmodifiableMap(new HashMap<>(data.ids));paths=Collections.unmodifiableMap(reverse);
 }

 static String forBlock(Block block){
  Identifier id=Registries.BLOCK.getId(block);return id.getNamespace().equals(BloodborneBlocks.ID)?ids.get(id.getPath()):null;
 }
 static String blockPath(String numericId){return paths.get(numericId);}
 static String lookup(String numericId){String path=blockPath(numericId);return path==null?null:BloodborneBlocks.ID+":"+path;}
 static Map<String,String> all(){return ids;}
}
