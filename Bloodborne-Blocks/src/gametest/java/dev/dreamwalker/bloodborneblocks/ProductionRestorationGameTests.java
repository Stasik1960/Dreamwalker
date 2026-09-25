package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;

/** Runtime checks for the 21 restored functional families and C003 successors. */
public final class ProductionRestorationGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(4,2,4);
 private static final List<String> FUNCTIONAL=List.of("o_acacia_door","o_birch_door","o_dark_oak_door","o_shuttered_window","o_stone_railing","o_ornate_balustrade","o_carved_balustrade","o_stepped_balustrade","o_high_balustrade","o_stone_curb","o_ladder_01","o_ladder_03","o_candles_0","o_lanterns","o_wall_lantern","o_lantern","o_lightning_rod","o_oak_wood","o_bench");
 private static final List<String> C003=List.of("o_barrel","o_books","o_bag","o_cases_0");

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="production_restoration")
 public void restoredRegistryContainsFunctionalAndC003Successors(TestContext context){
  for(String id:FUNCTIONAL)context.assertTrue(BloodborneBlocks.BLOCKS.containsKey(id),"required restored functional ID: "+id);
  for(String id:C003)context.assertTrue(BloodborneBlocks.BLOCKS.containsKey(id),"required C003 successor: "+id);
  context.assertTrue(!BloodborneBlocks.BLOCKS.containsKey("o_c003"),"retired C003 composite is not registered");context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="production_restoration")
 public void restoredDoorsAndShutterToggleAtStationaryRoot(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();BlockPos root=context.getAbsolutePos(ROOT);
  try{for(String id:List.of("o_acacia_door","o_birch_door","o_dark_oak_door","o_shuttered_window"))for(Direction facing:Direction.Type.HORIZONTAL){
   ArchitectureBlock block=required(id);clear(context,block);BlockState closed=block.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.OPEN,false);
   world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"closed rebuild "+id+" "+facing);VoxelShape closedShape=wholeCollision(closed);
   var bounds=closedShape.getBoundingBox();double cx=(bounds.minX+bounds.maxX)/2,cz=(bounds.minZ+bounds.maxZ)/2;
   VoxelShape passage=VoxelShapes.cuboid(cx-.05,.3,cz-.05,cx+.05,1,cz+.05);
   context.assertTrue(VoxelShapes.matchesAnywhere(closedShape,passage,BooleanBiFunction.AND),"closed panel blocks passage "+id+" "+facing);
   context.useBlock(ROOT,player);BlockState open=world.getBlockState(root);context.assertTrue(open.isOf(block)&&open.get(Properties.OPEN),"opens at unchanged root "+id+" "+facing);
   if(id.equals("o_shuttered_window"))context.assertTrue(VoxelShapes.matchesAnywhere(wholeCollision(open),passage,BooleanBiFunction.AND),"open shutters retain the confined window pane "+facing);
   else context.assertTrue(!VoxelShapes.matchesAnywhere(wholeCollision(open),passage,BooleanBiFunction.AND),"open wings free passage "+id+" "+facing);
   assertOwned(context,root,open,id+" open");context.useBlock(ROOT,player);BlockState reclosed=world.getBlockState(root);context.assertTrue(reclosed.isOf(block)&&!reclosed.get(Properties.OPEN),"closes at unchanged root "+id+" "+facing);assertOwned(context,root,reclosed,id+" closed");
  }context.complete();}finally{for(String id:List.of("o_acacia_door","o_birch_door","o_dark_oak_door","o_shuttered_window"))clear(context,required(id));player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="production_restoration")
 public void lanternsBenchesLaddersAndConnectionsBehave(TestContext context){
  ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);PlayerEntity player=context.createMockCreativePlayer();
  try{
   for(String id:List.of("o_candles_0","o_lanterns","o_wall_lantern","o_lantern","o_lightning_rod","o_oak_wood")){ArchitectureBlock lantern=required(id);clear(context,lantern);BlockState unlit=lantern.getDefaultState().with(Properties.LIT,false);world.setBlockState(root,unlit,Block.NOTIFY_ALL);GeometryRuntime.rebuild(world,root,unlit);context.useBlock(ROOT,player);context.assertTrue(world.getBlockState(root).get(Properties.LIT)&&world.getBlockState(root).getLuminance()==15,"lit restoration "+id);clear(context,lantern);}
   ArchitectureBlock bench=required("o_bench");world.setBlockState(root,bench.getDefaultState(),Block.NOTIFY_ALL);GeometryRuntime.rebuild(world,root,world.getBlockState(root));context.useBlock(ROOT,player);context.assertTrue(player.hasVehicle(),"bench mounts player");context.assertTrue(Math.abs(player.getVehicle().getY()-root.getY()-1)<.001,"seat plank height, not clipped backrest cap");player.stopRiding();clear(context,bench);
   ArchitectureBlock landing=required("o_ladder_01");world.setBlockState(root,landing.getDefaultState(),Block.NOTIFY_ALL);GeometryRuntime.rebuild(world,root,world.getBlockState(root));context.assertTrue(!FunctionalFurniture.isClimbable(world,root),"ladder_01 is a solid landing, not climbable");clear(context,landing);
   ArchitectureBlock ladder=required("o_ladder_03");world.setBlockState(root,ladder.getDefaultState(),Block.NOTIFY_ALL);GeometryRuntime.rebuild(world,root,world.getBlockState(root));context.assertTrue(FunctionalFurniture.isClimbable(world,root),"ladder root climbs o_ladder_03");BlockPos helper=firstHelper(root,world.getBlockState(root));if(helper!=null)context.assertTrue(FunctionalFurniture.isClimbable(world,helper),"ladder helper climbs o_ladder_03");clear(context,ladder);
   ArchitectureBlock connected=required("o_stone_railing");world.setBlockState(root,connected.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.east(),connected.getDefaultState(),Block.NOTIFY_ALL);BooleanProperty east=(BooleanProperty)connected.getStateManager().getProperty("east");context.assertTrue(east!=null&&world.getBlockState(root).get(east),"connected family joins adjacent roots");clear(context,connected);context.complete();
  }finally{player.discard();}
 }

 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing required restoration "+id);return block;}
 private static VoxelShape wholeCollision(BlockState state){VoxelShape shape=VoxelShapes.empty();for(double[] b:GeometryRuntime.state(state).gameplayBoxes)shape=VoxelShapes.union(shape,VoxelShapes.cuboid(b[0],b[1],b[2],b[3],b[4],b[5]));return shape;}
 private static BlockPos firstHelper(BlockPos root,BlockState state){return GeometryRuntime.state(state).parsedCells.keySet().stream().filter(offset->!offset.equals(BlockPos.ORIGIN)).map(root::add).findFirst().orElse(null);}
 private static void assertOwned(TestContext context,BlockPos root,BlockState state,String label){for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())if(!offset.equals(BlockPos.ORIGIN)){BlockPos cell=root.add(offset);ArchitecturePartBlockEntity part=GeometryRuntime.part(context.getWorld(),cell);context.assertTrue(part!=null&&part.rootPos().equals(root)&&GeometryRuntime.ownsHelper(state,root,cell,part.ownerId()),"owned helper "+label+" "+offset);}}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-8;x<17;x++)for(int y=0;y<16;y++)for(int z=-8;z<17;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block)||world.getBlockState(pos).isOf(BloodborneBlocks.PART_BLOCK))world.removeBlock(pos,false);}}
}
