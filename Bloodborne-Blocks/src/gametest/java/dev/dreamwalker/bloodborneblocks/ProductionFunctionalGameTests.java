package dev.dreamwalker.bloodborneblocks;

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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Production functional mechanics retained from the retired compatibility batches. */
public final class ProductionFunctionalGameTests implements FabricGameTest {
 private static final BlockPos CLICK=new BlockPos(4,0,4),ROOT=new BlockPos(4,2,4);

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="production_functional")
 public void gateTransitionsAndRejectsBlockedOpen(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock gate=required("o_iron_gate");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   BlockState closed=gate.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.OPEN,false),open=closed.with(Properties.OPEN,true);
   world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"gate closed rebuild");
   context.useBlock(ROOT,player);context.assertTrue(world.getBlockState(root).equals(open),"gate opens at its root");context.useBlock(ROOT,player);context.assertTrue(world.getBlockState(root).equals(closed),"gate closes at its root");
   BlockPos blocked=exclusiveHelper(root,closed,open);world.setBlockState(blocked,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
   ActionResult result=closed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));
   context.assertTrue(result==ActionResult.FAIL&&world.getBlockState(root).equals(closed)&&world.getBlockState(blocked).isOf(Blocks.STONE),"blocked gate open is atomic");world.removeBlock(blocked,false);context.complete();
  }finally{clear(context,gate);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="production_functional")
 public void railingConnectsInAllFourDirectionsAndLanternToggles(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock railing=required("o_iron_railing"),lantern=required("o_c618");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   for(Direction direction:Direction.Type.HORIZONTAL){clear(context,railing);BlockPos other=root.offset(direction);world.setBlockState(root,railing.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(other,railing.getDefaultState(),Block.NOTIFY_ALL);BooleanProperty a=(BooleanProperty)world.getBlockState(root).getBlock().getStateManager().getProperty(direction.asString()),b=(BooleanProperty)world.getBlockState(other).getBlock().getStateManager().getProperty(direction.getOpposite().asString());context.assertTrue(a!=null&&b!=null&&world.getBlockState(root).get(a)&&world.getBlockState(other).get(b),"railing connects "+direction);world.removeBlock(other,false);}
   PlayerEntity player=context.createMockCreativePlayer();BlockState unlit=lantern.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.LIT,false);world.setBlockState(root,unlit,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,unlit),"lantern rebuilds");context.useBlock(ROOT,player);BlockState lit=world.getBlockState(root);context.assertTrue(lit.get(Properties.LIT)&&lit.getLuminance()>unlit.getLuminance(),"lantern lights and raises luminance");player.discard();context.complete();
  }finally{clear(context,railing);clear(context,lantern);}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="production_functional")
 public void wallPanelsPlaceAdjacentToConcreteForEveryFacing(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();
  try{
   for(String id:List.of("o_c654_a","o_c654_b"))for(Direction side:Direction.Type.HORIZONTAL){ArchitectureBlock wall=required(id);BlockPos expected=context.getAbsolutePos(CLICK.offset(side));context.useStackOnBlock(player,new ItemStack(wall),CLICK,side);context.assertTrue(world.getBlockState(expected).isOf(wall)&&world.getBlockState(expected).get(Properties.HORIZONTAL_FACING)==side,"wall panel places adjacent to concrete: "+id+" "+side);clear(context,wall);}
   context.complete();
  }finally{player.discard();}
 }

 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing production fixture "+id);return block;}
 private static BlockPos exclusiveHelper(BlockPos root,BlockState before,BlockState after){Set<BlockPos> old=new LinkedHashSet<>(GeometryRuntime.state(before).parsedCells.keySet());return GeometryRuntime.state(after).parsedCells.keySet().stream().filter(offset->!offset.equals(BlockPos.ORIGIN)&&!old.contains(offset)).map(root::add).findFirst().orElseThrow(()->new AssertionError("transition must claim a new helper"));}
 private static void floor(TestContext context){context.setBlockState(CLICK,Blocks.WHITE_CONCRETE);}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-5;x<14;x++)for(int y=0;y<12;y++)for(int z=-5;z<14;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block)||world.getBlockState(pos).isOf(BloodborneBlocks.PART_BLOCK))world.removeBlock(pos,false);}}
}
