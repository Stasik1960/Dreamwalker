package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Dedicated-server checks for the bounded NightmareRunning replacements. */
public final class NightmareGameTests implements FabricGameTest {
 private static final BlockPos FIRST=new BlockPos(3,2,3),SECOND=new BlockPos(12,2,3);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="nightmare")
 public void splitDoorsKeepIndependentOpenHingeAndFacing(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();
  ArchitectureBlock left=required("o_c282_a"),right=required("o_c282_b");
  try{
   for(Direction facing:Direction.Type.HORIZONTAL){
    BlockPos first=context.getAbsolutePos(FIRST),second=context.getAbsolutePos(SECOND);
    BlockState a=left.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.DOOR_HINGE,DoorHinge.LEFT).with(Properties.OPEN,false);
    BlockState b=right.getDefaultState().with(Properties.HORIZONTAL_FACING,facing.getOpposite()).with(Properties.DOOR_HINGE,DoorHinge.RIGHT).with(Properties.OPEN,false);
    world.setBlockState(first,a,Block.NOTIFY_ALL);world.setBlockState(second,b,Block.NOTIFY_ALL);
    context.assertTrue(GeometryRuntime.rebuild(world,first,a)&&GeometryRuntime.rebuild(world,second,b),"split doors rebuild: "+facing);
    context.useBlock(FIRST,player);
    BlockState opened=world.getBlockState(first),untouched=world.getBlockState(second);
    context.assertTrue(opened.get(Properties.OPEN)&&opened.get(Properties.DOOR_HINGE)==DoorHinge.LEFT&&opened.get(Properties.HORIZONTAL_FACING)==facing,"left leaf toggles independently: "+facing);
    context.assertTrue(!untouched.get(Properties.OPEN)&&untouched.get(Properties.DOOR_HINGE)==DoorHinge.RIGHT&&untouched.get(Properties.HORIZONTAL_FACING)==facing.getOpposite(),"right leaf keeps its state: "+facing);
    clear(world,first,left);clear(world,second,right);
   }
   context.complete();
  }finally{player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="nightmare")
 public void windowsHaveBroadCollisionAndWallYawPlacement(TestContext context){
  ServerWorld world=context.getWorld();ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();
  try{
   for(String id:List.of("o_c654_a","o_c654_b")){
    ArchitectureBlock block=required(id);BlockState state=block.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH);
    VoxelShape collision=wholeCollision(state);var box=collision.getBoundingBox();
    context.assertTrue(!collision.isEmpty()&&box.getXLength()>2.5&&box.getYLength()>2.5,"broad panel collision: "+id);
    context.assertTrue(GeometryRuntime.applyPlacementPolicy(state,Direction.EAST).get(Properties.HORIZONTAL_FACING)==Direction.EAST,"wall placement faces clicked wall: "+id);
    context.assertTrue(block.rotate(state,BlockRotation.CLOCKWISE_90).get(Properties.HORIZONTAL_FACING)==Direction.EAST,"wall panel yaw rotates: "+id);
    BlockPos root=context.getAbsolutePos(FIRST);player.refreshPositionAndAngles(root.getX()+.94,root.getY()+1,root.getZ()+.94,0,0);
    int checked=0;
    for(var entry:GeometryRuntime.state(state).parsedCells.entrySet()){
     VoxelShape cell=GeometryRuntime.cellShape(state,entry.getKey(),false);if(cell.isEmpty())continue;
     Box cellBox=cell.getBoundingBox();BlockPos target=root.add(entry.getKey());
     player.refreshPositionAndAngles(target.getX()+(cellBox.minX+cellBox.maxX)/2,target.getY()+cellBox.minY,target.getZ()+(cellBox.minZ+cellBox.maxZ)/2,0,0);
     context.assertTrue(!GeometryRuntime.canPlace(world,root,state),"panel cell blocks entity placement: "+id+" "+entry.getKey());checked++;
    }
    context.assertTrue(checked>1,"broad panel collision spans helper cells: "+id);
   }
   context.complete();
  }finally{removeMockServerPlayer(player);}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="nightmare")
 public void raisedLanternAndSplitStatuesRemainIndividualObjects(TestContext context){
 ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();
  try{
   BlockPos outside=context.getAbsolutePos(new BlockPos(-6,4,-6));player.refreshPositionAndAngles(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);
   ArchitectureBlock lantern=required("o_c618");BlockPos lanternRoot=context.getAbsolutePos(FIRST);BlockState lit=lantern.getDefaultState().with(Properties.LIT,true);
   world.setBlockState(lanternRoot,lit,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,lanternRoot,lit)&&world.getBlockState(lanternRoot).get(Properties.LIT),"raised lantern keeps lit state");clear(world,lanternRoot,lantern);
   int index=0;
   for(String id:List.of("o_c008_1","o_c008_2","o_c008_3","o_c008_4","o_c008_5")){
    ArchitectureBlock block=required(id);BlockPos root=context.getAbsolutePos(new BlockPos(2+index++*5,2,10));BlockState state=block.getDefaultState();
    world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),"statue rebuild: "+id);
    context.assertTrue(block.getPickStack(world,root,state).isOf(block.asItem()),"statue pick stays individual: "+id);
    world.breakBlock(root,true,player);context.assertTrue(world.getBlockState(root).isAir(),"statue break removes one root: "+id);
    long drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(4),item->item.getStack().isOf(block.asItem())).stream().mapToInt(item->item.getStack().getCount()).sum();
    context.assertTrue(drops==1,"statue break drops one individual item: "+id);
    world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(4),item->item.getStack().isOf(block.asItem())).forEach(ItemEntity::discard);
   }
   context.complete();
  }finally{player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="nightmare")
 public void raisedGravesAndSpiresDoNotConsumeUnrelatedCells(TestContext context){
  ServerWorld world=context.getWorld();int index=0;
  for(String id:List.of("o_c1491_a","o_c1491_b","o_c1491_c","o_c471_a","o_c471_b")){
   ArchitectureBlock block=required(id);BlockPos root=context.getAbsolutePos(new BlockPos(2+index++*5,2,3));BlockState state=block.getDefaultState();
   context.assertTrue(GeometryRuntime.rootShape(state,false).getBoundingBox().minY==0.0,"raised floor collision starts at Y=0: "+id);
   BlockPos foreign=root.add(3,0,0);world.setBlockState(foreign,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),"raised object rebuilds: "+id);
   context.assertTrue(world.getBlockState(foreign).isOf(Blocks.STONE),"helpers do not consume unrelated cell: "+id);
   clear(world,root,block);world.removeBlock(foreign,false);
  }
  context.complete();
 }

 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing NightmareRunning block "+id);return block;}
 private static VoxelShape wholeCollision(BlockState state){
  VoxelShape result=VoxelShapes.empty();for(var entry:GeometryRuntime.state(state).parsedCells.entrySet()){BlockPos offset=entry.getKey();result=VoxelShapes.union(result,GeometryRuntime.cellShape(state,offset,false).offset(offset.getX(),offset.getY(),offset.getZ()));}return result;
 }
 private static void removeMockServerPlayer(ServerPlayerEntity player){player.getServer().getPlayerManager().remove(player);}
 private static void clear(ServerWorld world,BlockPos root,Block block){if(world.getBlockState(root).isOf(block))world.breakBlock(root,false);}
}
