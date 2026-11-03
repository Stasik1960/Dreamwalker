package dev.dreamwalker.bloodbornedw.visual;

import com.google.gson.*;
import dev.dreamwalker.qacommon.NativeReadiness;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.registry.Registries;
import java.nio.file.*;

/** Finite visual evidence recorder, excluded from the production mod. */
public final class CornerLightingQa implements ModInitializer, ClientModInitializer {
    private static final double[][] VIEWS = {
        {261.207,55.25743,-1007.733,110.2,14.0},
        {118.670,101.16723,-1134.088,-140.5,2.6},
        {117.194,108.28656,-1141.992,-114.8,23.7},
        {-11.529,23.75691,-859.509,5.1,10.7},
        {234.539,51.48537,-818.588,2.0,8.4},
        {318.951,88.99883,-987.116,-84.8,8.4},
        {316.196,84.86644,-986.267,-69.9,44.8},
        {313.325,73.22640,-986.443,-15.3,47.5},
        {264.166,55.20138,-918.374,119.4,1.8}
    };
    private static final int[][] TARGETS = {
        {257,55,-1010},{121,102,-1139},{120,108,-1143},{-12,24,-855},
        {232,52,-815},{321,90,-987},{319,83,-986},{316,70,-984},
        {260,56,-921}
    };
    // Indices are stable camera IDs; stage scheduling uses the selected count.
    private static final int[] CAMERAS = selectCameras();
    private static int[] selectCameras(){
        String selection=System.getProperty("dw.cornerqa.cameras","all").trim();
        if(selection.equals("all"))return java.util.stream.IntStream.range(0,VIEWS.length).toArray();
        String[] parts=selection.split(",",-1);
        int[] result=new int[parts.length];boolean[] seen=new boolean[VIEWS.length];
        for(int i=0;i<parts.length;i++){
            int camera;
            try{camera=Integer.parseInt(parts[i].trim());}
            catch(NumberFormatException error){throw new IllegalArgumentException("Invalid dw.cornerqa.cameras: "+selection,error);}
            if(camera<1||camera>VIEWS.length||seen[camera-1])throw new IllegalArgumentException("Invalid or duplicate camera: "+camera);
            seen[camera-1]=true;result[i]=camera-1;
        }
        return result;
    }
    private int cameraIndex(){return CAMERAS[index%CAMERAS.length];}
    private boolean night(){return index/CAMERAS.length%2==1;}
    private static volatile boolean corrected;
    private static volatile boolean stageNight;
    public static boolean corrected(){return corrected;}
    public static boolean stageNight(){return stageNight;}
    private static final Path OUT=Path.of(System.getProperty("dw.cornerqa.output"));
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final JsonArray rows=new JsonArray();
    private int index,polls;
    private long startedAt,stableAt;
    private volatile boolean teleported,rendered,screenshotSaved;
    private volatile Throwable serverError;
    private volatile Throwable screenshotError;
    private volatile long screenshotBytes;
    private JsonObject pendingCapture;
    private boolean started,capturing,finished,target9ModelWritten;
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            CityVisualModeState state=CityVisualModeState.get(server);
            state.alt=Boolean.getBoolean("dw.cornerqa.alt");state.markDirty();
            server.getOverworld().setTimeOfDay(6000);
            server.getOverworld().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
            server.getOverworld().getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);
            server.getOverworld().getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS).set(true,server);
            server.getOverworld().setWeather(0,0,false,false);
        });
    }
    @Override public void onInitializeClient(){
        WorldRenderEvents.END.register(context->{
            MinecraftClient c=MinecraftClient.getInstance();
            if(started&&!finished&&atTarget(c))rendered=true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient c){
        if(finished||c.world==null||c.player==null||c.getServer()==null)return;
        try{
            Files.createDirectories(OUT);
            if(!started){begin(c);return;}
            if(serverError!=null)throw new IllegalStateException("Camera teleport failed",serverError);
            if(screenshotError!=null)throw new IllegalStateException("Screenshot save failed at view "+(index+1),screenshotError);
            long now=System.nanoTime();
            // Keep the deadline active while asynchronous screenshot I/O runs.
            if(now-startedAt>180_000_000_000L)throw new IllegalStateException("Visual readiness/screenshot timeout at view "+(index+1));
            if(capturing){
                if(!screenshotSaved)return;
                pendingCapture.addProperty("screenshotBytes",screenshotBytes);
                pendingCapture.addProperty("pngWriteVerified",true);
                rows.add(pendingCapture);Files.writeString(OUT.resolve("captures.json"),JSON.toJson(rows));
                pendingCapture=null;
                index++;
                if(index==CAMERAS.length*4){
                    finished=true;
                    JsonObject complete=new JsonObject();complete.addProperty("status","CAPTURED");complete.addProperty("count",index);complete.addProperty("cameraCount",CAMERAS.length);complete.addProperty("testCount",CAMERAS.length*4);complete.addProperty("cameraSelection",System.getProperty("dw.cornerqa.cameras","all"));complete.addProperty("alt",CityVisualClient.isAlt());complete.addProperty("readinessGated",true);complete.addProperty("pngWritesVerified",true);
                    Files.writeString(OUT.resolve("complete.json"),JSON.toJson(complete));
                    c.scheduleStop();return;
                }
                begin(c);return;
            }
            if(!teleported||!atTarget(c))return;
            c.player.setYaw((float)VIEWS[cameraIndex()][3]);c.player.setPitch((float)VIEWS[cameraIndex()][4]);
            if(++polls%5!=0)return;
            JsonObject chunks=chunks(c);
            JsonObject nativeState=NativeReadiness.sample(c.world);
            boolean ready=chunks.get("missingChunks").getAsInt()==0&&c.worldRenderer.isTerrainRenderComplete()&&rendered&&c.currentScreen==null&&c.getOverlay()==null&&NativeReadiness.idle(nativeState);
            if(!ready){stableAt=0;return;}
            if(stableAt==0)stableAt=now;
            if(now-stableAt<2_000_000_000L)return;
            if(c.world.getTimeOfDay()%24000L!=(night()?18000L:6000L))return;
            if(CityVisualClient.isAlt()!=Boolean.getBoolean("dw.cornerqa.alt"))throw new IllegalStateException("Wrong active visual mode");
            JsonObject row=new JsonObject();row.addProperty("view",index+1);row.addProperty("clientTimeOfDay",c.world.getTimeOfDay());row.addProperty("clientSkyAngle",c.world.getSkyAngle(1f));row.addProperty("alt",CityVisualClient.isAlt());row.addProperty("corrected",corrected);row.addProperty("night",night());row.addProperty("camera",cameraIndex()+1);
            BlockPos target=new BlockPos(TARGETS[cameraIndex()][0],TARGETS[cameraIndex()][1],TARGETS[cameraIndex()][2]);var targetState=c.world.getBlockState(target);row.addProperty("targetState",targetState.toString());row.addProperty("targetBlockId",Registries.BLOCK.getId(targetState.getBlock()).toString());row.addProperty("targetX",target.getX());row.addProperty("targetY",target.getY());row.addProperty("targetZ",target.getZ());row.addProperty("targetSky",c.world.getLightLevel(net.minecraft.world.LightType.SKY,target));row.addProperty("targetBlockLight",c.world.getLightLevel(net.minecraft.world.LightType.BLOCK,target));
            if(cameraIndex()==8&&!target9ModelWritten){
                if(!Registries.BLOCK.getId(targetState.getBlock()).toString().equals("bloodborne_dw:source_wet_sponge"))throw new IllegalStateException("Camera 9 target differs from user screenshot: "+targetState);
                CornerTargetModelDiagnostic.write(c,target,OUT.resolve("target9-model.json"));target9ModelWritten=true;
            }
            if(cameraIndex()==8)CornerTargetSamplerDiagnostic.write(OUT.resolve("target9-sampler-"+(corrected?"fixed":"reference")+"-"+(night()?"night":"day")+".json"));
            row.addProperty("x",c.player.getX());row.addProperty("y",c.player.getY());row.addProperty("z",c.player.getZ());
            row.addProperty("yaw",c.player.getYaw());row.addProperty("pitch",c.player.getPitch());
            var camera=c.gameRenderer.getCamera().getPos();row.addProperty("cameraX",camera.x);row.addProperty("cameraY",camera.y);row.addProperty("cameraZ",camera.z);row.addProperty("playerEyeHeight",c.player.getEyeY()-c.player.getY());
            JsonArray requestedCamera=new JsonArray();for(double coordinate:VIEWS[cameraIndex()])requestedCamera.add(coordinate);row.add("requestedCameraPlayerPositionYawPitch",requestedCamera);
            row.addProperty("renderDistance",c.options.getViewDistance().getValue());
            row.addProperty("terrainComplete",true);row.addProperty("renderedAtTarget",true);row.addProperty("readyAndSettleSeconds",(now-startedAt)/1e9);row.add("chunks",chunks);
            row.addProperty("nativeIdleStableSeconds",(now-stableAt)/1e9);row.add("nativeReadiness",nativeState);
            String name=String.format("corner-%s-%s-%s-%02d.png",CityVisualClient.isAlt()?"alt":"original",corrected?"fixed":"reference",night()?"night":"day",cameraIndex()+1);
            Path png=c.runDirectory.toPath().resolve("screenshots").resolve(name);
            if(Files.exists(png,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Capture target must be fresh: "+png);
            row.addProperty("screenshot",name);pendingCapture=row;
            capturing=true;screenshotSaved=false;screenshotError=null;screenshotBytes=0;
            ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),message->{
                try{
                    if(!(message.getContent() instanceof TranslatableTextContent translated)||!translated.getKey().equals("screenshot.success"))
                        throw new IllegalStateException("Screenshot recorder reported failure: "+message.getString());
                    if(!Files.isRegularFile(png,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Screenshot PNG missing: "+png);
                    long bytes=Files.size(png);
                    if(bytes<=0)throw new IllegalStateException("Screenshot PNG is empty: "+png);
                    screenshotBytes=bytes;screenshotSaved=true;
                }catch(Throwable error){screenshotError=error;}
            });
        }catch(Throwable error){
            finished=true;error.printStackTrace();
            try{Files.writeString(OUT.resolve("error.txt"),error.toString());}catch(Exception ignored){}
            c.scheduleStop();
        }
    }
    private void begin(MinecraftClient c)throws Exception{
        if(index==0){
            dev.dreamwalker.bloodbornedw.composite.CornerSurfaceLightTest.main(new String[0]);
            dev.dreamwalker.bloodbornedw.composite.CornerQuadSplitterTest.main(new String[0]);
            dev.dreamwalker.bloodbornedw.composite.CornerQuadParentScopeTest.main(new String[0]);
            dev.dreamwalker.bloodbornedw.composite.CornerQuadCacheTest.main(new String[0]);
            corrected=true;
            try{dev.dreamwalker.bloodbornedw.composite.CornerQuadCellClipperTest.main(new String[0]);}
            finally{corrected=false;}
            Files.writeString(OUT.resolve("native-sampler-split-cache-unit.json"),"{\"status\":\"PASS\",\"environment\":\"running Minecraft client\",\"testCount\":5}");
        }
        boolean nextCorrected=index>=CAMERAS.length*2;
        if(index%CAMERAS.length==0){corrected=nextCorrected;stageNight=night();CornerTargetSamplerDiagnostic.clear();dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh.reload();c.worldRenderer.reload();}
        started=true;startedAt=System.nanoTime();stableAt=0;polls=0;
        teleported=rendered=capturing=screenshotSaved=false;serverError=screenshotError=null;screenshotBytes=0;pendingCapture=null;
        c.options.hudHidden=true;c.options.getFov().setValue(70);c.options.pauseOnLostFocus=false;
        double[] v=VIEWS[cameraIndex()];
        c.getServer().execute(()->{
            try{
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                p.stopRiding();p.changeGameMode(GameMode.SPECTATOR);p.setNoGravity(true);
                p.getServerWorld().setTimeOfDay(night()?18000:6000);
                p.teleport(p.getServerWorld(),v[0],v[1],v[2],(float)v[3],(float)v[4]);p.setVelocity(0,0,0);teleported=true;
            }catch(Throwable error){serverError=error;}
        });
    }
    private boolean atTarget(MinecraftClient c){
        if(c.player==null)return false;
        double[] v=VIEWS[cameraIndex()];double x=c.player.getX()-v[0],y=c.player.getY()-v[1],z=c.player.getZ()-v[2];
        return x*x+y*y+z*z<.0025;
    }
    private static JsonObject chunks(MinecraftClient c){
        int cx=c.player.getBlockX()>>4,cz=c.player.getBlockZ()>>4,d=c.options.getViewDistance().getValue(),required=0,missing=0;
        for(int x=cx-d-2;x<=cx+d+2;x++)for(int z=cz-d-2;z<=cz+d+2;z++){
            if(!ThreadedAnvilChunkStorage.isWithinDistance(x,z,cx,cz,d))continue;
            required++;if(c.world.getChunkManager().getChunk(x,z,ChunkStatus.FULL,false)==null)missing++;
        }
        JsonObject o=new JsonObject();o.addProperty("requiredFullChunks",required);o.addProperty("missingChunks",missing);
        o.addProperty("residentChunks",c.world.getChunkManager().getLoadedChunkCount());return o;
    }
}
