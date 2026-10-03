package dev.dreamwalker.bloodborneblocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

/** Strict schema for generated single-ID owner families with baked rotations. */
final class UnifiedOwnerDefinitions {
 private static final int MAX_VARIANTS=32768;
 private static final List<String> FACING=List.of("north","east","south","west");
 private static final List<String> ROOT_ANCHOR=List.of("canonical","upper_1","upper_2","upper_3","upper_4","upper_5","upper_6","upper_7","upper_8");
 private UnifiedOwnerDefinitions() {}

 static boolean matches(BloodborneBlocks.Definition definition){return definition.unified;}
 static String resourceKey(BloodborneBlocks.Definition definition,Map<String,String> values){
  Map<String,String> complete=new java.util.TreeMap<>(values);
  if(definition!=null&&definition.unified)definition.properties.forEach((name,allowed)->{if(allowed.size()==1)complete.put(name,allowed.get(0));});
  return String.join(",",complete.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());
 }
 static String resourceKey(BloodborneBlocks.Definition definition,String variant){
  if(!definition.unified||variant.equals("inventory"))return variant;
  Map<String,String> values=new java.util.TreeMap<>();for(String part:variant.split(",")){String[] pair=part.split("=",2);if(pair.length==2)values.put(pair[0],pair[1]);}
  return resourceKey(definition,values);
 }

 static void validate(BloodborneBlocks.Definition definition){
  if(!definition.city_compat||definition.logical||!definition.whole_owner||!definition.modular||!definition.extra_facing||!definition.id.matches("owner_unified_[0-9a-f]+")||!"generic".equals(definition.kind)||!"minecraft:stone".equals(definition.source)||definition.semantic==null||definition.semantic.isBlank()||definition.states==null||definition.models==null||definition.defaultProperties==null)throw new IllegalStateException("Invalid unified whole owner definition "+definition.id);
  Map<String,List<String>> properties=definition.properties;
  if(properties==null||!properties.keySet().equals(Set.of("facing","variant","root_anchor"))||!FACING.equals(properties.get("facing"))||!rootAnchors(properties.get("root_anchor"))||!variants(properties.get("variant")))throw new IllegalStateException("Invalid unified whole owner properties "+definition.id);
  if(!definition.defaultProperties.keySet().equals(properties.keySet())||!"north".equals(definition.defaultProperties.get("facing"))||!"0".equals(definition.defaultProperties.get("variant"))||!"canonical".equals(definition.defaultProperties.get("root_anchor")))throw new IllegalStateException("Invalid unified whole owner defaults "+definition.id);
  if(!Map.of("variant","0").equals(definition.placement_properties))throw new IllegalStateException("Invalid unified placement properties "+definition.id);
  Set<String> keys=stateKeys(properties);
  if(!keys.equals(definition.states.keySet())||!keys.equals(definition.models.keySet())||definition.models.values().stream().anyMatch(mesh->mesh==null||!mesh.startsWith(definition.id+"_")))throw new IllegalStateException("Invalid unified whole owner states "+definition.id);
 }

 static Property<String> property(String name,List<String> values){return new UnifiedProperty(name,values);}

 @SuppressWarnings({"rawtypes","unchecked"}) static BlockState canonicalItemRootAnchor(BlockState state){
  state=DocumentOwnerDefinitions.canonicalItemRootAnchor(state);
  if(!(state.getBlock() instanceof ArchitectureBlock block)||!matches(block.definition))return state;
  Property rootAnchor=state.getBlock().getStateManager().getProperty("root_anchor");return rootAnchor==null?state:state.with(rootAnchor,(Comparable)rootAnchor.parse("canonical").orElseThrow());
 }

 private static boolean variants(List<String> values){
  if(values==null||values.isEmpty()||values.size()>MAX_VARIANTS)return false;
  for(int index=0;index<values.size();index++)if(!Integer.toString(index).equals(values.get(index)))return false;
  return true;
 }
 private static boolean rootAnchors(List<String> values){
  if(values==null||values.isEmpty()||values.size()>ROOT_ANCHOR.size())return false;
  return ROOT_ANCHOR.subList(0,values.size()).equals(values);
 }

 private static Set<String> stateKeys(Map<String,List<String>> properties){
  List<String> names=properties.keySet().stream().sorted().toList();Set<String> keys=new LinkedHashSet<>();stateKeys(names,properties,0,new ArrayList<>(),keys);return keys;
 }
 private static void stateKeys(List<String> names,Map<String,List<String>> properties,int index,List<String> entries,Set<String> keys){
  if(index==names.size()){keys.add(String.join(",",entries));return;}
  String name=names.get(index);for(String value:properties.get(name)){entries.add(name+"="+value);stateKeys(names,properties,index+1,entries,keys);entries.remove(entries.size()-1);}
 }

 private static final class UnifiedProperty extends Property<String> {
  private final List<String> values;
  UnifiedProperty(String name,List<String> values){
   super(name,String.class);
   if(values==null||values.isEmpty()||values.size()>MAX_VARIANTS||values.stream().distinct().count()!=values.size()||values.stream().anyMatch(value->value==null||!value.matches("[a-z0-9_]+")))throw new IllegalArgumentException("Invalid unified owner property");
   this.values=List.copyOf(values);
  }
  @Override public Collection<String> getValues(){return values;}
  @Override public Optional<String> parse(String value){return values.contains(value)?Optional.of(value):Optional.empty();}
  @Override public String name(String value){return value;}
  @Override public boolean equals(Object other){return this==other||other instanceof UnifiedProperty property&&super.equals(other)&&values.equals(property.values);}
  @Override public int computeHashCode(){return 31*super.computeHashCode()+values.hashCode();}
 }
}
