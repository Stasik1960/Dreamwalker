package dev.dreamwalker.bloodborneblocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Authored Agony bench seats: three independent anchors in all eight poses. */
public final class AgonyFurnitureGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(4,2,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="agony_furniture")
 public void authoredBenchAnchorsRotateThroughEightOrientations(TestContext context){
  BlockPos root=context.getAbsolutePos(ROOT);
  ArchitectureBlock bench=required("o_bench");BooleanProperty diagonal=diagonal(bench);
  for(boolean diagonalState:List.of(false,true))for(Direction facing:Direction.Type.HORIZONTAL){
    BlockState state=bench.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(diagonal,diagonalState);
    List<Vec3d> points=FunctionalFurniture.seatPoints(root,state);
    context.assertTrue(points.size()==3,"three authored anchors "+facing+" diagonal="+diagonalState);
    double angle=Math.toRadians(facing.asRotation()-180+(diagonalState?45:0));
    for(int index=0;index<3;index++){
     double[] anchor=bench.definition.seat_anchors[index];double x=anchor[0]-.5,z=anchor[2]-.5;
     Vec3d expected=new Vec3d(root.getX()+.5+x*Math.cos(angle)-z*Math.sin(angle),root.getY()+anchor[1],root.getZ()+.5+x*Math.sin(angle)+z*Math.cos(angle));
     context.assertTrue(points.get(index).squaredDistanceTo(expected)<1e-12,"anchor rotation "+facing+" diagonal="+diagonalState+" #"+index);
    }
   }
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="agony_furniture")
 public void threePlayersOccupyDistinctSeatsAtPlankHeightAndCleanup(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock bench=required("o_bench");BlockPos root=context.getAbsolutePos(ROOT);
  PlayerEntity first=context.createMockCreativePlayer(),second=context.createMockCreativePlayer(),third=context.createMockCreativePlayer(),fourth=context.createMockCreativePlayer();
  try{
   ArchitectureSeatEntity legacyDefault=BloodborneBlocks.SEAT_ENTITY.create(world);context.assertTrue(legacyDefault!=null&&legacyDefault.mountedHeightOffset()==0f,"unbound legacy seat retains zero tracked offset");legacyDefault.discard();
   BooleanProperty diagonal=diagonal(bench);List<PlayerEntity> players=List.of(first,second,third);
   for(boolean diagonalState:List.of(false,true))for(Direction facing:Direction.Type.HORIZONTAL){
    BlockState state=bench.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(diagonal,diagonalState);world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),"bench rebuild "+facing+" diagonal="+diagonalState);
    List<Vec3d> points=FunctionalFurniture.seatPoints(root,state);
    for(int playerIndex=0;playerIndex<players.size();playerIndex++){
     int index=(playerIndex+2)%players.size(); // World-space hit, intentionally not anchor-list order.
     ActionResult result=FunctionalFurniture.sit(world,root,state,players.get(playerIndex),new BlockHitResult(points.get(index),Direction.UP,root,false));
     context.assertTrue(result==ActionResult.CONSUME&&players.get(playerIndex).hasVehicle(),"player mounts authored seat "+index+" "+facing+" diagonal="+diagonalState);
     ArchitectureSeatEntity seat=(ArchitectureSeatEntity)players.get(playerIndex).getVehicle();context.assertTrue(seat.seatIndex()==index,"nearest free world-space anchor "+index);
     context.assertTrue(Math.abs(seat.mountedHeightOffset()+.4f)<.0001,"authored mounted offset is tracked for client sync "+index);
     context.assertTrue(Math.abs(players.get(playerIndex).getVehicle().getY()-points.get(index).y)<.001,"seat vehicle stays on authored plank height "+index);
     context.assertTrue(Math.abs(players.get(playerIndex).getY()-(points.get(index).y-.75))<.001,"biped hip pivot sits on authored plank "+index);
    }
    context.assertTrue(FunctionalFurniture.sit(world,root,state,fourth,new BlockHitResult(points.get(0),Direction.UP,root,false))==ActionResult.FAIL&&!fourth.hasVehicle(),"full authored bench rejects a fourth rider");
    List<ArchitectureSeatEntity> seats=world.getEntitiesByClass(ArchitectureSeatEntity.class,new Box(root).expand(8),seat->seat.rootPos().equals(root));
    context.assertTrue(seats.size()==3&&seats.stream().allMatch(ArchitectureSeatEntity::hasPassengers),"no idle seat entities");
    Set<Integer> indices=new HashSet<>();for(ArchitectureSeatEntity seat:seats)indices.add(seat.seatIndex());context.assertTrue(indices.equals(Set.of(0,1,2)),"one seat entity per root/seat index");
    world.removeBlock(root,false);context.assertTrue(world.getEntitiesByClass(ArchitectureSeatEntity.class,new Box(root).expand(8),seat->seat.rootPos().equals(root)).isEmpty(),"removing root cleans every owned seat");for(PlayerEntity player:players)player.stopRiding();
   }
   context.complete();
  }finally{first.discard();second.discard();third.discard();fourth.discard();clear(context,bench);}
 }

 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing Agony bench "+id);return block;}
 private static BooleanProperty diagonal(ArchitectureBlock block){Object property=block.getStateManager().getProperty("diagonal");if(!(property instanceof BooleanProperty value))throw new AssertionError("bench lacks diagonal property");return value;}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-8;x<17;x++)for(int y=0;y<16;y++)for(int z=-8;z<17;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block)||world.getBlockState(pos).isOf(BloodborneBlocks.PART_BLOCK))world.removeBlock(pos,false);}}
}
