package dev.dreamwalker.perfqa;

import com.google.gson.*;
import dev.dreamwalker.qacommon.NativeReadiness;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.opengl.GL11;

/** Bounded independent recorder: no production classes, mode commands or resource reloads.
 * The launcher selects the mod/profile and gives each process an independent world copy.
 */
public final class PerformanceQa implements ClientModInitializer {
    private static PerformanceQa instance;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private Path output;
    private JsonObject plan,report=new JsonObject(),current;
    private final JsonArray phases=new JsonArray(),memorySamples=new JsonArray();
    private final List<JsonObject> route=new ArrayList<>();
    private final Set<Long> delivered=new HashSet<>();
    private Stage stage=Stage.BOOT;
    private enum Stage {BOOT,WAITING_FOCUS,LOADING,STEADY,SCREENSHOT,SAVING,DONE}
    private int index,rounds,loads,unloads,totalLoads,totalUnloads,readinessPoll;
    private long bootAt,loadingAt,firstReadyAt,stableAt,steadyAt,nextMemoryAt;
    private double readyTimeout,steadySeconds,settleSeconds,totalTimeout;
    private volatile boolean teleported,renderedAtTarget,screenshotSaved;
    private volatile Throwable screenshotError;
    private volatile long screenshotBytes;
    private long screenshotAt;
    private CompletableFuture<Void> serverJob;
    private JsonObject phaseMemoryStart,loadingStartMemory,steadyStartMemory;
    private boolean finished;

