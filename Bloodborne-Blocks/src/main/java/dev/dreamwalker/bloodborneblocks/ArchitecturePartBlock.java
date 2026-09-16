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
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,pos);if(part==null)return null;
  if(world instanceof World loadedWorld&&!loadedWorld.isChunkLoaded(part.rootPos()))return null;
  BlockState state=world.getBlockState(part.rootPos());
  if(!(state.getBlock() instanceof ArchitectureBlock)||!state.isOf(part.ownerBlock()))return null;
  return new Root(part.rootPos(),state,pos.subtract(part.rootPos()));
 }
 private record Root(BlockPos pos,BlockState state,BlockPos offset){}

 @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){Root root=root(world,pos);return root==null?VoxelShapes.empty():GeometryRuntime.cellShape(root.state,root.offset,true);}
 @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){Root root=root(world,pos);return root==null?VoxelShapes.empty():GeometryRuntime.cellShape(root.state,root.offset,false);}
 @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
 @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView world,BlockPos pos){return 1.0F;}
 @Override public float calcBlockBreakingDelta(BlockState state,PlayerEntity player,BlockView world,BlockPos pos){Root root=root(world,pos);return root==null?1.0F:root.state.calcBlockBreakingDelta(player,world,root.pos);}

 @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
  Root root=root(world,pos);if(root==null)return ActionResult.PASS;
  Vec3dOffset offset=new Vec3dOffset(root.pos.getX()-pos.getX(),root.pos.getY()-pos.getY(),root.pos.getZ()-pos.getZ());
  BlockHitResult delegated=new BlockHitResult(hit.getPos().add(offset.x,offset.y,offset.z),hit.getSide(),root.pos,hit.isInsideBlock());
  return root.state.onUse(world,player,hand,delegated);
 }

 @Override public void onBreak(World world,BlockPos pos,BlockState state,PlayerEntity player){
  Root root=root(world,pos);
  if(root!=null&&!world.isClient)world.breakBlock(root.pos,!player.isCreative(),player);
  super.onBreak(world,pos,state,player);
 }

 @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){Root root=root(world,pos);return root==null?ItemStack.EMPTY:root.state.getBlock().getPickStack(world,root.pos,root.state);}

 @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
  if(!next.isOf(this)&&!GeometryRuntime.isMutating()){
   Root root=root(world,pos);if(root!=null&&!world.isClient)world.breakBlock(root.pos,true);
  }
  super.onStateReplaced(state,world,pos,next,moved);
 }

 private record Vec3dOffset(double x,double y,double z){}
}
