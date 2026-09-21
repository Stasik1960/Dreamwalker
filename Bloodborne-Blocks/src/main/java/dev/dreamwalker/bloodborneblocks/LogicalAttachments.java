package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Server-side state transitions for logical objects which carry another logical item. */
final class LogicalAttachments {
 private static final String LANTERN="lantern";
 private LogicalAttachments() {}

 static void validateDefinitions(List<BloodborneBlocks.Definition> definitions){
  for(BloodborneBlocks.Definition definition:definitions){
   if(definition.attachment_item==null)continue;
   BooleanProperty property=lanternProperty(definition);
   ArchitectureBlock item=BloodborneBlocks.BLOCKS.get(definition.attachment_item);
   if(property==null||definition.placement_properties==null||!"false".equals(definition.placement_properties.get(LANTERN))||item==null||!item.definition.logical||item.asItem()==net.minecraft.item.Items.AIR)throw new IllegalStateException("Invalid logical attachment "+definition.id);
  }
 }

 static ActionResult use(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||block.definition.attachment_item==null)return null;
  BooleanProperty property=lanternProperty(block.definition);if(property==null)return null;
  ItemStack held=player.getStackInHand(hand);boolean attached=state.get(property);
  ArchitectureBlock item=BloodborneBlocks.BLOCKS.get(block.definition.attachment_item);
  if(attached&&item!=null&&held.isOf(item.asItem()))return ActionResult.FAIL;
  if(!attached&&item!=null&&held.isOf(item.asItem())){
   if(world.isClient)return ActionResult.SUCCESS;
   return replace(state,world,pos,property,true)?consume(player,hand,held):ActionResult.FAIL;
  }
  if(attached&&player.isSneaking()&&held.isEmpty()){
   if(world.isClient)return ActionResult.SUCCESS;
   if(!replace(state,world,pos,property,false))return ActionResult.FAIL;
   if(!player.getAbilities().creativeMode){
    ItemStack returned=new ItemStack(item);
    if(!player.giveItemStack(returned)&&!returned.isEmpty())player.dropItem(returned,false);
   }
   return ActionResult.SUCCESS;
  }
  return null;
 }

 private static ActionResult consume(PlayerEntity player,Hand hand,ItemStack held){
  if(!player.getAbilities().creativeMode)held.decrement(1);
  return ActionResult.SUCCESS;
 }
 private static boolean replace(BlockState state,World world,BlockPos pos,BooleanProperty property,boolean value){
  BlockState next=state.with(property,value);
  if(!GeometryRuntime.allCellsLoaded(world,pos,state)||!GeometryRuntime.allCellsLoaded(world,pos,next)||!GeometryRuntime.canOccupy(world,pos,next,pos))return false;
  world.setBlockState(pos,next,Block.NOTIFY_ALL);
  return world.getBlockState(pos).equals(next);
 }
 private static BooleanProperty lanternProperty(BloodborneBlocks.Definition definition){
  List<String> values=definition.properties==null?null:definition.properties.get(LANTERN);
  if(!definition.logical||values==null||values.size()!=2||!Set.of("false","true").equals(new java.util.HashSet<>(values)))return null;
  return definition.propertyObjects.get(LANTERN) instanceof BooleanProperty property?property:null;
 }
}
