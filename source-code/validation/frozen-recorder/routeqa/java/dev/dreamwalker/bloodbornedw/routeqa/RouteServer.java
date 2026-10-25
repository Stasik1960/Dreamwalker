package dev.dreamwalker.bloodbornedw.routeqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import jdk.jfr.*;
import net.fabricmc.api.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/** Real dedicated-server timing and read-only position ACK for the existing
 * native spectator route. These classes are never part of production. */
public final class RouteServer implements ModInitializer {
    public static final Identifier CONTROL=new Identifier("dw_route_qa:control"),ACK=new Identifier("dw_route_qa:ack");
    private final Gson json=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject report=new JsonObject();private final JsonArray phases=new JsonArray();
    private Path out;private BufferedWriter ticks;private Recording recording;
    private String phase="STARTUP";private long start,lastStart;private boolean playerGone,ending,failed;private int unloadTicks,tickRows;
    private UUID routePlayer;
    @Override public void onInitialize(){
        if(FabricLoader.getInstance().getEnvironmentType()!=EnvType.SERVER)return;
        String output=System.getProperty("dw.route.output");if(output==null)return;out=Path.of(output);
        try{Files.createDirectories(out);ticks=Files.newBufferedWriter(out.resolve("server-ticks.csv"));ticks.write("utc,phase,workNs,startIntervalNs\n");}catch(IOException e){throw new UncheckedIOException(e);}
        report.addProperty("status","STARTING");report.add("phases",phases);
        ServerPlayNetworking.registerGlobalReceiver(CONTROL,(server,player,handler,buffer,sender)->{
            String action=buffer.readString(64);
            if(action.equals("phase")){
                String next=buffer.readString(64);server.execute(()->{try{
                    if(recording==null){recording=new Recording(Configuration.getConfiguration("profile"));recording.setMaxSize(128L<<20);recording.start();}
                    routePlayer=player.getUuid();phase=next;JsonObject p=new JsonObject();p.addProperty("phase",phase);p.addProperty("utc",Instant.now().toString());phases.add(p);
                    snapshot(player.getServerWorld(),phase);report.addProperty("status","RUNNING");save();
                }catch(Exception e){fail(server,e);}});
            }else if(action.equals("position")){
                double x=buffer.readDouble(),y=buffer.readDouble(),z=buffer.readDouble();
                server.execute(()->{routePlayer=player.getUuid();PacketByteBuf answer=PacketByteBufs.create();answer.writeBoolean(player.isSpectator()&&player.squaredDistanceTo(x,y,z)<.0025);answer.writeDouble(player.getX());answer.writeDouble(player.getY());answer.writeDouble(player.getZ());ServerPlayNetworking.send(player,ACK,answer);});
            }else if(action.equals("finish"))server.execute(()->{phase="UNLOAD";playerGone=true;unloadTicks=0;snapshot(player.getServerWorld(),"endRoute");save();PacketByteBuf answer=PacketByteBufs.create();answer.writeBoolean(true);answer.writeDouble(player.getX());answer.writeDouble(player.getY());answer.writeDouble(player.getZ());ServerPlayNetworking.send(player,ACK,answer);});
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{if(!playerGone&&handler.player.getUuid().equals(routePlayer))fail(server,new IllegalStateException("Native client disconnected before acknowledged completion"));});
        ServerTickEvents.START_SERVER_TICK.register(server->start=System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server->{
            try{long now=System.nanoTime();ticks.write(Instant.now()+","+phase+","+(now-start)+","+(lastStart==0?0:start-lastStart)+"\n");lastStart=start;if(++tickRows%20==0)ticks.flush();
                if(playerGone&&!ending){if(++unloadTicks==440){snapshot(server.getOverworld(),"afterUnloadBeforeGc");System.gc();}
                    if(unloadTicks==480){ending=true;snapshot(server.getOverworld(),"afterUnloadGc");if(recording!=null){recording.stop();recording.dump(out.resolve("server.jfr"));recording.close();recording=null;}server.save(false,true,true);report.addProperty("status","PASS_SAVED");save();server.stop(false);}}
            }catch(Throwable e){fail(server,e);}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{
            try{ticks.close();}catch(Exception e){e.printStackTrace();}
            // End only this opt-in recorder VM after every server callback and
            // the actual game-server thread have completed their normal stop.
            Thread completion=new Thread(()->{
                try{
                    server.getThread().join(30000);
                    if(server.getThread().isAlive())return;
                    report.addProperty("normalServerThreadFinished",true);save();
                    System.exit(!failed&&report.has("status")&&report.get("status").getAsString().equals("PASS_SAVED")?0:1);
                }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            },"NativeRouteQaExit");
            completion.setDaemon(true);completion.start();
        });
    }
    private void snapshot(ServerWorld world,String key){JsonObject s=new JsonObject();s.addProperty("utc",Instant.now().toString());s.addProperty("heapUsed",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());s.add("streaming",json.toJsonTree(CompositeRuntime.streamingMetrics(world)));s.add("snapshots",json.toJsonTree(CompositeShapeSnapshots.metrics(world)));report.add(key,s);}
    private void save(){try{Files.writeString(out.resolve("server-route.json"),json.toJson(report)+"\n");}catch(IOException e){throw new UncheckedIOException(e);}}
    private void fail(MinecraftServer server,Throwable e){if(failed)return;failed=true;ending=true;e.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",e.toString());try{if(recording!=null){recording.stop();recording.dump(out.resolve("server-partial.jfr"));recording.close();recording=null;}}catch(Exception x){x.printStackTrace();}try{save();}finally{server.stop(false);}}
}
