package dev.dreamwalker.flightqa;

import com.google.gson.*;
import dev.dreamwalker.qacommon.NativeReadiness;
import java.lang.management.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.BackupPromptScreen;
import dev.dreamwalker.flightqa.mixin.FlightBackupPromptAccessor;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/** Independent recorder. Only the initial placement is a server teleport. */
public final class FlightQa implements ClientModInitializer {
    private static FlightQa instance;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int LIMIT=200000;
    private final long[] intervals=new long[LIMIT], elapsed=new long[LIMIT];
    private final int[] ticks=new int[LIMIT];
    private int count, tick, segment; private long previous, flightStart, boot, stable, nextMemory;
    private boolean recording, done, placed, renderedInitial, closing, captureDone, backedUp; private int correctionCount; private long flightEnd,flightEndEpoch; private volatile Throwable serverError;
    private record ServerPosition(int tick,double x,double y,double z) {}
    private volatile ServerPosition serverPosition=new ServerPosition(-1,0,0,0);
    private Path output; private JsonObject report=new JsonObject(),plan; private JsonArray points, memory=new JsonArray(),positions=new JsonArray();
    private CompletableFuture<Void> job; private int state; // 0 boot, 1 prepare, 2 flight, 3 save, 4 camera, 5 wait for window
    private JsonArray cameras, stationary = new JsonArray(), stationaryMemory;
    private JsonObject destination;
    private boolean diagonal;
    private int camera;
    private int windowRetries;
    private long stationaryEpoch;
    private WaterSurfaceQa waterProbe;
    private CityFinishQa cityFinish;
    private double x,y,z; private long fixedTime, loadingAt; private double routeLength; private double[] lengths;
    @Override public void onInitializeClient(){
        instance=this;boot=System.nanoTime();
        try {
            byte[] bytes=Files.readAllBytes(Path.of(required("dw.flight.plan")));plan=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(plan.get("schema").getAsInt()==1,"plan schema");points=plan.getAsJsonArray("points");
            cameras=plan.getAsJsonArray("cameras");diagonal=cameras!=null;
            if(diagonal) {
                check(cameras.size()==4 && points.size()==2 && plan.get("laps").getAsInt()==1
                        && plan.get("ticksPerLap").getAsInt()>=800,"four cameras and one uninterrupted diagonal");
                report.add("stationary",stationary);
                report.addProperty("restartsInterruptedStages", true);
            } else {
                check(points.size()>=3,"route points");
                check(plan.get("laps").getAsInt()==2 && plan.get("ticksPerLap").getAsInt()>=2400,"two laps of at least 120 seconds each");
            }
            for(JsonElement e:points){JsonObject p=e.getAsJsonObject();for(String key:List.of("x","y","z"))check(Double.isFinite(p.get(key).getAsDouble()),"finite coordinates");}
            lengths=new double[points.size()-(diagonal?1:0)];
            for(int i=0;i<lengths.length;i++){JsonObject a=points.get(i).getAsJsonObject(),b=points.get((i+1)%points.size()).getAsJsonObject();double dx=b.get("x").getAsDouble()-a.get("x").getAsDouble(),dy=b.get("y").getAsDouble()-a.get("y").getAsDouble(),dz=b.get("z").getAsDouble()-a.get("z").getAsDouble();lengths[i]=Math.sqrt(dx*dx+dy*dy+dz*dz);check(lengths[i]>0,"zero length segment");routeLength+=lengths[i];}
            check(routeLength*20/plan.get("ticksPerLap").getAsInt()<=12,"route exceeds 12 blocks per second");
            fixedTime=plan.get("fixedTime").getAsLong();output=Path.of(required("dw.flight.output"));Files.createDirectories(output);check(!Files.exists(output.resolve("flight.json")),"output already exists");
            report.addProperty("schema",1);report.addProperty("status","STARTING");report.addProperty("startedUtc",Instant.now().toString());report.addProperty("planSha256",sha(bytes));report.add("plan",plan.deepCopy());
            report.addProperty("pid",ProcessHandle.current().pid());report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("vmArguments",ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
            report.addProperty("productionSha256",modSha("bloodborne_dw"));report.addProperty("qaSha256",modSha("dw_flight_qa"));
            check(report.get("productionSha256").getAsString().equals(required("dw.flight.expectedSha256")),"production SHA mismatch");
            report.addProperty("variantLabel",required("dw.flight.variant"));report.add("memorySamples",memory);report.add("positions",positions);
            if (Boolean.getBoolean("dw.flight.cityFinish")) cityFinish = new CityFinishQa(output);
            if (Boolean.getBoolean("dw.flight.waterProbe")) waterProbe = new WaterSurfaceQa(output);
            JsonArray mods=new JsonArray();FabricLoader.getInstance().getAllMods().stream().sorted(Comparator.comparing(m->m.getMetadata().getId())).forEach(m->{JsonObject a=new JsonObject();a.addProperty("id",m.getMetadata().getId());a.addProperty("version",m.getMetadata().getVersion().getFriendlyString());mods.add(a);});report.add("loadedMods",mods);
            ClientTickEvents.END_CLIENT_TICK.register(this::onTick);save();
        }catch(Throwable e){fail(e);}
    }
    public static void presentation(){
        FlightQa q=instance;if(q==null)return;if(q.state==1 && q.placed)q.renderedInitial=true;if(!q.recording)return;
        MinecraftClient c=MinecraftClient.getInstance();
        if(!visibleWorld(c)){q.interruptWindow(c);return;}
        long now=System.nanoTime();if(q.previous!=0){if(q.count>=LIMIT){q.fail(new IllegalStateException("frame buffer overflow"));return;}q.intervals[q.count]=now-q.previous;q.elapsed[q.count]=now-q.flightStart;q.ticks[q.count]=q.tick;q.count++;}q.previous=now;if(q.closing){q.flightEnd=now;q.flightEndEpoch=System.currentTimeMillis();q.recording=false;q.captureDone=true;}
    }
    private static boolean visibleWorld(MinecraftClient client) {
        return client.isWindowFocused() && client.currentScreen == null && client.getOverlay() == null && !client.isPaused();
    }

    private void interruptWindow(MinecraftClient client) {
        if (!diagonal) {
            fail(new IllegalStateException("focus or visible world lost during flight"));
            return;
        }
        recording = false;
        if (state == 2) {
            positions = new JsonArray();
            memory = new JsonArray();
            report.add("positions", positions);
            report.add("memorySamples", memory);
            closing = false;
            captureDone = false;
            serverPosition = new ServerPosition(-1, 0, 0, 0);
        }
        state = 5;
        report.addProperty("status", "WAITING_FOR_WINDOW");
        report.addProperty("windowRetries", ++windowRetries);
        report.addProperty("lastWindowInterruption", !client.isWindowFocused() ? "focus"
                : client.currentScreen != null ? client.currentScreen.getClass().getName() : "overlay or pause");
        try { save(); }
        catch (Exception failure) { fail(failure); }
    }
    public static void correction(){FlightQa q=instance;if(q!=null)MinecraftClient.getInstance().execute(()->{if(q.state==2&&!q.done){q.correctionCount++;q.fail(new IllegalStateException("server position correction during flight"));}});}
    private void onTick(MinecraftClient c){
        if(done)return;
        try{
            long now=System.nanoTime();check(now-boot<3_600_000_000_000L,"total timeout");if(serverError!=null)throw new IllegalStateException("server task",serverError);
            if(state==3){if(job.isDone()){job.join();report.addProperty("saved",true);report.addProperty("status","PASS");report.addProperty("finishedUtc",Instant.now().toString());done=true;save();c.scheduleStop();}return;}
            if (state == 0 && !backedUp && Boolean.getBoolean("dw.flight.backupDisposableCopy")
                    && c.currentScreen instanceof BackupPromptScreen screen
                    && Files.isRegularFile(output.getParent().resolve("disposable-flight-world.marker"))) {
                backedUp = true;
                ((FlightBackupPromptAccessor)(Object)screen).dw$backupCallback().proceed(true, false);
                return;
            }
            if(c.world==null||c.player==null||c.getServer()==null){check(now-boot<1_800_000_000_000L,"startup timeout");return;}
            if (cityFinish != null) { cityFinish.tick(c); return; }
            if (waterProbe != null) { waterProbe.tick(c); return; }
            if(state==0){
                if(!visibleWorld(c)){
                    if(diagonal && !report.get("status").getAsString().equals("WAITING_FOR_WINDOW")){
                        report.addProperty("status","WAITING_FOR_WINDOW");save();
                    }
                    return;
                }
                JsonObject options=options(c);report.add("actualOptions",options);report.addProperty("glRenderer",GL11.glGetString(GL11.GL_RENDERER));report.addProperty("glVendor",GL11.glGetString(GL11.GL_VENDOR));report.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));report.addProperty("focusedAtStart",true);for(var e:plan.getAsJsonObject("expectedOptions").entrySet())check(options.has(e.getKey())&&options.get(e.getKey()).equals(e.getValue()),"option mismatch "+e.getKey());
                c.options.hudHidden=true;c.options.pauseOnLostFocus=false;
                prepareView(c,now);
                return;
            }
            if(state==5){
                if(visibleWorld(c))prepareView(c,now);
                return;
            }
            if(!visibleWorld(c)){interruptWindow(c);return;}
            if(state==1){
                check(now-loadingAt<240_000_000_000L,"view readiness timeout");if(!placed)return;
                if(c.player.squaredDistanceTo(destination.get("x").getAsDouble(),destination.get("y").getAsDouble(),destination.get("z").getAsDouble())>.01)return;
                if(tick++%5!=0)return;boolean ready=renderedInitial&&full(c)&&c.worldRenderer.isTerrainRenderComplete()&&NativeReadiness.idle(NativeReadiness.sample(c.world));
                if(!ready){stable=0;return;}if(stable==0){stable=now;return;}if(now-stable<2_000_000_000L)return;
                if (diagonal && camera<cameras.size()) {
                    state=4;count=0;tick=0;previous=0;flightStart=now;nextMemory=now;
                    stationaryEpoch=System.currentTimeMillis();stationaryMemory=new JsonArray();recording=true;
                    report.addProperty("status","MEASURING_"+destination.get("name").getAsString());save();
                    return;
                }
                report.addProperty("initialTerrainComplete",true);report.addProperty("initialFullChunks",true);report.addProperty("initialRendered",renderedInitial);report.addProperty("initialStage","cold initial view; first lap streams, second lap revisits");report.add("initialNativeReadiness",NativeReadiness.sample(c.world));report.addProperty("initialStableSeconds",(now-stable)/1e9);
                memory(); // Initialize the same management collectors before recording, without adding a sample.
                count=0;tick=0;report.addProperty("flightStartEpochMillis",System.currentTimeMillis());report.addProperty("status","FLYING");save();state=2;flightStart=System.nanoTime();report.addProperty("flightStartNanoTime",flightStart);previous=flightStart;nextMemory=flightStart;recording=true;return;
            }
            if(state==4){
                if(now>=nextMemory){stationaryMemory.add(memorySample(now));nextMemory=now+1_000_000_000L;}
                if(now-flightStart<15_000_000_000L)return;
                recording=false;
                check(count>60&&full(c)&&c.worldRenderer.isTerrainRenderComplete(),"steady camera lost readiness");
                JsonObject measurement=new JsonObject();measurement.add("camera",destination);
                measurement.addProperty("startEpochMillis",stationaryEpoch);measurement.addProperty("endEpochMillis",System.currentTimeMillis());
                measurement.add("frames",frames());measurement.add("memorySamples",stationaryMemory);stationary.add(measurement);
                camera++;save();prepareView(c,now);return;
            }
            int duration=plan.get("ticksPerLap").getAsInt(),total=duration*plan.get("laps").getAsInt();
            if(tick>=total){if(!closing){closing=true;return;}if(!captureDone)return;report.addProperty("flightEndEpochMillis",flightEndEpoch);report.addProperty("flightEndNanoTime",flightEnd);report.addProperty("wallSeconds",(flightEnd-flightStart)/1e9);report.addProperty("correctionCount",correctionCount);report.add("frames",frames());check(count>=1000,"too few frames");check(serverPosition.tick()>=total-40,"no recent server position");state=3;job=new CompletableFuture<>();c.getServer().execute(()->{try{c.getServer().save(false,true,true);job.complete(null);}catch(Throwable e){job.completeExceptionally(e);}});report.addProperty("status","SAVING");save();return;}
            // Equal distance per tick; move through vanilla collision handling and fail on obstruction.
            double distance=(tick%duration)*routeLength/(diagonal?duration-1:duration);segment=0;
            while(segment<lengths.length-1 && distance>=lengths[segment]){distance-=lengths[segment];segment++;}
            double f=distance/lengths[segment];JsonObject a=points.get(segment).getAsJsonObject(),b=points.get((segment+1)%points.size()).getAsJsonObject();
            x=lerp(a,b,"x",f);y=lerp(a,b,"y",f);z=lerp(a,b,"z",f);
            c.player.setNoGravity(true);c.player.setVelocity(0,0,0);
            c.player.move(MovementType.SELF,new Vec3d(x-c.player.getX(),y-c.player.getY(),z-c.player.getZ()));
            check(c.player.squaredDistanceTo(x,y,z)<.0025,"flight route obstructed or player corrected");
            c.player.setYaw((float)(Math.toDegrees(Math.atan2(-(b.get("x").getAsDouble()-a.get("x").getAsDouble()),b.get("z").getAsDouble()-a.get("z").getAsDouble()))));c.player.setPitch(15);
            if(tick%20==0){int sampleTick=tick;double sx=x,sy=y,sz=z;c.getServer().execute(()->{try{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());check(p!=null,"server player missing");ServerPosition sample=new ServerPosition(sampleTick,p.getX(),p.getY(),p.getZ());serverPosition=sample;check((sample.x()-sx)*(sample.x()-sx)+(sample.y()-sy)*(sample.y()-sy)+(sample.z()-sz)*(sample.z()-sz)<100,"server movement diverged");}catch(Throwable e){serverError=e;}});
                ServerPosition sample=serverPosition;JsonObject pos=new JsonObject();pos.addProperty("tick",tick);pos.addProperty("elapsedSeconds",(now-flightStart)/1e9);pos.addProperty("segment",segment);pos.addProperty("x",x);pos.addProperty("y",y);pos.addProperty("z",z);pos.addProperty("serverSampleTick",sample.tick());pos.addProperty("serverX",sample.x());pos.addProperty("serverY",sample.y());pos.addProperty("serverZ",sample.z());positions.add(pos);
            }
            if(now>=nextMemory){memory.add(memorySample(now));nextMemory=now+1_000_000_000L;}tick++;
        }catch(Throwable e){fail(e);}
    }
    private void prepareView(MinecraftClient c,long now) {
        JsonObject next=diagonal&&camera<cameras.size()?cameras.get(camera).getAsJsonObject():points.get(0).getAsJsonObject();
        destination=next;state=1;loadingAt=now;placed=false;renderedInitial=false;stable=0;
        c.getServer().execute(()->{try{
            var server=c.getServer();var player=server.getPlayerManager().getPlayer(c.player.getUuid());
            check(player!=null,"server player missing");var world=server.getOverworld();world.setTimeOfDay(fixedTime);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
            world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);
            world.setWeather(0,0,false,false);player.stopRiding();player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().flying=true;player.sendAbilitiesUpdate();player.setNoGravity(true);
            player.teleport(world,next.get("x").getAsDouble(),next.get("y").getAsDouble(),next.get("z").getAsDouble(),
                    next.has("yaw")?next.get("yaw").getAsFloat():0,next.has("pitch")?next.get("pitch").getAsFloat():15);
            player.setVelocity(0,0,0);placed=true;
        }catch(Throwable e){serverError=e;}});
    }
    private JsonObject memorySample(long now) {
        JsonObject sample=memory();sample.addProperty("elapsedSeconds",(now-flightStart)/1e9);
        sample.addProperty("epochMillis",System.currentTimeMillis());sample.addProperty("nanoTime",now);
        return sample;
    }
    private JsonObject frames(){JsonObject out=new JsonObject();JsonArray raw=new JsonArray(),times=new JsonArray(),frameTicks=new JsonArray();long total=0;for(int i=0;i<count;i++){raw.add(intervals[i]);times.add(elapsed[i]);frameTicks.add(ticks[i]);total+=intervals[i];}out.add("rawIntervalsNs",raw);out.add("elapsedNs",times);out.add("ticks",frameTicks);out.addProperty("samples",count);out.addProperty("totalNs",total);out.addProperty("clock","RenderSystem.flipFrame HEAD; CPU presentation intervals");return out;}
    static boolean full(MinecraftClient c){int cx=c.player.getBlockX()>>4,cz=c.player.getBlockZ()>>4,d=c.options.getViewDistance().getValue();for(int x=cx-d-2;x<=cx+d+2;x++)for(int z=cz-d-2;z<=cz+d+2;z++)if(ThreadedAnvilChunkStorage.isWithinDistance(x,z,cx,cz,d)&&c.world.getChunkManager().getChunk(x,z,ChunkStatus.FULL,false)==null)return false;return true;}
    private static JsonObject options(MinecraftClient c){JsonObject o=new JsonObject();o.addProperty("renderDistance",c.options.getViewDistance().getValue());o.addProperty("simulationDistance",c.options.getSimulationDistance().getValue());o.addProperty("maxFps",c.options.getMaxFps().getValue());o.addProperty("vsync",c.options.getEnableVsync().getValue());o.addProperty("fov",c.options.getFov().getValue());o.addProperty("mipmapLevels",c.options.getMipmapLevels().getValue());o.addProperty("framebufferWidth",c.getWindow().getFramebufferWidth());o.addProperty("framebufferHeight",c.getWindow().getFramebufferHeight());o.addProperty("resourcePacks",c.options.resourcePacks.toString());o.addProperty("graphics",c.options.getGraphicsMode().getValue().toString());o.addProperty("gamma",c.options.getGamma().getValue());return o;}
    private static JsonObject memory(){JsonObject o=new JsonObject();var h=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();o.addProperty("heapUsedBytes",h.getUsed());o.addProperty("heapCommittedBytes",h.getCommitted());o.addProperty("heapMaxBytes",h.getMax());long n=0,t=0;for(var g:ManagementFactory.getGarbageCollectorMXBeans()){n+=Math.max(0,g.getCollectionCount());t+=Math.max(0,g.getCollectionTime());}o.addProperty("gcCount",n);o.addProperty("gcTimeMs",t);return o;}
    private void fail(Throwable e){if(done)return;done=true;recording=false;e.printStackTrace();try{if(output!=null){report.addProperty("status","FAIL");report.addProperty("error",e.toString());report.addProperty("stage",state);report.addProperty("correctionCount",correctionCount);report.add("frames",frames());save();}}catch(Throwable ignored){ignored.printStackTrace();}MinecraftClient.getInstance().scheduleStop();}
    private void save(){try{Files.writeString(output.resolve("flight.json"),JSON.toJson(report)+"\n");}catch(Exception e){throw new RuntimeException(e);}}
    private static double lerp(JsonObject a,JsonObject b,String k,double f){return a.get(k).getAsDouble()*(1-f)+b.get(k).getAsDouble()*f;}
    private static String required(String k){String v=System.getProperty(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing -D"+k);return v;}
    private static void check(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    private static String modSha(String id)throws Exception{var paths=FabricLoader.getInstance().getModContainer(id).orElseThrow().getOrigin().getPaths();check(paths.size()==1&&Files.isRegularFile(paths.get(0)),"one JAR required: "+id);return sha(Files.readAllBytes(paths.get(0)));}
}

