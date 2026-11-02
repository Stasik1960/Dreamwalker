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
import java.nio.file.*;

/** Finite visual evidence recorder, excluded from the production mod. */
public final class VisualRevisionQa implements ModInitializer, ClientModInitializer {
    private static final double[][] VIEWS = {
        {262.312,50.18922,-910.191,-103.9,8.4},
        {221.217,46.06987,-895.841,-161.8,18.7},
        {68.873,104.9155,-788.288,121.6,21.0},
        {125.434,60.07117,-858.094,-111.9,2.9},
        {120.85,47,-950.338,173.0,-6.0},
        {177.089,66.99565,-933.721,-153.3,21.9},
        {278.429,44.87609,-846.249,-91.8,0.3},
        {194.524,60.05132,-971.282,86.7,10.0},
        {293.234,61.5177,-979.893,-151.4,8.9},
        {249.608,46.24593,-839.298,-31.1,22.8},
        {240,57,-886,-90,32.4},
        {240,57,-864,-90,26.1},
        {240,57,-857.25,-90,31.1},
        {161.810,88.99000,-1022.377,-122.9,34.7},
        {165,57,-1005,-90,45}
    };
    private static final Path OUT=Path.of(System.getProperty("dw.visualqa.output"));
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final JsonArray rows=new JsonArray();
    private int index,polls;
    private long startedAt,stableAt;
    private volatile boolean teleported,rendered,screenshotSaved;
    private volatile Throwable serverError;
    private volatile Throwable screenshotError;
    private volatile long screenshotBytes;
    private JsonObject pendingCapture;
    private boolean started,capturing,finished;
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            CityVisualModeState state=CityVisualModeState.get(server);
            state.alt=Boolean.getBoolean("dw.visualqa.alt");state.markDirty();
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
                if(index==VIEWS.length){
                    finished=true;
                    Files.writeString(OUT.resolve("complete.json"),"{\"status\":\"CAPTURED\",\"count\":"+index+",\"alt\":"+CityVisualClient.isAlt()+",\"readinessGated\":true,\"pngWritesVerified\":true}");
                    c.scheduleStop();return;
                }
                begin(c);return;
            }
            if(!teleported||!atTarget(c))return;
            c.player.setYaw((float)VIEWS[index][3]);c.player.setPitch((float)VIEWS[index][4]);
            if(++polls%5!=0)return;
            JsonObject chunks=chunks(c);
            JsonObject nativeState=NativeReadiness.sample(c.world);
            boolean ready=chunks.get("missingChunks").getAsInt()==0&&c.worldRenderer.isTerrainRenderComplete()&&rendered&&c.currentScreen==null&&c.getOverlay()==null&&NativeReadiness.idle(nativeState);
            if(!ready){stableAt=0;return;}
            if(stableAt==0)stableAt=now;
            if(now-stableAt<2_000_000_000L)return;
            if(CityVisualClient.isAlt()!=Boolean.getBoolean("dw.visualqa.alt"))throw new IllegalStateException("Wrong active visual mode");
            JsonObject row=new JsonObject();row.addProperty("view",index+1);row.addProperty("alt",CityVisualClient.isAlt());
            row.addProperty("x",c.player.getX());row.addProperty("y",c.player.getY());row.addProperty("z",c.player.getZ());
            row.addProperty("yaw",c.player.getYaw());row.addProperty("pitch",c.player.getPitch());
            row.addProperty("renderDistance",c.options.getViewDistance().getValue());
            row.addProperty("terrainComplete",true);row.addProperty("renderedAtTarget",true);row.addProperty("readyAndSettleSeconds",(now-startedAt)/1e9);row.add("chunks",chunks);
            row.addProperty("nativeIdleStableSeconds",(now-stableAt)/1e9);row.add("nativeReadiness",nativeState);
            String name=String.format("visual8-%s-%02d.png",CityVisualClient.isAlt()?"alt":"original",index+1);
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
            int[][] negatives={{161,88,-1022},{184,57,-943},{182,64,-943},{200,64,-944},{183,71,-943}};
            for(int[] q:negatives){BlockPos b=new BlockPos(q[0],q[1],q[2]);
                if(FacadeMaterials.role(b)==4||ShutterSubstrate.role(c.world,b)==4)throw new IllegalStateException("Red leaked outside house at "+b);
            }
            Files.writeString(OUT.resolve("scope-runtime.json"),"{\"status\":\"PASS\",\"negativePositions\":5}");
        }
        started=true;startedAt=System.nanoTime();stableAt=0;polls=0;
        teleported=rendered=capturing=screenshotSaved=false;serverError=screenshotError=null;screenshotBytes=0;pendingCapture=null;
        c.options.hudHidden=true;c.options.getFov().setValue(70);c.options.pauseOnLostFocus=false;
        double[] v=VIEWS[index];
        c.getServer().execute(()->{
            try{
                var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());
                p.stopRiding();p.changeGameMode(GameMode.SPECTATOR);p.setNoGravity(true);
                p.teleport(p.getServerWorld(),v[0],v[1],v[2],(float)v[3],(float)v[4]);p.setVelocity(0,0,0);teleported=true;
            }catch(Throwable error){serverError=error;}
        });
    }
    private boolean atTarget(MinecraftClient c){
        if(c.player==null)return false;
        double[] v=VIEWS[index];double x=c.player.getX()-v[0],y=c.player.getY()-v[1],z=c.player.getZ()-v[2];
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
