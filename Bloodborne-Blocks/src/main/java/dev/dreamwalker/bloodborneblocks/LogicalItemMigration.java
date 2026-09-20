package dev.dreamwalker.bloodborneblocks;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Exact inventory migration for generated logical objects. Missing or ambiguous rules keep the old item. */
final class LogicalItemMigration {
 private static final class Data {int schemaVersion;List<Rule> rules=List.of();}
 private static final class Rule {Source source;Target target;List<Component> components=List.of();}
 private static final class Source {String id;Map<String,String> properties=Map.of();}
 private static final class Component {String id;Map<String,String> properties=Map.of();}
 private static final class Target {String id;Map<String,String> properties=Map.of();}
 record Result(ItemStack stack,boolean matched) {}
 private static Map<Key,Target> rules=Map.of();
 private static Map<Key,List<Target>> components=Map.of();
 private LogicalItemMigration() {}

 static void load(){
  try(InputStream stream=LogicalItemMigration.class.getResourceAsStream("/bloodborne_blocks/logical/migration.json")){
   if(stream==null){rules=Map.of();components=Map.of();return;}
   Data data=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
   if(data==null||data.schemaVersion!=1||data.rules==null)throw new IllegalStateException("Invalid logical migration schema");
   Map<Key,Target> loaded=new HashMap<>();Map<Key,List<Target>> byComponent=new HashMap<>();Set<Key> ambiguous=new HashSet<>();
   for(Rule rule:data.rules){
    if(rule==null||rule.source==null||rule.target==null||rule.source.id==null||rule.target.id==null)throw new IllegalStateException("Invalid logical migration rule");
    ArchitectureBlock source=BloodborneBlocks.BLOCKS.get(rule.source.id);
    Map<String,String> normalized=new TreeMap<>(source==null||source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);
    normalized.putAll(rule.source.properties);
    Key key=new Key(rule.source.id,canonical(normalized));
    Target previous=loaded.putIfAbsent(key,rule.target);
    if(previous!=null&&!sameTarget(previous,rule.target))ambiguous.add(key);
    if(rule.components!=null)for(Component component:rule.components){
     if(component==null||component.id==null)throw new IllegalStateException("Invalid logical migration component");
     byComponent.computeIfAbsent(key(component.id,component.properties),ignored->new ArrayList<>()).add(rule.target);
    }
   }
   ambiguous.forEach(loaded::remove);rules=Map.copyOf(loaded);
   Map<Key,List<Target>> immutable=new HashMap<>();byComponent.forEach((key,targets)->immutable.put(key,List.copyOf(targets)));components=Map.copyOf(immutable);
  }catch(java.io.IOException|RuntimeException e){throw new IllegalStateException("Cannot load logical item migration",e);}
 }

 static Result migrate(ArchitectureBlock source,ItemStack stack){
  Map<String,String> values=new TreeMap<>(source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);
  NbtCompound tag=stack.getSubNbt("BlockStateTag");if(tag!=null)for(String name:tag.getKeys())if(source.definition.propertyObjects.containsKey(name))values.put(name,tag.getString(name));
  Target target=rules.get(new Key(source.definition.id,canonical(values)));if(target==null)return componentPick(source,stack);
  ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(target.id);if(block==null)return new Result(stack,false);
  ItemStack migrated=new ItemStack(block,stack.getCount());if(stack.hasNbt())migrated.setNbt(stack.getNbt().copy());
  NbtCompound properties=new NbtCompound();target.properties.forEach(properties::putString);if(properties.isEmpty())migrated.removeSubNbt("BlockStateTag");else migrated.getOrCreateNbt().put("BlockStateTag",properties);
  return new Result(migrated,true);
 }
 static Result componentPick(ArchitectureBlock source,ItemStack stack){
  if(!source.definition.modular)return new Result(stack,false);
  Map<String,String> values=new TreeMap<>(source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);
  NbtCompound tag=stack.getSubNbt("BlockStateTag");if(tag!=null)for(String name:tag.getKeys())if(source.definition.propertyObjects.containsKey(name))values.put(name,tag.getString(name));
  List<Target> candidates=components.get(new Key(source.definition.id,canonical(values)));if(candidates==null||candidates.isEmpty())return new Result(stack,false);
  Map<TargetKey,Target> unique=new HashMap<>();for(Target candidate:candidates)unique.putIfAbsent(targetKey(candidate),candidate);
  if(unique.size()!=1)return new Result(stack,false);
  Target target=unique.values().iterator().next();ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(target.id);if(block==null)return new Result(stack,false);
  ItemStack migrated=new ItemStack(block,stack.getCount());if(stack.hasNbt())migrated.setNbt(stack.getNbt().copy());
  NbtCompound properties=new NbtCompound();target.properties.forEach((name,value)->{if(!name.equals("facing")&&!name.equals("face"))properties.putString(name,value);});
  if(properties.isEmpty())migrated.removeSubNbt("BlockStateTag");else migrated.getOrCreateNbt().put("BlockStateTag",properties);
  return new Result(migrated,true);
 }

 private static boolean sameTarget(Target first,Target second){return Objects.equals(first.id,second.id)&&Objects.equals(canonical(first.properties),canonical(second.properties));}
 private static Key key(String id,Map<String,String> properties){ArchitectureBlock source=BloodborneBlocks.BLOCKS.get(id);Map<String,String> normalized=new TreeMap<>(source==null||source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);if(properties!=null)normalized.putAll(properties);return new Key(id,canonical(normalized));}
 private static TargetKey targetKey(Target target){Map<String,String> properties=new TreeMap<>(target.properties);properties.remove("facing");properties.remove("face");return new TargetKey(target.id,canonical(properties));}

 private static String canonical(Map<String,String> values){List<String> fields=new ArrayList<>();new TreeMap<>(values).forEach((name,value)->fields.add(name+"="+value));return String.join(",",fields);}
 private record Key(String id,String properties) {}
 private record TargetKey(String id,String properties) {}
}
