package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.List;

public final class ReviewedWallGameTests implements FabricGameTest {
 private static final BlockPos ROOT=new BlockPos(10,2,10);
 private static ArchitectureBlock wall(){return BloodborneBlocks.CITY_BLOCKS.get(ReviewedWallConnections.ID);}
 private static void place(TestContext c,PlayerEntity p,ArchitectureBlock block,BlockPos root,Direction facing){
  BlockPos outside=c.getAbsolutePos(new BlockPos(25,4,25));p.refreshPositionAndAngles(outside.getX(),outside.getY(),outside.getZ(),facing.getOpposite().asRotation(),0);
  ItemStack stack=new ItemStack(block);p.setStackInHand(Hand.MAIN_HAND,stack);
  BlockPos clicked=c.getAbsolutePos(root.down());
  var result=stack.useOnBlock(new net.minecraft.item.ItemUsageContext(p,Hand.MAIN_HAND,new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(clicked),Direction.UP,clicked,false)));
  c.assertTrue(result.isAccepted()&&c.getBlockState(root).isOf(block),"actual item placed "+block.definition.id+" at "+root+" result="+result+" target="+c.getBlockState(root));
 }
 private static ActionResult use(PlayerEntity player,ItemStack stack,BlockPos clicked,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(clicked),side,clicked,false)));}
 private static void connection(TestContext c,BlockPos p,String value){
  BlockState state=c.getBlockState(p);c.assertTrue(state.isOf(wall())&&state.get(wall().getStateManager().getProperty("connection")).equals(value),"exact supported connection "+value+" at "+p+" actual="+state);
 }
 private static void floor(TestContext c){for(int x=5;x<16;x++)for(int z=5;z<16;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.WHITE_CONCRETE);}
 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=100,batchId="reviewed_wall")
 public void fourFacingsAndLegacyPicksUseOneBuildingItem(TestContext c){
  floor(c);PlayerEntity player=c.createMockSurvivalPlayer();
  try{
   for(Direction facing:Direction.Type.HORIZONTAL){
    place(c,player,wall(),ROOT,facing);c.assertTrue(c.getBlockState(ROOT).get(Properties.HORIZONTAL_FACING)==facing,"placement orientation "+facing);connection(c,ROOT,"low_0");
    place(c,player,wall(),ROOT.east(),Direction.NORTH);
    int mask=switch(facing){case NORTH->2;case EAST->1;case SOUTH->8;case WEST->4;default->throw new AssertionError();};
    connection(c,ROOT,"low_"+mask);
    ItemStack picked=wall().getPickStack(c.getWorld(),c.getAbsolutePos(ROOT),c.getBlockState(ROOT));
    c.assertTrue(picked.isOf(wall().asItem())&&picked.getSubNbt("BlockStateTag")==null,"pick does not freeze facing or connections");
    c.getWorld().breakBlock(c.getAbsolutePos(ROOT.east()),false);connection(c,ROOT,"low_0");c.getWorld().breakBlock(c.getAbsolutePos(ROOT),false);
   }
   for(String id:List.of("owner_58bccf82adec219c90f0","owner_103c69237444338a3df4","owner_53e58987698a7c96e613","owner_32dc66a6e819fe19c3b8","owner_16c5223d3d45e3ca0c5f","owner_a631fe463a1af9f0fe4e")){
    ArchitectureBlock old=BloodborneBlocks.CITY_BLOCKS.get(id);
    for(Direction facing:Direction.Type.HORIZONTAL){BlockState state=old.getDefaultState().with(Properties.HORIZONTAL_FACING,facing);c.setBlockState(ROOT,state);ItemStack picked=old.getPickStack(c.getWorld(),c.getAbsolutePos(ROOT),state);c.assertTrue(picked.isOf(wall().asItem())&&picked.getSubNbt("BlockStateTag")==null,"legacy wall pick maps to the same proved stone-brick building type: "+id+" "+facing);c.getWorld().breakBlock(c.getAbsolutePos(ROOT),false);}
   }
   c.complete();
  }finally{player.discard();}
 }
 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=100,batchId="reviewed_wall")
 public void pairCornerTAndExistingPostlessCrossUpdateOnRemoval(TestContext c){
  floor(c);PlayerEntity player=c.createMockSurvivalPlayer();
  try{
   place(c,player,wall(),ROOT,Direction.NORTH);
   Direction[] sides={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};int[] masks={1,3,7,15};
   for(int i=0;i<4;i++){place(c,player,wall(),ROOT.offset(sides[i]),Direction.NORTH);connection(c,ROOT,"low_"+masks[i]);}
   c.setBlockState(ROOT.up(),Blocks.STONE);connection(c,ROOT,"tall_15");c.setBlockState(ROOT.up(),Blocks.AIR);connection(c,ROOT,"low_15");
   for(int i=3;i>=0;i--){c.getWorld().breakBlock(c.getAbsolutePos(ROOT.offset(sides[i])),false);connection(c,ROOT,"low_"+(i==0?0:masks[i-1]));}
   c.complete();
  }finally{player.discard();}
 }
 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=100,batchId="reviewed_wall")
 public void connectionTransitionKeepsWindowGuestBinding(TestContext c){
  floor(c);PlayerEntity player=c.createMockSurvivalPlayer();ArchitectureBlock window=BloodborneBlocks.BLOCKS.get("o_shuttered_window");
  try{
   place(c,player,window,ROOT,Direction.NORTH);
   BlockPos carrier=c.getAbsolutePos(ROOT.up()),windowRoot=c.getAbsolutePos(ROOT);
   var windowBinding=GeometryRuntime.part(c.getWorld(),carrier);c.assertTrue(windowBinding!=null,"window helper binding exists before imported wall");NbtCompound importedBindings=windowBinding.createNbt();BlockState imported=wall().getDefaultState();c.getWorld().removeBlockEntity(carrier);c.getWorld().setBlockState(carrier,imported,Block.NOTIFY_ALL);var importedCarrier=new ArchitecturePartBlockEntity(carrier,imported);importedCarrier.readNbt(importedBindings);c.getWorld().addBlockEntity(importedCarrier);c.assertTrue(GeometryRuntime.rebuild(c.getWorld(),carrier,imported),"imported wall rebuild preserves the pre-existing window overlap");
   var part=GeometryRuntime.part(c.getWorld(),carrier);c.assertTrue(part!=null&&part.hasBinding(windowRoot,BloodborneBlocks.id("o_shuttered_window")),"imported wall root retains existing whole window guest");
   c.setBlockState(ROOT.east(),Blocks.WHITE_CONCRETE);place(c,player,wall(),ROOT.east().up(),Direction.NORTH);connection(c,ROOT.up(),"low_2");
   part=GeometryRuntime.part(c.getWorld(),carrier);c.assertTrue(part!=null&&part.hasBinding(windowRoot,BloodborneBlocks.id("o_shuttered_window")),"connection update retains exact window owner");
   c.getWorld().breakBlock(c.getAbsolutePos(ROOT.east().up()),false);connection(c,ROOT.up(),"low_0");
   var saved=part.createNbt();c.getWorld().removeBlockEntity(carrier);
   var loaded=new ArchitecturePartBlockEntity(carrier,c.getWorld().getBlockState(carrier));loaded.readNbt(saved);c.getWorld().addBlockEntity(loaded);
   c.assertTrue(loaded.createNbt().equals(saved),"window/wall exact ownership NBT round trip");
   c.getWorld().breakBlock(carrier,false);c.assertTrue(c.getBlockState(ROOT).isOf(window),"breaking reloaded wall preserves window before queued helper recovery");
   ItemStack immediate=new ItemStack(wall());ActionResult immediateResult=use(player,immediate,c.getAbsolutePos(ROOT),Direction.UP);
   c.assertTrue(!immediateResult.isAccepted()&&immediate.getCount()==1&&c.getBlockState(ROOT).isOf(window),"pending window guest denies an immediate overlapping wall item before recovery drains");
   c.runAtTick(2,()->{try{
    var preserved=GeometryRuntime.part(c.getWorld(),carrier);c.assertTrue(c.getBlockState(ROOT.up()).isOf(BloodborneBlocks.PART_BLOCK)&&preserved!=null&&preserved.hasBinding(windowRoot,BloodborneBlocks.id("o_shuttered_window")),"queued helper recovery restores exact window binding");
    ItemStack rejected=new ItemStack(wall());ActionResult result=use(player,rejected,c.getAbsolutePos(ROOT),Direction.UP);var after=GeometryRuntime.part(c.getWorld(),carrier);
    c.assertTrue(!result.isAccepted()&&rejected.getCount()==1&&c.getBlockState(ROOT).isOf(window)&&after!=null&&after.hasBinding(windowRoot,BloodborneBlocks.id("o_shuttered_window")),"overlapping wall item remains denied after helper recovery");c.complete();
   }finally{player.discard();}});
  }catch(Throwable error){player.discard();throw error;}
 }
 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=60,batchId="reviewed_wall")
 public void pendingDeletedGuestDoesNotBlockFreeWallPlacement(TestContext c){
  floor(c);PlayerEntity player=c.createMockSurvivalPlayer();ArchitectureBlock window=BloodborneBlocks.BLOCKS.get("o_shuttered_window");
  try{
   place(c,player,window,ROOT,Direction.NORTH);BlockPos carrier=c.getAbsolutePos(ROOT.up()),windowRoot=c.getAbsolutePos(ROOT);ArchitecturePartBlockEntity helper=GeometryRuntime.part(c.getWorld(),carrier);c.assertTrue(helper!=null,"window helper exists before stale-pending fixture");NbtCompound saved=helper.createNbt();BlockState imported=wall().getDefaultState();c.getWorld().removeBlockEntity(carrier);c.getWorld().setBlockState(carrier,imported,Block.NOTIFY_ALL);ArchitecturePartBlockEntity importedCarrier=new ArchitecturePartBlockEntity(carrier,imported);importedCarrier.readNbt(saved);c.getWorld().addBlockEntity(importedCarrier);
   c.getWorld().breakBlock(carrier,false);c.getWorld().breakBlock(windowRoot,false);c.assertTrue(!c.getBlockState(ROOT).isOf(window)&&GeometryRuntime.canPlace(c.getWorld(),carrier,imported),"a deleted pending guest contributes no resident placement footprint");
   c.runAtTick(2,()->{try{c.assertTrue(GeometryRuntime.part(c.getWorld(),carrier)==null,"stale pending guest is discarded instead of recreating a helper");c.complete();}finally{player.discard();}});
  }catch(Throwable error){player.discard();throw error;}
 }
}
