package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class PlacementPhysicsGameTests implements FabricGameTest {
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=240,batchId="placement_physics_v9")
    public void touchingNewVolumeRejectsOverlapAndOnlyInitialInstanceMayRetainSourceIntersection(TestContext test){
        var world=test.getWorld();BlockPos first=test.getAbsolutePos(new BlockPos(5,4,5)),second=first.east();
        var block=CompositeArchitecture.kindBlock("prototype_roof");var state=block.getDefaultState();
        var player=test.createMockSurvivalPlayer();player.refreshPositionAndAngles(first.getX()+.5,first.getY()+2,first.getZ()-8,0,0);
        for(BlockPos p:BlockPos.iterate(first.add(-5,-1,-5),first.add(7,8,7)))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        world.setBlockState(first.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(second.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        try{
            UUID a=UUID.randomUUID();test.assertTrue(CompositeRuntime.place(world,first,state,a,player).outcome()==Outcome.COMMITTED,"first ordinary cube places");
            UUID b=UUID.randomUUID();test.assertTrue(CompositeRuntime.place(world,second,state,b,player).outcome()==Outcome.COMMITTED,"face touching is allowed even while visual selection footprints overlap");
            var owner=((CompositeBlockEntity)world.getBlockEntity(second)).resident();CompositeRuntime.remove(world,owner,player,false);
            CompositeRuntime.remove(world,((CompositeBlockEntity)world.getBlockEntity(first)).resident(),player,false);
            NbtCompound originalShift=new NbtCompound();NbtList shift=new NbtList();shift.add(NbtDouble.of(.5));shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(0));originalShift.put("SourceShift",shift);
            UUID shiftedSource=UUID.randomUUID();test.assertTrue(SourceConversionScope.initialInstances(Set.of(shiftedSource),()->CompositeRuntime.place(world,first,state,shiftedSource,null,originalShift)).outcome()==Outcome.COMMITTED,"specific original source pose retained");
            NbtCompound shifted=new NbtCompound();shifted.putInt("OpaqueTypedNote",123);
            NbtCompound before=CompositeLedger.get(world).writeNbt(new NbtCompound());
            var ordinary=CompositeRuntime.place(world,second,state,UUID.randomUUID(),player,shifted);
            test.assertTrue(ordinary.outcome()==Outcome.COMMITTED,"90003 protruding physical overlap permits any incoming block while its actual root remains occupied: "+ordinary.reason());
            test.assertTrue(!before.equals(CompositeLedger.get(world).writeNbt(new NbtCompound())),"permitted roof overlap publishes the new UUID exactly once");
            CompositeRuntime.remove(world,((CompositeBlockEntity)world.getBlockEntity(second)).resident(),player,false);UUID migrated=UUID.randomUUID();var converted=SourceConversionScope.initialInstances(Set.of(migrated),()->CompositeRuntime.place(world,second,state,migrated,null,shifted));
            test.assertTrue(converted.outcome()==Outcome.COMMITTED&&!SourceConversionScope.initialInstance(migrated),"only this explicit initial source instance receives a scoped exception");
            owner=((CompositeBlockEntity)world.getBlockEntity(second)).resident();ItemStack picked=CompositeRuntime.pick(world,owner);
            test.assertTrue(picked.getSubNbt("CompositePayload")==null&&((CompositeBlockEntity)world.getBlockEntity(second)).payload().getInt("OpaqueTypedNote")==123,"installed typed compatibility data remains exact; new item contains no instance data or conversion privilege");
            test.assertTrue(!world.isSpaceEmpty(new Box(first.getX()+1.1,first.getY()+.1,first.getZ()+.2,first.getX()+1.3,first.getY()+.8,first.getZ()+.8)),"retained original intersection remains physically solid");
            test.assertTrue(CompositeRuntime.transition(world,owner,world.getBlockState(second).with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT),player).outcome()==Outcome.COMMITTED,"decorative profile change does not invalidate existing unchanged source physics");
            CompositeRuntime.remove(world,owner,player,false);player.setStackInHand(Hand.MAIN_HAND,picked);
            var used=picked.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(second.down()).add(0,.5,0),Direction.UP,second.down(),false)));
            test.assertTrue(used.isAccepted()&&picked.isEmpty(),"ordinary block reinstall needs no conversion privilege for90003 protruding overlap");
            test.complete();
        }finally{
            for(BlockPos p:java.util.List.of(first,second))if(world.getBlockEntity(p) instanceof CompositeBlockEntity root&&root.resident()!=null)CompositeRuntime.remove(world,root.resident(),null,false);
            player.discard();
        }
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=80,batchId="placement_physics_v9")
    public void physicalContactEpsilonUsesPositiveVolumeOnEveryAxis(TestContext test){
        Box base=new Box(0,0,0,1,1,1);
        test.assertTrue(!PlacementPhysics.overlaps(base,new Box(1,0,0,2,1,1)),"exact contact allowed");
        test.assertTrue(!PlacementPhysics.overlaps(base,new Box(1-PlacementPhysics.EPS/2,0,0,2,1,1)),"floating-point contact tolerance allowed");
        test.assertTrue(PlacementPhysics.overlaps(base,new Box(1-PlacementPhysics.EPS*2,0,0,2,1,1)),"positive volume above tolerance rejected");test.complete();
    }
}
