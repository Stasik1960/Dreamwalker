package dev.dreamwalker.bloodborneblocks;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Invisible occupied cell. All behavior and drops are owned by its root block. */
public final class ArchitecturePartBlock extends BlockWithEntity {
 public ArchitecturePartBlock(){super(Settings.create().strength(0.0F,3600000.0F).nonOpaque().noBlockBreakParticles().dropsNothing().dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));}

 @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
 @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ArchitecturePartBlockEntity(pos,state);}
 @Override public void scheduledTick(BlockState state,ServerWorld world,BlockPos pos,Random random){
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,pos);if(part==null)return;
  if(!world.isChunkLoaded(part.rootPos())){part.validateWhenRootLoads(world);return;}
  part.validateOwner(world);
 }

 private Root root(BlockView world,BlockPos pos){
  var roots=GeometryRuntime.ownedRoots(world,pos);if(roots.isEmpty())return null;var root=roots.get(0);return new Root(root.pos(),root.state(),root.offset());
 }
 private record Root(BlockPos pos,BlockState state,BlockPos offset){}

 @Override public boolean canReplace(BlockState state,net.minecraft.item.ItemPlacementContext context){
  return false;
 }

 @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){VoxelShape shape=VoxelShapes.empty();for(var root:GeometryRuntime.ownedRoots(world,pos)){VoxelShape next=root.state().getBlock() instanceof ArchitectureBlock block&&block.definition.logical?GeometryRuntime.rootShape(root.state(),true).offset(-root.offset().getX(),-root.offset().getY(),-root.offset().getZ()):GeometryRuntime.cellShape(root.state(),root.offset(),true);shape=VoxelShapes.union(shape,next);}return shape;}
 @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return GeometryRuntime.guestShape(world,pos,false);}
 @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
 @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView world,BlockPos pos){return 1.0F;}
 @Override public float calcBlockBreakingDelta(BlockState state,PlayerEntity player,BlockView world,BlockPos pos){if(GeometryRuntime.ownedRoots(world,pos).size()>1)return 0.0F;Root root=root(world,pos);return root==null?1.0F:root.state.calcBlockBreakingDelta(player,world,root.pos);}

 @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
  if(GeometryRuntime.ownedRoots(world,pos).size()>1)return ActionResult.PASS;
  Root root=root(world,pos);if(root==null)return ActionResult.PASS;
  Vec3dOffset offset=new Vec3dOffset(root.pos.getX()-pos.getX(),root.pos.getY()-pos.getY(),root.pos.getZ()-pos.getZ());
  boolean authoredSeats=root.state.getBlock() instanceof ArchitectureBlock block&&block.definition.seat_anchors!=null;
  BlockHitResult delegated=new BlockHitResult(authoredSeats?hit.getPos():hit.getPos().add(offset.x,offset.y,offset.z),hit.getSide(),root.pos,hit.isInsideBlock());
  return root.state.onUse(world,player,hand,delegated);
 }

 @Override public void onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player){
  if(GeometryRuntime.ownedRoots(world,pos).size()>1)return;
  Root root=root(world,pos);
  if(root!=null&&!world.isClient)world.breakBlock(root.pos,!player.isCreative(),player);
  super.onBreak(world,pos,state,player);
 }

 @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){if(GeometryRuntime.ownedRoots(world,pos).size()>1)return ItemStack.EMPTY;Root root=root(world,pos);return root==null?ItemStack.EMPTY:root.state.getBlock().getPickStack(world,root.pos,root.state);}

 @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
  if(!next.isOf(this)&&!GeometryRuntime.isMutating()&&!world.isClient){
   ArchitecturePartBlockEntity part=GeometryRuntime.part(world,pos);
   if(part!=null&&GeometryRuntime.hasUnloadedGuest(world,part.bindings())){GeometryRuntime.restoreCarrier(world,pos,state,part.bindings());return;}
   if(part!=null&&part.bindings().size()>1)GeometryRuntime.preserveGuestsAfterCarrierRemoval(world,pos,part.bindings());
   else{Root root=root(world,pos);if(root!=null)world.breakBlock(root.pos,true);}
  }
  super.onStateReplaced(state,world,pos,next,moved);
 }

 private record Vec3dOffset(double x,double y,double z){}
}
