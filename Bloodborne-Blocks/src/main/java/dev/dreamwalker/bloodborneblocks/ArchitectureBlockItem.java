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
  if(PaletteAliases.removed(source.definition.id))return ActionResult.FAIL;
  ArchitectureBlock canonical=PaletteAliases.canonical(source);
  ItemStack working=new ItemStack(canonical,original.getStack().getCount());
  if(original.getStack().hasNbt())working.setNbt(original.getStack().getNbt().copy());
  if(canonical!=source){
   BlockState selected=PaletteAliases.replacement(applyStateTag(source.getDefaultState(),working)).state();
   NbtCompound props=new NbtCompound();selected.getEntries().forEach((p,v)->props.putString(p.getName(),BloodborneBlocks.value(p,v)));
   working.getOrCreateNbt().put("BlockStateTag",props);
  }
  ArchitectureBlock section=LegacyItemSections.target(canonical,working);if(section==null)return ActionResult.FAIL;
  if(section!=canonical){
   ItemStack migrated=new ItemStack(section,working.getCount());
   if(working.hasNbt()){migrated.setNbt(working.getNbt().copy());migrated.removeSubNbt("BlockStateTag");}
   working=migrated;canonical=section;
  }
  ItemStack placedStack=working;
  ItemPlacementContext prepared=new ItemPlacementContext(original){@Override public ItemStack getStack(){return placedStack;}};
  ActionResult result=((ArchitectureBlockItem)canonical.asItem()).placePrepared(prepared);
  if(result.isAccepted())original.getStack().decrement(Math.max(0,original.getStack().getCount()-working.getCount()));
  return result;
 }

 private ActionResult placePrepared(ItemPlacementContext original){
  ArchitectureBlock block=(ArchitectureBlock)getBlock();
  BlockState tentative=block.getPlacementState(original);if(tentative==null)return ActionResult.FAIL;
  normalizePlacementTag(original.getStack(),block,tentative);tentative=applyStateTag(tentative,original.getStack());
  BlockPos root=original.getBlockPos().subtract(GeometryRuntime.anchor(tentative,original.getSide()));
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
  BlockState state=applyStateTag(base,context.getStack());return canPlace(context,state)?state:null;
 }

 private static void normalizePlacementTag(ItemStack stack,ArchitectureBlock block,BlockState placement){
  NbtCompound properties=stack.getOrCreateSubNbt("BlockStateTag");
  if(placement.contains(BloodborneBlocks.ASSEMBLED))properties.putString("assembled","true");
  if(placement.contains(net.minecraft.state.property.Properties.WATERLOGGED))properties.putString("waterlogged",Boolean.toString(placement.get(net.minecraft.state.property.Properties.WATERLOGGED)));
  if(block.definition.kind.equals("slab"))properties.putString("type",placement.get(net.minecraft.state.property.Properties.SLAB_TYPE).asString());
  if(block.definition.kind.equals("stairs")){
   properties.putString("facing",placement.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING).asString());
   properties.putString("half",placement.get(net.minecraft.state.property.Properties.BLOCK_HALF).asString());
   properties.putString("shape",placement.get(net.minecraft.state.property.Properties.STAIR_SHAPE).asString());
  }
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
