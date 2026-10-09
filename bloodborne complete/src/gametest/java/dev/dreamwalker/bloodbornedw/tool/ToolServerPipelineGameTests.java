package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Exercises the actual server request/session/history path with fake connections.
 * It does not simulate a mouse, packet transport, screen, or client render. */
public final class ToolServerPipelineGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_pipeline")
    public void selectTwoUpReverseDownRotateAwayOpenUndoAndRejectForeignEdit(TestContext c){
        var world=c.getWorld();BlockPos root=root(c);var block=CompositeArchitecture.kindBlock("prototype_roof");
        world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
        var placement=CompositeRuntime.place(world,root,block.getDefaultState(),UUID.randomUUID(),null);
        c.assertTrue(placement.outcome()==TransactionCore.Outcome.COMMITTED,"place actual composite roof on required foundation: "+placement.outcome()+" / "+placement.reason());
        var owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();
        var p=player(world,"ToolPipelineA");var other=player(world,"ToolPipelineB");
        aimAtRoot(p,root);other.setPosition(root.getX()+4,root.getY(),root.getZ()-4);
        request(p,q("click",1));var ref=selected(p);
        c.assertTrue(ref!=null&&ref.instance().equals(owner.instanceId()),"actual candidate ray and click select exact UUID");
        request(other,q("refresh",1));c.assertTrue(selected(other)==null,"second player's session has no shared selection");
        c.runAtTick(2,()->{
            var mode=q("mode",2);mode.addProperty("action","UP");request(p,mode);
            request(p,targetRequest(p,"click",3));
            c.assertTrue(VerticalMount.offset(world,root)==.125&&selected(p).instance().equals(owner.instanceId()),"first UP commits one 1/8 step with fixed UUID");
        });
        c.runAtTick(3,()->{
            request(p,targetRequest(p,"click",4));
            c.assertTrue(VerticalMount.offset(world,root)==.25,"second UP commits second step");
        });
        c.runAtTick(4,()->{
            var reverse=targetRequest(p,"click",5);reverse.addProperty("reverse",true);request(p,reverse);
            c.assertTrue(VerticalMount.offset(world,root)==.125,"Shift inverse on UP applies exactly one DOWN step");
        });
        c.runAtTick(5,()->{
            p.setYaw(180);p.setPitch(0);
            var mode=q("mode",6);mode.addProperty("action","ROTATE");request(p,mode);
            request(p,targetRequest(p,"click",7));
            c.assertTrue(world.getBlockState(root).get(CompositeRootBlock.ROTATION)==1&&selected(p).instance().equals(owner.instanceId()),"pinned rotation works with aim away and keeps UUID");
            BuilderServer.open(p);c.assertTrue(selected(p).instance().equals(owner.instanceId()),"opening actual server menu path preserves pinned target");
            var undo=targetRequest(p,"undo",8);request(p,undo);
            c.assertTrue(world.getBlockState(root).get(CompositeRootBlock.ROTATION)==0&&VerticalMount.offset(world,root)==.125,"undo restores only last geometry operation");
            int count=history(p).size();request(p,undo);
            c.assertTrue(history(p).size()==count&&VerticalMount.offset(world,root)==.125,"duplicate request ID does not undo again");
        });
        c.runAtTick(6,()->{
            var stale=targetRequest(p,"object",9);stale.addProperty("field","offset");stale.addProperty("value",.5);
            c.assertTrue(VerticalMount.setOffset(world,root,.3125,other),"independent second player's geometry mutation committed");
            request(p,stale);
            c.assertTrue(VerticalMount.offset(world,root)==.3125&&!success(p),"stale version rejects form without overwriting other player");
            request(p,targetRequest(p,"undo",10));
            c.assertTrue(VerticalMount.offset(world,root)==.3125&&!success(p)&&message(p).contains("Чужая работа"),"history refuses undo after another player changed geometry");
            c.assertTrue(selected(p).instance().equals(owner.instanceId())&&selected(other)==null,"conflict does not replace either selection");
            CompositeRuntime.remove(world,owner,null,false);world.removeBlock(root.down(),false);clear(p);clear(other);p.discard();other.discard();c.complete();
        });
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_history_limit")
    public void sessionHistoryKeepsExactlyLastTwentyGeometryChanges(TestContext c){
        var world=c.getWorld();BlockPos root=root(c);var block=CompositeArchitecture.kindBlock("prototype_roof");
        world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
        var placement=CompositeRuntime.place(world,root,block.getDefaultState(),UUID.randomUUID(),null);
        c.assertTrue(placement.outcome()==TransactionCore.Outcome.COMMITTED,"place history fixture on required foundation: "+placement.outcome()+" / "+placement.reason());
        var owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();var p=player(world,"ToolHistoryLimit");aimAtRoot(p,root);request(p,q("click",1));var target=selected(p);var history=history(p);
        for(int i=1;i<=25;i++){
            var before=BuilderHistory.capture(p,target);
            c.assertTrue(VerticalMount.setOffset(world,root,i*.0625,p),"valid geometry edit "+i);history.record(p,target,before,"height "+i);
        }
        c.assertTrue(history.size()==20,"history cap is exactly 20 entries after 25 edits");
        for(int i=0;i<20;i++)c.assertTrue(history.undo(p).startsWith("Отменено:"),"bounded entry can be undone in reverse order: "+i);
        c.assertTrue(history.size()==0&&VerticalMount.offset(world,root)==5*.0625,"oldest five changes were discarded, newest twenty restored exactly");
        c.assertTrue(history.undo(p).contains("Нет поддерживаемого"),"empty history never repeats a mutation");
        CompositeRuntime.remove(world,owner,null,false);world.removeBlock(root.down(),false);clear(p);p.discard();c.complete();
    }

    private static BlockPos root(TestContext c){BlockPos at=c.getAbsolutePos(new BlockPos(3,3,3));BlockPos root=new BlockPos(at.getX(),150,at.getZ());for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.getWorld().getChunk(root.add(x*16,0,z*16));return root;}
    private static ServerPlayerEntity player(ServerWorld world,String name){
        var p=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),name));
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND){
            @Override public void send(net.minecraft.network.packet.Packet<?> packet){}
            @Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks){}
        };
        p.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,p);p.changeGameMode(GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;p.getAbilities().flying=true;p.setNoGravity(true);p.setStackInHand(Hand.MAIN_HAND,CompositeArchitecture.BUILDER.getDefaultStack());world.spawnEntity(p);return p;
    }
    private static void aimAtRoot(ServerPlayerEntity p,BlockPos root){p.setPosition(root.getX()+.5,root.getY()+.5-p.getStandingEyeHeight(),root.getZ()-4);p.setYaw(0);p.setPitch(0);}
    private static JsonObject q(String op,long id){var q=new JsonObject();q.addProperty("op",op);q.addProperty("requestId",id);return q;}
    private static JsonObject targetRequest(ServerPlayerEntity p,String op,long id){var q=q(op,id);var target=selected(p);q.addProperty("expectedKey",target.key());q.addProperty("expectedVersion",invoke("version",new Class<?>[]{ServerPlayerEntity.class,MechanismLinks.TargetRef.class},p,target).toString());return q;}
    private static void request(ServerPlayerEntity p,JsonObject q){invoke("request",new Class<?>[]{ServerPlayerEntity.class,JsonObject.class},p,q);}
    private static void clear(ServerPlayerEntity p){invoke("clearPlayer",new Class<?>[]{UUID.class},p.getUuid());}
    private static Object session(ServerPlayerEntity p){return invoke("session",new Class<?>[]{ServerPlayerEntity.class},p);}
    private static MechanismLinks.TargetRef selected(ServerPlayerEntity p){return (MechanismLinks.TargetRef)field(session(p),"target");}
    private static BuilderHistory history(ServerPlayerEntity p){return (BuilderHistory)field(session(p),"history");}
    private static boolean success(ServerPlayerEntity p){return (boolean)field(session(p),"success");}
    private static String message(ServerPlayerEntity p){return (String)field(session(p),"message");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object invoke(String name,Class<?>[] types,Object...args){try{Method m=BuilderServer.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;if(e.getCause() instanceof Error r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
