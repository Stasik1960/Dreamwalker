package dev.dreamwalker.bloodborneblocks;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Dedicated-server integration checks. They validate lifecycle behavior, not rendered appearance. */
public final class LogicalRuntimeGameTests implements FabricGameTest {
 private static final BlockPos CLICK=new BlockPos(4,0,4);
 private static final BlockPos ROOT=new BlockPos(4,2,4);
 private static final Set<String> BATCH_FAMILIES=Set.of("o_c001_a","o_c001_b","o_c009_a","o_c009_b","o_c002","o_c003","o_c008","o_c471","o_c046","o_c1680","o_c474","o_c1962","o_c1979","o_c028","o_c282","o_c561","o_c618","o_c654","o_c1319","o_c1491");

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_placement")
 public void logicalPlacementCreatesAndBreaksOneObject(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock block=required("o_crowned_stone_post");
  try{
   moveOutside(context,player);context.useStackOnBlock(player,new ItemStack(block),CLICK,Direction.UP);
   BlockPos root=find(context,block);BlockPos helper=find(context,BloodborneBlocks.PART_BLOCK);
   context.assertTrue(root!=null&&context.getRelativePos(root).getY()>=2,"logical placement keeps helpers above the base floor");context.assertTrue(helper!=null,"logical item placement must create helper cells");
   world.breakBlock(root,true,player);context.assertTrue(find(context,BloodborneBlocks.PART_BLOCK)==null,"breaking a root removes all helpers");
   context.assertTrue(drops(world,root,block)==1,"breaking a logical root produces exactly one drop");clearItems(world,root,block);
   context.useStackOnBlock(player,new ItemStack(block),CLICK,Direction.UP);root=find(context,block);helper=find(context,BloodborneBlocks.PART_BLOCK);
   context.assertTrue(root!=null&&helper!=null,"logical object can be placed again after root break");world.breakBlock(helper,true,player);
   context.assertTrue(world.getBlockState(root).isAir(),"breaking a helper removes its root");context.assertTrue(find(context,BloodborneBlocks.PART_BLOCK)==null,"breaking one object removes all helpers");
   context.assertTrue(drops(world,root,block)==1,"breaking a logical helper produces exactly one root drop");context.complete();
  }finally{clear(context,block);clearItems(world,context.getAbsolutePos(ROOT),block);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_interactions")
 public void logicalInteractionsOrientationsAndConnections(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();
  try{
   moveOutside(context,player);
   for(String id:List.of("o_acacia_door","o_iron_gate","o_shuttered_window")){
    ArchitectureBlock block=required(id);
    for(Direction facing:Direction.Type.HORIZONTAL){
     BlockState state=block.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.OPEN,false);world.setBlockState(context.getAbsolutePos(ROOT),state,Block.NOTIFY_ALL);
     context.useBlock(ROOT,player);assertOpen(context,block,facing,true);context.useBlock(ROOT,player);assertOpen(context,block,facing,false);clear(context,block);
    }
   }
   assertConnected(context,required("o_stone_curb"),new BlockPos(1,2,5));clear(context,required("o_stone_curb"));
   assertConnected(context,required("o_high_balustrade"),new BlockPos(5,2,5));context.complete();
  }finally{clearFixtures(context);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_attachments")
 public void statueLanternAttachmentLifecycle(TestContext context){
  ServerWorld world=context.getWorld();ServerPlayerEntity player=createMockSurvivalServerPlayer(context);ArchitectureBlock statue=required("o_statue"),lantern=required("o_lanterns");BooleanProperty attached=attachmentProperty(statue);
  try{
   moveOutside(context,player);install(context,statue);ItemStack lamps=new ItemStack(lantern,2);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,lamps);
   context.useBlock(ROOT,player);assertAttachment(context,attached,true,"master accepts lantern");context.assertTrue(lamps.getCount()==1,"successful master attachment consumes one survival lantern");
   player.setSneaking(true);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,ItemStack.EMPTY);context.useBlock(ROOT,player);player.setSneaking(false);
   assertAttachment(context,attached,false,"sneak-empty master detaches lantern");context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(ROOT)).isOf(statue)&&countItem(player,lantern)==1,"detach returns exactly one lantern while statue remains");clear(context,statue);
   install(context,statue);BlockPos helper=ownedHelper(context,context.getAbsolutePos(ROOT),world.getBlockState(context.getAbsolutePos(ROOT)));context.assertTrue(helper!=null,"statue has an attachable helper");lamps=new ItemStack(lantern,2);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,lamps);
   BlockPos root=context.getAbsolutePos(ROOT);BlockState detached=world.getBlockState(root),withLantern=detached.with(attached,true);
   context.assertTrue(GeometryRuntime.canOccupy(world,root,withLantern,root),"helper attachment preflight: conflict="+GeometryRuntime.conflict(world,root,withLantern,root));
   // GeometryRuntime returns world coordinates; do not apply TestContext's structure rotation again.
   ActionResult helperResult=world.getBlockState(helper).onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(helper),Direction.NORTH,helper,true));
   context.assertTrue(helperResult.isAccepted(),"helper attachment interaction result="+helperResult+", owner="+GeometryRuntime.part(world,helper)+", held="+player.getMainHandStack());
   assertAttachment(context,attached,true,"helper delegates lantern attachment to master");context.assertTrue(lamps.getCount()==1,"successful helper attachment consumes one survival lantern");
   world.breakBlock(context.getAbsolutePos(ROOT),true,player);context.assertTrue(drops(world,context.getAbsolutePos(ROOT),statue)==1,"attached statue drops one statue");context.assertTrue(drops(world,context.getAbsolutePos(ROOT),lantern)==1,"attached statue drops one lantern");clearItems(world,context.getAbsolutePos(ROOT),statue);clearItems(world,context.getAbsolutePos(ROOT),lantern);
   install(context,statue);BlockPos obstruction=attachmentOnlyTarget(statue,attached,context.getAbsolutePos(ROOT));world.setBlockState(obstruction,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);lamps=new ItemStack(lantern,2);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,lamps);
   BlockState beforeBlocked=world.getBlockState(root);ActionResult blocked=beforeBlocked.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));
   context.assertTrue(blocked==ActionResult.FAIL,"obstructed attachment explicitly refuses interaction");assertAttachment(context,attached,false,"obstructed attachment leaves statue state unchanged");context.assertTrue(lamps.getCount()==2,"failed attachment does not consume survival lantern");
   world.setBlockState(root,beforeBlocked.with(attached,true),Block.NOTIFY_ALL);
   context.assertTrue(world.getBlockState(root).equals(beforeBlocked),"direct editor update rolls back an obstructed attachment");
   for(BlockPos offset:helperOffsets(beforeBlocked)){BlockPos cell=root.add(offset);ArchitecturePartBlockEntity part=GeometryRuntime.part(world,cell);context.assertTrue(part!=null&&part.rootPos().equals(root)&&GeometryRuntime.ownsHelper(beforeBlocked,root,cell,part.ownerId()),"rollback keeps every owned helper");}
   context.assertTrue(world.getBlockState(obstruction).isOf(Blocks.STONE),"rollback preserves foreign obstruction");
   context.assertTrue(drops(world,root,statue)==0&&drops(world,root,lantern)==0,"failed interaction and editor rollback create no drops");world.removeBlock(obstruction,false);context.complete();
  }finally{clear(context,statue);clearItems(world,context.getAbsolutePos(ROOT),statue);clearItems(world,context.getAbsolutePos(ROOT),lantern);removeMockServerPlayer(player);}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_full_inventory")
 public void statueLanternDetachWithFullSurvivalInventory(TestContext context){
  ServerWorld world=context.getWorld();ServerPlayerEntity player=createMockSurvivalServerPlayer(context);ArchitectureBlock statue=required("o_statue"),lantern=required("o_lanterns");BooleanProperty attached=attachmentProperty(statue);
  try{
   // Keep this item-spawn assertion inside the template's entity-tracked chunk.
   // moveOutside uses z=-6: far-along batches then spawn a real saved item in
   // a non-tracked neighboring chunk, invisible to the immediate entity query.
   BlockPos dropPosition=context.getAbsolutePos(new BlockPos(0,4,0));player.refreshPositionAndAngles(dropPosition.getX()+.5,dropPosition.getY(),dropPosition.getZ()+.5,0,0);
   install(context,statue);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(lantern));context.useBlock(ROOT,player);assertAttachment(context,attached,true,"full-inventory fixture attaches lantern first");
   BlockPos helper=ownedHelper(context,context.getAbsolutePos(ROOT),world.getBlockState(context.getAbsolutePos(ROOT)));context.assertTrue(helper!=null,"attached statue retains a helper for delegated detach");fillMainInventory(player);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);player.setSneaking(true);
   BlockState helperState=world.getBlockState(helper);ActionResult result=helperState.onUse(world,player,Hand.OFF_HAND,new BlockHitResult(Vec3d.ofCenter(helper),Direction.UP,helper,false));player.setSneaking(false);
   context.assertTrue(result.isAccepted(),"off-hand helper detach succeeds with full main inventory");assertAttachment(context,attached,false,"full-inventory detach clears attached state");
   context.assertTrue(countItem(player,lantern)==0,"full main inventory cannot retain detached lantern");long dropped=drops(world,player.getBlockPos(),lantern);context.assertTrue(dropped==1,"full-inventory detach drops exactly one lantern at the player; actual="+dropped);context.complete();
  }finally{clear(context,statue);clearItems(world,context.getAbsolutePos(ROOT),statue);clearItems(world,context.getAbsolutePos(ROOT),lantern);clearItems(world,player.getBlockPos(),lantern);removeMockServerPlayer(player);}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_creative_attachment")
 public void statueLanternCreativeAttachmentDoesNotCreateItems(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();player.getAbilities().creativeMode=true;ArchitectureBlock statue=required("o_statue"),lantern=required("o_lanterns");BooleanProperty attached=attachmentProperty(statue);
  try{
   moveOutside(context,player);install(context,statue);ItemStack lamps=new ItemStack(lantern,2);player.setStackInHand(Hand.MAIN_HAND,lamps);context.useBlock(ROOT,player);
   assertAttachment(context,attached,true,"creative master attachment succeeds");context.assertTrue(lamps.getCount()==2,"creative attachment does not consume lanterns");
   context.useBlock(ROOT,player);assertAttachment(context,attached,true,"duplicate creative attachment leaves state attached");context.assertTrue(lamps.getCount()==2,"duplicate creative attachment does not consume lanterns");
   player.setSneaking(true);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);context.useBlock(ROOT,player);player.setSneaking(false);
   assertAttachment(context,attached,false,"creative detach clears state");context.assertTrue(countItem(player,lantern)==0&&drops(world,context.getAbsolutePos(ROOT),lantern)==0,"creative detach creates neither returned inventory item nor drop");context.complete();
  }finally{clear(context,statue);clearItems(world,context.getAbsolutePos(ROOT),statue);clearItems(world,context.getAbsolutePos(ROOT),lantern);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=400, batchId="logical_inventory")
 public void everyLogicalCreativeItemPlacesAsOneObject(TestContext context){
  PlayerEntity player=context.createMockSurvivalPlayer();List<ArchitectureBlock> families=new ArrayList<>();for(ArchitectureBlock block:BloodborneBlocks.BLOCKS.values())if(block.definition.logical&&block.definition.creative&&!PaletteAliases.hidden(block.definition.id))families.add(block);
  context.assertTrue(!families.isEmpty(),"generated logical creative inventory is not empty");context.assertTrue(families.size()<=350,"logical creative inventory fits bounded GameTest schedule");moveOutside(context,player);
  for(int index=0;index<families.size();index++){ArchitectureBlock block=families.get(index);context.runAtTick(index+1,()->verifyFamilyPlacement(context,player,block));}
  context.runAtTick(families.size()+2,()->{player.discard();context.complete();});
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_ladders")
 public void logicalLaddersClimbAcrossOwnedCells(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();BlockPos root=context.getAbsolutePos(ROOT);
  try{
   for(String id:List.of("o_ladder_01","o_ladder_02","o_ladder_03")){
    ArchitectureBlock ladder=required(id);
    for(Direction facing:Direction.Type.HORIZONTAL){
     moveOutside(context,player);BlockState state=ladder.getDefaultState().with(Properties.HORIZONTAL_FACING,facing);world.setBlockState(root,state,Block.NOTIFY_ALL);
     context.assertTrue(GeometryRuntime.rebuild(world,root,state),"ladder helpers created: "+id+" "+facing);
     for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet()){
      BlockPos cell=root.add(offset);var shape=GeometryRuntime.cellShape(state,offset,true);if(shape.isEmpty())continue;
      context.assertTrue(FunctionalFurniture.isClimbable(world,cell),"ladder root/helper is climbable: "+id+" "+offset);
      Box box=shape.getBoundingBox().offset(cell);player.refreshPositionAndAngles((box.minX+box.maxX)/2,box.minY+.05,(box.minZ+box.maxZ)/2,0,0);
      context.assertTrue(player.isClimbing(),"Minecraft climbing activates at ladder geometry: "+id+" "+facing+" "+offset);
     }
     moveOutside(context,player);context.assertTrue(!player.isClimbing(),"ladder does not grant climbing away from its geometry");world.breakBlock(root,false);
     for(BlockPos offset:helperOffsets(state))context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"ladder removal clears climbable helpers");
    }
   }
   context.complete();
  }finally{for(String id:List.of("o_ladder_01","o_ladder_02","o_ladder_03"))clear(context,required(id));player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=160, batchId="logical_contract_v2")
 public void contractV2ManualPlacementAndForeignVisualCells(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock tree=required("o_c001");
  try{
   // The visual tree reaches this foreign cell, but its interaction contract does not.
   for(Direction yaw:Direction.Type.HORIZONTAL)for(boolean breakHelper:List.of(false,true)){
    BlockPos expected=context.getAbsolutePos(CLICK.up()),visualOnly=expected.add(-1,3,0);world.setBlockState(visualOnly,Blocks.LIGHT.getDefaultState(),Block.NOTIFY_ALL);moveOutside(context,player);player.setYaw(yaw.asRotation());context.useStackOnBlock(player,new ItemStack(tree),CLICK,Direction.UP);
    BlockPos root=find(context,tree);context.assertTrue(expected.equals(root),"tree canonical master origin "+yaw);BlockState state=world.getBlockState(root);context.assertTrue(state.get(Properties.HORIZONTAL_FACING)==yaw.getOpposite(),"tree facing follows player yaw "+yaw);context.assertTrue(world.getBlockState(visualOnly).isOf(Blocks.LIGHT)&&!world.getBlockState(visualOnly).isOf(BloodborneBlocks.PART_BLOCK),"tree never claims visual-only cell");
    if(breakHelper){BlockPos helper=ownedHelper(context,root,state);context.assertTrue(helper!=null,"tree helper exists");world.breakBlock(helper,true,player);}else world.breakBlock(root,true,player);
    context.assertTrue(world.getBlockState(visualOnly).isOf(Blocks.LIGHT)&&!world.getBlockState(visualOnly).isOf(BloodborneBlocks.PART_BLOCK),"tree removal preserves foreign visual-only cell "+yaw);world.removeBlock(visualOnly,false);clear(context,tree);
   }
   ArchitectureBlock cases=required("o_cases_0");BlockPos caseRoot=context.getAbsolutePos(CLICK.up()),caseVisual=caseRoot.west();world.setBlockState(caseVisual,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);context.useStackOnBlock(player,new ItemStack(cases),CLICK,Direction.UP);context.assertTrue(caseRoot.equals(find(context,cases))&&world.getBlockState(caseVisual).isOf(Blocks.STONE),"cases ignores visual-only foreign cell");world.breakBlock(caseRoot,true,player);context.assertTrue(world.getBlockState(caseVisual).isOf(Blocks.STONE),"cases break preserves visual-only foreign cell");world.removeBlock(caseVisual,false);clear(context,cases);
   for(Direction side:Direction.Type.HORIZONTAL){ArchitectureBlock wall=required("o_wall_deco_1");BlockPos wallRoot=context.getAbsolutePos(CLICK.offset(side));BlockPos wallVisual=wallRoot.add(side.getAxis()==Direction.Axis.X?0:-1,0,side.getAxis()==Direction.Axis.X?-1:0);world.setBlockState(wallVisual,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);context.useStackOnBlock(player,new ItemStack(wall),CLICK,side);context.assertTrue(wallRoot.equals(find(context,wall))&&world.getBlockState(wallRoot).get(Properties.HORIZONTAL_FACING)==side&&world.getBlockState(wallVisual).isOf(Blocks.STONE),"wall placement preserves rotated visual-only cell "+side);world.breakBlock(wallRoot,true,player);context.assertTrue(world.getBlockState(wallVisual).isOf(Blocks.STONE),"wall break preserves rotated visual-only cell "+side);world.removeBlock(wallVisual,false);clear(context,wall);}
   // CLICK is on the flat world's ground; its DOWN neighbour is not an empty
   // destination. Use a floating support to isolate anchor handling from normal
   // vanilla occupied-cell refusal, and assert the precise adjacent origin.
   BlockPos floatingClick=new BlockPos(4,4,4);context.setBlockState(floatingClick,Blocks.STONE);
   for(Direction side:List.of(Direction.UP,Direction.DOWN)){ArchitectureBlock wall=required("o_wall_deco_1");BlockPos expected=context.getAbsolutePos(floatingClick.offset(side));context.assertTrue(world.getBlockState(expected).isAir(),"vertical fixture destination starts empty");context.useStackOnBlock(player,new ItemStack(wall),floatingClick,side);context.assertTrue(expected.equals(find(context,wall)),"wall placement keeps canonical origin for vertical clicked side "+side);clear(context,wall);}
   world.removeBlock(context.getAbsolutePos(floatingClick),false);
   context.complete();
  }finally{clear(context,tree);clear(context,required("o_wall_deco_1"));player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_contract_v2_transitions")
 public void contractV2GateAndRailingTransitions(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock gate=required("o_iron_gate"),railing=required("o_iron_railing");BlockPos root=context.getAbsolutePos(ROOT);
  try{
   BlockState closed=gate.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.OPEN,false);world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"closed gate rebuild");Set<BlockPos> closedHelpers=helperOffsets(closed);context.assertTrue(!closedHelpers.isEmpty(),"closed gate has helpers");for(BlockPos cell:closedHelpers)context.assertTrue(world.getBlockState(root.add(cell)).isOf(BloodborneBlocks.PART_BLOCK),"closed gate physical helper exists");context.assertTrue(!GeometryRuntime.rootShape(closed,false).isEmpty(),"closed gate blocks centre");context.useBlock(ROOT,player);BlockState opened=world.getBlockState(root);Set<BlockPos> openedHelpers=helperOffsets(opened);context.assertTrue(opened.isOf(gate)&&opened.get(Properties.OPEN)&&!openedHelpers.isEmpty(),"gate opens without moving root and creates open helpers");context.assertTrue(GeometryRuntime.rootShape(opened,false).isEmpty(),"open gate frees centre collision independently of selection");context.assertTrue(!GeometryRuntime.rootShape(opened,true).isEmpty(),"open gate retains whole selection");for(BlockPos cell:openedHelpers)context.assertTrue(world.getBlockState(root.add(cell)).isOf(BloodborneBlocks.PART_BLOCK),"opened gate physical helper exists");for(BlockPos oldOnly:closedHelpers)if(!openedHelpers.contains(oldOnly))context.assertTrue(world.getBlockState(root.add(oldOnly)).isAir(),"gate removes old-only helper");context.useBlock(ROOT,player);context.assertTrue(world.getBlockState(root).isOf(gate)&&!world.getBlockState(root).get(Properties.OPEN),"gate closes without moving root");
   BlockPos newPhysical=openedHelpers.stream().filter(cell->!closedHelpers.contains(cell)).map(root::add).findFirst().orElseThrow(()->new AssertionError("gate open must introduce a physical cell"));world.setBlockState(newPhysical,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);ActionResult blocked=closed.onUse(world,player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,true));context.assertTrue(blocked==ActionResult.FAIL&&world.getBlockState(root).equals(closed),"foreign new gate cell refuses open and keeps root");world.removeBlock(newPhysical,false);clear(context,gate);
   assertConnected(context,railing,new BlockPos(1,2,5));assertConnectedFourFacings(context,railing,new BlockPos(1,2,5));context.complete();
  }finally{clear(context,gate);clear(context,railing);player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=400, batchId="logical_contract_v2_batch")
 public void contractV2BatchFamiliesPlacePickBreakAndPreserveForeignCells(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();
  try{
   Set<String> families=LogicalContractV2.declaredFamilyIds();Set<String> active=new HashSet<>(families);active.removeIf(PaletteAliases::hidden);
   context.assertTrue(families.containsAll(BATCH_FAMILIES)&&families.contains("o_c001"),"all legacy and replacement contracts remain declared");
   for(String id:families){
    ArchitectureBlock declared=required(id);String key=BloodborneBlocks.key(declared.getDefaultState());LogicalContractV2.DebugMetadata metadata=LogicalContractV2.debugMetadata(id,key);
    context.assertTrue(metadata!=null,"every declared contract has runtime review metadata: "+id);
    if(id.matches("o_c\\d.*"))context.assertTrue(metadata.reviewId()!=null,"reviewed contract keeps its review id: "+id);
    if(id.equals("o_wall_deco_1"))context.assertTrue(metadata.reviewId()==null&&metadata.sourceFamily().contains("noCatalogID"),"wall remains explicitly no-catalog");
   }
   for(String id:active){
    ArchitectureBlock block=required(id);BlockPos canonical=null;
    for(Direction yaw:Direction.Type.HORIZONTAL){
     clear(context,block);moveOutside(context,player);player.setYaw(yaw.asRotation());BlockPos expectedRoot=context.getAbsolutePos(CLICK.up());Direction artworkFacing=yaw.getOpposite();BlockPos foreign=id.equals("o_c1979")?expectedRoot.offset(artworkFacing.rotateYCounterclockwise()).up():id.equals("o_c046")||id.equals("o_c474")?expectedRoot.east():context.getAbsolutePos(CLICK.up(20));world.setBlockState(foreign,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
     context.useStackOnBlock(player,new ItemStack(block),CLICK,Direction.UP);BlockPos root=find(context,block);context.assertTrue(root!=null,"batch family places: "+id+" "+yaw);
     if(canonical==null)canonical=root;else context.assertTrue(canonical.equals(root),"canonical master has no yaw offset: "+id+" "+yaw);
     BlockState state=world.getBlockState(root);if(state.contains(Properties.HORIZONTAL_FACING)){Direction expectedFacing=("door".equals(block.definition.behavior)||"gate".equals(block.definition.behavior))?yaw:yaw.getOpposite();context.assertTrue(state.get(Properties.HORIZONTAL_FACING)==expectedFacing,"placement stores every yaw without a master offset: "+id+" "+yaw);}GeometryRuntime.GeometryState geometry=GeometryRuntime.state(state);context.assertTrue(!GeometryRuntime.rootShape(state,true).isEmpty(),"outline exists: "+id+" "+yaw);
     for(GeometryRuntime.GeometryCell cell:geometry.parsedCells.values())context.assertTrue(cell.outline.size()<=1&&cell.collision.size()<=5,"simple per-cell geometry budget: "+id+" "+yaw);
     context.assertTrue(state.getBlock().getPickStack(world,root,state).isOf(block.asItem()),"master pick is canonical: "+id+" "+yaw);
     Set<BlockPos> helpers=helperOffsets(state);for(BlockPos offset:helpers)context.assertTrue(world.getBlockState(root.add(offset)).isOf(BloodborneBlocks.PART_BLOCK),"helper exists: "+id+" "+yaw+" "+offset);
     if(!helpers.isEmpty()){BlockPos helper=root.add(helpers.iterator().next());BlockState helperState=world.getBlockState(helper);context.assertTrue(helperState.getBlock().getPickStack(world,helper,helperState).isOf(block.asItem()),"helper pick is canonical: "+id+" "+yaw);}
     world.breakBlock(root,true,player);context.assertTrue(world.getBlockState(root).isAir(),"break removes master: "+id+" "+yaw);for(BlockPos offset:helpers)context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"break removes helpers: "+id+" "+yaw+" "+offset);
     context.assertTrue(world.getBlockState(foreign).isOf(Blocks.STONE),"foreign cell survives: "+id+" "+yaw);world.removeBlock(foreign,false);clearItems(world,root,block);
    }
   }
   context.complete();
  }finally{for(String id:LogicalContractV2.declaredFamilyIds())clear(context,required(id));player.discard();}
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, tickLimit=100, batchId="logical_contract_v2_batch")
 public void contractV2WallLanternAndDoorStates(TestContext context){
  floor(context);ServerWorld world=context.getWorld();PlayerEntity player=context.createMockCreativePlayer();ArchitectureBlock wall=required("o_c654"),lantern=required("o_c618"),door=required("o_c282");
  try{
   for(Direction side:Direction.Type.HORIZONTAL){
    clear(context,wall);context.useStackOnBlock(player,new ItemStack(wall),CLICK,side);BlockPos root=find(context,wall);context.assertTrue(context.getAbsolutePos(CLICK.offset(side)).equals(root),"wall master is clicked-face adjacent: "+side);context.assertTrue(world.getBlockState(root).get(Properties.HORIZONTAL_FACING)==side,"wall faces clicked horizontal side: "+side);
   }
   clear(context,wall);BlockPos root=context.getAbsolutePos(ROOT);BlockState unlit=lantern.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH).with(Properties.LIT,false);world.setBlockState(root,unlit,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,unlit),"lantern rebuilds unlit");int unlitLight=unlit.getLuminance();context.useBlock(ROOT,player);BlockState lit=world.getBlockState(root);context.assertTrue(lit.get(Properties.LIT)&&lit.getLuminance()>unlitLight,"lantern toggles lit state and luminance");context.useBlock(ROOT,player);context.assertTrue(!world.getBlockState(root).get(Properties.LIT),"lantern toggles off");clear(context,lantern);
   for(Direction facing:Direction.Type.HORIZONTAL){
    clear(context,door);BlockState closed=door.getDefaultState().with(Properties.HORIZONTAL_FACING,facing).with(Properties.OPEN,false);world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"door rebuilds closed: "+facing);context.useBlock(ROOT,player);BlockState open=world.getBlockState(root);context.assertTrue(open.get(Properties.OPEN)&&open.get(Properties.HORIZONTAL_FACING)==facing&&open.contains(Properties.DOOR_HINGE),"door toggles one logical panel: "+facing);context.assertTrue(!world.getBlockState(root.up()).isOf(door),"logical door does not create conventional upper block: "+facing);
   }
   for(Direction facing:Direction.Type.HORIZONTAL){
    clear(context,door);BlockState closed=BloodborneBlocks.set(door.getDefaultState(),door.getStateManager().getProperty("placement_height"),"source_height").with(Properties.HORIZONTAL_FACING,facing);
    BlockPos left=root.offset(facing.rotateYCounterclockwise()),right=root.offset(facing.rotateYClockwise());
    for(BlockPos frame:List.of(left,left.up(),right,right.up()))world.setBlockState(frame,Blocks.GLASS_PANE.getDefaultState(),Block.NOTIFY_ALL);
    world.setBlockState(root,closed,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,closed),"source-height central passage avoids pane frame");
    context.useBlock(ROOT,player);context.assertTrue(world.getBlockState(root).get(Properties.OPEN),"source-height opens without frame overwrite");
    context.assertTrue(GeometryRuntime.rootShape(world.getBlockState(root),false).isEmpty(),"source-height open passage has no collision");
    context.useBlock(ROOT,player);context.assertTrue(!world.getBlockState(root).get(Properties.OPEN),"source-height closes");
    world.breakBlock(root.up(),true,player);context.assertTrue(world.getBlockState(root).isAir(),"source-height helper break removes door");
    for(BlockPos frame:List.of(left,left.up(),right,right.up())){context.assertTrue(world.getBlockState(frame).isOf(Blocks.GLASS_PANE),"source frame preserved through open/close/helper break");world.removeBlock(frame,false);}
   }
   context.complete();
  }finally{clear(context,wall);clear(context,lantern);clear(context,door);player.discard();}
 }

 private static void floor(TestContext context){context.setBlockState(CLICK,Blocks.STONE);}
 // TestContext's PlayerEntity mock constructs an ItemEntity but does not spawn it; ServerPlayerEntity does.
 private static ServerPlayerEntity createMockSurvivalServerPlayer(TestContext context){ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(GameMode.SURVIVAL);return player;}
 private static void removeMockServerPlayer(ServerPlayerEntity player){player.getServer().getPlayerManager().remove(player);}
 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing generated logical fixture "+id);return block;}
 private static BlockPos find(TestContext context,Block block){ServerWorld world=context.getWorld();for(int x=-4;x<12;x++)for(int y=-4;y<12;y++)for(int z=-4;z<12;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));if(world.getBlockState(pos).isOf(block))return pos;}return null;}
 private static void assertOpen(TestContext context,ArchitectureBlock block,Direction facing,boolean open){BlockPos root=context.getAbsolutePos(ROOT);BlockState state=context.getWorld().getBlockState(root);context.assertTrue(state.get(Properties.OPEN)==open&&state.get(Properties.HORIZONTAL_FACING)==facing,"interaction preserves facing and sets open="+open);for(BlockPos target:helperOffsets(state))context.assertTrue(context.getWorld().getBlockState(root.add(target)).isOf(BloodborneBlocks.PART_BLOCK),block.definition.id+" helpers rebuilt for open="+open);}
 private static void moveOutside(TestContext context,PlayerEntity player){BlockPos pos=context.getAbsolutePos(new BlockPos(-6,4,-6));player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);}
 private static void clear(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(int x=-4;x<12;x++)for(int y=-4;y<12;y++)for(int z=-4;z<12;z++){BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));BlockState state=world.getBlockState(pos);if(state.isOf(block))world.breakBlock(pos,false);}}
 private static void clearFixtures(TestContext context){for(String id:List.of("o_acacia_door","o_iron_gate","o_shuttered_window","o_stone_curb","o_high_balustrade"))clear(context,required(id));}
 private static long drops(ServerWorld world,BlockPos root,ArchitectureBlock block){return world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(4),entity->entity.getStack().isOf(block.asItem())).stream().mapToInt(entity->entity.getStack().getCount()).sum();}
 private static void clearItems(ServerWorld world,BlockPos root,ArchitectureBlock block){world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(12),entity->entity.getStack().isOf(block.asItem())).forEach(ItemEntity::discard);}
 private static void assertConnected(TestContext context,ArchitectureBlock block,BlockPos first){ServerWorld world=context.getWorld();BlockPos second=first.east();world.setBlockState(context.getAbsolutePos(first),block.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(context.getAbsolutePos(second),block.getDefaultState(),Block.NOTIFY_ALL);context.assertTrue(world.getBlockState(context.getAbsolutePos(first)).get(Properties.EAST),block.definition.id+" connects east");context.assertTrue(world.getBlockState(context.getAbsolutePos(second)).get(Properties.WEST),block.definition.id+" connects west");}
 private static void assertConnectedFourFacings(TestContext context,ArchitectureBlock block,BlockPos first){ServerWorld world=context.getWorld();for(Direction facing:Direction.Type.HORIZONTAL){clear(context,block);BlockPos second=first.offset(facing);BlockState initial=block.getDefaultState();if(initial.contains(Properties.HORIZONTAL_FACING))initial=initial.with(Properties.HORIZONTAL_FACING,facing);world.setBlockState(context.getAbsolutePos(first),initial,Block.NOTIFY_ALL);world.setBlockState(context.getAbsolutePos(second),initial,Block.NOTIFY_ALL);BlockState a=world.getBlockState(context.getAbsolutePos(first)),b=world.getBlockState(context.getAbsolutePos(second));context.assertTrue(connected(a,facing)&&connected(b,facing.getOpposite()),block.definition.id+" connects in facing "+facing);if(a.contains(Properties.HORIZONTAL_FACING))context.assertTrue(a.get(Properties.HORIZONTAL_FACING)==facing,block.definition.id+" retains explicit facing "+facing);}}
 private static boolean connected(BlockState state,Direction direction){var property=state.getBlock().getStateManager().getProperty(direction.asString());return property instanceof BooleanProperty value&&state.get(value);}
 private static BooleanProperty attachmentProperty(ArchitectureBlock block){var property=block.getStateManager().getProperty("lantern");if(!(property instanceof BooleanProperty result))throw new AssertionError("statue lantern property missing");return result;}
 private static void install(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(ROOT);BlockState state=block.getDefaultState().with(Properties.HORIZONTAL_FACING,Direction.NORTH);world.setBlockState(root,state,Block.NOTIFY_ALL);if(!GeometryRuntime.rebuild(world,root,state))throw new AssertionError("statue fixture cannot rebuild");}
 private static void assertAttachment(TestContext context,BooleanProperty property,boolean expected,String message){context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(ROOT)).get(property)==expected,message);}
 private static int countItem(PlayerEntity player,ArchitectureBlock block){int count=0;for(int slot=0;slot<player.getInventory().size();slot++){ItemStack stack=player.getInventory().getStack(slot);if(stack.isOf(block.asItem()))count+=stack.getCount();}return count;}
 private static void fillMainInventory(PlayerEntity player){for(int slot=0;slot<PlayerInventory.MAIN_SIZE;slot++)player.getInventory().setStack(slot,new ItemStack(Blocks.STONE,64));}
 private static Set<BlockPos> helperOffsets(BlockState state){Set<BlockPos> targets=new HashSet<>(GeometryRuntime.state(state).parsedCells.keySet());targets.remove(BlockPos.ORIGIN);return targets;}
 private static BlockPos ownedHelper(TestContext context,BlockPos root,BlockState state){ServerWorld world=context.getWorld();for(BlockPos offset:helperOffsets(state)){BlockPos helper=root.add(offset);ArchitecturePartBlockEntity part=GeometryRuntime.part(world,helper);if(part!=null&&part.rootPos().equals(root)&&GeometryRuntime.ownsHelper(state,root,helper,part.ownerId()))return helper;}return null;}
 private static BlockPos attachmentOnlyTarget(ArchitectureBlock block,BooleanProperty property,BlockPos root){BlockState off=block.getDefaultState().with(property,false),on=off.with(property,true);Set<BlockPos> offTargets=helperOffsets(off);return helperOffsets(on).stream().filter(target->!offTargets.contains(target)).map(root::add).findFirst().orElseThrow(()->new AssertionError("attached statue must reserve a new helper cell"));}
 private static BlockPos placedRoot(TestContext context,ArchitectureBlock block){ServerWorld world=context.getWorld();for(BlockState candidate:block.getStateManager().getStates()){BlockPos root=context.getAbsolutePos(CLICK.up().subtract(GeometryRuntime.anchor(candidate,Direction.UP)));if(world.getBlockState(root).isOf(block))return root;}return null;}
 private static void verifyFamilyPlacement(TestContext context,PlayerEntity player,ArchitectureBlock block){
  ServerWorld world=context.getWorld();floor(context);BlockPos root=null;
  try{
   context.useStackOnBlock(player,new ItemStack(block),CLICK,Direction.UP);root=placedRoot(context,block);context.assertTrue(root!=null,"logical item placement creates root: "+block.definition.id);
   BlockState state=world.getBlockState(root);Set<BlockPos> expected=helperOffsets(state);for(BlockPos offset:expected)context.assertTrue(world.getBlockState(root.add(offset)).isOf(BloodborneBlocks.PART_BLOCK),"logical helper exists: "+block.definition.id+" "+offset);
   context.assertTrue(state.getBlock().getPickStack(world,root,state).isOf(block.asItem()),"logical master pick returns family item: "+block.definition.id);
   if(!expected.isEmpty()){
    BlockPos helper=root.add(expected.iterator().next());BlockState helperState=world.getBlockState(helper);context.assertTrue(helperState.getBlock().getPickStack(world,helper,helperState).isOf(block.asItem()),"logical helper pick returns family item: "+block.definition.id);
    world.breakBlock(helper,true,player);context.assertTrue(world.getBlockState(root).isAir(),"breaking helper removes family root: "+block.definition.id);
    for(BlockPos offset:expected)context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"breaking helper removes every owned cell: "+block.definition.id);
   }else{
    world.breakBlock(root,true,player);context.assertTrue(world.getBlockState(root).isAir(),"breaking single-cell family removes root: "+block.definition.id);
   }
   context.assertTrue(drops(world,root,block)==1,"breaking object drops one family item: "+block.definition.id);
  }finally{if(root!=null&&world.getBlockState(root).isOf(block))world.breakBlock(root,false);if(root!=null)clearItems(world,root,block);}
 }
}
