package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.*;
import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Compound server-request and nearest-instance regression checks; no GUI/input claims. */
public final class ToolAtomicFormGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_atomic")
    public void malformedContentsDoesNotCommitHeightAndInvalidHeightDoesNotCommitManualPolicy(TestContext c){
        BlockPos at=at(c);var p=player(c,"AtomicForm");var cage=object(c,"cage_obj_1",at);near(p,cage);select(p,cage);
        UUID identity=cage.getUuid();var invalid=q(1);var values=new JsonObject();values.addProperty("offset",.125);values.add("dogs",new JsonObject());invalid.add("values",values);request(p,invalid);
        c.assertTrue(!success(p)&&cage.verticalOffset()==0&&cage.dogsVisible()&&cage.getUuid().equals(identity),"malformed boolean is parsed before any height/contents mutation");
        // A valid contents-only edit is allowed even while a player is inside the unchanged cage body.
        p.setPosition(cage.getX(),cage.getY()+1,cage.getZ());var valid=q(2);values=new JsonObject();values.addProperty("offset",0);values.addProperty("dogs",false);valid.add("values",values);request(p,valid);
        c.assertTrue(success(p)&&!cage.dogsVisible()&&cage.verticalOffset()==0,"unchanged geometry does not run a new obstruction check for contents only");
        var door=object(c,"door_1",at.add(20,0,0));near(p,door);select(p,door);var ref=MechanismLinks.TargetRef.rp(door);
        c.assertTrue(ObjectPolicies.canSetManual(p,ref),"manual-policy preflight fixture is a supported loaded door");
        var rejected=q(3);values=new JsonObject();values.addProperty("manual",true);values.addProperty("offset",10000);rejected.add("values",values);request(p,rejected);
        c.assertTrue(!success(p)&&door.verticalOffset()==0&&!ObjectPolicies.leversOnly(c.getWorld().getServer(),ref),"rejected final geometry leaves manual policy and original pose untouched");
        cage.removeByBuilder();door.removeByBuilder();clear(p);p.discard();c.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_atomic")
    public void mountingAndHeightAreOneTransactionAndRejectedFinalPosePreservesWholePayload(TestContext c){
        BlockPos root=at(c);var world=c.getWorld();var block=CompositeArchitecture.kindBlock(GlazingTypes.WINDOW01);
        var payload=new NbtCompound();payload.putBoolean("GlazingMounted",true);payload.putDouble("GlazingSurfaceY",0);payload.putString("MountFace","north");
        c.assertTrue(CompositeRuntime.place(world,root,block.getDefaultState(),UUID.randomUUID(),null,payload).outcome()==TransactionCore.Outcome.COMMITTED,"mounted glass fixture placed");
        var own=(CompositeBlockEntity)world.getBlockEntity(root);var owner=own.resident();var p=player(c,"AtomicMount");p.setPosition(root.getX()+.5,root.getY(),root.getZ()-3);
        NbtCompound before=own.createNbt();var desired=world.getBlockState(root).with(ThinWindowRootBlock.MOUNT,ThinWindowRootBlock.Mount.FLOOR);
        c.assertTrue(!VerticalMount.setGeometry(world,root,desired,10000,p),"invalid final mounting/height pose is refused");
        c.assertTrue(world.getBlockState(root).get(ThinWindowRootBlock.MOUNT)==ThinWindowRootBlock.Mount.VERTICAL&&before.equals(((CompositeBlockEntity)world.getBlockEntity(root)).createNbt()),"no plane, offset, source shift or authored anchor was partially committed");
        c.assertTrue(VerticalMount.setGeometry(world,root,desired,.125,p),"valid final plane+height commits atomically");
        own=(CompositeBlockEntity)world.getBlockEntity(root);
        c.assertTrue(world.getBlockState(root).get(ThinWindowRootBlock.MOUNT)==ThinWindowRootBlock.Mount.FLOOR&&own.verticalOffset()==.125&&owner.equals(own.resident())&&own.payload().getDouble("GlazingSurfaceY")==0,"final values retain UUID and authored anchor");
        CompositeRuntime.remove(world,owner,null,false);clear(p);p.discard();c.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_rp_living")
    public void quickRpHeightAndHistoryUndoRefuseNewLivingObstructionsWithoutChangingInstance(TestContext c){
        var cage=object(c,"cage_obj_1",at(c));cage.setDogsVisible(false);
        var p=player(c,"RPSafeHeight");near(p,cage);select(p,cage);var blocker=player(c,"RPLivingBlocker");
        UUID identity=cage.getUuid();var original=cage.activePhysicalBoxes();
        c.assertTrue(!original.isEmpty(),"RP cage has actual physical geometry for the obstruction fixture");
        Box top=original.stream().max(Comparator.comparingDouble(box->box.maxY)).orElseThrow();
        blocker.setPosition((top.minX+top.maxX)/2,top.maxY+.0625,(top.minZ+top.maxZ)/2);
        c.assertTrue(dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(c.getWorld(),original,cage,false)==null&&
            dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(c.getWorld(),original.stream().map(box->box.offset(0,.125,0)).toList(),cage,false)!=null,
            "living player is clear of the current cage and intersects only the proposed upward geometry");
        var mode=command("mode",1);mode.addProperty("action","UP");request(p,mode);
        c.runAtTick(2,()->{
            request(p,targetRequest(p,"click",2));
            c.assertTrue(!success(p)&&cage.verticalOffset()==0&&!cage.dogsVisible()&&cage.getUuid().equals(identity)&&history(p).size()==0,
                "actual quick UP refuses living obstruction and preserves height, contents, UUID and history");
        });
        c.runAtTick(3,()->{
            blocker.setPosition(cage.getX()+20,cage.getY(),cage.getZ()+20);request(p,targetRequest(p,"click",3));
            c.assertTrue(success(p)&&cage.verticalOffset()==.125&&!cage.dogsVisible()&&cage.getUuid().equals(identity)&&history(p).size()==1,
                "same quick request commits and records history once the living obstacle moves away");
        });
        c.runAtTick(4,()->{
            Box bottom=original.stream().min(Comparator.comparingDouble(box->box.minY)).orElseThrow();
            blocker.setPosition((bottom.minX+bottom.maxX)/2,bottom.minY-blocker.getHeight()+.0625,(bottom.minZ+bottom.maxZ)/2);
            c.assertTrue(dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(c.getWorld(),cage.activePhysicalBoxes(),cage,false)==null&&
                dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(c.getWorld(),original,cage,false)!=null,
                "new living obstacle is clear of the raised cage and intersects only the pose that undo would restore");
            request(p,targetRequest(p,"undo",4));
            c.assertTrue(!success(p)&&cage.verticalOffset()==.125&&!cage.dogsVisible()&&cage.getUuid().equals(identity)&&history(p).size()==1,
                "actual history undo refuses living obstruction without changing height, contents, UUID or consuming history");
        });
        c.runAtTick(5,()->{
            blocker.setPosition(cage.getX()+20,cage.getY(),cage.getZ()+20);request(p,targetRequest(p,"undo",5));
            c.assertTrue(success(p)&&cage.verticalOffset()==0&&!cage.dogsVisible()&&cage.getUuid().equals(identity)&&history(p).size()==0,
                "the retained history entry restores the original pose after the living obstacle moves away");
            cage.removeByBuilder();clear(p);clear(blocker);p.discard();blocker.discard();c.complete();
        });
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=60,batchId="tool_fix_nearest")
    public void authoredCubeOrderingCannotSelectTheFartherOverlappingInstance(TestContext c){
        var at=at(c);var a=object(c,"cage_obj_1",at);a.setDogsVisible(false);var p=player(c,"NearestSelection");
        Vec3d eye=null;double first=0,minimum=0;var boxes=a.selectionBoxes();double front=boxes.stream().mapToDouble(box->box.minZ).min().orElseThrow()-1;
        // Use actual authored cube bounds to obtain a ray where the first declared cube is behind
        // another cube in the same instance. This asserts the real fixture exposes the old defect.
        for(Box sample:boxes){Vec3d start=new Vec3d((sample.minX+sample.maxX)/2,(sample.minY+sample.maxY)/2,front),end=start.add(0,0,6);double declared=Double.POSITIVE_INFINITY,nearest=Double.POSITIVE_INFINITY;
            for(Box box:boxes){var hit=box.expand(.075).raycast(start,end);if(hit.isPresent()){double distance=hit.get().distanceTo(start);if(!Double.isFinite(declared))declared=distance;nearest=Math.min(nearest,distance);}}
            if(Double.isFinite(declared)&&declared-nearest>.2){eye=start;first=declared;minimum=nearest;break;}
        }
        c.assertTrue(eye!=null,"authored multi-cube cage has an out-of-order nearer hit");
        var b=object(c,"npc_window",at.add(20,0,0));Box single=b.selectionBoxes().get(0);double desired=eye.z+(first+minimum)/2;
        b.setPosition(b.getX()+eye.x-(single.minX+single.maxX)/2,b.getY()+eye.y-(single.minY+single.maxY)/2,b.getZ()+desired-(single.minZ-.075));
        p.setPosition(eye.x,eye.y-p.getStandingEyeHeight(),eye.z);p.setYaw(0);p.setPitch(0);
        var candidates=BuilderServer.candidates(p);
        c.assertTrue(candidates.size()>=2&&candidates.get(0).instance().equals(a.getUuid())&&candidates.stream().filter(t->t.instance().equals(a.getUuid())).count()==1,"nearest part wins despite authored cube order; one instance occurs once in the cycling list");
        a.removeByBuilder();b.removeByBuilder();clear(p);p.discard();c.complete();
    }

    private static BlockPos at(TestContext c){BlockPos p=c.getAbsolutePos(new BlockPos(3,3,3));var at=new BlockPos(p.getX(),150,p.getZ());for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.getWorld().getChunk(at.add(x*16,0,z*16));return at;}
    private static RpObjectEntity object(TestContext c,String id,BlockPos at){Entity e=Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp",id)).create(c.getWorld());if(!(e instanceof RpObjectEntity object))throw new AssertionError(id);object.refreshPositionAndAngles(at.getX()+.5,at.getY(),at.getZ()+.5,90,0);c.getWorld().spawnEntity(object);return object;}
    private static ServerPlayerEntity player(TestContext c,String name){var p=new ServerPlayerEntity(c.getWorld().getServer(),c.getWorld(),new GameProfile(UUID.randomUUID(),name));var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND){@Override public void send(net.minecraft.network.packet.Packet<?> packet){}@Override public void send(net.minecraft.network.packet.Packet<?> packet,net.minecraft.network.PacketCallbacks callbacks){}};p.networkHandler=new ServerPlayNetworkHandler(c.getWorld().getServer(),connection,p);p.changeGameMode(GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;p.getAbilities().flying=true;p.setNoGravity(true);p.setStackInHand(Hand.MAIN_HAND,CompositeArchitecture.BUILDER.getDefaultStack());c.getWorld().spawnEntity(p);return p;}
    private static void near(ServerPlayerEntity p,RpObjectEntity object){Box b=object.selectionBoxes().get(0);p.setPosition((b.minX+b.maxX)/2,(b.minY+b.maxY)/2-p.getStandingEyeHeight(),b.minZ-2);}
    private static void select(ServerPlayerEntity p,RpObjectEntity object){Object s=invoke("session",new Class<?>[]{ServerPlayerEntity.class},p);set(s,"target",MechanismLinks.TargetRef.rp(object));set(s,"pinned",true);}
    private static JsonObject q(long id){return command("object",id);}
    private static JsonObject command(String op,long id){var q=new JsonObject();q.addProperty("op",op);q.addProperty("requestId",id);return q;}
    private static JsonObject targetRequest(ServerPlayerEntity p,String op,long id){var q=command(op,id);var target=(MechanismLinks.TargetRef)get(invoke("session",new Class<?>[]{ServerPlayerEntity.class},p),"target");q.addProperty("expectedKey",target.key());q.addProperty("expectedVersion",invoke("version",new Class<?>[]{ServerPlayerEntity.class,MechanismLinks.TargetRef.class},p,target).toString());return q;}
    private static BuilderHistory history(ServerPlayerEntity p){return (BuilderHistory)get(invoke("session",new Class<?>[]{ServerPlayerEntity.class},p),"history");}
    private static void request(ServerPlayerEntity p,JsonObject q){invoke("request",new Class<?>[]{ServerPlayerEntity.class,JsonObject.class},p,q);}
    private static boolean success(ServerPlayerEntity p){return (boolean)get(invoke("session",new Class<?>[]{ServerPlayerEntity.class},p),"success");}
    private static void clear(ServerPlayerEntity p){invoke("clearPlayer",new Class<?>[]{UUID.class},p.getUuid());}
    private static Object get(Object o,String n){try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void set(Object o,String n,Object v){try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Object invoke(String n,Class<?>[] types,Object...args){try{var m=BuilderServer.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(null,args);}catch(InvocationTargetException e){throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
