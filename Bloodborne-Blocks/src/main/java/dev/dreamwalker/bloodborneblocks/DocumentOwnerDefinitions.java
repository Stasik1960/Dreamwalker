package dev.dreamwalker.bloodborneblocks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

/** Strict schema for the manually documented multipart owners. */
final class DocumentOwnerDefinitions {
 private static final List<String> FACING=List.of("north","east","south","west");
 private static final List<String> OPEN=List.of("false","true");
 private static final List<String> FACE=List.of("floor","wall","ceiling");
 private static final List<String> ROOT_ANCHOR=List.of("canonical","upper_1","upper_2","upper_3","upper_4","upper_5","upper_6","upper_7","upper_8");
 private DocumentOwnerDefinitions() {}

 static boolean manual(BloodborneBlocks.Definition definition){return definition.document_item!=0;}
 static Set<String> ids(){
  Set<String> ids=new LinkedHashSet<>();for(int item=1;item<=21;item++)ids.add(id(item,""));ids.add(id(3,"_bracket"));ids.add(id(7,"_cap"));return Set.copyOf(ids);
 }
 static void validate(BloodborneBlocks.Definition definition){
  int item=definition.document_item;
  if(item<1||item>21||!definition.id.equals(id(item,""))&&!definition.id.equals(id(item,"_cap"))&&!definition.id.equals(id(item,"_bracket"))||!definition.city_compat||definition.logical||!definition.whole_owner||!definition.modular||!definition.extra_facing||!"minecraft:stone".equals(definition.source)||!("generic".equals(definition.kind)||"model_door".equals(definition.kind))||definition.models==null||definition.defaultProperties==null)throw new IllegalStateException("Invalid documented whole owner definition "+definition.id);
  if(definition.id.endsWith("_cap")&&item!=7||definition.id.endsWith("_bracket")&&item!=3)throw new IllegalStateException("Invalid documented whole owner suffix "+definition.id);
  Map<String,List<String>> properties=definition.properties;
  if(properties==null||!properties.keySet().stream().allMatch(name->Set.of("facing","open","face","root_anchor").contains(name))||!properties.containsKey("facing")||!FACING.equals(properties.get("facing"))||!optional(properties,"open",OPEN)||!optional(properties,"face",FACE)||!optional(properties,"root_anchor",ROOT_ANCHOR))throw new IllegalStateException("Invalid documented whole owner properties "+definition.id);
  if(!definition.defaultProperties.keySet().equals(properties.keySet()))throw new IllegalStateException("Invalid documented whole owner defaults "+definition.id);
  for(var entry:properties.entrySet())if(!entry.getValue().contains(definition.defaultProperties.get(entry.getKey())))throw new IllegalStateException("Invalid documented whole owner default "+definition.id+"."+entry.getKey());
  Set<String> keys=stateKeys(properties);if(keys.size()>4*2*3*9||!keys.equals(definition.states.keySet())||!keys.equals(definition.models.keySet())||definition.models.values().stream().anyMatch(mesh->mesh==null||!mesh.startsWith(definition.id+"_")))throw new IllegalStateException("Invalid documented whole owner states "+definition.id);
 }
 static void validateMembership(List<BloodborneBlocks.Definition> definitions){
  Set<String> found=new LinkedHashSet<>();for(BloodborneBlocks.Definition definition:definitions)if(manual(definition)){if(!found.add(definition.id))throw new IllegalStateException("Duplicate documented whole owner "+definition.id);}
  if(!found.equals(ids()))throw new IllegalStateException("Documented whole owner membership mismatch: "+found);
 }
 @SuppressWarnings({"rawtypes","unchecked"}) static BlockState canonicalItemRootAnchor(BlockState state){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||!manual(block.definition))return state;
  Property rootAnchor=state.getBlock().getStateManager().getProperty("root_anchor");return rootAnchor==null?state:state.with(rootAnchor,(Comparable)rootAnchor.parse("canonical").orElseThrow());
 }
 private static boolean optional(Map<String,List<String>> properties,String key,List<String> values){return !properties.containsKey(key)||values.equals(properties.get(key));}
 private static String id(int item,String suffix){return String.format("owner_final_%02d%s",item,suffix);}
 private static Set<String> stateKeys(Map<String,List<String>> properties){
  List<String> names=properties.keySet().stream().sorted().toList();Set<String> keys=new LinkedHashSet<>();stateKeys(names,properties,0,new java.util.ArrayList<>(),keys);return keys;
 }
 private static void stateKeys(List<String> names,Map<String,List<String>> properties,int index,List<String> entries,Set<String> keys){
  if(index==names.size()){keys.add(String.join(",",entries));return;}
  String name=names.get(index);for(String value:properties.get(name)){entries.add(name+"="+value);stateKeys(names,properties,index+1,entries,keys);entries.remove(entries.size()-1);}
 }
}
