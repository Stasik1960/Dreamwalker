package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornerp.object.*;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public final class BbToolGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v12_tool")
    public void fiveModesCycleWithoutBreakingTargetAndRemainInItemNbt(TestContext t){
        var p=player(t);var stack=BbToolService.ITEM.getDefaultStack();p.setStackInHand(Hand.MAIN_HAND,stack);
        t.assertTrue(BbToolItem.mode(stack)==BbToolItem.Mode.UP,"fresh tool starts with up");
        for(int n=1;n<=5;n++){BbToolItem.cycle(p);t.assertTrue(BbToolItem.mode(stack).ordinal()==n%5,"exact five-mode right-click cycle "+n);}
        stack.getOrCreateNbt().putInt("BBMode",BbToolItem.Mode.CLEAR.ordinal());var restored=ItemStack.fromNbt(stack.writeNbt(new NbtCompound()));
        t.assertTrue(BbToolItem.mode(restored)==BbToolItem.Mode.CLEAR,"saved item retains selected mode");p.discard();t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v12_tool")
    public void oneBlockVerticalMovesKeepOwnersAndAllowNativeBlockOverlap(TestContext t){
        var w=t.getWorld();var p=player(t);var root=t.getAbsolutePos(new BlockPos(3,3,3));p.setPosition(root.getX()+5,root.getY(),root.getZ()+5);p.setStackInHand(Hand.MAIN_HAND,BbToolService.ITEM.getDefaultStack());
        var state=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true).with(PrototypeLadderBlock.DIAGONAL,true);
        w.setBlockState(root,state,Block.NOTIFY_ALL);VerticalMount.placed(w,root,state);var owner=VerticalMount.owner(w,root);t.assertTrue(owner!=null,"normal ladder has a native owner");
        var target=new ToolTarget(ToolTarget.Kind.ARCHITECTURE,w.getRegistryKey().getValue().toString(),owner.instanceId(),root.asLong(),owner.registryId());
        w.setBlockState(root.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        t.assertTrue(BbToolService.move(p,target,1),"height tool permits layering through native stone");
        t.assertTrue(VerticalMount.offset(w,root)==1&&VerticalMount.owner(w,root).instanceId().equals(owner.instanceId())&&w.getBlockState(root.up()).isOf(Blocks.STONE),"one-block move preserves UUID and real stone carrier");
        t.assertTrue(w.getBlockState(root).getCollisionShape(w,root).isEmpty(),"angular collider stays empty after moving");
        t.assertTrue(BbToolService.move(p,target,-1)&&VerticalMount.offset(w,root)==0,"down reverses exactly one block");
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)w.getChunk(root.add(x*16,0,z*16));
        var rp=ObjectRegistry.TYPES.get("cage_obj_1").create(w);rp.refreshPositionAndAngles(root.getX()+10.5,120,root.getZ()+.5,0,0);w.spawnEntity(rp);p.setPosition(rp.getX()+3,rp.getY(),rp.getZ()+3);var before=rp.getPos();var rpTarget=ToolTarget.entity(rp);
        t.assertTrue(BbToolService.move(p,rpTarget,1)&&rp.verticalOffset()==1&&rp.getPos().equals(before.add(0,1,0)),"RP position and metadata move exactly once");
        var saved=new NbtCompound();rp.writeNbt(saved);var clone=ObjectRegistry.TYPES.get("cage_obj_1").create(w);clone.readNbt(saved);
        t.assertTrue(clone.getPos().equals(rp.getPos())&&clone.verticalOffset()==1,"reloading saved Pos never applies offset twice");
        t.assertTrue(BbToolService.move(p,rpTarget,-1)&&rp.getPos().equals(before),"RP down returns exact original position");rp.removeObject();clone.discard();p.discard();t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=3000,batchId="v12_tool")
    public void radiusAltChangesOnlySameCatalogueIdAndNoneRestoresSingleSelection(TestContext t){
        var w=t.getWorld();var p=player(t);var root=t.getAbsolutePos(new BlockPos(2,3,2));var near=root.east(3);var far=root.east(16);p.setPosition(root.getX()+.5,root.getY(),root.getZ()+.5);var stack=BbToolService.ITEM.getDefaultStack();stack.getOrCreateNbt().putInt("BBMode",BbToolItem.Mode.SPECIAL.ordinal());p.setStackInHand(Hand.MAIN_HAND,stack);
        var state=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true);for(var pos:new BlockPos[]{root,near,far}){w.setBlockState(pos,state,Block.NOTIFY_ALL);VerticalMount.placed(w,pos,state);}
        var owner=VerticalMount.owner(w,root);var target=new ToolTarget(ToolTarget.Kind.ARCHITECTURE,w.getRegistryKey().getValue().toString(),owner.instanceId(),root.asLong(),owner.registryId());
        var source=p.getCommandSource().withLevel(2);t.assertTrue(w.getServer().getCommandManager().executeWithPrefix(source,"bb select 6")==1,"real command selects radius");BbToolService.apply(p,target);
        t.assertTrue(BbAlternateService.status(w.getServer()).running(),"radius apply starts bounded asynchronous job");
        t.runAtTick(1,()->awaitRadiusJob(t,p,()->{
            t.assertTrue(w.getBlockState(root).get(PrototypeLadderBlock.PROFILE)==PrototypeLadderBlock.Profile.ALT&&w.getBlockState(near).get(PrototypeLadderBlock.PROFILE)==PrototypeLadderBlock.Profile.ALT&&w.getBlockState(far).get(PrototypeLadderBlock.PROFILE)==PrototypeLadderBlock.Profile.BASE,"only same-ID owners in player-centred sphere change");
            t.assertTrue(w.getServer().getCommandManager().executeWithPrefix(source,"bb none")==1,"real command resets radius");BbToolService.apply(p,target);
            t.assertTrue(w.getBlockState(root).get(PrototypeLadderBlock.PROFILE)==PrototypeLadderBlock.Profile.BASE&&w.getBlockState(near).get(PrototypeLadderBlock.PROFILE)==PrototypeLadderBlock.Profile.ALT,"none affects selected object only");
            t.assertTrue(VerticalMount.owner(w,root).instanceId().equals(owner.instanceId()),"ALT change retains instance identity");p.discard();t.complete();
        },1,2500));
    }
    private static void awaitRadiusJob(TestContext t,ServerPlayerEntity player,Runnable done,long tick,int remaining){
        if(!BbAlternateService.status(t.getWorld().getServer()).running()){done.run();return;}
        if(remaining==0){BbAlternateService.cancel(t.getWorld().getServer());player.discard();t.assertTrue(false,"bounded radius job must complete within test timeout");return;}
        t.runAtTick(tick+1,()->awaitRadiusJob(t,player,done,tick+1,remaining-1));
    }
    static ServerPlayerEntity player(TestContext t){var w=t.getWorld();var p=new ServerPlayerEntity(w.getServer(),w,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BBToolTest"));var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND){@Override public void send(net.minecraft.network.packet.Packet<?> packet){}@Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callback){}};p.networkHandler=new net.minecraft.server.network.ServerPlayNetworkHandler(w.getServer(),connection,p);p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;return p;}
}