    @Override public void onInitializeClient(){
        instance=this;bootAt=System.nanoTime();
        try{
            Path path=Path.of(requiredProperty("dw.perf.plan"));
            output=Path.of(requiredProperty("dw.perf.output"));
            Files.createDirectories(output);
            if(Files.exists(output.resolve("performance.json")))throw new IllegalStateException("Preserve existing benchmark report");
            byte[] planBytes=Files.readAllBytes(path);plan=JsonParser.parseString(new String(planBytes,StandardCharsets.UTF_8)).getAsJsonObject();
            require(plan.get("schema").getAsInt()==1,"Performance plan schema must be 1");
            rounds=plan.has("rounds")?plan.get("rounds").getAsInt():2;
            require(rounds==2,"This comparison requires two complete visits");
            readyTimeout=number("readyTimeoutSeconds",90,10,120);
            steadySeconds=number("steadySeconds",15,10,30);
            settleSeconds=number("settleSeconds",2,2,5);
            totalTimeout=number("totalTimeoutSeconds",840,60,900);
            JsonArray sites=plan.getAsJsonArray("cameras");require(sites!=null&&sites.size()==6,"Exactly six locked camera sites required");
            Set<String> names=new HashSet<>();
            for(JsonElement element:sites){
                JsonObject site=element.getAsJsonObject();String name=site.get("name").getAsString();
                require(name.matches("[a-z0-9-]{1,64}")&&names.add(name),"Safe unique camera name required");
                for(String key:List.of("x","y","z","yaw","pitch"))require(Double.isFinite(site.get(key).getAsDouble()),"Nonfinite camera");
            }
            for(int round=1;round<=rounds;round++)for(JsonElement element:sites){
                JsonObject site=element.getAsJsonObject().deepCopy();site.addProperty("round",round);
                site.addProperty("phase",site.get("name").getAsString()+"-visit"+round);route.add(site);
            }
            report.addProperty("schema",1);report.addProperty("status","STARTING");report.addProperty("startedUtc",Instant.now().toString());
            report.addProperty("planSha256",sha(planBytes));report.add("plan",plan.deepCopy());
            report.addProperty("pid",ProcessHandle.current().pid());report.addProperty("java",System.getProperty("java.runtime.version"));
            report.addProperty("vmArguments",ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
            report.addProperty("frameClock","RenderSystem.flipFrame HEAD, System.nanoTime");
            report.addProperty("memoryMeaning","Heap/GC samples are Java metrics. Process Working Set and Private Bytes must come from the separate same-PID Windows sampler.");
            report.addProperty("loadingMeaning","Teleport request until FULL view chunks and terrain complete plus fixed stable interval; includes server/client work and settling, not pure disk I/O.");
            report.add("phases",phases);report.add("memorySamples",memorySamples);
            JsonArray mods=new JsonArray();
            FabricLoader.getInstance().getAllMods().stream().sorted(Comparator.comparing(m->m.getMetadata().getId())).forEach(mod->{
                JsonObject m=new JsonObject();m.addProperty("id",mod.getMetadata().getId());m.addProperty("version",mod.getMetadata().getVersion().getFriendlyString());mods.add(m);
            });report.add("loadedMods",mods);
            var production=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow();
            var paths=production.getOrigin().getPaths();require(paths.size()==1,"One production JAR origin required");
            Path productionPath=paths.get(0);require(Files.isRegularFile(productionPath,LinkOption.NOFOLLOW_LINKS),"Production origin must be a real file");
            String modSha=shaFile(productionPath);report.addProperty("productionSha256",modSha);
            var qaPaths=FabricLoader.getInstance().getModContainer("dw_performance_qa").orElseThrow().getOrigin().getPaths();
            require(qaPaths.size()==1&&Files.isRegularFile(qaPaths.get(0),LinkOption.NOFOLLOW_LINKS),"One immutable QA JAR origin required");
            report.addProperty("qaSha256",shaFile(qaPaths.get(0)));
            report.addProperty("variantLabel",System.getProperty("dw.perf.variant","UNSPECIFIED"));
            report.addProperty("productionVersion",production.getMetadata().getVersion().getFriendlyString());
            if(System.getProperty("dw.perf.expectedSha256")!=null)require(modSha.equals(System.getProperty("dw.perf.expectedSha256")),"Loaded production differs from runner manifest");
            ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->{
                if(stage==Stage.LOADING||stage==Stage.STEADY){loads++;totalLoads++;delivered.add(chunk.getPos().toLong());}
            });
            ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{if(stage==Stage.LOADING||stage==Stage.STEADY){unloads++;totalUnloads++;}});
            WorldRenderEvents.END.register(context->{
                var c=MinecraftClient.getInstance();
                if(!renderedAtTarget&&(stage==Stage.LOADING||stage==Stage.STEADY)&&current!=null&&c.player!=null&&atTarget(c))renderedAtTarget=true;
            });
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);save();
        }catch(Throwable error){fail(error);}
    }

    public static void presentation(){if(instance!=null&&!instance.finished)FrameWindow.frame();}

    private void tick(MinecraftClient c){
        if(finished)return;
        try{
            long now=System.nanoTime();
            require((now-bootAt)/1e9<totalTimeout,"Overall bounded benchmark deadline exceeded");
            if(stage==Stage.SCREENSHOT){
                require(now-screenshotAt<30_000_000_000L,"Asynchronous screenshot callback/write deadline exceeded");
                if(screenshotError!=null)throw new IllegalStateException("Screenshot save failed",screenshotError);
            }
            if(stage==Stage.SAVING){
                if(serverJob!=null&&serverJob.isDone()){
                    serverJob.join();report.addProperty("saved",true);report.addProperty("status","PASS");
                    report.addProperty("finishedUtc",Instant.now().toString());report.addProperty("elapsedSeconds",(now-bootAt)/1e9);
                    report.addProperty("totalChunkLoadCallbacks",totalLoads);report.addProperty("totalChunkUnloadCallbacks",totalUnloads);
                    finished=true;stage=Stage.DONE;save();c.scheduleStop();
                }return;
            }
            if(c.world==null||c.player==null||c.getServer()==null){
                require((now-bootAt)/1e9<240,"No integrated world within startup deadline");return;
            }
            if(stage==Stage.BOOT||stage==Stage.WAITING_FOCUS){
                if(!c.isWindowFocused()||c.currentScreen!=null||c.getOverlay()!=null||c.isPaused()){
                    if(stage!=Stage.WAITING_FOCUS){stage=Stage.WAITING_FOCUS;report.addProperty("status","WAITING_FOCUS");report.addProperty("action","Activate the benchmark game window; measurement starts only when Minecraft reports real focus and world visibility.");save();}
                    return;
                }
                if(!report.has("actualOptions")){
                    report.add("actualOptions",options(c));report.addProperty("glRenderer",GL11.glGetString(GL11.GL_RENDERER));
                    report.addProperty("glVendor",GL11.glGetString(GL11.GL_VENDOR));report.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));
                    validateOptions(c);report.remove("action");
                }
                beginPhase(c,now);return;
            }
            require(c.isWindowFocused(),"Window focus lost during a measured phase; comparisons require an uninterrupted focused run");
            require(c.currentScreen==null&&c.getOverlay()==null&&!c.isPaused(),"World interrupted by a screen/overlay during benchmark");
            if(now>=nextMemoryAt){JsonObject memory=memory();memory.addProperty("elapsedSeconds",(now-bootAt)/1e9);memory.addProperty("stage",stage.name());memory.addProperty("phase",current.get("phase").getAsString());memorySamples.add(memory);nextMemoryAt=now+1_000_000_000L;}
            if(stage==Stage.LOADING){
                require((now-loadingAt)/1e9<readyTimeout,"Chunk/terrain readiness timeout at "+current.get("phase").getAsString());
                if(serverJob!=null&&serverJob.isCompletedExceptionally())serverJob.join();
                if(!teleported||!atTarget(c))return;
                lockView(c);
                if(++readinessPoll%5!=0)return;
                JsonObject chunks=chunkReadiness(c);
                JsonObject nativeState=NativeReadiness.sample(c.world);
                boolean ready=chunks.get("allViewChunksFull").getAsBoolean()&&c.worldRenderer.isTerrainRenderComplete()&&renderedAtTarget&&sceneProbe(c)&&NativeReadiness.idle(nativeState);
                if(!ready){stableAt=0;return;}
                if(firstReadyAt==0)firstReadyAt=now;
                if(stableAt==0)stableAt=now;
                if((now-stableAt)/1e9<settleSeconds)return;
                JsonObject frames=FrameWindow.stop();require(!frames.get("overflow").getAsBoolean(),"Loading frame recorder overflow");
                frames.addProperty("firstReadySeconds",(firstReadyAt-loadingAt)/1e9);
                frames.addProperty("readyAndSettleSeconds",(now-loadingAt)/1e9);
                frames.add("readinessAtBoundary",chunks);frames.addProperty("terrainComplete",true);
                frames.add("nativeReadinessAtBoundary",nativeState);frames.addProperty("nativeIdleStableSeconds",(now-stableAt)/1e9);
                frames.addProperty("renderedAtTarget",true);frames.addProperty("sceneProbeNonAir",true);
                frames.addProperty("chunkLoadCallbacks",loads);frames.addProperty("uniqueDeliveredChunks",delivered.size());frames.addProperty("chunkUnloadCallbacks",unloads);
                frames.add("memoryAtStart",loadingStartMemory);frames.add("memoryAtEnd",memory());
                current.add("loading",frames);
                current.addProperty("loadingEndEpochMillis",System.currentTimeMillis());
                loads=unloads=0;delivered.clear();steadyStartMemory=memory();
                stage=Stage.STEADY;steadyAt=System.nanoTime();FrameWindow.begin();
                current.addProperty("steadyStartEpochMillis",System.currentTimeMillis());
                report.addProperty("status","MEASURING_STEADY");save();return;
            }
            if(stage==Stage.STEADY){
                lockView(c);require(atTarget(c),"Measured camera position drifted");
                if((now-steadyAt)/1e9<steadySeconds)return;
                JsonObject frames=FrameWindow.stop();require(!frames.get("overflow").getAsBoolean(),"Steady frame recorder overflow");
                require(frames.get("samples").getAsInt()>=30,"Too few real frames for a usable steady sample");
                JsonObject chunks=chunkReadiness(c);require(chunks.get("allViewChunksFull").getAsBoolean()&&c.worldRenderer.isTerrainRenderComplete(),"Final steady camera terrain/chunks incomplete");
                JsonObject nativeState=NativeReadiness.sample(c.world);require(NativeReadiness.idle(nativeState),"Native work still pending at the end of the steady camera window");
                frames.addProperty("wallSeconds",(now-steadyAt)/1e9);frames.addProperty("minimumDesiredSamples",120);
                frames.addProperty("atLeast120Samples",frames.get("samples").getAsInt()>=120);
                frames.addProperty("chunkLoadCallbacks",loads);frames.addProperty("uniqueDeliveredChunks",delivered.size());frames.addProperty("chunkUnloadCallbacks",unloads);
                frames.add("memoryAtStart",steadyStartMemory);frames.add("memoryAtEnd",memory());frames.add("readinessAtEnd",chunks);
                frames.add("nativeReadinessAtEnd",nativeState);
                frames.addProperty("terrainComplete",true);frames.addProperty("windowFocused",true);
                current.add("steady",frames);current.add("actualCamera",actualCamera(c));current.add("actualOptions",options(c));
                current.addProperty("steadyEndEpochMillis",System.currentTimeMillis());
                current.add("memoryAtPhaseStart",phaseMemoryStart);current.add("memoryAtPhaseEnd",memory());
                stage=Stage.SCREENSHOT;screenshotSaved=false;screenshotError=null;screenshotBytes=0;screenshotAt=System.nanoTime();
                String name=current.get("phase").getAsString()+".png";Path png=c.runDirectory.toPath().resolve("screenshots").resolve(name);
                require(!Files.exists(png,LinkOption.NOFOLLOW_LINKS),"Performance screenshot target must be fresh: "+png);
                current.addProperty("screenshot",name);
                ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),message->{
                    try{
                        if(!(message.getContent() instanceof TranslatableTextContent translated)||!translated.getKey().equals("screenshot.success"))
                            throw new IllegalStateException("Screenshot recorder reported failure: "+message.getString());
                        require(Files.isRegularFile(png,LinkOption.NOFOLLOW_LINKS),"Screenshot PNG missing: "+png);
                        long bytes=Files.size(png);require(bytes>0,"Screenshot PNG empty: "+png);
                        screenshotBytes=bytes;screenshotSaved=true;
                    }catch(Throwable error){screenshotError=error;}
                });
                report.addProperty("status","CAPTURING_PHASE");save();return;
            }
            if(stage==Stage.SCREENSHOT&&screenshotSaved){
                current.addProperty("screenshotBytes",screenshotBytes);current.addProperty("pngWriteVerified",true);
                current.addProperty("status","PASS");phases.add(current.deepCopy());save();
                index++;
                if(index==route.size()){
                    report.addProperty("status","SAVING");save();stage=Stage.SAVING;serverJob=new CompletableFuture<>();
                    c.getServer().execute(()->{try{c.getServer().save(false,true,true);serverJob.complete(null);}catch(Throwable error){serverJob.completeExceptionally(error);}});
                }else beginPhase(c,now);
            }
        }catch(Throwable error){fail(error);}
    }

    private void beginPhase(MinecraftClient c,long now){
        current=route.get(index).deepCopy();current.addProperty("startedUtc",Instant.now().toString());
        current.addProperty("loadingStartEpochMillis",System.currentTimeMillis());
        current.addProperty("startElapsedSeconds",(now-bootAt)/1e9);report.addProperty("currentPhase",current.get("phase").getAsString());
        report.addProperty("status","MEASURING_LOADING");
        loads=unloads=readinessPoll=0;delivered.clear();firstReadyAt=stableAt=0;teleported=renderedAtTarget=false;
        phaseMemoryStart=memory();loadingStartMemory=phaseMemoryStart.deepCopy();stage=Stage.LOADING;
        c.options.hudHidden=true;c.options.pauseOnLostFocus=false;lockView(c);
        serverJob=new CompletableFuture<>();JsonObject camera=current;
        loadingAt=System.nanoTime();FrameWindow.begin();
        c.getServer().execute(()->{
            try{
                var server=c.getServer();var player=server.getPlayerManager().getPlayer(c.player.getUuid());
                require(player!=null,"Integrated player missing");var world=server.getOverworld();
                world.setTimeOfDay(plan.has("fixedTime")?plan.get("fixedTime").getAsLong():6000);
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);
                world.getGameRules().get(GameRules.SPECTATORS_GENERATE_CHUNKS).set(true,server);
                world.setWeather(0,0,false,false);player.stopRiding();player.changeGameMode(GameMode.SPECTATOR);player.setNoGravity(true);
                player.teleport(world,camera.get("x").getAsDouble(),camera.get("y").getAsDouble(),camera.get("z").getAsDouble(),camera.get("yaw").getAsFloat(),camera.get("pitch").getAsFloat());
                player.setVelocity(0,0,0);teleported=true;serverJob.complete(null);
            }catch(Throwable error){serverJob.completeExceptionally(error);}
        });
        save();
    }
    private boolean atTarget(MinecraftClient c){double x=c.player.getX()-current.get("x").getAsDouble(),y=c.player.getY()-current.get("y").getAsDouble(),z=c.player.getZ()-current.get("z").getAsDouble();return x*x+y*y+z*z<.0025;}
    private void lockView(MinecraftClient c){c.player.setYaw(current.get("yaw").getAsFloat());c.player.setPitch(current.get("pitch").getAsFloat());}
    private boolean sceneProbe(MinecraftClient c){
        if(!current.has("probe"))return true;JsonArray p=current.getAsJsonArray("probe");
        return !c.world.getBlockState(new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt())).isAir();
    }
    private static JsonObject chunkReadiness(MinecraftClient c){
        int cx=c.player.getBlockX()>>4,cz=c.player.getBlockZ()>>4,distance=c.options.getViewDistance().getValue(),required=0,missing=0;
        for(int x=cx-distance-2;x<=cx+distance+2;x++)for(int z=cz-distance-2;z<=cz+distance+2;z++){
            if(!ThreadedAnvilChunkStorage.isWithinDistance(x,z,cx,cz,distance))continue;
            required++;if(c.world.getChunkManager().getChunk(x,z,ChunkStatus.FULL,false)==null)missing++;
        }
        JsonObject result=new JsonObject();result.addProperty("requiredFullChunks",required);result.addProperty("missingChunks",missing);
        result.addProperty("allViewChunksFull",missing==0);result.addProperty("residentChunks",c.world.getChunkManager().getLoadedChunkCount());return result;
    }
    private static JsonObject actualCamera(MinecraftClient c){
        JsonObject result=new JsonObject();result.addProperty("x",c.player.getX());result.addProperty("y",c.player.getY());result.addProperty("z",c.player.getZ());
        result.addProperty("yaw",c.player.getYaw());result.addProperty("pitch",c.player.getPitch());result.addProperty("dimension",c.world.getRegistryKey().getValue().toString());return result;
    }
    private static JsonObject options(MinecraftClient c){
        JsonObject o=new JsonObject();o.addProperty("renderDistance",c.options.getViewDistance().getValue());o.addProperty("simulationDistance",c.options.getSimulationDistance().getValue());
        o.addProperty("maxFps",c.options.getMaxFps().getValue());o.addProperty("vsync",c.options.getEnableVsync().getValue());
        o.addProperty("fov",c.options.getFov().getValue());o.addProperty("gamma",c.options.getGamma().getValue());
        o.addProperty("mipmapLevels",c.options.getMipmapLevels().getValue());o.addProperty("graphics",c.options.getGraphicsMode().getValue().toString());
        o.addProperty("windowFocused",c.isWindowFocused());o.addProperty("framebufferWidth",c.getWindow().getFramebufferWidth());o.addProperty("framebufferHeight",c.getWindow().getFramebufferHeight());
        o.addProperty("resourcePacks",c.options.resourcePacks.toString());return o;
    }
    private void validateOptions(MinecraftClient c){
        JsonObject actual=options(c),expected=plan.getAsJsonObject("expectedOptions");
        if(expected!=null)for(var entry:expected.entrySet())require(actual.has(entry.getKey())&&actual.get(entry.getKey()).equals(entry.getValue()),"Configured option differs: "+entry.getKey()+" actual="+actual.get(entry.getKey())+" expected="+entry.getValue());
        require(c.getWindow().getFramebufferWidth()>0&&c.getWindow().getFramebufferHeight()>0,"Invalid framebuffer");
    }
    private static JsonObject memory(){
        JsonObject result=new JsonObject();MemoryMXBean bean=ManagementFactory.getMemoryMXBean();MemoryUsage heap=bean.getHeapMemoryUsage();
        result.addProperty("heapUsedBytes",heap.getUsed());result.addProperty("heapCommittedBytes",heap.getCommitted());result.addProperty("heapMaxBytes",heap.getMax());
        result.addProperty("nonHeapUsedBytes",bean.getNonHeapMemoryUsage().getUsed());
        long collections=0,time=0;for(GarbageCollectorMXBean gc:ManagementFactory.getGarbageCollectorMXBeans()){if(gc.getCollectionCount()>0)collections+=gc.getCollectionCount();if(gc.getCollectionTime()>0)time+=gc.getCollectionTime();}
        result.addProperty("gcCount",collections);result.addProperty("gcTimeMs",time);
        for(BufferPoolMXBean pool:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)){result.addProperty(pool.getName()+"BufferUsedBytes",pool.getMemoryUsed());}
        return result;
    }
    private void fail(Throwable error){
        if(finished)return;error.printStackTrace();finished=true;FrameWindow.stop();
        try{
            if(output!=null){report.addProperty("status","FAIL");report.addProperty("error",error.toString());report.addProperty("stage",stage.name());report.addProperty("finishedUtc",Instant.now().toString());if(current!=null)report.add("incompletePhase",current);save();}
        }catch(Throwable secondary){secondary.printStackTrace();}
        MinecraftClient.getInstance().scheduleStop();
    }
    private void save(){try{Files.writeString(output.resolve("performance.json"),JSON.toJson(report)+"\n");}catch(Exception error){throw new RuntimeException(error);}}
    private double number(String key,double fallback,double min,double max){double value=plan.has(key)?plan.get(key).getAsDouble():fallback;require(Double.isFinite(value)&&value>=min&&value<=max,"Invalid bounded plan value: "+key);return value;}
    private static String requiredProperty(String key){String value=System.getProperty(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing -D"+key);return value;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static String shaFile(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var stream=Files.newInputStream(path)){byte[] buffer=new byte[262144];int n;while((n=stream.read(buffer))>0)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
}
