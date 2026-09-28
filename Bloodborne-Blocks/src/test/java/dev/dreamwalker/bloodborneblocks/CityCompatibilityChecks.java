package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.BlockPos;

/** Bootstrap-only gate for the bounded city compatibility registry. */
public final class CityCompatibilityChecks {
 private CityCompatibilityChecks() {}

 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data production=BloodborneBlocks.loadDefinitions();
  check(production.blocks.size()==57,"production membership includes all eight authored grass alternatives");
  GeometryRuntime.loadAndValidate(production);
  BloodborneBlocks.Data city=BloodborneBlocks.loadCityDefinitions();
  GeometryRuntime.loadCityAndValidate(city);
  rejectsVariants("variant",java.util.stream.IntStream.range(0,65).mapToObj(i->"v_"+i).toList());
  rejectsVariants("connection",java.util.stream.IntStream.range(0,88).mapToObj(i->"c_"+i).toList());
  rejectsVariants("connection",java.util.List.of("low_0","low_0"));
  int pages=0,nativeBlocks=0,states=0;
  var meshes=ModularMeshData.loadCityIfPresent();
  for(BloodborneBlocks.Definition definition:city.blocks){
   BloodborneBlocks.prepareDefinition(definition);
   ArchitectureBlock block=ArchitectureBlock.create(definition);
   check(definition.city_compat&&!definition.logical,"city marker: "+definition.id);
   if(ReviewedWallConnections.ID.equals(definition.id)){
    ReviewedWallConnections.validate(definition,city);
    check(block instanceof SharedArchitectureBlock,"reviewed building retains shared ownership");
    check(definition.properties.get("connection").size()==87,"reviewed wall retains 32 canonical and 55 authored connections");
    check(block.getStateManager().getStates().size()==348,"reviewed building exact supported states");
    for(String mesh:definition.models.values())check(meshes.containsKey(mesh)&&!meshes.get(mesh).polygons.isEmpty(),"reviewed wall existing mesh: "+mesh);
    for(var state:block.getStateManager().getStates())check(GeometryRuntime.state(state).parsedCells.keySet().equals(java.util.Set.of(BlockPos.ORIGIN)),"reviewed wall remains one physical cell");
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
  check(pages>0&&nativeBlocks>0,"city registry contains module pages and native blocks");
  System.out.println("CITY COMPATIBILITY CHECKS PASSED: blocks="+city.blocks.size()+" pages="+pages+" native="+nativeBlocks+" states="+states);
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void rejectsVariants(String name,java.util.List<String> values){try{new LogicalVariantProperty(name,values);}catch(IllegalArgumentException expected){return;}throw new AssertionError("variant budget/uniqueness must reject "+name+" with "+values.size()+" values");}
}
