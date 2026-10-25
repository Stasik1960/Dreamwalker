package dev.dreamwalker.bloodbornedw.routeqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.MovementType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.ChunkStatus;

/** Existing bounded flipFrame clock and native spectator movement, with a
 * fixed route plan and actual delivered-scene gates on a dedicated server. */
public final class RouteClient implements ClientModInitializer {
    private static RouteClient instance;
    private static final AtomicLong OWNER_PACKETS=new AtomicLong(),OWNER_BYTES=new AtomicLong();
    public static void ownerPacket(int bytes){OWNER_PACKETS.incrementAndGet();OWNER_BYTES.addAndGet(bytes);}
    private static final int LIMIT=131072;
    private final long[] frameEnds=new long[LIMIT],intervals=new long[LIMIT];
    private int count,totalFrames;private long previous,phaseStart,epochStart,anchorUncertainty;private String phase="BOOT";private JsonObject startCounters;
    private final Gson json=new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject report=new JsonObject();private final JsonArray rows=new JsonArray();
    private double[][] cameras={{272.848,63.6296,-903.327,120.3,-3.5},{650,110,-150,43.23,35},{980,100,95,44.36,35}};
    private int[][] probes={{253,66,-915},{603,70,-100},{940,28,129}};
    private String[] names={"facade","tower","brick"};
    private Path out;private Recording recording;private BufferedWriter frames;
    private int point,age,settled,loads,unloads,allLoads,allUnloads;
    private final Set<Long> delivered=new HashSet<>();
    private final Set<Long> allDelivered=new HashSet<>();
    private boolean sent,rendered,waitingScreenshot,finished,teleporting;private volatile boolean screenshotSaved,queryPending,ack;
    private Vec3d target;private int travelTicks,moveTicks,chunkWaits;private double movedDistance,maxStep;
    @Override public void onInitializeClient(){
        String output=System.getProperty("dw.route.output");if(output==null)return;instance=this;out=Path.of(output);
        loadPlan();
        try{Files.createDirectories(out);frames=Files.newBufferedWriter(out.resolve("client-frames.csv"));frames.write("phase,endEpochNs,intervalNs\n");}catch(IOException e){throw new UncheckedIOException(e);}
        report.addProperty("status","STARTING");report.add("phases",rows);save();
        ClientPlayNetworking.registerGlobalReceiver(RouteServer.ACK,(client,handler,buffer,sender)->{boolean accepted=buffer.readBoolean();double x=buffer.readDouble(),y=buffer.readDouble(),z=buffer.readDouble();client.execute(()->{ack=accepted;queryPending=false;if(finished&&accepted)client.scheduleStop();});});
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->{loads++;allLoads++;delivered.add(chunk.getPos().toLong());allDelivered.add(chunk.getPos().toLong());});
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{unloads++;allUnloads++;});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(!finished)fail(new IllegalStateException("Dedicated server disconnected before route completion"));});
        WorldRenderEvents.END.register(context->{MinecraftClient c=MinecraftClient.getInstance();if(c.player!=null&&target!=null&&c.player.getPos().squaredDistanceTo(target)<.0025)rendered=true;});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    public static void presentation(){RouteClient i=instance;if(i==null||i.finished||i.phase.equals("BOOT")||i.phase.equals("WAIT_SPECTATOR")||i.phase.equals("INITIAL_POSITION")||i.waitingScreenshot)return;long now=System.nanoTime();if(i.previous!=0){if(i.count>=LIMIT){try{i.flushSamples();}catch(IOException failure){i.fail(failure);return;}}i.frameEnds[i.count]=now;i.intervals[i.count++]=now-i.previous;}i.previous=now;}
    private void tick(MinecraftClient c){
        if(finished)return;
        try{
            if(++age>16000)throw new IllegalStateException("Native route tick deadline at "+phase);
            if(c.world==null||c.player==null||c.currentScreen!=null||c.isPaused())return;
            if(phase.equals("BOOT")){
                require(!c.isInSingleplayer()&&c.getServer()==null,"Must use a real dedicated server");
                require(ClientPlayNetworking.canSend(RouteServer.CONTROL),"Dedicated QA channel missing");
                c.player.networkHandler.sendChatCommand("gamemode spectator @s");c.player.networkHandler.sendChatCommand("gamerule doDaylightCycle false");c.player.networkHandler.sendChatCommand("gamerule doWeatherCycle false");c.player.networkHandler.sendChatCommand("time set 6000");c.player.networkHandler.sendChatCommand("weather clear");
                phase="WAIT_SPECTATOR";return;
            }
            if(phase.equals("WAIT_SPECTATOR")){
                if(!c.player.isSpectator())return;
                point=Boolean.getBoolean("dw.route.validationTail")&&cameras.length>1?1:0;
                phase="INITIAL_POSITION";target=position(cameras[point]);
                report.addProperty("initialPositioningBeforeMeasurement",true);report.addProperty("initialPositioningUtc",Instant.now().toString());
                report.add("initialPosition",json.toJsonTree(cameras[point]));save();
                c.player.networkHandler.sendChatCommand("tp @s "+cameraText(cameras[point]));sent=teleporting=true;return;
            }
            if(phase.equals("INITIAL_POSITION")){
                if(c.player.getPos().squaredDistanceTo(target)>.0025)return;
                teleporting=false;aim(c);
                if(!ack){requestPosition();return;}
                recording=new Recording(Configuration.getConfiguration("profile"));recording.setMaxSize(128L<<20);recording.start();
                begin(names[point]);rendered=false;return;
            }
            if(waitingScreenshot){if(!screenshotSaved)return;waitingScreenshot=false;
                if(++point==cameras.length){finish(c);return;}
                begin(names[point]);target=position(cameras[point]);aim(c);travelTicks=moveTicks=chunkWaits=0;movedDistance=maxStep=0;ack=queryPending=false;rendered=false;return;
            }
            if(teleporting){if(c.player.getPos().squaredDistanceTo(target)>.0025)return;teleporting=false;}
            if(point>0&&c.player.getPos().squaredDistanceTo(target)>.0000001){
                require(c.player.isSpectator(),"Native movement requires spectator");require(++travelTicks<=4096,"Native movement tick deadline");
                Vec3d current=c.player.getPos(),remaining=target.subtract(current),step=remaining.multiply(Math.min(.75,remaining.length())/remaining.length());
                c.player.setVelocity(Vec3d.ZERO);c.player.setYaw((float)cameras[point][3]);c.player.setPitch((float)cameras[point][4]);
                if(!chunkPresent(c,BlockPos.ofFloored(current.add(step)))){chunkWaits++;return;}
                c.player.move(MovementType.SELF,step);double moved=c.player.getPos().distanceTo(current);require(moved<=1.000001,"Excess native movement step");movedDistance+=moved;maxStep=Math.max(maxStep,moved);moveTicks++;return;
            }
            if(c.player.getPos().squaredDistanceTo(target)>.0025)return;
            aim(c);
            if(!ack){requestPosition();return;}
            if(!ready(c)||!rendered||!c.worldRenderer.isTerrainRenderComplete()){settled=0;return;}
            if(++settled<80)return;
            end(c);waitingScreenshot=true;screenshotSaved=false;
            ScreenshotRecorder.saveScreenshot(c.runDirectory,"route-"+phase+".png",c.getFramebuffer(),message->screenshotSaved=true);
        }catch(Throwable e){fail(e);}
    }
    private boolean ready(MinecraftClient c){
        for(long chunk:requiredChunks())if(c.world.getChunkManager().getChunk(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),ChunkStatus.FULL,false)==null)return false;
        int[] p=probes[point];return !c.world.getBlockState(new BlockPos(p[0],p[1],p[2])).isAir();
    }
    private Set<Long> requiredChunks(){Set<Long> chunks=new LinkedHashSet<>();chunks.add(ChunkPos.toLong(MathHelper.floor(target.x)>>4,MathHelper.floor(target.z)>>4));int[] probe=probes[point];chunks.add(ChunkPos.toLong(probe[0]>>4,probe[2]>>4));return chunks;}
    private void aim(MinecraftClient client){client.player.setYaw((float)cameras[point][3]);client.player.setPitch((float)cameras[point][4]);}
    private void requestPosition(){if(queryPending)return;PacketByteBuf buffer=PacketByteBufs.create();buffer.writeString("position");buffer.writeDouble(target.x);buffer.writeDouble(target.y);buffer.writeDouble(target.z);queryPending=true;ClientPlayNetworking.send(RouteServer.CONTROL,buffer);}
    private static boolean chunkPresent(MinecraftClient c,BlockPos p){return c.world.getChunkManager().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)!=null;}
    private void begin(String name){
        phase=name;count=totalFrames=loads=unloads=settled=0;previous=0;delivered.clear();long before=System.nanoTime();Instant now=Instant.now();long after=System.nanoTime();phaseStart=after;anchorUncertainty=(after-before+1)/2;epochStart=now.getEpochSecond()*1_000_000_000L+now.getNano()+anchorUncertainty;startCounters=counters();
        PacketByteBuf buffer=PacketByteBufs.create();buffer.writeString("phase");buffer.writeString(name);ClientPlayNetworking.send(RouteServer.CONTROL,buffer);report.addProperty("currentPhase",phase);save();
    }
    private JsonObject counters(){JsonObject o=new JsonObject();o.addProperty("ownerPackets",OWNER_PACKETS.get());o.addProperty("ownerPayloadBytes",OWNER_BYTES.get());o.add("terrainUpdates",json.toJsonTree(CompositeTerrainUpdates.metrics()));o.add("terrain",json.toJsonTree(StaticArchitectureClient.terrainMetrics()));o.add("snapshots",json.toJsonTree(CompositeShapeSnapshots.metrics(MinecraftClient.getInstance().world)));return o;}
    private void flushSamples()throws IOException {for(int i=0;i<count;i++)frames.write(phase+","+(epochStart+frameEnds[i]-phaseStart)+","+intervals[i]+"\n");frames.flush();totalFrames+=count;count=0;}
    private void end(MinecraftClient c)throws IOException {
        long end=System.nanoTime();flushSamples();
        JsonObject p=new JsonObject();p.addProperty("phase",phase);p.addProperty("startEpochNs",epochStart);p.addProperty("endEpochNs",epochStart+end-phaseStart);p.addProperty("clockAnchorUncertaintyNs",anchorUncertainty);p.addProperty("durationNs",end-phaseStart);p.addProperty("frames",totalFrames);p.addProperty("requiredFullChunks",requiredChunks().size());p.addProperty("loads",loads);p.addProperty("uniqueDeliveredChunks",delivered.size());p.addProperty("unloads",unloads);p.addProperty("residentChunks",c.world.getChunkManager().getLoadedChunkCount());p.addProperty("nativeMovementTicks",moveTicks);p.addProperty("chunkWaitTicks",chunkWaits);p.addProperty("nativeDistance",movedDistance);p.addProperty("maxStep",maxStep);p.addProperty("serverPositionAcknowledged",ack);p.addProperty("terrainRenderComplete",c.worldRenderer.isTerrainRenderComplete());p.addProperty("renderedAtTarget",rendered);p.addProperty("heapUsed",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());p.add("camera",json.toJsonTree(cameras[point]));p.add("probe",json.toJsonTree(probes[point]));int[] probe=probes[point];p.addProperty("probeState",c.world.getBlockState(new BlockPos(probe[0],probe[1],probe[2])).toString());JsonArray evidence=new JsonArray();for(long chunk:requiredChunks()){JsonObject item=new JsonObject();item.addProperty("cx",ChunkPos.getPackedX(chunk));item.addProperty("cz",ChunkPos.getPackedZ(chunk));item.addProperty("full",c.world.getChunkManager().getChunk(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk),ChunkStatus.FULL,false)!=null);item.addProperty("deliveryObserved",allDelivered.contains(chunk));evidence.add(item);}p.add("requiredChunkEvidence",evidence);p.add("countersAtStart",startCounters);p.add("counters",counters());p.addProperty("glRenderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));p.addProperty("glVendor",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));rows.add(p);save();
    }
    private void finish(MinecraftClient c)throws Exception {
        frames.close();recording.stop();recording.dump(out.resolve("client.jfr"));recording.close();recording=null;report.addProperty("allLoads",allLoads);report.addProperty("allUnloads",allUnloads);report.addProperty("allUniqueDeliveredChunks",allDelivered.size());report.addProperty("status","PASS_NATIVE_GRAPHICAL_ROUTE");save();finished=true;
        PacketByteBuf buffer=PacketByteBufs.create();buffer.writeString("finish");ClientPlayNetworking.send(RouteServer.CONTROL,buffer);
    }
    private void fail(Throwable e){if(finished)return;finished=true;e.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",e.toString());try{if(frames!=null)frames.close();if(recording!=null){recording.stop();recording.dump(out.resolve("client-partial.jfr"));recording.close();recording=null;}}catch(Exception x){x.printStackTrace();}try{save();}finally{MinecraftClient.getInstance().scheduleStop();}}
    private void save(){try{Files.writeString(out.resolve("client-route.json"),json.toJson(report)+"\n");}catch(IOException e){throw new UncheckedIOException(e);}}
    private void loadPlan(){
        String path=System.getProperty("dw.route.plan");
        if(path!=null)try{
            byte[] bytes=Files.readAllBytes(Path.of(path));JsonObject plan=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray views=plan.getAsJsonArray("cameras"),samples=plan.getAsJsonArray("probes"),labels=plan.getAsJsonArray("names");
            require(views!=null&&views.size()>0&&views.size()<=64,"Route needs 1..64 fixed cameras");
            require(samples!=null&&samples.size()==views.size(),"Each route camera needs one visible-scene probe");
            require(labels==null||labels.size()==views.size(),"Route names must match cameras");
            cameras=new double[views.size()][5];probes=new int[views.size()][3];names=new String[views.size()];Set<String> unique=new HashSet<>();
            for(int i=0;i<views.size();i++){
                JsonArray camera=views.get(i).getAsJsonArray(),probe=samples.get(i).getAsJsonArray();require(camera.size()==5&&probe.size()==3,"Camera needs xyz/yaw/pitch and probe needs xyz");
                for(int j=0;j<5;j++){cameras[i][j]=camera.get(j).getAsDouble();require(Double.isFinite(cameras[i][j]),"Nonfinite route camera");}
                for(int j=0;j<3;j++){double value=probe.get(j).getAsDouble();require(Double.isFinite(value)&&value==Math.rint(value)&&value>=Integer.MIN_VALUE&&value<=Integer.MAX_VALUE,"Probe coordinates must be integers");probes[i][j]=(int)value;}
                names[i]=labels==null?"camera-"+i:labels.get(i).getAsString();require(names[i].matches("[\\p{L}\\p{N}_.-]{1,64}")&&unique.add(names[i]),"Route names must be unique safe 1..64 character labels");
            }
            report.addProperty("routePlan",Path.of(path).toAbsolutePath().toString());report.addProperty("routePlanSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        }catch(IOException e){throw new UncheckedIOException(e);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        report.add("cameras",json.toJsonTree(cameras));report.add("names",json.toJsonTree(names));report.add("probes",json.toJsonTree(probes));report.addProperty("readinessPolicy","central_and_scene_probe_FULL_nonair_then_rendered_terrain_complete");report.addProperty("networkTimeoutWidened",false);
    }
    private static Vec3d position(double[] camera){return new Vec3d(camera[0],camera[1],camera[2]);}
    private static String cameraText(double[] camera){return camera[0]+" "+camera[1]+" "+camera[2]+" "+camera[3]+" "+camera[4];}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
