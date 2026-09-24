package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;

import java.util.HashSet;
import java.util.Set;

/** Bootstrap-only production registry contract; no compatibility IDs or migration resources. */
public final class LogicalRegistryChecks {
 private LogicalRegistryChecks() {}

 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  BloodborneBlocks.Data data=BloodborneBlocks.loadDefinitions();GeometryRuntime.loadAndValidate(data);
  Set<String> ids=new HashSet<>();
  for(BloodborneBlocks.Definition definition:data.blocks){
   check(definition.logical&&definition.id.startsWith("o_"),"production definition is logical: "+definition.id);
   check(BloodborneBlocks.productionEntry(definition.id)!=null&&ids.add(definition.id),"manifest has one definition: "+definition.id);
   BloodborneBlocks.prepareDefinition(definition);ArchitectureBlock block=ArchitectureBlock.create(definition);BlockState state=block.getDefaultState();
   if(state.contains(Properties.HORIZONTAL_FACING))check(block.rotate(state,BlockRotation.CLOCKWISE_90).get(Properties.HORIZONTAL_FACING)==BlockRotation.CLOCKWISE_90.rotate(state.get(Properties.HORIZONTAL_FACING)),"rotation preserves object identity: "+definition.id);
   if(definition.properties.containsKey("visual"))check(block.getStateManager().getProperty("visual")!=null,"visual state property: "+definition.id);
   check(definition.states.keySet().equals(definition.models.keySet()),"one production mesh per state: "+definition.id);
  }
  check(ids.equals(BloodborneBlocks.productionPalette().keySet()),"definitions exactly match production palette");
  LogicalAttachments.validateDefinitions(data.blocks);
  System.out.println("LOGICAL REGISTRY CHECKS PASSED: production="+ids.size());
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
