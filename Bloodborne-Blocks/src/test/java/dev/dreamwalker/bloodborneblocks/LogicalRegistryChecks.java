package dev.dreamwalker.bloodborneblocks;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
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
   Registry.register(Registries.BLOCK,BloodborneBlocks.id(definition.id),block);
   ArchitectureBlockItem item=new ArchitectureBlockItem(block,new Item.Settings());
   Registry.register(Registries.ITEM,BloodborneBlocks.id(definition.id),item);
   item.appendBlocks(Item.BLOCK_ITEMS,item);
   BloodborneBlocks.BLOCKS.put(definition.id,block);
   if(state.contains(Properties.HORIZONTAL_FACING))check(block.rotate(state,BlockRotation.CLOCKWISE_90).get(Properties.HORIZONTAL_FACING)==BlockRotation.CLOCKWISE_90.rotate(state.get(Properties.HORIZONTAL_FACING)),"rotation preserves object identity: "+definition.id);
   if(definition.properties.containsKey("visual"))check(block.getStateManager().getProperty("visual")!=null,"visual state property: "+definition.id);
   check(definition.states.keySet().equals(definition.models.keySet()),"one production mesh per state: "+definition.id);
   if(definition.properties.containsKey("root_anchor"))checkExplicitRootStates(block);
  }
  check(ids.equals(BloodborneBlocks.productionPalette().keySet()),"definitions exactly match production palette");
  LogicalAttachments.validateDefinitions(data.blocks);
  System.out.println("LOGICAL REGISTRY CHECKS PASSED: production="+ids.size());
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

 private static void checkExplicitRootStates(ArchitectureBlock block){
  check(Set.of("o_bench","o_high_balustrade").contains(block.definition.id),"root exception family whitelist");
  var property=block.getStateManager().getProperty("root_anchor");
  var origin=new net.minecraft.util.math.BlockPos(-374,73,-291);var moved=origin.up();
  for(BlockState upper:block.getStateManager().getStates()){
   if(!BloodborneBlocks.key(upper).contains("root_anchor=upper"))continue;
   BlockState canonical=BloodborneBlocks.set(upper,property,"canonical");
   var a=GeometryRuntime.state(canonical);var b=GeometryRuntime.state(upper);
   for(int i=0;i<3;i++)check(a.render_offset[i]==b.render_offset[i]+(i==1?1:0),"root render compensation");
   check(a.parsedCells.containsKey(new net.minecraft.util.math.BlockPos(0,1,0)),"new root was owned");
   for(var cell:b.parsedCells.keySet())check(a.parsedCells.containsKey(cell.up()),"physical footprint must not expand");
   check(!b.parsedCells.containsKey(new net.minecraft.util.math.BlockPos(0,-1,0)),"shared canonical root released");
   var oldPlaced=LogicalTransform.masterOrigin(origin,a.anchor,a.rotation);
   var newPlaced=LogicalTransform.masterOrigin(origin,b.anchor,b.rotation);
   check(newPlaced.equals(oldPlaced.up()),"manual placement uses converter root policy");
   check(FunctionalFurniture.seatPoints(origin,canonical).equals(FunctionalFurniture.seatPoints(moved,upper)),"seat world coordinates preserved");
   check("upper".equals(block.getPickStack(null,moved,upper).getSubNbt("BlockStateTag").getString("root_anchor")),"pick retains root policy");
   for(BlockRotation rotation:BlockRotation.values())check(BloodborneBlocks.key(block.rotate(upper,rotation)).contains("root_anchor=upper"),"rotation retains technical root");
  }
 }
}
