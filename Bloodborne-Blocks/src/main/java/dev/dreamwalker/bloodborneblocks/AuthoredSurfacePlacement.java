package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Narrow placement rules for the reviewed window and two-cell ladder. No carving or tick work. */
final class AuthoredSurfacePlacement {
 private AuthoredSurfacePlacement(){}
 static ActionResult use(ArchitectureBlockItem item,ItemUsageContext use){
  String id=((ArchitectureBlock)item.getBlock()).definition.id;
  // Do not alter the normal item-use path of any unrelated production family.
  if(!java.util.Set.of("o_lantern","o_ladder_03","o_shuttered_window").contains(id))return null;
  World world=use.getWorld();PlayerEntity player=use.getPlayer();BlockPos clicked=use.getBlockPos();
  if(player==null||!world.isChunkLoaded(clicked))return ActionResult.FAIL;
  BlockPos owner=owner(world,clicked);BlockState ownerState=world.getBlockState(owner);
  // Sneaking bypasses vanilla Block.onUse; it must still attach, not place a block into a helper.
  if(ownerState.getBlock() instanceof ArchitectureBlock target&&target.definition.attachment_item!=null
    &&target.definition.attachment_item.equals(((ArchitectureBlock)item.getBlock()).definition.id)){
   if(!world.canPlayerModifyAt(player,owner))return ActionResult.FAIL;
   return LogicalAttachments.use(ownerState,world,owner,player,use.getHand());
  }
  if("o_shuttered_window".equals(id)){
   if(!use.getSide().getAxis().isHorizontal()||!player.getAbilities().allowModifyWorld
     ||!world.canPlayerModifyAt(player,clicked))return ActionResult.FAIL;
   BlockState previous=world.getBlockState(clicked);
   if(previous.isAir()||previous.isReplaceable())return null;
   if(!safeWall(world,clicked))return ActionResult.FAIL;
   Hand toolHand=use.getHand()==Hand.MAIN_HAND?Hand.OFF_HAND:Hand.MAIN_HAND;
   ItemStack tool=player.getStackInHand(toolHand);
   if(!player.isCreative()&&previous.isToolRequired()&&!tool.isSuitableFor(previous))return ActionResult.FAIL;
   SurfaceContext context=new SurfaceContext(new ItemPlacementContext(use),clicked,use.getSide(),true);
   ActionResult result=item.place(context);
   // Replaces only this previously validated cell; neighboring aperture cells must already be free.
   if(result.isAccepted()&&!world.isClient&&!player.isCreative()&&world.getBlockState(clicked).isOf(item.getBlock())){
    Block.dropStacks(previous,world,clicked,null,player,tool);
    if(previous.getHardness(world,clicked)>0&&!tool.isEmpty())tool.damage(1,player,p->p.sendToolBreakStatus(toolHand));
   }
   return result;
  }
  if("o_ladder_03".equals(id)&&ownerState.isOf(item.getBlock())){
   if(use.getSide()!=Direction.UP&&use.getHitPos().y<owner.getY()+.5)return ActionResult.FAIL;
   var geometry=GeometryRuntime.state(ownerState);
   int min=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
   int max=geometry.parsedCells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
   BlockPos next=owner.up(max-min+1);
   return item.place(new SurfaceContext(new ItemPlacementContext(use),next,ownerState.get(Properties.HORIZONTAL_FACING),false));
  }
  return null;
 }
 private static BlockPos owner(World world,BlockPos pos){
  var part=GeometryRuntime.part(world,pos);
  if(part!=null&&world.isChunkLoaded(part.rootPos())&&GeometryRuntime.ownsHelper(world.getBlockState(part.rootPos()),part.rootPos(),pos,part.ownerId()))return part.rootPos();
  return pos;
 }
 static boolean safeWall(World world,BlockPos pos){
  if(!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos))return false;
  BlockState state=world.getBlockState(pos);
  return !state.hasBlockEntity()&&world.getBlockEntity(pos)==null&&!(state.getBlock() instanceof ArchitectureBlock)
    &&state.getHardness(world,pos)>=0&&state.isFullCube(world,pos)&&state.getFluidState().isEmpty();
 }
 static boolean mayReplace(ItemPlacementContext context,BlockPos root){
  return context instanceof SurfaceContext surface&&surface.replaceWall&&root.equals(surface.root)&&safeWall(context.getWorld(),root);
 }
 static ItemPlacementContext copy(ItemPlacementContext context,ItemStack stack){
  if(context instanceof SurfaceContext surface)return new SurfaceContext(context,surface.root,surface.side,surface.replaceWall){@Override public ItemStack getStack(){return stack;}};
  return new ItemPlacementContext(context){@Override public ItemStack getStack(){return stack;}};
 }
 static boolean validate(ArchitectureBlock block,ItemPlacementContext context,BlockPos root,BlockState state){
  if(!"o_ladder_03".equals(block.definition.id)&&!"o_shuttered_window".equals(block.definition.id))return true;
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
  final BlockPos root;final Direction side;final boolean replaceWall;
  SurfaceContext(ItemPlacementContext original,BlockPos root,Direction side,boolean replaceWall){
   super(original.getWorld(),original.getPlayer(),original.getHand(),original.getStack(),new BlockHitResult(original.getHitPos(),side,root,false));
   this.root=root;this.side=side;this.replaceWall=replaceWall;
  }
  @Override public BlockPos getBlockPos(){return root;}
  @Override public Direction getSide(){return side;}
  @Override public boolean canPlace(){return mayReplace(this,root)||getWorld().getBlockState(root).canReplace(this);}
 }
}
