package dev.dreamwalker.bloodborneblocks;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Bounded, explicit migration of loaded roots; preview and apply use the same plan. */
final class PaletteMigration {
 record Result(int removed,int replaced,int repaired,int conflicts,int orphans) {}
 private record Change(BlockPos oldRoot,BlockState oldState,BlockPos root,BlockState state) {}
 private PaletteMigration() {}

 static Result update(ServerWorld world,BlockPos center,int radius,boolean apply){
  List<Change> plan=new ArrayList<>();List<BlockPos> orphans=new ArrayList<>();
  Map<BlockPos,BlockPos> reserved=new HashMap<>();int removed=0,replaced=0,repaired=0,conflicts=0;
  for(BlockPos cursor:BlockPos.iterate(center.add(-radius,-radius,-radius),center.add(radius,radius,radius))){
   if(!world.isInBuildLimit(cursor)||!world.isChunkLoaded(cursor))continue;
   BlockPos pos=cursor.toImmutable();BlockState old=world.getBlockState(pos);
   if(old.isOf(BloodborneBlocks.PART_BLOCK)){
    ArchitecturePartBlockEntity part=GeometryRuntime.part(world,pos);
    if(part==null||(world.isChunkLoaded(part.rootPos())&&!world.getBlockState(part.rootPos()).isOf(part.ownerBlock())))orphans.add(pos);
    continue;
   }
   if(!(old.getBlock() instanceof ArchitectureBlock block))continue;
   if(!GeometryRuntime.allCellsLoaded(world,pos,old)){conflicts++;continue;}
   if(PaletteAliases.removed(block.definition.id)){
    plan.add(new Change(pos,old,pos,Blocks.AIR.getDefaultState()));removed++;continue;
   }
   var replacement=PaletteAliases.replacement(old);BlockState next=replacement.state();BlockPos root=pos.add(replacement.offset());
   if(!canReplaceRoot(world,pos,root)||!GeometryRuntime.canOccupy(world,root,next,pos)){conflicts++;continue;}
   Set<BlockPos> occupied=new HashSet<>();occupied.add(root);
   for(BlockPos offset:GeometryRuntime.state(next).parsedCells.keySet())occupied.add(root.add(offset));
   if(occupied.stream().anyMatch(target->reserved.containsKey(target)&&!reserved.get(target).equals(pos))){conflicts++;continue;}
   occupied.forEach(target->reserved.put(target,pos));plan.add(new Change(pos,old,root,next));
   if(!next.isOf(old.getBlock())||!root.equals(pos))replaced++;else repaired++;
  }
  if(apply){
   for(Change change:plan){
    if(change.state.isAir()||change.state!=change.oldState||!change.root.equals(change.oldRoot))GeometryRuntime.replaceRoot(world,change.oldRoot,change.oldState,change.root,change.state);
    else GeometryRuntime.rebuild(world,change.root,change.state);
   }
   for(BlockPos pos:orphans)GeometryRuntime.removeHelper(world,pos);
  }
  return new Result(removed,replaced,repaired,conflicts,orphans.size());
 }

 private static boolean canReplaceRoot(ServerWorld world,BlockPos oldRoot,BlockPos root){
  if(!world.isChunkLoaded(root)||!world.isInBuildLimit(root)||!world.getWorldBorder().contains(root))return false;
  if(root.equals(oldRoot))return true;
  BlockState there=world.getBlockState(root);if(there.isAir()||there.isReplaceable())return true;
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,root);return part!=null&&part.rootPos().equals(oldRoot);
 }
}
