package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bootstrap-only gate for the bounded city compatibility registry. */
public final class CityCompatibilityChecks {
 private CityCompatibilityChecks() {}

 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  unifiedOwnerSchema();
  BloodborneBlocks.Data production=BloodborneBlocks.loadDefinitions();
  check(production.blocks.size()==50,"production membership remains 50");
  GeometryRuntime.loadAndValidate(production);
  BloodborneBlocks.Data city=BloodborneBlocks.loadCityDefinitions();
  GeometryRuntime.loadCityAndValidate(city);
  int pages=0,unified=0,nativeBlocks=0,states=0;
  var meshes=ModularMeshData.loadCityIfPresent();
  for(BloodborneBlocks.Definition definition:city.blocks){
   BloodborneBlocks.prepareDefinition(definition);
   ArchitectureBlock block=ArchitectureBlock.create(definition);
   check(definition.city_compat&&!definition.logical,"city marker: "+definition.id);
   if(ReviewedWallConnections.ID.equals(definition.id)){
    ReviewedWallConnections.validate(definition,city);
    check(block instanceof SharedArchitectureBlock,"reviewed building retains shared ownership");
    check(block.getStateManager().getStates().size()==128,"reviewed building exact supported states");
    for(String mesh:definition.models.values())check(meshes.containsKey(mesh)&&!meshes.get(mesh).polygons.isEmpty(),"reviewed wall existing mesh: "+mesh);
    for(var state:block.getStateManager().getStates())check(GeometryRuntime.state(state).parsedCells.keySet().equals(java.util.Set.of(BlockPos.ORIGIN)),"reviewed wall remains one physical cell");
   }else if(DocumentOwnerDefinitions.manual(definition)){
    check(definition.models!=null&&DocumentOwnerDefinitions.ids().contains(definition.id),"documented whole owner art: "+definition.id);
    check(GeometryRuntime.usesHelpers(block),"documented whole owner helper lifecycle: "+definition.id);
    check(GeometryRuntime.rebuildsHelperTransitions(block),"documented whole owner helper state transitions: "+definition.id);
    int product=definition.properties.values().stream().mapToInt(List::size).reduce(1,Math::multiplyExact);
    check(product<=4*2*3*9&&block.getStateManager().getStates().size()==product,"documented whole owner Cartesian states: "+definition.id);
    check(block.getStateManager().getProperty("facing")!=null,"documented whole owner pivot rotation: "+definition.id);
    for(String mesh:definition.models.values())check(meshes.containsKey(mesh)&&!meshes.get(mesh).polygons.isEmpty(),"documented whole owner complete mesh: "+mesh);
   }else if(definition.unified){
    unified++;
    int product=definition.properties.values().stream().mapToInt(List::size).reduce(1,Math::multiplyExact);
    check(definition.models!=null&&definition.id.startsWith("owner_unified_"),"unified whole owner art: "+definition.id);
    check(GeometryRuntime.usesHelpers(block)&&GeometryRuntime.rebuildsHelperTransitions(block),"unified whole owner helper lifecycle: "+definition.id);
    check(block.getStateManager().getStates().size()==product,"unified whole owner Cartesian states: "+definition.id);
    check(block.getStateManager().getProperty("facing")!=null,"unified whole owner pivot rotation: "+definition.id);
    if(definition.properties.get("root_anchor").contains("upper_1")){
     ItemStack stale=new ItemStack(block);stale.getOrCreateSubNbt("BlockStateTag").putString("root_anchor","upper_1");BlockState tagged=ArchitectureBlockItem.applyStateTag(block.getDefaultState(),stale);BlockState canonical=UnifiedOwnerDefinitions.canonicalItemRootAnchor(tagged);Property<?> rootAnchor=block.getStateManager().getProperty("root_anchor");check("canonical".equals(BloodborneBlocks.value((Property)rootAnchor,(Comparable)canonical.get((Property)rootAnchor))),"unified item tag resets offline root anchor: "+definition.id);
    }
    for(String mesh:definition.models.values())check(meshes.containsKey(mesh)&&!meshes.get(mesh).polygons.isEmpty(),"unified whole owner complete mesh: "+mesh);
   }else if(definition.whole_owner){
    check(definition.models!=null&&definition.id.startsWith("owner_"),"whole owner art: "+definition.id);
    check(GeometryRuntime.usesHelpers(block),"whole owner helper lifecycle: "+definition.id);
    check(GeometryRuntime.rebuildsHelperTransitions(block),"whole owner helper state transitions: "+definition.id);
    check(block.getStateManager().getStates().size()==4,"whole owner rotation states: "+definition.id);
    check(block.getStateManager().getProperty("facing")!=null,"whole owner pivot rotation: "+definition.id);
    for(String mesh:definition.models.values())check(meshes.containsKey(mesh)&&!meshes.get(mesh).polygons.isEmpty(),"whole owner complete mesh: "+mesh);
   }else if(definition.models!=null){
    pages++;check(definition.modular&&block.getStateManager().getProperty("facing")==null,"module page is cell-local without facing: "+definition.id);
    check(!GeometryRuntime.usesHelpers(block),"module page remains cell-local: "+definition.id);
    check(block.getStateManager().getStates().size()<=16,"module page state bound: "+definition.id);
    for(String variant:definition.properties.get("variant"))check(("variant="+variant).equals(BloodborneBlocks.cityVariantModelKey(definition,variant)),"item variant resolves its baked state: "+definition.id+"/"+variant);
    check(BloodborneBlocks.cityVariantModelKey(definition,"invalid")==null,"invalid item variant falls back: "+definition.id);
   }else nativeBlocks++;
   for(var state:block.getStateManager().getStates()){
    states++;GeometryRuntime.GeometryState geometry=GeometryRuntime.state(state);
    check(geometry!=null&&geometry.parsedCells!=null&&!geometry.parsedCells.isEmpty(),"prepared city geometry: "+state);
    if(definition.models!=null&&!definition.whole_owner)check(geometry.parsedCells.keySet().equals(java.util.Set.of(BlockPos.ORIGIN)),"module page has no helper cells: "+state);
   }
  }
  check((pages+unified)>0&&nativeBlocks>0,"city registry contains authored owners or module pages and native blocks");
  System.out.println("CITY COMPATIBILITY CHECKS PASSED: blocks="+city.blocks.size()+" pages="+pages+" unified="+unified+" native="+nativeBlocks+" states="+states);
 }
 private static void unifiedOwnerSchema(){
  BloodborneBlocks.Definition definition=unifiedOwner(17);
  UnifiedOwnerDefinitions.validate(definition);
  BloodborneBlocks.prepareDefinition(definition);
  check(definition.propertyObjects.get("variant").parse("16").isPresent(),"unified owner accepts variants beyond legacy page size");
  check(UnifiedOwnerDefinitions.property("variant",variants(32768)).parse("32767").isPresent(),"unified owner supports the bounded variant maximum");
  check(definition.propertyObjects.get("root_anchor").parse("upper_8").isPresent(),"unified owner accepts all offline root anchors");
  BloodborneBlocks.Definition canonicalOnly=unifiedOwner(17);canonicalOnly.properties.put("root_anchor",List.of("canonical"));canonicalOnly.defaultProperties=Map.of("facing","north","variant","0","root_anchor","canonical");canonicalOnly.states.clear();canonicalOnly.models.clear();populateStates(canonicalOnly);UnifiedOwnerDefinitions.validate(canonicalOnly);
  BloodborneBlocks.prepareDefinition(canonicalOnly);
  check(!canonicalOnly.propertyObjects.containsKey("root_anchor"),"constant root anchor does not create an invalid one-value runtime property");
  BloodborneBlocks.Definition pruned=unifiedOwner(3);pruned.properties.put("variant",List.of("0","2"));pruned.states.clear();pruned.models.clear();populateStates(pruned);UnifiedOwnerDefinitions.validate(pruned);BloodborneBlocks.prepareDefinition(pruned);
  check(pruned.propertyObjects.get("variant").parse("2").isPresent()&&pruned.propertyObjects.get("variant").parse("1").isEmpty(),"pruning preserves saved numeric variant names without renumbering");
  check(Properties.HORIZONTAL_FACING.parse("north").map(facing->BlockRotation.CLOCKWISE_90.rotate(facing).asString()).orElseThrow().equals("east"),"unified owner facing uses vanilla view-angle rotation");
  definition.states.remove("facing=north,root_anchor=canonical,variant=0");
  expectInvalid(definition,"unified owner rejects state gaps");
 }
 private static BloodborneBlocks.Definition unifiedOwner(int count){
  BloodborneBlocks.Definition definition=new BloodborneBlocks.Definition();definition.id="owner_unified_0123abcd";definition.source="minecraft:stone";definition.kind="generic";definition.offset="none";definition.semantic="tree";definition.city_compat=true;definition.whole_owner=true;definition.modular=true;definition.extra_facing=true;definition.unified=true;
  definition.properties=new LinkedHashMap<>();definition.properties.put("facing",List.of("north","east","south","west"));definition.properties.put("variant",variants(count));definition.properties.put("root_anchor",List.of("canonical","upper_1","upper_2","upper_3","upper_4","upper_5","upper_6","upper_7","upper_8"));
  definition.defaultProperties=Map.of("facing","north","variant","0","root_anchor","canonical");definition.placement_properties=Map.of("variant","0");definition.states=new LinkedHashMap<>();definition.models=new LinkedHashMap<>();populateStates(definition);
  return definition;
 }
 private static void populateStates(BloodborneBlocks.Definition definition){int mesh=0;
  for(String facing:definition.properties.get("facing"))for(String anchor:definition.properties.get("root_anchor"))for(String variant:definition.properties.get("variant")){
   String key="facing="+facing+",root_anchor="+anchor+",variant="+variant;definition.states.put(key,new int[]{0,0,0});definition.models.put(key,definition.id+"_"+(mesh++));
  }
 }
 private static List<String> variants(int count){java.util.ArrayList<String> values=new java.util.ArrayList<>();for(int index=0;index<count;index++)values.add(Integer.toString(index));return values;}
 private static void expectInvalid(BloodborneBlocks.Definition definition,String message){try{UnifiedOwnerDefinitions.validate(definition);throw new AssertionError(message);}catch(IllegalStateException expected){}}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
