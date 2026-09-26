package dev.dreamwalker.bloodborneblocks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** In-world acceptance checks for physical masks independently of rendered overhang. */
public final class GridPhysicsGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(12,8,12);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=500,batchId="grid_physics_rotations")
 public void protectedMultiCellRotationsAllowStoneOutsidePhysicalMask(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock block=requiredProduction("o_ladder_01");
  try{
   moveOutside(context,player);
   for(Direction facing:Direction.Type.HORIZONTAL){
    clearFixture(context);BlockState state=block.getDefaultState().with(Properties.HORIZONTAL_FACING,facing);placeLogical(context,state);
    placeStoneAroundMask(context,player,state,"protected ladder "+facing);
   }
   context.complete();
  }finally{clearFixture(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=180,batchId="grid_physics_removal")
 public void breakingProtectedRootRemovesOnlyItsPhysicalHelpers(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock block=requiredProduction("o_c001");BlockState state=block.getDefaultState();BlockPos root=context.getAbsolutePos(ROOT);
  try{
   placeLogical(context,state);Set<BlockPos> mask=GeometryRuntime.state(state).parsedCells.keySet();context.assertTrue(mask.size()>1,"protected fixture has physical helpers");
   BlockPos outside=boundary(mask).get(0).outside;world.setBlockState(root.add(outside),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   world.breakBlock(root,false);
   for(BlockPos offset:mask)context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"root removal clears owned physical cell "+offset);
   context.assertTrue(world.getBlockState(root.add(outside)).isOf(Blocks.STONE),"root removal preserves adjacent foreign stone "+outside);
   world.removeBlock(root.add(outside),false);
   context.complete();
  }finally{clearFixture(context);}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=500,batchId="grid_physics_city")
 public void representativeCityNativeStatesRemainOneEditableCell(TestContext context){
  PlayerEntity player=context.createMockCreativePlayer();
  try{
   moveOutside(context,player);
   for(String id:CITY_NATIVE_RECONCILED){
    clearFixture(context);ArchitectureBlock block=requiredCity(id);BlockPos root=context.getAbsolutePos(ROOT);
    context.getWorld().setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);context.useStackOnBlock(player,new ItemStack(block),ROOT.down(),Direction.UP);
    context.assertTrue(context.getWorld().getBlockState(root).isOf(block),"city item places at clicked grid cell with origin anchor: "+id);
    BlockState state=context.getWorld().getBlockState(root);context.getWorld().removeBlock(root.down(),false);
    context.assertTrue(GeometryRuntime.state(state).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN)),"city physical mask is origin-only: "+id);
    placeStoneAroundMask(context,player,state,"city "+id);
   }
   context.complete();
  }finally{clearFixture(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=140,batchId="grid_physics_connected")
 public void connectedAuthoredStateUpdatesWithoutNeighborHelpers(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock block=requiredProduction("o_stone_curb");BlockPos first=context.getAbsolutePos(ROOT),second=first.east();
  try{
   moveOutside(context,player);world.setBlockState(first.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(second.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   context.useStackOnBlock(player,new ItemStack(block),ROOT.down(),Direction.UP);context.useStackOnBlock(player,new ItemStack(block),ROOT.east().down(),Direction.UP);
   context.assertTrue(world.getBlockState(first).isOf(block)&&world.getBlockState(second).isOf(block),"connected items place in adjacent grid cells");
   BooleanProperty east=(BooleanProperty)block.getStateManager().getProperty("east"),west=(BooleanProperty)block.getStateManager().getProperty("west");
   context.assertTrue(east!=null&&west!=null&&world.getBlockState(first).get(east)&&world.getBlockState(second).get(west),"authored connected variants activate on neighbor add");
   context.assertTrue(GeometryRuntime.state(world.getBlockState(first)).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN)),"connected state creates no neighbor helper");
   world.removeBlock(second,false);context.assertTrue(!world.getBlockState(first).get(east),"authored connected variant clears on neighbor removal");
   context.complete();
  }finally{clearFixture(context);player.discard();}
 }

 private static void placeLogical(TestContext context,BlockState state){BlockPos root=context.getAbsolutePos(ROOT);context.getWorld().setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(context.getWorld(),root,state),"logical state rebuilds: "+state);}

 private static void placeStoneAroundMask(TestContext context,PlayerEntity player,BlockState state,String label){
  Set<BlockPos> mask=GeometryRuntime.state(state).parsedCells.keySet();context.assertTrue(mask.contains(BlockPos.ORIGIN),"physical mask contains root: "+label);
  BlockPos absoluteRoot=context.getAbsolutePos(ROOT);
  for(Boundary boundary:boundary(mask)){
   BlockPos outside=absoluteRoot.add(boundary.outside);context.assertTrue(context.getWorld().getBlockState(outside).isAir(),"outside physical mask starts editable: "+label+" "+boundary.outside);
   context.useStackOnBlock(player,new ItemStack(Blocks.STONE),ROOT.add(boundary.inside),boundary.direction);
   context.assertTrue(context.getWorld().getBlockState(outside).isOf(Blocks.STONE),"stone item places outside physical mask: "+label+" "+boundary.outside);
  }
 }

 private static List<Boundary> boundary(Set<BlockPos> mask){
  List<Boundary> result=new ArrayList<>();Set<BlockPos> seen=new LinkedHashSet<>();
  for(BlockPos inside:mask)for(Direction direction:Direction.values()){BlockPos outside=inside.offset(direction);if(!mask.contains(outside)&&seen.add(outside))result.add(new Boundary(inside,outside,direction));}
  return result;
 }

 private static ArchitectureBlock requiredProduction(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing production block "+id);return block;}
 private static ArchitectureBlock requiredCity(String id){ArchitectureBlock block=BloodborneBlocks.CITY_BLOCKS.get(id);if(block==null)throw new AssertionError("missing city block "+id);return block;}
 private static void moveOutside(TestContext context,PlayerEntity player){BlockPos pos=context.getAbsolutePos(new BlockPos(1,20,1));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);}
 private static void clearFixture(TestContext context){
  ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);BlockState rootState=world.getBlockState(root);Set<BlockPos> cleanup=new LinkedHashSet<>(List.of(root,root.down(),root.east(),root.east().down()));
  if(rootState.getBlock() instanceof ArchitectureBlock){for(BlockPos offset:GeometryRuntime.state(rootState).parsedCells.keySet())cleanup.add(root.add(offset));for(Boundary boundary:boundary(GeometryRuntime.state(rootState).parsedCells.keySet()))cleanup.add(root.add(boundary.outside));world.breakBlock(root,false);}
  for(BlockPos pos:cleanup){BlockState state=world.getBlockState(pos);if(state.isOf(Blocks.STONE)||state.isOf(BloodborneBlocks.PART_BLOCK)||state.getBlock() instanceof ArchitectureBlock)world.removeBlock(pos,false);}
 }
 private static final List<String> CITY_NATIVE_RECONCILED=List.of("acacia_stairs","birch_stairs","brown_terracotta","crimson_fence_gate","dark_oak_fence_gate","dark_oak_stairs","dead_brain_coral_wall_fan","dead_bubble_coral","dead_bubble_coral_fan","flower_pot","lantern","light_blue_glazed_terracotta","lightning_rod","magenta_glazed_terracotta","magenta_wool","nether_brick_stairs","oak_wood","orange_glazed_terracotta","orange_wool","oxidized_cut_copper_stairs","waxed_cut_copper_stairs","waxed_weathered_cut_copper_stairs","weathered_cut_copper_stairs","white_glazed_terracotta","white_wool");
 private record Boundary(BlockPos inside,BlockPos outside,Direction direction) {}
}
