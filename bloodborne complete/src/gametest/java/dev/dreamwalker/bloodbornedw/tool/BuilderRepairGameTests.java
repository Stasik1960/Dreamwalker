package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornerp.lamp.LampEditor;
import dev.dreamwalker.bloodbornerp.object.*;
import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

/** Actual server request-handler/state checks; not TCP transport, mouse, GUI or visual proof. */
public final class BuilderRepairGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="builder_repair")
    public void exactUuidRequestsAreIdempotentRejectStaleFormsAndUndoSameInstance(TestContext c)throws Exception{
        var p=player(c);var object=object(c,"door_1",3);near(p,object);Object s=call("session",new Class<?>[]{ServerPlayerEntity.class},p);set(s,"target",MechanismLinks.TargetRef.rp(object));set(s,"pinned",true);
        String oldVersion=version(p,object);UUID id=object.getUuid();JsonObject first=offset(p,object,.125,oldVersion,1);request(p,first);
        c.assertTrue(object.verticalOffset()==.125&&object.getUuid().equals(id),"accepted form keeps exact UUID and changes real offset");
        var history=(BuilderUndoHistory<?,?>)get(s,"undo");c.assertTrue(history.size()==1,"one successful request recorded once");request(p,first);c.assertTrue(history.size()==1&&object.verticalOffset()==.125,"duplicate request ID changes nothing");
        request(p,offset(p,object,.25,oldVersion,2));c.assertTrue(object.verticalOffset()==.125&&!((Boolean)get(s,"accepted")),"stale expected version refuses overwrite");
        JsonObject wrong=offset(p,object,.25,version(p,object),3);wrong.addProperty("expectedTarget",UUID.randomUUID().toString());request(p,wrong);c.assertTrue(object.verticalOffset()==.125,"wrong UUID never targets replacement");
        JsonObject undo=base("undo",4);request(p,undo);c.assertTrue(object.verticalOffset()==0&&object.getUuid().equals(id)&&history.size()==0,"undo restores authored offset on same existing UUID");
        NbtCompound saved=object.writeNbt(new NbtCompound());object.readNbt(saved);c.assertTrue(object.getUuid().equals(id)&&object.verticalOffset()==0,"typed entity save/read retains builder result and identity");
        var missing=new MechanismLinks.TargetRef(MechanismLinks.Kind.RP,object.getWorld().getRegistryKey().getValue().toString(),UUID.randomUUID(),object.getBlockPos().asLong(),object.assetId());set(s,"target",missing);BuilderServer.open(p);c.assertTrue(((MechanismLinks.TargetRef)get(s,"target")).instance().equals(missing.instance()),"opening menu never silently replaces unavailable pinned UUID");
        call("clearPlayer",new Class<?>[]{UUID.class},p.getUuid());Object fresh=call("session",new Class<?>[]{ServerPlayerEntity.class},p);c.assertTrue(fresh!=s&&((BuilderUndoHistory<?,?>)get(fresh,"undo")).size()==0,"disconnect cleanup starts a new empty session");cleanup(p,object);c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="builder_repair")
    public void completeDraftSaveIsAtomicAndVersionDetectsConcurrentComposition(TestContext c){
        var p=player(c);var lever=object(c,"lever_1",1);var target=object(c,"door_1",9);var draft=MechanismRules.draft("draft not persisted");
        c.assertTrue(MechanismRules.rule(p.getServer(),draft.id)==null,"opening draft creates no persistent rule");c.assertTrue(MechanismRules.draftAdd(p,draft,MechanismLinks.TargetRef.rp(lever),true),"source accepted into draft");
        c.assertTrue(!MechanismRules.saveDraft(p,draft,false)&&MechanismRules.rule(p.getServer(),draft.id)==null,"incomplete composition does not save");c.assertTrue(MechanismRules.draftAdd(p,draft,MechanismLinks.TargetRef.rp(target),false)&&MechanismRules.saveDraft(p,draft,false),"complete composition saved once");
        var saved=MechanismRules.rule(p.getServer(),draft.id);String before=MechanismRules.version(saved);var local=MechanismRules.copy(saved);local.name="local unsaved";c.assertTrue(saved.name.equals("draft not persisted")&&!MechanismRules.version(local).equals(before),"local edit cannot mutate saved rule");
        MechanismRules.configure(p,saved.id,"other editor",saved.condition,saved.effect);c.assertTrue(!MechanismRules.version(saved).equals(before)&&!target.isOpen(),"concurrent configuration changes version without firing rule");
        NbtCompound n=MechanismRules.get(p.getServer()).writeNbt(new NbtCompound());c.assertTrue(MechanismRules.fromNbt(n).writeNbt(new NbtCompound()).equals(n),"rule UUID/configuration/order roundtrip persisted");
        MechanismRules.delete(p,saved.id);cleanup(p,lever,target);c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="builder_repair")
    public void BusyRepeatsAreDroppedButOneSafeCloseSurvives(TestContext c){
        var p=player(c);var lever=object(c,"lever_1",1);var target=object(c,"door_1",9);var r=MechanismRules.draft("busy guard");MechanismRules.draftAdd(p,r,MechanismLinks.TargetRef.rp(lever),true);MechanismRules.draftAdd(p,r,MechanismLinks.TargetRef.rp(target),false);r.effect=MechanismRules.Effect.OPEN;c.assertTrue(MechanismRules.saveDraft(p,r,false),"busy rule saved");
        c.assertTrue(target.setOpen(true)&&target.animationBusy(),"actual RP open animation started");var ref=MechanismLinks.TargetRef.rp(target);MechanismRules.pulse(lever);MechanismRules.pulse(lever);c.assertTrue(!MechanismRules.pending(p.getServer(),ref),"repeat OPEN during animation is not queued");
        MechanismRules.configure(p,r.id,r.name,MechanismRules.Condition.ANY,MechanismRules.Effect.CLOSE);MechanismRules.pulse(lever);MechanismRules.pulse(lever);var n=MechanismRules.get(p.getServer()).writeNbt(new NbtCompound());long closes=n.getList("Queue",10).stream().map(e->(NbtCompound)e).filter(e->e.getCompound("Target").getUuid("Instance").equals(target.getUuid())).count();c.assertTrue(closes==1,"only one legitimate close waits through animation");
        for(int i=0;i<80;i++)target.tick();MechanismRules.retry(p.getServer(),256);c.assertTrue(!target.isOpen()&&!MechanismRules.pending(p.getServer(),ref),"pending close applies after animation when unoccupied");MechanismRules.delete(p,r.id);cleanup(p,lever,target);c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="builder_repair")
    public void renamedLampLineRemainsSelectedForNextSimpleLink(TestContext c)throws Exception{
        var p=player(c);var a=object(c,"hunterlamp",3);var b=object(c,"hunterlamp",9);var third=object(c,"hunterlamp",15);String oldName="rename-"+UUID.randomUUID().toString().substring(0,8),newName=oldName+"-new";
        p.getServer().getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(p.getGameProfile(),4,false));
        try{
            near(p,a);c.assertTrue(LampEditor.selectSource(p,a.getUuid()).success(),"actual lamp source registered and selected");Object s=call("session",new Class<?>[]{ServerPlayerEntity.class},p);set(s,"target",MechanismLinks.TargetRef.rp(a));set(s,"source",MechanismLinks.TargetRef.rp(a));set(s,"pinned",true);p.getMainHandStack().getOrCreateNbt().putString("BuilderLampLine",oldName);
            near(p,b);c.assertTrue(simpleLink(p,s,b),"first simple link creates selected line");String lineId=lineId(p,a,oldName);c.assertTrue(!lineId.isBlank(),"created line has stable identity");
            near(p,a);JsonObject q=base("lamp",1);q.addProperty("action","RENAME_LINE");q.addProperty("line",oldName);q.addProperty("name",newName);q.addProperty("both",true);q.addProperty("expectedTarget",a.getUuidAsString());q.addProperty("expectedLampVersion",(String)call("lampVersion",new Class<?>[]{ServerPlayerEntity.class,s.getClass()},p,s));request(p,q);
            c.assertTrue((Boolean)get(s,"accepted")&&p.getMainHandStack().getOrCreateNbt().getString("BuilderLampLine").equals(newName),"successful rename updates selected next-link line");
            near(p,third);c.assertTrue(simpleLink(p,s,third),"next simple link adds third lamp");JsonObject view=new com.google.gson.Gson().toJsonTree(LampEditor.view(p,a.getUuid())).getAsJsonObject();long connections=java.util.stream.StreamSupport.stream(view.getAsJsonArray("connections").spliterator(),false).filter(e->e.getAsJsonObject().get("lineName").getAsString().equals(newName)).count();
            c.assertTrue(connections==2&&lineId(p,a,newName).equals(lineId)&&lineId(p,a,oldName).isBlank(),"rename keeps same line UUID and both explicit links without duplicate old-name line");
        }finally{near(p,a);LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.DELETE_LINE,a.getUuid(),null,newName,"",true,true));LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.CANCEL,null,null,"","",true));p.getServer().getPlayerManager().removeFromOperators(p.getGameProfile());call("clearPlayer",new Class<?>[]{UUID.class},p.getUuid());cleanup(p,a,b,third);}
        c.complete();
    }
    private static boolean simpleLink(ServerPlayerEntity p,Object s,RpObjectEntity target)throws Exception{return (Boolean)call("simpleLink",new Class<?>[]{ServerPlayerEntity.class,s.getClass(),MechanismLinks.TargetRef.class,boolean.class},p,s,MechanismLinks.TargetRef.rp(target),false);}
    private static String lineId(ServerPlayerEntity p,RpObjectEntity source,String name){JsonObject view=new com.google.gson.Gson().toJsonTree(LampEditor.view(p,source.getUuid())).getAsJsonObject();for(var e:view.getAsJsonArray("lines")){var line=e.getAsJsonObject();if(line.get("name").getAsString().equals(name))return line.get("lineUuid").getAsString();}return "";}
    private static ServerPlayerEntity player(TestContext c){var p=c.createMockCreativeServerPlayerInWorld();p.getAbilities().creativeMode=true;p.getAbilities().allowModifyWorld=true;p.setStackInHand(Hand.MAIN_HAND,CompositeArchitecture.BUILDER.getDefaultStack());return p;}
    private static RpObjectEntity object(TestContext c,String asset,int x){var e=ObjectRegistry.TYPES.get(asset).create(c.getWorld());Vec3d pos=Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(x,64,3)));e.refreshPositionAndAngles(pos.x,pos.y,pos.z,0,0);c.getWorld().spawnEntity(e);return e;}
    private static void near(ServerPlayerEntity p,RpObjectEntity e){var b=e.selectionBoxes().get(0);p.setPosition(b.getCenter().x,b.getCenter().y-p.getStandingEyeHeight(),b.minZ-2);}
    private static String version(ServerPlayerEntity p,RpObjectEntity e)throws Exception{return (String)call("version",new Class<?>[]{ServerPlayerEntity.class,MechanismLinks.TargetRef.class},p,MechanismLinks.TargetRef.rp(e));}
    private static JsonObject base(String op,int sequence){JsonObject q=new JsonObject();q.addProperty("op",op);q.addProperty("requestId",UUID.randomUUID().toString());q.addProperty("sequence",sequence);return q;}
    private static JsonObject offset(ServerPlayerEntity p,RpObjectEntity e,double value,String version,int sequence){var q=base("object",sequence);q.addProperty("field","offset");q.addProperty("value",value);q.addProperty("expectedTarget",e.getUuidAsString());q.addProperty("expectedVersion",version);return q;}
    private static void request(ServerPlayerEntity p,JsonObject q)throws Exception{call("request",new Class<?>[]{ServerPlayerEntity.class,JsonObject.class},p,q);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{Method method=BuilderServer.class.getDeclaredMethod(name,types);method.setAccessible(true);try{return method.invoke(null,args);}catch(InvocationTargetException failure){throw new AssertionError(name+" failed",failure.getCause());}}
    private static Object get(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private static void cleanup(ServerPlayerEntity p,RpObjectEntity... objects){for(var e:objects)e.removeByBuilder();p.discard();}
}
