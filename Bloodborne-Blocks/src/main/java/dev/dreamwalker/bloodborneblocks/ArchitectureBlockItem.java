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
  ArchitectureBlock block=(ArchitectureBlock)getBlock();
  BlockState tentative=block.getPlacementState(original);if(tentative==null)return ActionResult.FAIL;
  normalizePlacementTag(original.getStack(),block,tentative);tentative=applyStateTag(tentative,original.getStack());
  BlockPos anchor=GeometryRuntime.anchor(tentative,original.getSide());BlockPos root=original.getBlockPos().subtract(anchor);
  Vec3d delta=Vec3d.of(root.subtract(original.getBlockPos()));
  ItemPlacementContext shifted=new ItemPlacementContext(original.getWorld(),original.getPlayer(),original.getHand(),original.getStack(),new BlockHitResult(original.getHitPos().add(delta),original.getSide(),root,original.hitsInsideBlock()));
  // ItemPlacementContext silently offsets away from a non-replaceable hit block.
  // Never let that move placement away from the root covered by the preflight below.
  if(!shifted.getBlockPos().equals(root))return ActionResult.FAIL;
  if(!shifted.canPlace())return ActionResult.FAIL;
  BlockState baseState=block.getPlacementState(shifted);if(baseState==null)return ActionResult.FAIL;
  normalizePlacementTag(original.getStack(),block,baseState);BlockState finalState=applyStateTag(baseState,original.getStack());
  if(finalState==null||!GeometryRuntime.canPlace(original.getWorld(),root,finalState)||!block.canPlaceConventionalDoor(original.getWorld(),root,finalState))return ActionResult.FAIL;
  ActionResult result=super.place(shifted);
  if(result.isAccepted()&&!original.getWorld().isClient){
   BlockState placed=original.getWorld().getBlockState(root);
   if(placed.isOf(block))GeometryRuntime.rebuild(original.getWorld(),root,placed);
  }
  return result;
 }

 private static void normalizePlacementTag(ItemStack stack,ArchitectureBlock block,BlockState placement){
  NbtCompound properties=stack.getOrCreateSubNbt("BlockStateTag");
  if(placement.contains(BloodborneBlocks.ASSEMBLED))properties.putString("assembled","true");
  boolean authored=block.definition.kind.equals("generic")||block.definition.kind.equals("model_door");
  if(authored){
   Property<?> facing=block.getStateManager().getProperty("facing");if(facing!=null)properties.putString("facing",BloodborneBlocks.value((Property)facing,(Comparable)placement.get((Property)facing)));
   Property<?> axis=block.getStateManager().getProperty("axis");if(axis!=null)properties.putString("axis",BloodborneBlocks.value((Property)axis,(Comparable)placement.get((Property)axis)));
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

 @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState applyStateTag(BlockState state,ItemStack stack){
  NbtCompound properties=stack.getSubNbt("BlockStateTag");if(properties==null)return state;
  for(String name:properties.getKeys()){
   Property property=state.getBlock().getStateManager().getProperty(name);if(property==null)continue;
   java.util.Optional value=property.parse(properties.getString(name));if(value.isPresent())state=state.with(property,(Comparable)value.get());
  }
  return state;
 }
}
