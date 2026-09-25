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
  check(production.blocks.size()==49,"production membership remains 49");
  GeometryRuntime.loadAndValidate(production);
  BloodborneBlocks.Data city=BloodborneBlocks.loadCityDefinitions();
  GeometryRuntime.loadCityAndValidate(city);
  int pages=0,nativeBlocks=0,states=0;
  for(BloodborneBlocks.Definition definition:city.blocks){
   BloodborneBlocks.prepareDefinition(definition);
   ArchitectureBlock block=ArchitectureBlock.create(definition);
   check(definition.city_compat&&!definition.logical,"city marker: "+definition.id);
   if(definition.models!=null){
    pages++;check(definition.modular&&block.getStateManager().getProperty("facing")==null,"module page is cell-local without facing: "+definition.id);
    check(block.getStateManager().getStates().size()<=16,"module page state bound: "+definition.id);
    for(String variant:definition.properties.get("variant"))check(("variant="+variant).equals(BloodborneBlocks.cityVariantModelKey(definition,variant)),"item variant resolves its baked state: "+definition.id+"/"+variant);
    check(BloodborneBlocks.cityVariantModelKey(definition,"invalid")==null,"invalid item variant falls back: "+definition.id);
   }else nativeBlocks++;
   for(var state:block.getStateManager().getStates()){
    states++;GeometryRuntime.GeometryState geometry=GeometryRuntime.state(state);
    check(geometry!=null&&geometry.parsedCells!=null&&!geometry.parsedCells.isEmpty(),"prepared city geometry: "+state);
    if(definition.models!=null)check(geometry.parsedCells.keySet().equals(java.util.Set.of(BlockPos.ORIGIN)),"module page has no helper cells: "+state);
   }
  }
  check(pages>0&&nativeBlocks>0,"city registry contains module pages and native blocks");
  System.out.println("CITY COMPATIBILITY CHECKS PASSED: blocks="+city.blocks.size()+" pages="+pages+" native="+nativeBlocks+" states="+states);
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
