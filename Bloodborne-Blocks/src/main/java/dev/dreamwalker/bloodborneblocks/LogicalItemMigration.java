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
 private static final class Rule {Source source;Target target;List<Component> components=List.of();List<String> supersedes_targets=List.of();}
 private static final class Source {String id;Map<String,String> properties=Map.of();}
 private static final class Component {String id;Map<String,String> properties=Map.of();}
 private static final class Target {String id;Map<String,String> properties=Map.of();}
 record Result(ItemStack stack,boolean matched) {}
 private record Candidate(Target target,Set<String> supersedes) {}
 private static Map<Key,List<Candidate>> rules=Map.of();
 private static Map<Key,List<Candidate>> components=Map.of();
 private LogicalItemMigration() {}

 static void load(){
  try(InputStream stream=LogicalItemMigration.class.getResourceAsStream("/bloodborne_blocks/logical/migration.json")){
   if(stream==null){rules=Map.of();components=Map.of();return;}
   Data data=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Data.class);
   if(data==null||data.schemaVersion!=1||data.rules==null)throw new IllegalStateException("Invalid logical migration schema");
   Map<Key,List<Candidate>> loaded=new HashMap<>();Map<Key,List<Candidate>> byComponent=new HashMap<>();
   for(Rule rule:data.rules){
    if(rule==null||rule.source==null||rule.target==null||rule.source.id==null||rule.target.id==null)throw new IllegalStateException("Invalid logical migration rule");
    ArchitectureBlock source=BloodborneBlocks.BLOCKS.get(rule.source.id);
    Map<String,String> normalized=new TreeMap<>(source==null||source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);
    normalized.putAll(rule.source.properties);
    Key key=new Key(rule.source.id,canonical(normalized));
    Candidate candidate=new Candidate(rule.target,validatedSupersedes(rule));
    loaded.computeIfAbsent(key,ignored->new ArrayList<>()).add(candidate);
    if(rule.components!=null)for(Component component:rule.components){
     if(component==null||component.id==null)throw new IllegalStateException("Invalid logical migration component");
     byComponent.computeIfAbsent(key(component.id,component.properties),ignored->new ArrayList<>()).add(candidate);
    }
   }
   rules=immutable(loaded);components=immutable(byComponent);
  }catch(java.io.IOException|RuntimeException e){throw new IllegalStateException("Cannot load logical item migration",e);}
 }

 static Result migrate(ArchitectureBlock source,ItemStack stack){
  Map<String,String> values=new TreeMap<>(source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);
  NbtCompound tag=stack.getSubNbt("BlockStateTag");if(tag!=null)for(String name:tag.getKeys())if(source.definition.propertyObjects.containsKey(name))values.put(name,tag.getString(name));
  Target target=pick(rules.get(new Key(source.definition.id,canonical(values))),false);if(target==null)return componentPick(source,stack);
  ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(target.id);if(block==null)return new Result(stack,false);
  ItemStack migrated=new ItemStack(block,stack.getCount());if(stack.hasNbt())migrated.setNbt(stack.getNbt().copy());
  NbtCompound properties=new NbtCompound();target.properties.forEach(properties::putString);if(properties.isEmpty())migrated.removeSubNbt("BlockStateTag");else migrated.getOrCreateNbt().put("BlockStateTag",properties);
  return new Result(migrated,true);
 }
 static Result componentPick(ArchitectureBlock source,ItemStack stack){
  if(!source.definition.modular)return new Result(stack,false);
  Map<String,String> values=new TreeMap<>(source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);
  NbtCompound tag=stack.getSubNbt("BlockStateTag");if(tag!=null)for(String name:tag.getKeys())if(source.definition.propertyObjects.containsKey(name))values.put(name,tag.getString(name));
  Target target=pick(components.get(new Key(source.definition.id,canonical(values))),true);if(target==null)return new Result(stack,false);
  ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(target.id);if(block==null)return new Result(stack,false);
  ItemStack migrated=new ItemStack(block,stack.getCount());if(stack.hasNbt())migrated.setNbt(stack.getNbt().copy());
  NbtCompound properties=new NbtCompound();target.properties.forEach((name,value)->{if(!name.equals("facing")&&!name.equals("face"))properties.putString(name,value);});
  if(properties.isEmpty())migrated.removeSubNbt("BlockStateTag");else migrated.getOrCreateNbt().put("BlockStateTag",properties);
  return new Result(migrated,true);
 }

 private static Set<String> validatedSupersedes(Rule rule){
  if(rule.supersedes_targets==null)return Set.of();
  Set<String> values=new HashSet<>();for(String id:rule.supersedes_targets){ArchitectureBlock target=id==null?null:BloodborneBlocks.BLOCKS.get(id);if(id==null||id.isBlank()||target==null||!target.definition.logical||!values.add(id))throw new IllegalStateException("Invalid logical migration supersedes target");}
  return Set.copyOf(values);
 }
 private static Map<Key,List<Candidate>> immutable(Map<Key,List<Candidate>> source){Map<Key,List<Candidate>> result=new HashMap<>();source.forEach((key,candidates)->result.put(key,List.copyOf(candidates)));return Map.copyOf(result);}
 /** Prefer an explicitly complete target only within the same source/component key. */
 private static Target pick(List<Candidate> candidates,boolean ignoreOrientation){
  if(candidates==null||candidates.isEmpty())return null;
  List<Candidate> remaining=new ArrayList<>();for(Candidate candidate:candidates){boolean superseded=false;for(Candidate other:candidates)if(other!=candidate&&other.supersedes.contains(candidate.target.id)&&!sameEffectiveTarget(other.target,candidate.target,ignoreOrientation)){superseded=true;break;}if(!superseded)remaining.add(candidate);}
  Map<TargetKey,Target> unique=new HashMap<>();for(Candidate candidate:remaining)unique.putIfAbsent(effectiveTargetKey(candidate.target,ignoreOrientation),candidate.target);
  return unique.size()==1?unique.values().iterator().next():null;
 }

 private static boolean sameEffectiveTarget(Target first,Target second,boolean ignoreOrientation){return effectiveTargetKey(first,ignoreOrientation).equals(effectiveTargetKey(second,ignoreOrientation));}
 private static Key key(String id,Map<String,String> properties){ArchitectureBlock source=BloodborneBlocks.BLOCKS.get(id);Map<String,String> normalized=new TreeMap<>(source==null||source.definition.defaultProperties==null?Map.of():source.definition.defaultProperties);if(properties!=null)normalized.putAll(properties);return new Key(id,canonical(normalized));}
 private static TargetKey effectiveTargetKey(Target target,boolean ignoreOrientation){Map<String,String> properties=new TreeMap<>(target.properties);ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(target.id);if(block!=null&&block.definition.placement_properties!=null)properties.putAll(block.definition.placement_properties);if(ignoreOrientation){properties.remove("facing");properties.remove("face");}return new TargetKey(target.id,canonical(properties));}

 private static String canonical(Map<String,String> values){List<String> fields=new ArrayList<>();new TreeMap<>(values).forEach((name,value)->fields.add(name+"="+value));return String.join(",",fields);}
 private record Key(String id,String properties) {}
 private record TargetKey(String id,String properties) {}
}
