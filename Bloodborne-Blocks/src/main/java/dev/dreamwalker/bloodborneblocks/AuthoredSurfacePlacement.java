package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Narrow placement rules for attachments and the reviewed two-cell ladder. */
final class AuthoredSurfacePlacement {
 private AuthoredSurfacePlacement(){}
 static ActionResult use(ArchitectureBlockItem item,ItemUsageContext use){
  String id=((ArchitectureBlock)item.getBlock()).definition.id;
  // Do not alter the normal item-use path of any unrelated production family.
  if(!java.util.Set.of("o_lantern","o_ladder_03").contains(id))return null;
  World world=use.getWorld();PlayerEntity player=use.getPlayer();BlockPos clicked=use.getBlockPos();
  if(player==null||!world.isChunkLoaded(clicked))return ActionResult.FAIL;
  BlockPos owner=owner(world,clicked);if(owner==null)return ActionResult.FAIL;BlockState ownerState=world.getBlockState(owner);
  // Sneaking bypasses vanilla Block.onUse; it must still attach, not place a block into a helper.
  if(ownerState.getBlock() instanceof ArchitectureBlock target&&target.definition.attachment_item!=null
    &&target.definition.attachment_item.equals(((ArchitectureBlock)item.getBlock()).definition.id)){
   if(!world.canPlayerModifyAt(player,owner))return ActionResult.FAIL;
   return LogicalAttachments.use(ownerState,world,owner,player,use.getHand());
  }
  if("o_ladder_03".equals(id)&&ownerState.isOf(item.getBlock())){
   if(use.getSide()!=Direction.UP&&use.getHitPos().y<owner.getY()+.5)return ActionResult.FAIL;
   var geometry=GeometryRuntime.state(ownerState);
   int min=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
   int max=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
   BlockPos next=owner.up(max-min+1);
   return item.place(new SurfaceContext(new ItemPlacementContext(use),next,ownerState.get(Properties.HORIZONTAL_FACING)));
  }
  return null;
 }
 private static BlockPos owner(World world,BlockPos pos){
  if(world.getBlockState(pos).getBlock() instanceof ArchitectureBlock)return pos;
  var part=GeometryRuntime.part(world,pos);
  if(part!=null&&part.bindings().size()!=1)return null;
  if(part!=null&&world.isChunkLoaded(part.rootPos())&&GeometryRuntime.ownsHelper(world.getBlockState(part.rootPos()),part.rootPos(),pos,part.ownerId()))return part.rootPos();
  return pos;
 }
 static boolean mayReplace(ItemPlacementContext context,BlockPos root){return false;}
 static ItemPlacementContext copy(ItemPlacementContext context,ItemStack stack){
  if(context instanceof SurfaceContext surface)return new SurfaceContext(context,surface.root,surface.side){@Override public ItemStack getStack(){return stack;}};
  return new ItemPlacementContext(context){@Override public ItemStack getStack(){return stack;}};
 }
 static boolean validate(ArchitectureBlock block,ItemPlacementContext context,BlockPos root,BlockState state){
  if(!"o_ladder_03".equals(block.definition.id))return true;
  World world=context.getWorld();PlayerEntity player=context.getPlayer();
  for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet()){
   BlockPos cell=root.add(offset);
   if(!world.isChunkLoaded(cell)||player==null||!world.canPlayerModifyAt(player,cell))return false;
   if("o_ladder_03".equals(block.definition.id)){
    if(!context.getSide().getAxis().isHorizontal())return false;
    Direction facing=state.get(Properties.HORIZONTAL_FACING);BlockPos support=cell.offset(facing.getOpposite());
    if(!world.isChunkLoaded(support)||!world.getBlockState(support).isSideSolidFullSquare(world,support,facing))return false;
   }
  }
  return true;
 }
 private static class SurfaceContext extends ItemPlacementContext {
  final BlockPos root;final Direction side;
  SurfaceContext(ItemPlacementContext original,BlockPos root,Direction side){
   super(original.getWorld(),original.getPlayer(),original.getHand(),original.getStack(),new BlockHitResult(original.getHitPos(),side,root,false));
   this.root=root;this.side=side;
  }
  @Override public BlockPos getBlockPos(){return root;}
  @Override public Direction getSide(){return side;}
  @Override public boolean canPlace(){return getWorld().getBlockState(root).canReplace(this);}
 }
}
