package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;

/** Opt-in bounded timing of navigation/settling, separate from steady FPS.
 * The same QA jar runs against the frozen old mod and its replacement. */
final class GalleryStreamingProbe {
    private static final Gson JSON=new Gson();
    private static final int LIMIT=131072;
    private static final class Phase {
        final String name;final long started=System.nanoTime();
        final ArrayList<Long> frames=new ArrayList<>(),ticks=new ArrayList<>();
        long previousFrame;volatile long ended;volatile JsonObject serverMetrics=new JsonObject();
        int chunkLoads,chunkUnloads;final Set<Long> deliveredChunks=new HashSet<>();
        final int loadedAtStart;int loadedAtEnd=-1;
        Phase(String name){this.name=name;var world=MinecraftClient.getInstance().world;loadedAtStart=world==null?-1:world.getChunkManager().getLoadedChunkCount();}
        synchronized void delivered(long position){chunkLoads++;deliveredChunks.add(position);}
        synchronized void unloaded(){chunkUnloads++;}
        synchronized void frame(){long now=System.nanoTime();if(previousFrame!=0&&frames.size()<LIMIT)frames.add(now-previousFrame);previousFrame=now;}
        synchronized void tick(long elapsed,JsonObject metrics){if(ticks.size()<LIMIT)ticks.add(elapsed);serverMetrics=metrics;}
        synchronized JsonObject json(){JsonObject out=new JsonObject();out.addProperty("phase",name);out.addProperty("startedNanoTime",started);out.addProperty("endedNanoTime",ended);out.addProperty("durationMs",((ended==0?System.nanoTime():ended)-started)/1e6);out.add("presentation",stats(frames));out.add("serverWorldTicks",stats(ticks));out.add("serverMetrics",serverMetrics);var chunks=new JsonObject();chunks.addProperty("method","Fabric ClientChunkEvents.CHUNK_LOAD/CHUNK_UNLOAD; accepted native client chunk deliveries, separate from resident chunk count");chunks.addProperty("loadCallbacks",chunkLoads);chunks.addProperty("uniqueDeliveredChunks",deliveredChunks.size());chunks.addProperty("unloadCallbacks",chunkUnloads);chunks.addProperty("loadedAtStart",loadedAtStart);chunks.addProperty("loadedAtEnd",loadedAtEnd);out.add("chunkDelivery",chunks);return out;}
    }
    private static boolean enabled;private static volatile Phase active;
    private static final List<Phase> phases=new ArrayList<>();
    private static Phase tickPhase;private static long tickStarted;
    static void enable(){
        enabled=true;
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->{Phase phase=active;if(phase!=null)phase.delivered(chunk.getPos().toLong());});
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{Phase phase=active;if(phase!=null)phase.unloaded();});
        ServerTickEvents.START_WORLD_TICK.register(world->{if(world.getRegistryKey()!=net.minecraft.world.World.OVERWORLD)return;tickPhase=active;tickStarted=System.nanoTime();});
        ServerTickEvents.END_WORLD_TICK.register(world->{
            if(world.getRegistryKey()!=net.minecraft.world.World.OVERWORLD||tickPhase==null)return;
            long elapsed=System.nanoTime()-tickStarted;Phase target=tickPhase;
            // Tally outside the timed interval; no worker touches world state.
            target.tick(elapsed,metrics(world));tickPhase=null;
        });
    }
    static void begin(String name){if(!enabled)return;end();Phase phase=new Phase(name);phases.add(phase);active=phase;}
    static void end(){Phase phase=active;active=null;if(phase!=null){phase.ended=System.nanoTime();var world=MinecraftClient.getInstance().world;phase.loadedAtEnd=world==null?-1:world.getChunkManager().getLoadedChunkCount();}}
    static void presentation(){Phase phase=active;if(phase!=null)phase.frame();}
    static JsonArray report(){JsonArray out=new JsonArray();for(Phase phase:phases)out.add(phase.json());return out;}
    private static JsonObject metrics(ServerWorld world){
        JsonObject out=new JsonObject();
        try{out.add("loadWork",JSON.toJsonTree(dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.class.getMethod("streamingMetrics",ServerWorld.class).invoke(null,world)));}
        catch(NoSuchMethodException old){out.addProperty("loadWork","OLD_UNBOUNDED_QUEUE");}
        catch(ReflectiveOperationException failure){out.addProperty("loadWorkError",failure.toString());}
        out.add("snapshots",JSON.toJsonTree(dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots.metrics(world)));return out;
    }
    private static JsonObject stats(List<Long> samples){
        JsonObject out=new JsonObject();out.addProperty("count",samples.size());out.addProperty("sampleLimit",LIMIT);
        if(samples.isEmpty())return out;ArrayList<Long> sorted=new ArrayList<>(samples);Collections.sort(sorted);long total=0;int over50=0,over100=0,over1000=0;for(long n:samples){total+=n;if(n>50_000_000)over50++;if(n>100_000_000)over100++;if(n>1_000_000_000)over1000++;}
        out.addProperty("averageMs",total/1e6/samples.size());out.addProperty("p50Ms",sorted.get((int)Math.ceil(sorted.size()*.50)-1)/1e6);out.addProperty("p95Ms",sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1e6);out.addProperty("p99Ms",sorted.get((int)Math.ceil(sorted.size()*.99)-1)/1e6);out.addProperty("maxMs",sorted.get(sorted.size()-1)/1e6);
        out.addProperty("over50ms",over50);out.addProperty("over100ms",over100);out.addProperty("over1000ms",over1000);out.add("rawNs",JSON.toJsonTree(samples));return out;
    }
    private GalleryStreamingProbe(){}
}
