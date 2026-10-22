package dev.dreamwalker.bloodbornedw.streamingqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import jdk.jfr.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Fixed native dedicated-server route, opt-in QA JAR only. Production is not
 * instrumented. Tick work and start intervals are distinct measurements. */
public final class DedicatedRoute implements ModInitializer {
    private final Gson json=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject report=new JsonObject();
    private final JsonArray phases=new JsonArray();
    private final WireClient bot=new WireClient();
    private final double[][] targets={{272.5,100,-902.5},{650.5,110,-149.5},{980.5,100,95.5}};
    private Path out;
    private BufferedWriter ticks;
    private Recording recording;
    private long tickStarted,lastStart,startNanos,phaseNanos,startDeliveries,startUnloads,startOwnerPackets,startOwnerBytes;
    private int age,stageTicks,point,settled;
    private String stage="BOOT";
    private boolean ending;
    @Override public void onInitialize(){
        out=Path.of(System.getProperty("dw.qa.output")).toAbsolutePath();
        try{Files.createDirectories(out);ticks=Files.newBufferedWriter(out.resolve("ticks.csv"));ticks.write("utc,phase,workNanos,startIntervalNanos,deliveries\n");}catch(IOException e){throw new UncheckedIOException(e);}
        report.addProperty("status","STARTING");report.addProperty("kind","native-dedicated-real-loopback-receipts-no-renderer");report.add("phases",phases);
        report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("heapMax",Runtime.getRuntime().maxMemory());report.addProperty("viewDistance",8);report.addProperty("simulationDistance",6);
        ServerLifecycleEvents.SERVER_STARTED.register(server->{bot.start(server.getServerPort());save();});
        ServerTickEvents.START_SERVER_TICK.register(server->{tickStarted=System.nanoTime();});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            long end=System.nanoTime();try{ticks.write(Instant.now()+","+stage+","+(end-tickStarted)+","+(lastStart==0?0:tickStarted-lastStart)+","+bot.deliveries.get()+"\n");lastStart=tickStarted;tick(server);}catch(Throwable e){fail(server,e);}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{try{ticks.close();}catch(Exception e){e.printStackTrace();}});
    }
    private void tick(MinecraftServer server)throws Exception {
        if(ending)return;
        if(++age>6000)throw new IllegalStateException("QA tick deadline at "+stage);
        if(bot.failure!=null)throw new IllegalStateException(bot.failure);
        stageTicks++;
        ServerWorld world=server.getOverworld();ServerPlayerEntity player=server.getPlayerManager().getPlayer(WireClient.NAME);
        if(stage.equals("BOOT")){
            if(player==null||!bot.joined)return;
            player.changeGameMode(GameMode.SPECTATOR);player.requestTeleport(targets[0][0],targets[0][1],targets[0][2]);
            change("INITIAL_SETTLE");return;
        }
        if(stage.equals("INITIAL_SETTLE")){
            if(!ready(world,targets[0])){settled=0;return;}
            if(++settled<100)return;
            System.gc();change("INITIAL_GC_SETTLE");return;
        }
        if(stage.equals("INITIAL_GC_SETTLE")){
            if(stageTicks<40)return;
            snapshot(world,"beforeRoute");recording=new Recording(Configuration.getConfiguration("profile"));recording.setMaxSize(128L*1024*1024);recording.start();
            startNanos=System.nanoTime();point=1;beginPhase("MOVE_TOWER");return;
        }
        if(stage.startsWith("MOVE_")){
            if(player==null)throw new IllegalStateException("Route player absent");
            double[] target=targets[point];double dx=target[0]-player.getX(),dy=target[1]-player.getY(),dz=target[2]-player.getZ();double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(distance<.05){change(point==1?"SETTLE_TOWER":"SETTLE_BRICK");settled=0;return;}
            // Normal movement packets; wait for the currently addressed near
            // square before crossing another chunk. No teleport route shortcut.
            if(!ready(world,new double[]{player.getX(),player.getY(),player.getZ()},2))return;
            double step=Math.min(1.5,distance);bot.move(player.getX()+dx/distance*step,player.getY()+dy/distance*step,player.getZ()+dz/distance*step);return;
        }
        if(stage.startsWith("SETTLE_")){
            if(!ready(world,targets[point])){settled=0;return;}
            if(++settled<100)return;
            endPhase();snapshot(world,point==1?"tower":"brick");
            if(point==1){point=2;beginPhase("MOVE_BRICK");return;}
            report.addProperty("routeNanos",System.nanoTime()-startNanos);report.addProperty("routeEndUtc",Instant.now().toString());
            bot.close();change("UNLOAD");return;
        }
        if(stage.equals("UNLOAD")){
            if(stageTicks<440)return;
            snapshot(world,"afterUnloadBeforeGc");System.gc();change("UNLOAD_GC_SETTLE");return;
        }
        if(stage.equals("UNLOAD_GC_SETTLE")){
            if(stageTicks<40)return;
            snapshot(world,"afterUnloadGc");recording.stop();recording.dump(out.resolve("server.jfr"));recording.close();
            server.save(false,true,true);report.addProperty("status","PASS_ROUTE_NORMAL_SAVE");report.addProperty("totalDeliveries",bot.deliveries.get());report.addProperty("totalUnloads",bot.unloads.get());report.addProperty("remainingWireChunksAtDisconnect",bot.loaded.size());save();ending=true;server.stop(false);
        }
    }
    private boolean ready(ServerWorld world,double[] target){return ready(world,target,6);}
    private boolean ready(ServerWorld world,double[] target,int radius){
        int x=((int)Math.floor(target[0]))>>4,z=((int)Math.floor(target[2]))>>4;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(world.getChunkManager().getWorldChunk(x+dx,z+dz)==null||!bot.loaded.contains(ChunkPos.toLong(x+dx,z+dz)))return false;
        return true;
    }
    private void beginPhase(String next){change(next);phaseNanos=System.nanoTime();startDeliveries=bot.deliveries.get();startUnloads=bot.unloads.get();startOwnerPackets=bot.ownerPackets.get();startOwnerBytes=bot.ownerBytes.get();report.addProperty("phaseStartUtc",Instant.now().toString());save();}
    private void endPhase(){JsonObject phase=new JsonObject();phase.addProperty("point",point);phase.addProperty("durationNanos",System.nanoTime()-phaseNanos);phase.addProperty("deliveries",bot.deliveries.get()-startDeliveries);phase.addProperty("unloads",bot.unloads.get()-startUnloads);phase.addProperty("ownerPackets",bot.ownerPackets.get()-startOwnerPackets);phase.addProperty("ownerBytes",bot.ownerBytes.get()-startOwnerBytes);phase.addProperty("requiredEndpointChunks",169);phase.addProperty("wireResidentChunks",bot.loaded.size());phase.addProperty("endUtc",Instant.now().toString());phases.add(phase);save();}
    private void change(String next){stage=next;stageTicks=0;report.addProperty("phase",next);save();}
    private void snapshot(ServerWorld world,String key){
        JsonObject s=new JsonObject();s.addProperty("utc",Instant.now().toString());s.addProperty("heapUsed",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());s.addProperty("heapCommitted",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted());s.addProperty("wireChunks",bot.loaded.size());s.add("shards",json.toJsonTree(CompositeLedger.get(world).shardMetrics()));s.add("streaming",json.toJsonTree(CompositeRuntime.streamingMetrics(world)));s.add("snapshots",json.toJsonTree(CompositeShapeSnapshots.metrics(world)));report.add(key,s);save();
    }
    private void save(){try{Files.writeString(out.resolve("route.json"),json.toJson(report)+"\n");}catch(IOException e){throw new UncheckedIOException(e);}}
    private void fail(MinecraftServer server,Throwable e){if(ending)return;ending=true;e.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",e.toString());save();try{bot.close();if(recording!=null){recording.stop();recording.dump(out.resolve("partial.jfr"));recording.close();}}catch(Exception x){x.printStackTrace();}server.stop(false);}
}
