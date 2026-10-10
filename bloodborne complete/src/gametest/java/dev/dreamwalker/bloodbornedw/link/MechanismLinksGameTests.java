package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Ordinary interactions, delayed RP procedures, shared state and stable owner identity. */
public final class MechanismLinksGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void twoLeversUseTheActualDoorAndKeepOriginalSeventyTickDelay(TestContext context){
        ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();RpObjectEntity a=object(context,"lever_1",1,2,1),b=object(context,"lever_2",3,2,1),door=object(context,"door_1",9,2,1),gate=object(context,"small_gate",13,2,1);
        context.assertTrue(a.addLink(door)&&a.addLink(gate)&&b.addLink(door),"multiple levers and targets are accepted by legacy public link foundation");
        player.setPosition(a.getX(),a.getY(),a.getZ()+1);context.assertTrue(a.interact(player,Hand.MAIN_HAND).isAccepted(),"ordinary empty hand starts original lever procedure");
        for(int i=0;i<69;i++)a.tick();context.assertTrue(!door.isOpen()&&!gate.isOpen()&&a.isOpen(),"69 ticks do not deliver or reset lever early");
        a.tick();context.assertTrue(door.isOpen()&&gate.isOpen()&&a.isOpen()&&a.animationBusy(),"70th tick delivers exactly once while the authored lever clip still runs");context.assertTrue(!a.interact(player,Hand.MAIN_HAND).isAccepted(),"same lever cannot repeat while its remaining real clip runs");int leverTicks=(int)Math.ceil(a.asset().clip("open").seconds()*20);for(int i=70;i<leverTicks;i++){a.tick();door.tick();gate.tick();}context.assertTrue(!a.isOpen()&&!a.animationBusy()&&door.isOpen(),"lever returns idle only after full authored clip without toggling target twice");
        context.assertTrue(b.interact(player,Hand.MAIN_HAND).isAccepted(),"second ordinary lever starts its own real70tick procedure");for(int i=0;i<70;i++){b.tick();a.tick();door.tick();gate.tick();}context.assertTrue(!door.isOpen()&&gate.isOpen(),"second lever closes shared actual door but does not affect unlinked gate");
        context.assertTrue(!door.activePhysicalBoxes().isEmpty(),"closing updates real RP collision rather than metadata only");
        cleanup(a,b,door,gate);player.discard();context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void unloadedTargetEffectAndTypedGraphSurviveReloadWithoutUuidReuse(TestContext context){
        ServerWorld world=context.getWorld();RpObjectEntity a=object(context,"lever_1",1,2,1),b=object(context,"lever_2",3,2,1),door=object(context,"door_1",9,2,1);a.addLink(door);b.addLink(door);
        UUID id=door.getUuid();NbtCompound saved=door.writeNbt(new NbtCompound());var ref=MechanismLinks.TargetRef.rp(door);door.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        MechanismLinks.pulse(a);context.assertTrue(MechanismLinks.pending(world.getServer(),ref),"unloaded target retains pending desired effect");
        NbtCompound savedRules=MechanismRules.get(world.getServer()).writeNbt(new NbtCompound());NbtCompound roundtrip=MechanismRules.fromNbt(savedRules).writeNbt(new NbtCompound());
        context.assertTrue(roundtrip.getList("Queue",10).stream().map(v->(NbtCompound)v).anyMatch(v->v.getCompound("Target").getUuid("Instance").equals(ref.instance())),"V10 ordered pending UUID and effect persist in rule codec");
        RpObjectEntity replacement=object(context,"door_1",9,2,1);context.assertTrue(!replacement.getUuid().equals(id)&&!replacement.isOpen(),"new target at same location has a different identity");
        MechanismLinks.retryPending(world.getServer(),256);context.assertTrue(!replacement.isOpen()&&MechanismLinks.pending(world.getServer(),ref),"position reuse cannot receive old target effect");
        RpObjectEntity restored=ObjectRegistry.TYPES.get("door_1").create(world);restored.readNbt(saved);context.assertTrue(world.spawnEntity(restored),"same saved target UUID restores");MechanismLinks.loaded(restored);
        context.assertTrue(restored.getUuid().equals(id)&&restored.isOpen()&&!MechanismLinks.pending(world.getServer(),ref),"one pending effect applies when exact old target loads");
        settle(restored);MechanismLinks.pulse(b);context.assertTrue(!restored.isOpen(),"another lever observes exact restored state after its full open clip");
        cleanup(a,b,replacement,restored);context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void architectureOwnerLinkOperatesAtomicDoorAndCannotAttachToRebuild(TestContext context){
        ServerWorld world=context.getWorld();RpObjectEntity lever=object(context,"lever_1",1,2,1);BlockPos root=context.getAbsolutePos(new BlockPos(9,2,9));world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
        var block=CompositeArchitecture.kindBlock("prototype_double_door");var state=block.getDefaultState();for(var cell:block.spec.footprint(state,0).keySet())world.getChunk(root.add(cell.x(),cell.y(),cell.z()));
        context.assertTrue(CompositeRuntime.place(world,root,state,UUID.randomUUID(),null).outcome()==TransactionCore.Outcome.COMMITTED,"door fixture installs through production transaction");
        var owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();var ref=CompositeMechanismBridge.reference(world,owner);context.assertTrue(MechanismLinks.link(lever,ref),"architecture binding uses full stable owner/root");
        MechanismLinks.pulse(lever);context.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN),"lever changes real architectural pose through atomic transition");
        context.assertTrue(CompositeRuntime.remove(world,owner,null,false).outcome()==TransactionCore.Outcome.COMMITTED,"old owner removal commits");
        context.assertTrue(CompositeRuntime.place(world,root,state,UUID.randomUUID(),null).outcome()==TransactionCore.Outcome.COMMITTED,"new door installs with new owner");
        MechanismLinks.pulse(lever);context.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN)&&MechanismLinks.targets(lever).isEmpty(),"old owner binding is retired and does not operate new door at reused root");
        CompositeRuntime.remove(world,((CompositeBlockEntity)world.getBlockEntity(root)).resident(),null,false);cleanup(lever);context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void survivalHoldingOrdinaryItemStillUsesLever(TestContext context){
        ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);RpObjectEntity lever=object(context,"lever_1",1,2,1),door=object(context,"door_1",9,2,1);lever.addLink(door);
        player.setStackInHand(Hand.MAIN_HAND,new ItemStack(net.minecraft.item.Items.STICK));player.setPosition(lever.getX(),lever.getY(),lever.getZ()+1);
        context.assertTrue(lever.interact(player,Hand.MAIN_HAND).isAccepted(),"ordinary item does not block lever use");for(int i=0;i<70;i++)lever.tick();context.assertTrue(door.isOpen(),"ordinary gameplay retains the original 70 tick delay");cleanup(lever,door);player.discard();context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void deletedLinkCannotReturnFromUnloadedLeversOldEntityNbt(TestContext context){
        ServerWorld world=context.getWorld();RpObjectEntity lever=object(context,"lever_1",1,2,1),door=object(context,"door_1",9,2,1);
        context.assertTrue(lever.addLink(door),"initial entity link imports");NbtCompound old=lever.writeNbt(new NbtCompound());var ref=MechanismLinks.TargetRef.rp(door);
        lever.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);context.assertTrue(MechanismLinks.unlink(lever,ref.key()),"persistent pair can be removed while source is unloaded");
        RpObjectEntity restored=ObjectRegistry.TYPES.get("lever_1").create(world);restored.readNbt(old);context.assertTrue(world.spawnEntity(restored),"old entity NBT restores the exact lever UUID");MechanismLinks.loaded(restored);
        context.assertTrue(restored.links().isEmpty()&&MechanismLinks.targets(restored).isEmpty(),"authoritative saved graph removes stale entity Links rather than reimporting them");MechanismLinks.pulse(restored);context.assertTrue(!door.isOpen(),"deleted pair cannot actuate the former target");cleanup(restored,door);context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void pulseOnlyGateDropsBusyRepeatsAndAcceptsNewIdleEventWithoutInventingOpen(TestContext context){
        ServerWorld world=context.getWorld();RpObjectEntity a=object(context,"lever_1",1,2,1),b=object(context,"lever_2",3,2,1),gate=object(context,"wood_gate",9,2,1);context.assertTrue(a.addLink(gate)&&b.addLink(gate),"pulse-only authored gate is an active link capability");
        MechanismLinks.pulse(a);context.assertTrue(gate.woodGatePulseActive()&&!gate.isOpen(),"first delayed command starts source clip without inventing open/passable state");MechanismLinks.pulse(b);var ref=MechanismLinks.TargetRef.rp(gate);context.assertTrue(gate.woodGatePulseActive()&&!MechanismLinks.pending(world.getServer(),ref),"second command during current clip is rejected, never accumulated for a surprise replay");
        NbtCompound saved=MechanismRules.get(world.getServer()).writeNbt(new NbtCompound());var decoded=MechanismRules.fromNbt(saved).writeNbt(new NbtCompound());context.assertTrue(decoded.getList("Queue",10).stream().map(v->(NbtCompound)v).filter(v->v.getCompound("Target").getUuid("Instance").equals(ref.instance())&&v.getString("Effect").equals("PULSE")).count()==0,"rejected busy repeat creates no persistent queued pulse");
        for(int i=0;i<32;i++)gate.tick();context.assertTrue(!gate.woodGatePulseActive(),"source authored32 ticks return gate to idle");MechanismLinks.retryPending(world.getServer(),256);context.assertTrue(!gate.woodGatePulseActive()&&!MechanismLinks.pending(world.getServer(),ref),"retry after clip cannot replay discarded busy command");MechanismLinks.pulse(b);context.assertTrue(gate.woodGatePulseActive()&&!MechanismLinks.pending(world.getServer(),ref)&&!gate.isOpen(),"a new distinct command after idle starts one new authored clip");for(int i=0;i<32;i++)gate.tick();context.assertTrue(!gate.woodGatePulseActive()&&!gate.isOpen(),"both real events finish without fabricated animation state");cleanup(a,b,gate);context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v9_mechanisms")
    public void blockedCloseStaysPendingUntilRealLivingOccupantLeaves(TestContext context){
        ServerWorld world=context.getWorld();RpObjectEntity lever=object(context,"lever_1",1,2,1),door=object(context,"door_1",9,2,1);context.assertTrue(door.setOpen(true)&&lever.addLink(door),"actual door starts open");settle(door);
        var occupant=net.minecraft.entity.EntityType.ARMOR_STAND.create(world);occupant.setPosition(door.getPos());context.assertTrue(world.spawnEntity(occupant),"real living occupant enters door");var ref=MechanismLinks.TargetRef.rp(door);MechanismLinks.pulse(lever);context.assertTrue(door.isOpen()&&MechanismLinks.pending(world.getServer(),ref),"real close rejection leaves actual state and preserves pending intent");
        MechanismLinks.retryPending(world.getServer(),256);context.assertTrue(door.isOpen(),"retry cannot crush living occupant");occupant.discard();MechanismLinks.retryPending(world.getServer(),256);context.assertTrue(!door.isOpen()&&!door.activePhysicalBoxes().isEmpty()&&!MechanismLinks.pending(world.getServer(),ref),"intent commits to actual collision state once occupant leaves");cleanup(lever,door);context.complete();
    }
    private static void settle(RpObjectEntity object){var clip=object.asset().clip(object.isOpen()?"open":"close");int limit=clip==null?2:(int)Math.ceil(clip.seconds()*20)+2;for(int tick=0;object.animationBusy()&&tick<limit;tick++)object.tick();if(object.animationBusy())throw new AssertionError("Authored clip did not finish: "+object.assetId());}
    private static RpObjectEntity object(TestContext context,String asset,int x,int y,int z){RpObjectEntity result=ObjectRegistry.TYPES.get(asset).create(context.getWorld());BlockPos pos=context.getAbsolutePos(new BlockPos(x,y,z));result.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);if(!context.getWorld().spawnEntity(result))throw new AssertionError("RP object spawn failed:"+asset);return result;}
    private static void cleanup(RpObjectEntity... entities){for(var entity:entities)entity.removeObject();}
}
