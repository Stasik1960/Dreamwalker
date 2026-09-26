package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Real BlockItem placement, scoped saved-root exceptions; no map relocation here. */
public final class ExplicitRootGameTests implements FabricGameTest {
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="explicit_root")
 public void benchExceptionMinus37473Minus291UsesManualAnchor(TestContext context){exercise(context,"o_bench");}
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="explicit_root")
 public void balustradeExceptionMinus56098Minus9UsesManualAnchor(TestContext context){exercise(context,"o_high_balustrade");}

 private void exercise(TestContext context,String id){
  var world=context.getWorld();var player=context.createMockCreativePlayer();
  ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);BlockPos canonical=context.getAbsolutePos(new BlockPos(4,3,4)),root=canonical.up();
  try{
   for(int turn=0;turn<4;turn++){
    player.refreshPositionAndAngles(canonical.getX()+12,canonical.getY()+5,canonical.getZ()+12,turn*90,0);
    world.setBlockState(canonical.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
    world.setBlockState(canonical,Blocks.DIAMOND_ORE.getDefaultState(),Block.NOTIFY_ALL);
    ItemStack held=new ItemStack(block);held.getOrCreateSubNbt("BlockStateTag").putString("root_anchor","upper");player.setStackInHand(Hand.MAIN_HAND,held);
    var hit=new BlockHitResult(Vec3d.ofCenter(canonical.down()).add(0,.5,0),Direction.UP,canonical.down(),false);
    var result=held.getItem().useOnBlock(new ItemUsageContext(world,player,Hand.MAIN_HAND,held,hit));
    BlockState placed=world.getBlockState(root);
    context.assertTrue(result.isAccepted()&&placed.isOf(block),"manual upper-root placement "+id+" turn="+turn);
    context.assertTrue(BloodborneBlocks.key(placed).contains("root_anchor=upper"),"saved root property survives placement");
    context.assertTrue(world.getBlockState(canonical).isOf(Blocks.DIAMOND_ORE),"other object cell unchanged");
    BlockState ordinary=BloodborneBlocks.set(placed,block.getStateManager().getProperty("root_anchor"),"canonical");
    context.assertTrue(FunctionalFurniture.seatPoints(canonical,ordinary).equals(FunctionalFurniture.seatPoints(root,placed)),"seat coordinates unchanged");
    var geometry=GeometryRuntime.state(placed);
    for(BlockPos offset:geometry.parsedCells.keySet()){
     if(offset.equals(BlockPos.ORIGIN))continue;
     var owner=GeometryRuntime.part(world,root.add(offset));
     context.assertTrue(owner!=null,"upper-root helper exists "+offset);
    }
    context.assertTrue("upper".equals(block.getPickStack(world,root,placed).getSubNbt("BlockStateTag").getString("root_anchor")),"pick retains anchor");
    world.breakBlock(root,false,player);
    for(BlockPos offset:geometry.parsedCells.keySet())context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"owned cleanup "+offset);
    context.assertTrue(world.getBlockState(canonical).isOf(Blocks.DIAMOND_ORE),"cleanup preserves foreign cell");
   }
   context.complete();
  }finally{player.discard();}
 }
}
