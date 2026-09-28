package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Headless lifecycle proof for bounded helper bindings carried by a real root cell. */
public final class SharedPhysicalGameTests implements FabricGameTest {
 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=80,batchId="shared_physical_root_insertion")
 public void exactRepairTestOneWindowWallReplacementUsesOrdinaryAirPlacement(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();BlockPos origin=context.getAbsolutePos(new BlockPos(10,6,10));
  try{
   ArchitectureBlock window=required("o_shuttered_window"),wall=requiredCity("owner_a631fe463a1af9f0fe4e");
   Map<BlockPos,BlockState> roots=new LinkedHashMap<>();
   roots.put(origin.add(1,1,1),state(window,Map.of("facing","north","open","false","visual","base")));
   roots.put(origin.add(1,0,0),state(required("o_stone_railing"),Map.of("east","false","facing","north","north","true","south","false","up","true","visual","base","west","false")));
   roots.put(origin.add(0,0,1),state(requiredCity("owner_58bccf82adec219c90f0"),Map.of("facing","north")));
   roots.put(origin.add(0,1,1),state(requiredCity("owner_103c69237444338a3df4"),Map.of("facing","north")));
   roots.put(origin.add(0,2,1),state(requiredCity("owner_53e58987698a7c96e613"),Map.of("facing","north")));
   roots.put(origin.add(1,0,1),state(requiredCity("owner_32dc66a6e819fe19c3b8"),Map.of("facing","north")));
   roots.put(origin.add(2,0,1),state(requiredCity("owner_16c5223d3d45e3ca0c5f"),Map.of("facing","north")));
   roots.put(origin.add(2,1,1),state(wall,Map.of("facing","north")));roots.put(origin.add(2,2,1),state(wall,Map.of("facing","north")));
   for(var entry:roots.entrySet()){world.setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,entry.getKey(),entry.getValue()),"TEST1 exact output rebuilds at "+entry.getKey().subtract(origin));}
   BlockPos windowRoot=origin.add(1,1,1),windowHelper=windowRoot.up(),wallRoot=origin.add(2,2,1);ArchitecturePartBlockEntity helper=GeometryRuntime.part(world,windowHelper);Identifier windowId=Registries.BLOCK.getId(window);NbtCompound before=helper==null?new NbtCompound():helper.createNbt();
   context.assertTrue(helper!=null&&helper.hasBinding(windowRoot,windowId)&&before.getLong("Root")==windowRoot.asLong()&&before.getString("Owner").equals(windowId.toString()),"TEST1 window upper helper has exact saved singleton ownership");
   world.breakBlock(wallRoot,false);context.assertTrue(world.getBlockState(wallRoot).isAir(),"TEST1 east wall deletion leaves ordinary air, not the window helper");context.assertTrue(GeometryRuntime.part(world,windowHelper).createNbt().equals(before),"wall deletion leaves exact window helper NBT unchanged");
   face(player,Direction.SOUTH,origin);ItemStack stack=new ItemStack(wall);ActionResult result=useItem(player,stack,windowHelper,Direction.EAST);
   context.assertTrue(result.isAccepted()&&world.getBlockState(wallRoot).isOf(wall),"same wall item replaces the deleted TEST1 wall from the visible window-helper face");context.assertTrue(GeometryRuntime.part(world,windowHelper).createNbt().equals(before),"ordinary TEST1 wall replacement leaves window helper NBT unchanged");context.complete();
  }finally{player.discard();}
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=120,batchId="shared_physical_root_insertion")
 public void validHelperAcceptsAtomicItemRootInsertionInBothOrdersAndCycles(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock guest=wholeOwner(),inserted=carrier();BlockState guestState=guest.getDefaultState(),insertedState=inserted.getDefaultState();BlockPos guestRoot=context.getAbsolutePos(new BlockPos(10,8,10));BlockPos offset=helperOffsets(guestState).iterator().next(),carrier=guestRoot.add(offset);Identifier guestId=Registries.BLOCK.getId(guest);
   clear(world,guestRoot,guestState);world.setBlockState(guestRoot,guestState,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,guestRoot,guestState),"guest-first fixture creates its helper");ArchitecturePartBlockEntity helper=GeometryRuntime.part(world,carrier);context.assertTrue(helper!=null&&helper.hasBinding(guestRoot,guestId),"guest-first helper stores exact binding");NbtCompound saved=helper.createNbt();
   Direction side=supportSide(guestRoot,guestState,carrier);BlockPos support=carrier.offset(side.getOpposite());world.setBlockState(support,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);ItemStack first=new ItemStack(inserted,2);ActionResult placed=useItem(player,first,support,side);
   context.assertTrue(placed.isAccepted()&&first.getCount()==1&&world.getBlockState(carrier).isOf(inserted),"real item inserts a complete root into an existing valid helper");ArchitecturePartBlockEntity shared=GeometryRuntime.part(world,carrier);context.assertTrue(shared!=null&&shared.createNbt().equals(saved),"root insertion preserves exact guest helper NBT");
   world.removeBlockEntity(carrier);ArchitecturePartBlockEntity reloaded=new ArchitecturePartBlockEntity(carrier,world.getBlockState(carrier));reloaded.readNbt(saved);world.addBlockEntity(reloaded);context.assertTrue(reloaded.hasBinding(guestRoot,guestId),"inserted root carrier survives block-entity save/load");
   world.breakBlock(guestRoot,false);context.assertTrue(world.getBlockState(carrier).isOf(inserted)&&GeometryRuntime.part(world,carrier)==null,"independent guest break preserves inserted root and removes its last guest BE");
   world.setBlockState(guestRoot,guestState,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,guestRoot,guestState),"root-first reverse order binds guest onto the existing root");context.assertTrue(GeometryRuntime.part(world,carrier).hasBinding(guestRoot,guestId),"reverse order stores the same guest binding");
   world.breakBlock(carrier,false);context.runAtTick(2,()->{
    ArchitecturePartBlockEntity restored=GeometryRuntime.part(world,carrier);context.assertTrue(world.getBlockState(carrier).isOf(BloodborneBlocks.PART_BLOCK)&&restored!=null&&restored.hasBinding(guestRoot,guestId),"carrier-first removal restores exact helper and preserves guest");
    ItemStack second=new ItemStack(inserted);ActionResult cycled=useItem(player,second,support,side);context.assertTrue(cycled.isAccepted()&&second.isEmpty()&&world.getBlockState(carrier).isOf(inserted),"second real-item cycle reinserts the root atomically");
    world.breakBlock(guestRoot,false);context.assertTrue(world.getBlockState(carrier).isOf(inserted),"second-cycle independent guest break again preserves inserted root");player.discard();context.complete();
   });
 }

 @GameTest(templateName="bloodborne_blocks:practical_test_kit",tickLimit=80,batchId="shared_physical_root_insertion")
 public void rejectedHelperRootInsertionsLeaveItemWorldAndNbtUntouched(TestContext context){
  ServerWorld world=context.getWorld();PlayerEntity player=context.createMockSurvivalPlayer();ArchitectureBlock guest=wholeOwner(),inserted=carrier();BlockState guestState=guest.getDefaultState();BlockPos guestRoot=context.getAbsolutePos(new BlockPos(10,8,10)),carrier=guestRoot.add(helperOffsets(guestState).iterator().next());
  try{
   context.assertTrue(GeometryRuntime.hasRootInsertionCapacity(ArchitecturePartBlockEntity.MAX_BINDINGS-1)&&!GeometryRuntime.hasRootInsertionCapacity(ArchitecturePartBlockEntity.MAX_BINDINGS),"root insertion counts the new root inside the strict sixteen-owner carrier limit");
   world.setBlockState(guestRoot,guestState,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,guestRoot,guestState),"rejection fixture creates valid helper");ArchitecturePartBlockEntity helper=GeometryRuntime.part(world,carrier);NbtCompound before=helper.createNbt();BlockState beforeState=world.getBlockState(carrier);Direction side=supportSide(guestRoot,guestState,carrier);BlockPos support=carrier.offset(side.getOpposite());world.setBlockState(support,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);
   ItemStack foreign=new ItemStack(Blocks.DIAMOND_BLOCK);ActionResult foreignResult=useItem(player,foreign,support,side);context.assertTrue(!foreignResult.isAccepted()&&foreign.getCount()==1&&world.getBlockState(carrier).equals(beforeState)&&GeometryRuntime.part(world,carrier).createNbt().equals(before),"foreign item cannot overwrite a valid helper carrier or alter its NBT");
   BlockPos unloaded=carrier.add(4096,0,0);context.assertTrue(helper.bind(unloaded,Registries.BLOCK.getId(guest)),"synthetic unloaded binding makes insertion fail closed");before=helper.createNbt();ItemStack stack=new ItemStack(inserted,2);stack.getOrCreateNbt().putString("TestSentinel","unchanged");NbtCompound itemBefore=stack.getNbt().copy();ActionResult rejected=useItem(player,stack,support,side);context.assertTrue(!rejected.isAccepted()&&stack.getCount()==2&&stack.getNbt().equals(itemBefore)&&world.getBlockState(carrier).equals(beforeState)&&GeometryRuntime.part(world,carrier).createNbt().equals(before),"unloaded guest rejection preserves item count/NBT, helper state and exact ownership NBT");
   helper.unbind(unloaded,Registries.BLOCK.getId(guest));world.setBlockState(carrier,inserted.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(support,Blocks.WHITE_CONCRETE.getDefaultState(),Block.NOTIFY_ALL);ItemStack second=new ItemStack(inserted,2);BlockState occupied=world.getBlockState(carrier);ActionResult rootRoot=useItem(player,second,support,side);context.assertTrue(!rootRoot.isAccepted()&&second.getCount()==2&&world.getBlockState(carrier).equals(occupied),"root-root item insertion remains rejected without changing item or root");context.complete();
  }finally{player.discard();}
 }
 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="shared_physical")
 public void guestFirstAndCarrierFirstRemovalPreserveCompleteObjects(TestContext context){
  Fixture first=fixture(context,new BlockPos(4,4,4));ServerWorld world=context.getWorld();
  world.breakBlock(first.guestRoot,false);
  context.assertTrue(world.getBlockState(first.carrier).equals(first.carrierState),"guest removal preserves carrier root state");
  context.assertTrue(GeometryRuntime.part(world,first.carrier)==null,"last guest removal leaves no block entity on carrier root");
  Fixture second=fixture(context,new BlockPos(12,4,12));world.breakBlock(second.carrier,false);
  context.runAtTick(2,()->{
   context.assertTrue(world.getBlockState(second.guestRoot).equals(second.guestState),"carrier removal preserves guest root");
   context.assertTrue(world.getBlockState(second.carrier).isOf(BloodborneBlocks.PART_BLOCK),"removed carrier becomes ordinary guest helper");
   ArchitecturePartBlockEntity helper=GeometryRuntime.part(world,second.carrier);
   context.assertTrue(helper!=null&&helper.hasBinding(second.guestRoot,second.guestOwner),"restored helper retains exact guest binding");
   world.breakBlock(second.guestRoot,false);
   context.assertTrue(world.getBlockState(second.carrier).isAir(),"last guest removal clears restored helper");
   context.complete();
  });
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="shared_physical")
 public void ownershipNbtReloadIsBoundedSortedAndLegacyCompatible(TestContext context){
  Fixture fixture=fixture(context,new BlockPos(4,4,4));ServerWorld world=context.getWorld();ArchitecturePartBlockEntity original=GeometryRuntime.part(world,fixture.carrier);
  context.assertTrue(original!=null,"root-helper fixture has guest block entity");var singleton=original.createNbt();
  context.assertTrue(singleton.contains("Root",NbtElement.LONG_TYPE)&&singleton.contains("Owner",NbtElement.STRING_TYPE)&&!singleton.contains("Owners"),"singleton preserves legacy Root/Owner schema");
  world.removeBlockEntity(fixture.carrier);ArchitecturePartBlockEntity reloaded=new ArchitecturePartBlockEntity(fixture.carrier,fixture.carrierState);reloaded.readNbt(singleton);world.addBlockEntity(reloaded);
  context.assertTrue(reloaded.hasBinding(fixture.guestRoot,fixture.guestOwner),"singleton ownership survives block-entity reload");
  Identifier owner=fixture.guestOwner;context.assertTrue(reloaded.bind(fixture.guestRoot.add(2,0,0),owner),"second binding accepted");var plural=reloaded.createNbt();
  context.assertTrue(plural.getList("Owners",NbtElement.COMPOUND_TYPE).size()==2,"plural schema records every binding");
  NbtCompound mismatch=plural.copy();mismatch.putLong("Root",fixture.guestRoot.add(99,0,0).asLong());ArchitecturePartBlockEntity rejectedMismatch=new ArchitecturePartBlockEntity(fixture.carrier,fixture.carrierState);rejectedMismatch.readNbt(mismatch);context.assertTrue(rejectedMismatch.isEmpty(),"plural schema rejects mismatched legacy first pair");
  NbtCompound reversed=plural.copy();NbtList canonical=reversed.getList("Owners",NbtElement.COMPOUND_TYPE),noncanonical=new NbtList();noncanonical.add(canonical.getCompound(1).copy());noncanonical.add(canonical.getCompound(0).copy());reversed.put("Owners",noncanonical);ArchitecturePartBlockEntity rejectedOrder=new ArchitecturePartBlockEntity(fixture.carrier,fixture.carrierState);rejectedOrder.readNbt(reversed);context.assertTrue(rejectedOrder.isEmpty(),"plural schema rejects noncanonical order");
  ArchitecturePartBlockEntity bounded=new ArchitecturePartBlockEntity(fixture.carrier,fixture.carrierState);for(int i=0;i<ArchitecturePartBlockEntity.MAX_BINDINGS;i++)context.assertTrue(bounded.bind(fixture.carrier.add(i+1,3,0),owner),"binding within bound "+i);
  context.assertTrue(!bounded.bind(fixture.carrier.add(99,3,0),owner),"seventeenth binding rejected");context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="shared_physical")
 public void helperHelperCarrierTracksAndRemovesOwnersIndependently(TestContext context){
  ServerWorld world=context.getWorld();SharedFixture fixture=sharedFixture(context,new BlockPos(8,5,8));ArchitecturePartBlockEntity shared=GeometryRuntime.part(world,fixture.sharedCell);Identifier owner=Registries.BLOCK.getId(fixture.block);
  context.assertTrue(shared!=null&&shared.hasBinding(fixture.firstRoot,owner)&&shared.hasBinding(fixture.secondRoot,owner)&&shared.bindings().size()==2,"shared helper stores both exact owners");
  world.breakBlock(fixture.firstRoot,false);shared=GeometryRuntime.part(world,fixture.sharedCell);context.assertTrue(shared!=null&&!shared.hasBinding(fixture.firstRoot,owner)&&shared.hasBinding(fixture.secondRoot,owner),"first root removal preserves only second binding");
  world.breakBlock(fixture.secondRoot,false);context.assertTrue(world.getBlockState(fixture.sharedCell).isAir(),"last root removal clears shared helper");context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="shared_physical")
 public void sameBlockRotationRebuildsWholeOwnerHelpers(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock owner=wholeOwner();BlockState before=owner.getDefaultState();BlockPos root=context.getAbsolutePos(new BlockPos(7,4,7));clear(world,root,before);world.setBlockState(root,before,Block.NOTIFY_ALL);
  context.assertTrue(GeometryRuntime.rebuild(world,root,before),"whole owner initial helper rebuild succeeds");Set<BlockPos> old=helperOffsets(before);BlockState after=owner.rotate(before,BlockRotation.CLOCKWISE_90);context.assertTrue(!after.equals(before),"whole owner rotation changes state");
  context.assertTrue(world.setBlockState(root,after,Block.NOTIFY_ALL),"same-block rotation applies");Identifier ownerId=Registries.BLOCK.getId(owner);
  for(BlockPos offset:helperOffsets(after)){ArchitecturePartBlockEntity part=GeometryRuntime.part(world,root.add(offset));context.assertTrue(part!=null&&part.hasBinding(root,ownerId),"rotated helper owns exact root "+offset);}
  for(BlockPos offset:old)if(!helperOffsets(after).contains(offset))context.assertTrue(world.getBlockState(root.add(offset)).isAir(),"stale rotated helper removed "+offset);context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="shared_physical")
 public void storageRootOwnersUseEmptyRootAndCleanEveryOwnedHelper(TestContext context){
  ServerWorld world=context.getWorld();BlockPos root=context.getAbsolutePos(new BlockPos(8,4,8));
  for(String id:List.of("owner_0afae65ab577cf2ac96f","owner_110a574aebe8f2b33067","owner_5071a02a3096d9d9820f","owner_c130a81530121df11bd7")){
   ArchitectureBlock owner=requiredCity(id);BlockState state=owner.getDefaultState();var geometry=GeometryRuntime.state(state);context.assertTrue(geometry.parsedCells.containsKey(BlockPos.ORIGIN)&&GeometryRuntime.cellShape(state,BlockPos.ORIGIN,false).isEmpty()&&GeometryRuntime.cellShape(state,BlockPos.ORIGIN,true).isEmpty(),id+" stores its root in an intentionally empty cell");
   BlockPos physical=geometry.parsedCells.entrySet().stream().filter(entry->!entry.getKey().equals(BlockPos.ORIGIN)&&(!entry.getValue().collisionShape.isEmpty()||!entry.getValue().outlineShape.isEmpty())).map(Map.Entry::getKey).findFirst().orElseThrow(()->new AssertionError(id+" has no physical neighbor cell"));
   clear(world,root,state);world.setBlockState(root,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,root,state),id+" rebuilds from its empty storage root");Identifier ownerId=Registries.BLOCK.getId(owner);
   for(BlockPos offset:helperOffsets(state)){ArchitecturePartBlockEntity helper=GeometryRuntime.part(world,root.add(offset));context.assertTrue(helper!=null&&helper.hasBinding(root,ownerId),id+" helper owns the storage root at "+offset);}
   context.assertTrue(!GeometryRuntime.guestShape(world,root.add(physical),false).isEmpty()||!GeometryRuntime.guestShape(world,root.add(physical),true).isEmpty(),id+" exposes physics through a non-root cell");
   world.breakBlock(root,false);context.assertTrue(world.getBlockState(root).isAir(),id+" root breaks normally");for(BlockPos offset:helperOffsets(state))context.assertTrue(world.getBlockState(root.add(offset)).isAir()&&GeometryRuntime.part(world,root.add(offset))==null,id+" break leaves no orphan helper at "+offset);
  }
  context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="shared_physical")
 public void foreignReplacementNeverGetsOverwrittenAndGuestIsRemovedWhole(TestContext context){
  Fixture fixture=fixture(context,new BlockPos(4,4,4)),ordinaryRemoval=fixture(context,new BlockPos(12,4,12)),nativeReplacement=fixture(context,new BlockPos(4,4,12));ServerWorld world=context.getWorld();ArchitectureBlock nativeBlock=BloodborneBlocks.CITY_BLOCKS.values().stream().filter(block->!(block instanceof SharedArchitectureBlock)).findFirst().orElseThrow();BlockState nativeState=nativeBlock.getDefaultState();world.breakBlock(ordinaryRemoval.carrier,false);world.setBlockState(fixture.carrier,Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(nativeReplacement.carrier,nativeState,Block.NOTIFY_ALL);
  context.runAtTick(4,()->{
   context.assertTrue(world.getBlockState(ordinaryRemoval.carrier).isOf(BloodborneBlocks.PART_BLOCK)&&GeometryRuntime.part(world,ordinaryRemoval.carrier)!=null,"one recovery does not skip the next queued carrier");
   context.assertTrue(world.getBlockState(fixture.carrier).isOf(Blocks.DIAMOND_BLOCK),"foreign replacement is never overwritten by helper restoration");
   context.assertTrue(world.getBlockState(fixture.guestRoot).isAir(),"foreign replacement removes the complete loaded guest object");
   context.assertTrue(world.getBlockState(nativeReplacement.carrier).equals(nativeState)&&world.getBlockState(nativeReplacement.guestRoot).isAir(),"non-carrier architecture replacement stays intact and removes its loaded guest whole");
   for(BlockPos offset:GeometryRuntime.state(fixture.guestState).parsedCells.keySet())if(!offset.equals(BlockPos.ORIGIN))context.assertTrue(!world.getBlockState(fixture.guestRoot.add(offset)).isOf(BloodborneBlocks.PART_BLOCK),"foreign replacement leaves no guest fragment "+offset);
   context.complete();
  });
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="shared_physical")
 public void unloadedGuestRejectsCarrierReplacementAndSurvivesReload(TestContext context){
  Fixture fixture=fixture(context,new BlockPos(6,4,6));ServerWorld world=context.getWorld();ArchitecturePartBlockEntity carrier=GeometryRuntime.part(world,fixture.carrier);BlockPos unloaded=fixture.carrier.add(4096,0,0);
  context.assertTrue(carrier!=null&&!world.isChunkLoaded(unloaded)&&carrier.bind(unloaded,fixture.guestOwner),"synthetic unloaded guest binding is recorded without loading its chunk");world.setBlockState(fixture.carrier,Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
  context.assertTrue(world.getBlockState(fixture.carrier).equals(fixture.carrierState),"carrier replacement is synchronously rejected while a guest root is unloaded");carrier=GeometryRuntime.part(world,fixture.carrier);context.assertTrue(carrier!=null&&carrier.hasBinding(unloaded,fixture.guestOwner),"rejected replacement preserves unloaded binding");
  NbtCompound saved=carrier.createNbt();world.removeBlockEntity(fixture.carrier);ArchitecturePartBlockEntity reloaded=new ArchitecturePartBlockEntity(fixture.carrier,fixture.carrierState);reloaded.readNbt(saved);world.addBlockEntity(reloaded);context.assertTrue(reloaded.hasBinding(unloaded,fixture.guestOwner),"unloaded binding survives block-entity save and reload");context.complete();
 }

 @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="shared_physical")
 public void occupiedRootRemainsARejectedRootRootPlacement(TestContext context){
  ServerWorld world=context.getWorld();ArchitectureBlock owner=wholeOwner();BlockPos root=context.getAbsolutePos(new BlockPos(6,4,6));ArchitectureBlock carrier=carrier();world.setBlockState(root,carrier.getDefaultState(),Block.NOTIFY_ALL);
  context.assertTrue(!GeometryRuntime.canPlace(world,root,owner.getDefaultState()),"root-root placement remains blocked");context.assertTrue(GeometryRuntime.part(world,root)==null,"ordinary root does not allocate a block entity");context.complete();
 }

 private static Fixture fixture(TestContext context,BlockPos relativeRoot){
  ServerWorld world=context.getWorld();ArchitectureBlock guest=wholeOwner();BlockState guestState=guest.getDefaultState();BlockPos guestRoot=context.getAbsolutePos(relativeRoot);BlockPos offset=helperOffsets(guestState).iterator().next(),carrierPos=guestRoot.add(offset);ArchitectureBlock carrier=carrier();BlockState carrierState=carrier.getDefaultState();
  clear(world,guestRoot,guestState);ArchitecturePartBlockEntity stale=GeometryRuntime.part(world,carrierPos);if(stale!=null)for(var binding:stale.bindings())stale.unbind(binding.root(),binding.owner());world.removeBlock(carrierPos,false);world.setBlockState(carrierPos,carrierState,Block.NOTIFY_ALL);
  context.assertTrue(GeometryRuntime.part(world,carrierPos)==null,"ordinary carrier root has no block entity before sharing");world.setBlockState(guestRoot,guestState,Block.NOTIFY_ALL);
  context.assertTrue(GeometryRuntime.rebuild(world,guestRoot,guestState),"root-helper fixture rebuild succeeds");context.assertTrue(world.getBlockState(carrierPos).equals(carrierState),"root-helper rebuild keeps carrier state");
  ArchitecturePartBlockEntity part=GeometryRuntime.part(world,carrierPos);Identifier owner=Registries.BLOCK.getId(guest);context.assertTrue(part!=null&&part.hasBinding(guestRoot,owner),"carrier stores exact guest binding");
  context.assertTrue(!guest.getCollisionShape(guestState,world,guestRoot,net.minecraft.block.ShapeContext.absent()).isEmpty()||!GeometryRuntime.guestShape(world,carrierPos,false).isEmpty(),"root and guest physical shapes remain represented");
  return new Fixture(guestRoot,guestState,owner,carrierPos,carrierState);
 }
 private static SharedFixture sharedFixture(TestContext context,BlockPos relativeRoot){
  ServerWorld world=context.getWorld();
  for(ArchitectureBlock block:BloodborneBlocks.CITY_BLOCKS.values())if(block.definition.whole_owner){
   BlockState state=block.getDefaultState();Set<BlockPos> cells=GeometryRuntime.state(state).parsedCells.keySet();List<BlockPos> helpers=cells.stream().filter(p->!p.equals(BlockPos.ORIGIN)).toList();
   for(BlockPos first:helpers)for(BlockPos second:helpers){
    BlockPos delta=first.subtract(second);if(delta.equals(BlockPos.ORIGIN)||cells.contains(delta)||cells.contains(delta.multiply(-1)))continue;
    Set<BlockPos> shifted=cells.stream().map(delta::add).collect(java.util.stream.Collectors.toSet()),overlap=new LinkedHashSet<>(cells);overlap.retainAll(shifted);if(overlap.isEmpty())continue;
    BlockPos firstRoot=context.getAbsolutePos(relativeRoot),secondRoot=firstRoot.add(delta);Set<BlockPos> occupied=new HashSet<>();for(BlockPos cell:cells){occupied.add(firstRoot.add(cell));occupied.add(secondRoot.add(cell));}for(BlockPos cell:occupied)world.removeBlock(cell,false);
    world.setBlockState(firstRoot,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,firstRoot,state),"first helper-sharing owner rebuild succeeds");world.setBlockState(secondRoot,state,Block.NOTIFY_ALL);context.assertTrue(GeometryRuntime.rebuild(world,secondRoot,state),"second helper-sharing owner rebuild succeeds");return new SharedFixture(block,firstRoot,secondRoot,firstRoot.add(overlap.iterator().next()));
   }
  }
  throw new IllegalStateException("No helper-helper fixture found");
 }
 private static ArchitectureBlock wholeOwner(){return BloodborneBlocks.CITY_BLOCKS.values().stream().filter(block->block.definition.whole_owner&&!helperOffsets(block.getDefaultState()).isEmpty()&&helperOffsets(block.getDefaultState()).stream().allMatch(p->Math.abs(p.getX())<=2&&Math.abs(p.getY())<=2&&Math.abs(p.getZ())<=2)).findFirst().orElseThrow();}
 private static ArchitectureBlock carrier(){return BloodborneBlocks.BLOCKS.values().stream().filter(block->block instanceof SharedArchitectureBlock&&GeometryRuntime.state(block.getDefaultState()).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN))).findFirst().orElseThrow();}
 private static ArchitectureBlock required(String id){ArchitectureBlock block=BloodborneBlocks.BLOCKS.get(id);if(block==null)throw new AssertionError("missing shared fixture "+id);return block;}
 private static ArchitectureBlock requiredCity(String id){ArchitectureBlock block=BloodborneBlocks.CITY_BLOCKS.get(id);if(block==null)throw new AssertionError("missing city shared fixture "+id);return block;}
 private static BlockState state(ArchitectureBlock block,Map<String,String> properties){BlockState state=block.getDefaultState();for(var entry:properties.entrySet()){var property=block.getStateManager().getProperty(entry.getKey());if(property==null)throw new AssertionError(block.definition.id+" missing property "+entry.getKey());state=BloodborneBlocks.set(state,property,entry.getValue());}return state;}
 private static void face(PlayerEntity player,Direction direction,BlockPos origin){player.refreshPositionAndAngles(origin.getX()+.5,origin.getY(),origin.getZ()+.5,direction.asRotation(),0);}
 private static ActionResult useItem(PlayerEntity player,ItemStack stack,BlockPos clicked,Direction side){player.setStackInHand(Hand.MAIN_HAND,stack);BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(clicked),side,clicked,false);return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));}
 private static Direction supportSide(BlockPos guestRoot,BlockState guestState,BlockPos carrier){Set<BlockPos> occupied=GeometryRuntime.state(guestState).parsedCells.keySet().stream().map(guestRoot::add).collect(java.util.stream.Collectors.toSet());for(Direction side:Direction.values()){BlockPos support=carrier.offset(side.getOpposite());if(!occupied.contains(support))return side;}throw new AssertionError("no support side outside guest footprint");}
 private static Set<BlockPos> helperOffsets(BlockState state){Set<BlockPos> result=new LinkedHashSet<>(GeometryRuntime.state(state).parsedCells.keySet());result.remove(BlockPos.ORIGIN);return result;}
 private static void clear(ServerWorld world,BlockPos root,BlockState state){for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())world.removeBlock(root.add(offset),false);}
 private record Fixture(BlockPos guestRoot,BlockState guestState,Identifier guestOwner,BlockPos carrier,BlockState carrierState) {}
 private record SharedFixture(ArchitectureBlock block,BlockPos firstRoot,BlockPos secondRoot,BlockPos sharedCell) {}
}
