package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Headless lifecycle proof for bounded helper bindings carried by a real root cell. */
public final class SharedPhysicalGameTests implements FabricGameTest {
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
 private static ArchitectureBlock carrier(){return BloodborneBlocks.BLOCKS.values().stream().filter(block->GeometryRuntime.state(block.getDefaultState()).parsedCells.keySet().equals(Set.of(BlockPos.ORIGIN))).findFirst().orElseThrow();}
 private static Set<BlockPos> helperOffsets(BlockState state){Set<BlockPos> result=new LinkedHashSet<>(GeometryRuntime.state(state).parsedCells.keySet());result.remove(BlockPos.ORIGIN);return result;}
 private static void clear(ServerWorld world,BlockPos root,BlockState state){for(BlockPos offset:GeometryRuntime.state(state).parsedCells.keySet())world.removeBlock(root.add(offset),false);}
 private record Fixture(BlockPos guestRoot,BlockState guestState,Identifier guestOwner,BlockPos carrier,BlockState carrierState) {}
 private record SharedFixture(ArchitectureBlock block,BlockPos firstRoot,BlockPos secondRoot,BlockPos sharedCell) {}
}
