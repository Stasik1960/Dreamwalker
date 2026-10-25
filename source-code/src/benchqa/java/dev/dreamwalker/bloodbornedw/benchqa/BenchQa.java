package dev.dreamwalker.bloodbornedw.benchqa;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class BenchQa implements ModInitializer {
    public static final BlockPos STAND=new BlockPos(4096,220,4096),CLIENT=new BlockPos(4112,220,4096);
    private final JsonArray checks=new JsonArray();private final JsonObject report=new JsonObject();private int ticks;private boolean armed;private Path out;
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks.add(name);}
    @Override public void onInitialize(){String path=System.getProperty("dw.bench.qa.output");if(path==null)return;out=Path.of(path);ServerWorldEvents.LOAD.register((server,world)->{if(world.getRegistryKey()==World.OVERWORLD){world.setSpawnPos(STAND.up(3),0);world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);}});ServerLifecycleEvents.SERVER_STARTED.register(s->armed=true);ServerTickEvents.END_SERVER_TICK.register(s->{if(armed&&++ticks>=20){armed=false;run(s);}});}
    private ServerPlayerEntity actor(MinecraftServer server,ServerWorld world){var p=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"BenchProbe")){@Override public void sendMessage(Text t,boolean a){}@Override public boolean hasPermissionLevel(int l){return true;}};new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),p);p.changeGameMode(GameMode.CREATIVE);p.setPosition(STAND.getX(),STAND.getY(),STAND.getZ()-3);return p;}
    private void floor(ServerWorld world,BlockPos p){world.getChunk(p);for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)world.setBlockState(p.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
    private BenchSeating.Target place(ServerWorld world,BlockPos root,String kind,int rotation,boolean shifted){
        var block=CompositeArchitecture.kindBlock(kind);var state=block.getDefaultState().with(CompositeRootBlock.ROTATION,rotation);var tag=ImportedTypeAliases.markPayload(null);
        if(shifted){tag.putDouble("MountY",.25);var v=new net.minecraft.nbt.NbtList();for(double n:new double[]{.125,.25,-.25})v.add(net.minecraft.nbt.NbtDouble.of(n));tag.put("SourceShift",v);}
        var r=CompositeRuntime.place(world,root,state,UUID.randomUUID(),null,tag);check(r.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"place bench "+kind+" yaw "+rotation);var target=BenchSeating.targets(world,root).stream().filter(t->t.root().equals(root)).findFirst().orElseThrow();check(target.seats().size()==3,"exactly three authored seats");return target;
    }
    private List<BenchSeatEntity> seats(ServerWorld w,BlockPos p){return w.getEntitiesByClass(BenchSeatEntity.class,new Box(p).expand(8),e->e.rootPos().equals(p));}
    private void remove(ServerWorld w,BlockPos root){var owner=CompositeRuntime.contributions(w,root).stream().filter(c->CompositeData.pos(c.owner().root()).equals(root)).findFirst().orElseThrow().owner();var r=CompositeRuntime.remove(w,owner,null,false);check(r.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"remove bench through ordinary transaction");}
    private void run(MinecraftServer server){
        try{Files.createDirectories(out);var w=server.getOverworld();floor(w,STAND);floor(w,CLIENT);var players=List.of(actor(server,w),actor(server,w),actor(server,w),actor(server,w));
            for(String kind:List.of("source_hold_bench","source_hold_bench_1","source_hold_bench_rotate"))for(int rotation=0;rotation<8;rotation++){
                var target=place(w,STAND,kind,rotation,false);var before=((CompositeBlockEntity)w.getBlockEntity(STAND)).createNbt();var click=target.seats().get(1).contact();
                for(int i=0;i<3;i++){
                    var p=players.get(i);p.setPosition(STAND.getX(),STAND.getY(),STAND.getZ()-3);check(BenchSeating.sit(w,target,p,click).isAccepted(),"seat free player "+i);var seat=(BenchSeatEntity)p.getVehicle();check(seat!=null,"real passenger entity created");check(i!=0||seat.index()==1,"closest free middle seat selected");check(java.lang.Math.abs(p.getY()-(seat.getY()-.75))<1e-5,"hips meet plank without double height offset");check(!seat.shouldSave()&&!seat.isCollidable()&&!seat.isPushable(),"seat is invisible temporary non-collider");
                }
                check(BenchSeating.sit(w,target,players.get(3),click)==ActionResult.FAIL&&seats(w,STAND).size()==3,"full bench rejects fourth rider without extra entity");check(before.equals(((CompositeBlockEntity)w.getBlockEntity(STAND)).createNbt()),"seating preserves all bench data");
                for(var p:players){var vehicle=p.getVehicle();if(vehicle!=null){p.stopRiding();check(w.isSpaceEmpty(p),"dismount reaches collision-free space");vehicle.tick();check(vehicle.isRemoved(),"vacated seat removed immediately");}}
                remove(w,STAND);check(seats(w,STAND).isEmpty(),"no idle seats");
            }
            var moved=place(w,STAND,"source_hold_bench",1,true);check(java.lang.Math.abs(moved.seats().get(0).contact().y-(STAND.getY()+1.5))<1e-5,"mount and source shift applied once");
            var p=players.get(0);var contact=moved.seats().get(1).contact();p.setPosition(contact.x,contact.y-.5,contact.z-2);var hit=new BlockHitResult(contact,Direction.UP,STAND,false);
            check(UseBlockCallback.EVENT.invoker().interact(p,w,Hand.MAIN_HAND,hit).isAccepted()&&p.getVehicle() instanceof BenchSeatEntity,"normal right-click callback seats player");var entity=p.getVehicle();remove(w,STAND);entity.tick();check(entity.isRemoved()&&!p.hasVehicle(),"bench removal dismounts rider and discards seat");
            // Shared static form uses the same authored contacts without restoring old owners.
            var pose=StaticArchitectureCatalog.poses().stream().filter(v->v.block().spec.id.getPath().equals("source_hold_bench")).findFirst().orElseThrow();int recipe=-1;
            for(int i=0;i<StaticArchitectureCatalog.recipes().size();i++)for(var f:StaticArchitectureCatalog.recipes().get(i).fragments())if(f.pose()==pose&&f.origin().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN))recipe=i;
            check(recipe>=0,"existing shared bench root recipe found");w.setBlockState(STAND,StaticArchitectureCatalog.state(recipe),Block.NOTIFY_ALL);var staticTarget=BenchSeating.targets(w,STAND).stream().filter(t->t.identity().equals("static/"+pose.key())).findFirst().orElseThrow();check(staticTarget.seats().size()==3,"optimized static bench exposes three seats");check(BenchSeating.sit(w,staticTarget,p,staticTarget.seats().get(1).contact()).isAccepted(),"optimized static bench can be occupied");entity=p.getVehicle();p.stopRiding();entity.tick();w.setBlockState(STAND,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            var clientTarget=place(w,CLIENT,"source_hold_bench",0,false);report.addProperty("clientRoot",CLIENT.asLong());report.addProperty("status","PASS");
        }catch(Throwable error){error.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",error.toString());}
        report.add("checks",checks);try{Files.writeString(out.resolve("server-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}if(Boolean.getBoolean("dw.bench.qa.stop"))server.stop(false);
    }
}
