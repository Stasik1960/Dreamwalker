package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Server-side state transitions for logical objects which carry another logical item. */
final class LogicalAttachments {
 private static final String LEGACY_LANTERN="lantern",HAND_LANTERN="hand_lantern",LIT="lit";
 private static final Set<String> HAND_LANTERN_VALUES=Set.of("none","unlit","lit");
 private LogicalAttachments() {}

 static void validateDefinitions(List<BloodborneBlocks.Definition> definitions){
  for(BloodborneBlocks.Definition definition:definitions){
   if(definition.attachment_item==null)continue;
   Property<?> property=attachmentProperty(definition);
   ArchitectureBlock item=BloodborneBlocks.BLOCKS.get(definition.attachment_item);
   boolean legacy=property instanceof BooleanProperty&&definition.placement_properties!=null&&"false".equals(definition.placement_properties.get(LEGACY_LANTERN));
   boolean hand=property!=null&&HAND_LANTERN.equals(property.getName())&&HAND_LANTERN_VALUES.equals(new java.util.HashSet<>(property.getValues().stream().map(value->BloodborneBlocks.value((Property)property,(Comparable)value)).toList()))&&definition.placement_properties!=null&&"none".equals(definition.placement_properties.get(HAND_LANTERN));
   if((!legacy&&!hand)||item==null||!item.definition.logical||item.asItem()==Items.AIR||!isLantern(item))throw new IllegalStateException("Invalid logical attachment "+definition.id);
  }
 }

 static ActionResult use(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||block.definition.attachment_item==null)return null;
  if(!player.getAbilities().allowModifyWorld||!world.canPlayerModifyAt(player,pos))return ActionResult.FAIL;
  Property<?> property=attachmentProperty(block.definition);ArchitectureBlock item=BloodborneBlocks.BLOCKS.get(block.definition.attachment_item);
  if(property==null||item==null)return null;
  ItemStack held=player.getStackInHand(hand);String attached=attachment(state,property);
  if(!"none".equals(attached)&&held.isOf(item.asItem()))return ActionResult.FAIL;
  if("none".equals(attached)&&held.isOf(item.asItem())){
   if(!supportedItemData(held))return ActionResult.FAIL;
   if(world.isClient)return ActionResult.SUCCESS;
   String next=lanternState(held);return replace(state,world,pos,property,next)?consume(player,held):ActionResult.FAIL;
  }
  if(!"none".equals(attached)&&player.isSneaking()&&held.isEmpty()){
   if(world.isClient)return ActionResult.SUCCESS;
   if(!replace(state,world,pos,property,"none"))return ActionResult.FAIL;
   if(!player.isCreative())give(player,itemStack(item,attached));
   return ActionResult.SUCCESS;
  }
  if(!"none".equals(attached)&&held.isEmpty()){
   if(world.isClient)return ActionResult.SUCCESS;
   return replace(state,world,pos,property,"lit".equals(attached)?"unlit":"lit")?ActionResult.SUCCESS:ActionResult.FAIL;
  }
  return null;
 }

 /** Extra loot for a mounted item; empty means this root has no attachment. */
 static ItemStack attachedItem(BlockState state){
  if(!(state.getBlock() instanceof ArchitectureBlock block)||block.definition.attachment_item==null)return ItemStack.EMPTY;
  Property<?> property=attachmentProperty(block.definition);String attached=property==null?"none":attachment(state,property);
  ArchitectureBlock item=BloodborneBlocks.BLOCKS.get(block.definition.attachment_item);
  return !"none".equals(attached)&&item!=null?itemStack(item,attached):ItemStack.EMPTY;
 }

 private static boolean isLantern(ArchitectureBlock item){return "lantern".equals(item.definition.behavior)&&item.getStateManager().getProperty(LIT) instanceof BooleanProperty;}
 private static void give(PlayerEntity player,ItemStack stack){if(!player.giveItemStack(stack)&&!stack.isEmpty())player.dropItem(stack,false);}
 private static ActionResult consume(PlayerEntity player,ItemStack held){if(!player.isCreative())held.decrement(1);return ActionResult.SUCCESS;}
 private static ItemStack itemStack(ArchitectureBlock item,String attached){
  ItemStack stack=new ItemStack(item);NbtCompound tag=stack.getOrCreateSubNbt("BlockStateTag");tag.putString(LIT,"lit".equals(attached)?"true":"false");return stack;
 }
 private static String lanternState(ItemStack stack){
  NbtCompound tag=stack.getSubNbt("BlockStateTag");return tag!=null&&"true".equals(tag.getString(LIT))?"lit":"unlit";
 }
 /** Block state can preserve lit/visual, not custom names or arbitrary item NBT. */
 private static boolean supportedItemData(ItemStack stack){
  NbtCompound nbt=stack.getNbt();if(nbt==null||nbt.isEmpty())return true;
  if(!nbt.getKeys().equals(Set.of("BlockStateTag"))||!nbt.contains("BlockStateTag",10))return false;
  NbtCompound tag=nbt.getCompound("BlockStateTag");
  if(!Set.of("lit","visual").containsAll(tag.getKeys()))return false;
  return (!tag.contains("lit")||Set.of("false","true").contains(tag.getString("lit")))
   &&(!tag.contains("visual")||"base".equals(tag.getString("visual")));
 }
 private static boolean replace(BlockState state,World world,BlockPos pos,Property<?> property,String value){
  BlockState next=BloodborneBlocks.set(state,property,property instanceof BooleanProperty?("none".equals(value)?"false":"true"):value);
  if(!GeometryRuntime.canTransition(world,pos,state,next))return false;
  world.setBlockState(pos,next,Block.NOTIFY_ALL);return world.getBlockState(pos).equals(next);
 }
 private static String attachment(BlockState state,Property<?> property){
  if(property instanceof BooleanProperty legacy)return state.get(legacy)?"unlit":"none";
  return BloodborneBlocks.value((Property)property,(Comparable)state.get((Property)property));
 }
 private static Property<?> attachmentProperty(BloodborneBlocks.Definition definition){
  if(!definition.logical||definition.properties==null)return null;
  List<String> hand=definition.properties.get(HAND_LANTERN);
  if(hand!=null&&HAND_LANTERN_VALUES.equals(new java.util.HashSet<>(hand)))return definition.propertyObjects.get(HAND_LANTERN);
  List<String> legacy=definition.properties.get(LEGACY_LANTERN);
  if(legacy!=null&&legacy.size()==2&&Set.of("false","true").equals(new java.util.HashSet<>(legacy)))return definition.propertyObjects.get(LEGACY_LANTERN);
  return null;
 }
}
