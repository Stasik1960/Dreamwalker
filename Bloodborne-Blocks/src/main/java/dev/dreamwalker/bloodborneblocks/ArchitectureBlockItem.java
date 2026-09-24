package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.BlockState;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Shifts authored multi-cell objects so their minimum occupied cell is the clicked cell. */
public final class ArchitectureBlockItem extends BlockItem {
 public ArchitectureBlockItem(ArchitectureBlock block,Settings settings){super(block,settings);}

 @Override public ActionResult place(ItemPlacementContext original){
  ArchitectureBlock source=(ArchitectureBlock)getBlock();
  ItemStack working=new ItemStack(source,original.getStack().getCount());
  if(original.getStack().hasNbt())working.setNbt(original.getStack().getNbt().copy());
  ItemStack placedStack=working;
  ItemPlacementContext prepared=new ItemPlacementContext(original){@Override public ItemStack getStack(){return placedStack;}};
  ActionResult result=placePrepared(prepared);
  if(result.isAccepted())original.getStack().decrement(Math.max(0,original.getStack().getCount()-working.getCount()));
  return result;
 }

 private ActionResult placePrepared(ItemPlacementContext original){
  ArchitectureBlock block=(ArchitectureBlock)getBlock();
  BlockState tentative=block.getPlacementState(original);if(tentative==null)return ActionResult.FAIL;
  normalizePlacementTag(original.getStack(),block,tentative);tentative=applyStateTag(tentative,original.getStack());
  BlockPos anchor=GeometryRuntime.anchor(tentative,original.getSide());
  BlockPos root=GeometryRuntime.hasExplicitAnchor(tentative)
   ?LogicalTransform.masterOrigin(original.getBlockPos(),new int[]{anchor.getX(),anchor.getY(),anchor.getZ()},GeometryRuntime.rotation(tentative))
   :original.getBlockPos().subtract(anchor);
  Vec3d delta=Vec3d.of(root.subtract(original.getBlockPos()));
  ItemPlacementContext shifted=new ItemPlacementContext(original){
   @Override public BlockPos getBlockPos(){return root;}
   @Override public Vec3d getHitPos(){return original.getHitPos().add(delta);}
   @Override public ItemStack getStack(){return original.getStack();}
   @Override public boolean canPlace(){return getWorld().getBlockState(root).canReplace(this);}
  };
  if(!shifted.canPlace())return ActionResult.FAIL;
  BlockState base=block.getPlacementState(shifted);if(base==null)return ActionResult.FAIL;
  normalizePlacementTag(original.getStack(),block,base);
  BlockState finalState=applyStateTag(base,original.getStack());
  if(!GeometryRuntime.canPlace(original.getWorld(),root,finalState)||!block.canPlaceConventionalDoor(original.getWorld(),root,finalState))return ActionResult.FAIL;
  return super.place(shifted);
 }

 @Override protected BlockState getPlacementState(ItemPlacementContext context){
  BlockState base=getBlock().getPlacementState(context);if(base==null)return null;
  normalizePlacementTag(context.getStack(),(ArchitectureBlock)getBlock(),base);
  BlockState state=applyStateTag(base,context.getStack());return canPlace(context,state)?state:null;
 }

 static void normalizePlacementTag(ItemStack stack,ArchitectureBlock block,BlockState placement){
  NbtCompound properties=stack.getOrCreateSubNbt("BlockStateTag");
  if(placement.contains(BloodborneBlocks.ASSEMBLED))properties.putString("assembled","true");
  if(placement.contains(net.minecraft.state.property.Properties.WATERLOGGED))properties.putString("waterlogged",Boolean.toString(placement.get(net.minecraft.state.property.Properties.WATERLOGGED)));
  if(block.definition.kind.equals("slab"))properties.putString("type",placement.get(net.minecraft.state.property.Properties.SLAB_TYPE).asString());
  if(block.definition.kind.equals("stairs")){
   properties.putString("facing",placement.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING).asString());
   properties.putString("half",placement.get(net.minecraft.state.property.Properties.BLOCK_HALF).asString());
   properties.putString("shape",placement.get(net.minecraft.state.property.Properties.STAIR_SHAPE).asString());
  }
  boolean authored=block.definition.logical||block.definition.kind.equals("generic")||block.definition.kind.equals("model_door");
  if(authored){
   Property<?> facing=block.getStateManager().getProperty("facing");if(facing!=null)properties.putString("facing",BloodborneBlocks.value((Property)facing,(Comparable)placement.get((Property)facing)));
   Property<?> axis=block.getStateManager().getProperty("axis");if(axis!=null)properties.putString("axis",BloodborneBlocks.value((Property)axis,(Comparable)placement.get((Property)axis)));
  }
  if(block.definition.logical){
   for(String name:java.util.List.of("face","north","east","south","west","up","down")){
    Property<?> property=block.getStateManager().getProperty(name);
    if(property!=null)properties.putString(name,BloodborneBlocks.value((Property)property,(Comparable)placement.get((Property)property)));
   }
   if(placement.contains(net.minecraft.state.property.Properties.OPEN))properties.putString("open","false");
   Property<?> lit=block.getStateManager().getProperty("lit");if(lit instanceof net.minecraft.state.property.BooleanProperty value)properties.putString("lit",Boolean.toString(placement.get(value)));
   if(block.definition.placement_properties!=null)block.definition.placement_properties.forEach(properties::putString);
  }
  if(block.definition.kind.equals("door")){
   if(placement.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF))properties.putString("half","lower");
   if(placement.contains(net.minecraft.state.property.Properties.OPEN))properties.putString("open","false");
  }
  if(block.definition.kind.equals("model_door")){
   if(placement.contains(net.minecraft.state.property.Properties.BLOCK_HALF))properties.putString("half","bottom");
   if(placement.contains(net.minecraft.state.property.Properties.STAIR_SHAPE))properties.putString("shape","straight");
   if(placement.contains(net.minecraft.state.property.Properties.OPEN))properties.putString("open","false");
  }
 }

 @SuppressWarnings({"rawtypes","unchecked"}) static BlockState applyStateTag(BlockState state,ItemStack stack){
  NbtCompound properties=stack.getSubNbt("BlockStateTag");if(properties==null)return state;
  for(String name:properties.getKeys()){
   Property property=state.getBlock().getStateManager().getProperty(name);if(property==null)continue;
   java.util.Optional value=property.parse(properties.getString(name));if(value.isPresent())state=state.with(property,(Comparable)value.get());
  }
  return state;
 }
}
