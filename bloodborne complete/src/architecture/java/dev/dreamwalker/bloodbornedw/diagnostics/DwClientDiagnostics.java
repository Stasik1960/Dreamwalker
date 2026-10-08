package dev.dreamwalker.bloodbornedw.diagnostics;



import com.google.gson.*;

import com.sun.management.GarbageCollectionNotificationInfo;

import dev.dreamwalker.bloodbornedw.composite.*;

import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;

import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallModels;

import java.lang.management.*;

import java.nio.charset.StandardCharsets;

import java.nio.file.*;

import java.security.MessageDigest;

import java.util.*;

import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicLong;

import java.util.zip.*;

import jdk.jfr.FlightRecorder;

import jdk.jfr.consumer.RecordingStream;

import javax.management.NotificationEmitter;

import javax.management.openmbean.CompositeData;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.SharedConstants;

import net.minecraft.block.BlockState;

import net.minecraft.client.MinecraftClient;

import net.minecraft.client.render.model.BakedModel;

import net.minecraft.client.texture.MissingSprite;

import net.minecraft.registry.Registries;

import net.minecraft.util.Identifier;

import net.minecraft.util.math.BlockPos;

import net.minecraft.util.math.Direction;

import net.minecraft.util.math.random.Random;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



/** Opt-in bounded client telemetry. Errors retain a bounded, rotated journal even when recording is off. */

public final class DwClientDiagnostics {

    public static final Identifier SESSION=new Identifier("bloodborne_dw","diagnostics_session"),BATCH=new Identifier("bloodborne_dw","diagnostics_client_batch"),ACCEPT=new Identifier("bloodborne_dw","diagnostics_accept");

    private static final Gson GSON=new Gson();private static final Logger LOG=LoggerFactory.getLogger("DreamwalkerClientDiagnostics");

    private static final int MAX_EVENTS=256,MAX_ERRORS=256,MAX_TIMINGS=128,MAX_VALUES=4096,MAX_MODELS=1024;

    private static final ArrayDeque<JsonObject> EVENTS=new ArrayDeque<>();

    private static final LinkedHashMap<String,Fault> ERRORS=new LinkedHashMap<>();

    private static final Map<String,Samples> TIMINGS=new LinkedHashMap<>();private static final Map<String,long[]> NETWORK=new LinkedHashMap<>();
    private static final Map<String,Samples> SESSION_TIMINGS=new LinkedHashMap<>();
    private static final ArrayDeque<JsonObject> LOCAL_TIMING_WINDOWS=new ArrayDeque<>();
    private static long droppedLocalTimingWindows,droppedSessionTimingSections;

    private static final LinkedHashMap<String,Boolean> MODELS=new LinkedHashMap<>();

    private static final LinkedHashMap<String,JsonObject> ACCEPTED=new LinkedHashMap<>();

    private static final LinkedHashMap<String,JsonObject> RENDERED=new LinkedHashMap<>();

    private static final Samples FRAMES=new Samples(MAX_VALUES),GC_PAUSES=new Samples(256),GC_CYCLES=new Samples(256);

    private static final ArrayDeque<JsonObject> BATCH_HISTORY=new ArrayDeque<>();private static volatile RecordingStream jfr;private static volatile String jfrStatus="NOT_MEASURED_RECORDING_OFF";

    private static final ThreadMXBean THREADS=ManagementFactory.getThreadMXBean();

    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),task->{Thread thread=new Thread(task,"Dreamwalker-client-diagnostics-io");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());

    private static final AtomicLong IO_REJECTED=new AtomicLong(),IO_CPU=new AtomicLong();

    private static final Semaphore JFR_SLOTS=new Semaphore(2);private static final ArrayDeque<RecordingStream> JFR_CLOSES=new ArrayDeque<>();

    private static final LinkedHashSet<UUID> EXPORT_SUBMITTED=new LinkedHashSet<>();

    private static volatile UUID session;private static volatile long deadline;private static volatile String filter="all";

    private static volatile JsonObject parsedFilter=new JsonObject();

    private static JsonObject graphics;

    private static Samples reviewFrames,reviewCpu;private static long reviewPreviousFrame,reviewPreviousCpu,reviewStarted;private static String reviewLabel;

    private static boolean initialized;private static long previousFrame,lastBatch,lastJournal,operation,droppedEvents,droppedErrors,droppedTimings,timerSampleCounter,wireTrims,flushOverheadNs;

    private static long gcCount,gcMillis;private static volatile JsonObject metadata;private static volatile boolean metadataPending;private static volatile boolean journalWriting;

    private static final class Fault{final JsonObject data;long repeats=1,written;Fault(JsonObject data){this.data=data;}}

    private static final class Samples{final long[] values;int count;long observations,total;Samples(int capacity){values=new long[capacity];}void add(long value){if(value<0)return;observations++;total+=value;if(count<values.length)values[count++]=value;}JsonObject json(){JsonObject row=new JsonObject();row.addProperty("measurements",observations);row.addProperty("totalNs",total);row.addProperty("retainedSamples",count);row.addProperty("droppedSamples",observations-count);row.addProperty("percentilePopulation","bounded retained samples in the labelled reporting/comparison window; truncation explicit");if(count>0){long[] copy=Arrays.copyOf(values,count);Arrays.sort(copy);row.addProperty("averageNs",total/(double)observations);row.addProperty("medianNs",copy[(count-1)/2]);row.addProperty("p95Ns",copy[(int)Math.ceil(count*.95)-1]);row.addProperty("p99Ns",copy[(int)Math.ceil(count*.99)-1]);}else row.addProperty("status","NOT_MEASURED");return row;}void clear(){count=0;observations=0;total=0;}}

    private DwClientDiagnostics(){}

    public static void initialize(){

        if(initialized)return;initialized=true;

        ClientPlayNetworking.registerGlobalReceiver(SESSION,(client,handler,buf,response)->{try{int bytes=buf.readableBytes();if(bytes>3200)throw new IllegalArgumentException("Session payload exceeds bounded budget");UUID id=buf.readUuid();boolean active=buf.readBoolean();int seconds=buf.readInt();String selection=buf.readString(1024);if(buf.isReadable())throw new IllegalArgumentException("Trailing session bytes");client.execute(()->{try{setSession(id,active,seconds,selection);network("client","receive",SESSION.toString(),bytes);}catch(RuntimeException failure){error("client",null,null,"session","Invalid bounded diagnostic activation",failure);}});}catch(RuntimeException failure){error("client",null,null,"session","Malformed bounded diagnostic session packet",failure);}});

        ClientPlayNetworking.registerGlobalReceiver(ACCEPT,(client,handler,buf,response)->{try{int bytes=buf.readableBytes();UUID id=buf.readUuid();String instance=buf.readString(160),raw=buf.readString(8192);if(buf.isReadable()||raw.getBytes(StandardCharsets.UTF_8).length>24000)return;client.execute(()->{if(!enabled()||!id.equals(session))return;try{JsonObject row=JsonParser.parseString(raw).getAsJsonObject();if(!id.toString().equals(row.get("sessionId").getAsString())||!instance.equals(row.get("instanceId").getAsString()))throw new IllegalArgumentException("Acknowledgement identity mismatch");synchronized(DwClientDiagnostics.class){if(ACCEPTED.size()>=512)ACCEPTED.remove(ACCEPTED.keySet().iterator().next());ACCEPTED.put(instance,row);MODELS.clear();}network("client","receive",ACCEPT.toString(),bytes);}catch(RuntimeException failure){error("client",instance,null,"placement_acknowledgement","Invalid bounded server placement acknowledgement",failure);}});}catch(RuntimeException failure){error("client",null,null,"placement_acknowledgement","Malformed placement acknowledgement packet",failure);}});

        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->setSession(session,false,0,"all"));

        ClientTickEvents.END_CLIENT_TICK.register(client->tick(client));

        ModelLoadingPlugin.register(plugin->plugin.modifyModelAfterBake().register((model,context)->{

            if(context.id().getNamespace().equals("bloodborne_dw")&&sourceModel(context.id())&&model!=null&&!model.isBuiltin())inspectModel("resource",null,null,context.id(),model,"resource-bake");return model;

        }));

        for(var collector:ManagementFactory.getGarbageCollectorMXBeans())if(collector instanceof NotificationEmitter emitter)emitter.addNotificationListener((event,handback)->{if(enabled()&&event.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)){try{long ns=GarbageCollectionNotificationInfo.from((CompositeData)event.getUserData()).getGcInfo().getDuration()*1_000_000L;synchronized(DwClientDiagnostics.class){GC_CYCLES.add(ns);}}catch(RuntimeException ignored){}}},null,null);

    }

    public static boolean enabled(){return session!=null&&System.nanoTime()<deadline;}

    public static synchronized void setSession(UUID id,boolean active,int seconds,String selected){

        if(!active){UUID previous=session;deadline=0;session=null;previousFrame=0;stopJfr();if(previous!=null)exportClient(previous);return;}

        if(id==null||seconds<1||seconds>600){error("client",null,null,"session","Invalid bounded diagnostic activation",null);return;}

        if(session!=null)setSession(session,false,0,filter);

        session=id;deadline=System.nanoTime()+seconds*1_000_000_000L;filter=selected==null?"all":bounded(selected,1024);previousFrame=0;lastBatch=System.nanoTime();operation=0;

        try{parsedFilter=filter.startsWith("{")?JsonParser.parseString(filter).getAsJsonObject():new JsonObject();validateFilter(parsedFilter);}catch(RuntimeException invalid){parsedFilter=new JsonObject();parsedFilter.addProperty("mode","invalid");error("client",null,null,"session_filter","Invalid server diagnostic filter; retaining server authority",invalid);}

        ACCEPTED.clear();RENDERED.clear();graphics=null;

        EVENTS.clear();TIMINGS.clear();NETWORK.clear();FRAMES.clear();GC_PAUSES.clear();GC_CYCLES.clear();BATCH_HISTORY.clear();droppedEvents=droppedTimings=wireTrims=0;MODELS.clear();gcCount=gcCounts();gcMillis=gcTimes();
        SESSION_TIMINGS.clear();LOCAL_TIMING_WINDOWS.clear();droppedLocalTimingWindows=droppedSessionTimingSections=flushOverheadNs=0;

        record("client",null,null,"session_start",Map.of(),Map.of("seconds",seconds,"filter",filter),"OK","Server-authorized UUID session");prepareMetadata();startJfr(id);

    }

    public static boolean shouldSample(String typeId,BlockPos root,String section){return shouldSample(typeId,null,root,section);}

    public static boolean shouldSample(String typeId,String instanceId,BlockPos root,String section){if(!enabled()||!matches(typeId,instanceId,root))return false;return (++timerSampleCounter&15)==0;}

    /** Only an actual active-session server placement acknowledgement; null means item/type chain was not observed. */

    public static synchronized JsonObject acceptedPlacement(String instanceId){if(!enabled())return null;JsonObject row=ACCEPTED.get(instanceId);return row==null?null:row.deepCopy();}

    public static JsonObject acceptedPlacement(UUID instanceId){return acceptedPlacement(instanceId==null?null:instanceId.toString());}

    public static synchronized JsonObject renderedPlacement(UUID instanceId){JsonObject row=RENDERED.get(instanceId.toString());return row==null?null:row.deepCopy();}

    public static synchronized void measured(String section,long elapsedNs){measured("client-process",null,section,elapsedNs);}

    public static synchronized void measured(String typeId,BlockPos root,String section,long elapsedNs){

        if(!enabled()||elapsedNs<0)return;String key=bounded(typeId,100)+"|"+(root==null?"process":(root.getX()>>4)+","+(root.getZ()>>4))+"|"+bounded(section,80);

        Samples values=TIMINGS.get(key);if(values==null){if(TIMINGS.size()>=MAX_TIMINGS){droppedTimings++;return;}TIMINGS.put(key,values=new Samples(512));}values.add(elapsedNs);
        Samples retained=SESSION_TIMINGS.get(key);if(retained==null){if(SESSION_TIMINGS.size()>=MAX_TIMINGS){droppedSessionTimingSections++;return;}SESSION_TIMINGS.put(key,retained=new Samples(512));}retained.add(elapsedNs);

    }

    public static long threadCpuNs(){try{return THREADS.isCurrentThreadCpuTimeSupported()?THREADS.getCurrentThreadCpuTime():-1;}catch(RuntimeException ignored){return -1;}}

    public static synchronized void frame(){if(!enabled()&&reviewFrames==null)return;long now=System.nanoTime();if(reviewFrames!=null){if(reviewPreviousFrame!=0)reviewFrames.add(now-reviewPreviousFrame);reviewPreviousFrame=now;long cpu=threadCpuNs();if(cpu>=0&&reviewPreviousCpu>=0)reviewCpu.add(cpu-reviewPreviousCpu);reviewPreviousCpu=cpu;}if(enabled()){if(previousFrame!=0)FRAMES.add(now-previousFrame);previousFrame=now;}}

    /** Explicit QA observer for matched real rendered off/on windows. Inactive by default; not an FPS attribution to objects. */

    public static synchronized void beginFrameComparisonWindow(String label){reviewFrames=new Samples(4096);reviewCpu=new Samples(4096);reviewPreviousFrame=0;reviewPreviousCpu=-1;reviewStarted=System.nanoTime();reviewLabel=bounded(label,48);}

    public static synchronized JsonObject endFrameComparisonWindow(){JsonObject row=reviewFrames==null?new JsonObject():reviewFrames.json();if(reviewFrames!=null){JsonArray raw=new JsonArray();for(int i=0;i<reviewFrames.count;i++)raw.add(reviewFrames.values[i]);row.add("rawPresentationIntervalsNs",raw);row.add("renderThreadCpuIntervals",reviewCpu.json());JsonArray cpuRaw=new JsonArray();for(int i=0;i<reviewCpu.count;i++)cpuRaw.add(reviewCpu.values[i]);row.add("rawRenderThreadCpuIntervalsNs",cpuRaw);}row.addProperty("label",reviewLabel);row.addProperty("windowNs",System.nanoTime()-reviewStarted);row.addProperty("diagnosticsEnabledAtEnd",enabled());if(reviewFrames!=null&&reviewFrames.total>0)row.addProperty("fpsAverage",1e9*reviewFrames.observations/reviewFrames.total);if(reviewFrames!=null)row.add("instantaneousFpsDistribution",fpsDistribution(reviewFrames));row.addProperty("scope","Actual presentation intervals under matched camera/settings; observer constant across off/on, not exact causal cost or GPU timing");reviewFrames=null;reviewPreviousFrame=0;return row;}

    public static synchronized void network(String side,String direction,String channel,int bytes){if(!enabled()||bytes<0)return;String key=bounded(side,16)+"|"+bounded(direction,16)+"|"+bounded(channel,100);long[] value=NETWORK.get(key);if(value==null){if(NETWORK.size()>=64)return;NETWORK.put(key,value=new long[2]);}value[0]++;value[1]+=bytes;}

    public static synchronized void record(String typeId,String instanceId,BlockPos root,String action,Map<String,?> before,Map<String,?> after,String result,String reason){

        if(!enabled()||!matches(typeId,instanceId,root))return;JsonObject row=base(typeId,instanceId,root);row.addProperty("action",bounded(action,80));row.add("before",states(before));row.add("after",states(after));row.addProperty("result",bounded(result,80));row.addProperty("reason",bounded(reason,512));enqueue(row);

    }

    public static synchronized void error(String typeId,String instanceId,BlockPos root,String category,String reason,Throwable failure){

        String key=bounded(typeId,100)+"|"+bounded(instanceId,100)+"|"+bounded(category,80)+"|"+bounded(reason,512);Fault existing=ERRORS.get(key);if(existing!=null){existing.repeats++;return;}if(ERRORS.size()>=MAX_ERRORS){droppedErrors++;return;}

        JsonObject row=base(typeId,instanceId,root);row.addProperty("category",bounded(category,80));row.addProperty("reason",bounded(reason,512));if(failure!=null){row.addProperty("exception",bounded(failure.toString(),512));JsonArray trace=new JsonArray();for(int i=0;i<Math.min(8,failure.getStackTrace().length);i++)trace.add(failure.getStackTrace()[i].toString());row.add("stack",trace);}ERRORS.put(key,new Fault(row));LOG.warn("Dreamwalker [{}] {}: {} (repeats retained)",bounded(typeId,100),category,reason);

    }

    public static void visualModel(String itemId,BlockState acceptedState,String instanceId,BlockPos root,Identifier modelId,BakedModel actual,String purpose){

        if(!enabled())return;DebugCatalogue.Entry entry=acceptedState==null?null:DebugCatalogue.entry(acceptedState);String type=entry==null?"UNASSIGNED":entry.temporaryId();

        if(instanceId==null&&root!=null)instanceId="root:"+root.getX()+","+root.getY()+","+root.getZ();

        if(!matches(type,instanceId,root))return;String key=String.valueOf(instanceId)+"|"+String.valueOf(root)+"|"+acceptedState+"|"+modelId+"|"+purpose;

        synchronized(DwClientDiagnostics.class){if(MODELS.containsKey(key))return;if(MODELS.size()>=MAX_MODELS)MODELS.remove(MODELS.keySet().iterator().next());MODELS.put(key,true);}

        inspectModel(type,instanceId,root,modelId,actual,purpose);

        synchronized(DwClientDiagnostics.class){JsonObject row=base(type,instanceId,root);row.addProperty("action","visual_model_chain");JsonObject ack=acceptedPlacement(instanceId);if(ack==null)row.addProperty("serverPlacementAcknowledgement","ITEM_NOT_OBSERVED_IN_ACTIVE_SESSION");else row.add("serverPlacementAcknowledgement",ack);row.addProperty("itemInMainHandAtObservation",itemId==null?"NOT_MEASURED":itemId);row.addProperty("clientRegistry",acceptedState==null?"NOT_MEASURED":Registries.BLOCK.getId(acceptedState.getBlock()).toString());row.addProperty("clientState",String.valueOf(acceptedState));row.addProperty("selectedClientModel",modelId.toString());row.addProperty("purpose",purpose);row.addProperty("result","ACTUAL_RENDER_MODEL_SELECTED");row.addProperty("reason","Render observation is separate from server acceptance and later handheld item; visual acceptance NOT_RUN");if(ack!=null&&purpose.equals("world-root-render")){if(RENDERED.size()>=512)RENDERED.remove(RENDERED.keySet().iterator().next());RENDERED.put(instanceId,row.deepCopy());}enqueue(row);}

    }

    private static void inspectModel(String type,String instance,BlockPos root,Identifier id,BakedModel actual,String purpose){

        try{var client=MinecraftClient.getInstance();if(actual==null||actual==client.getBakedModelManager().getMissingModel()){error(type,instance,root,"model_fallback","Missing/fallback baked model "+id+" at "+purpose,null);return;}

            if(actual.isBuiltin())return;boolean missing=actual.getParticleSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId());int quads=0;

            for(Direction face:Direction.values())for(var quad:actual.getQuads(null,face,Random.create(0))){quads++;missing|=quad.getSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId());}

            for(var quad:actual.getQuads(null,null,Random.create(0))){quads++;missing|=quad.getSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId());}

            if(missing)error(type,instance,root,"texture_fallback","Missing sprite in actual baked model "+id+" at "+purpose,null);

            if(quads==0&&!id.getPath().contains("placeholder")&&!id.getPath().startsWith("item/"))error(type,instance,root,"model_empty","Zero geometry in actual model "+id+" at "+purpose,null);

        }catch(RuntimeException failure){error(type,instance,root,"model_inspection","Cannot inspect baked model "+id,failure);}

    }

    public static synchronized JsonObject snapshot(){JsonObject result=new JsonObject();result.addProperty("enabled",enabled());result.addProperty("session",session==null?"NONE":session.toString());result.addProperty("eventsBuffered",EVENTS.size());result.addProperty("errorKeys",ERRORS.size());result.addProperty("frameSamples",FRAMES.count);result.addProperty("modelKeys",MODELS.size());result.addProperty("acceptedPlacementKeys",ACCEPTED.size());result.addProperty("droppedEvents",droppedEvents);result.addProperty("droppedErrors",droppedErrors);result.addProperty("flushOverheadNs",flushOverheadNs);result.addProperty("localTimingWindowLimit",64);result.addProperty("localTimingWindowsRetained",LOCAL_TIMING_WINDOWS.size());result.addProperty("localTimingWindowsDropped",droppedLocalTimingWindows);result.addProperty("localTimingSectionsRetained",SESSION_TIMINGS.size());result.addProperty("sessionTimingSectionObservationsDropped",droppedSessionTimingSections);result.addProperty("windowTimingSectionObservationsDroppedCumulative",droppedTimings);result.addProperty("wireBudgetRowsOmittedCumulative",wireTrims);result.addProperty("ioQueueSize",IO.getQueue().size());result.addProperty("ioQueueCapacity",4);result.addProperty("ioActiveTasks",IO.getActiveCount());result.addProperty("ioRejectedTasks",IO_REJECTED.get());result.addProperty("ioThreadCpuNsProcessCumulative",IO_CPU.get());return result;}

    public static synchronized JsonArray recentBatches(){JsonArray rows=new JsonArray();BATCH_HISTORY.forEach(row->rows.add(row.deepCopy()));return rows;}

    public static synchronized JsonArray recentEvents(){JsonArray rows=new JsonArray();EVENTS.forEach(row->rows.add(row.deepCopy()));return rows;}

    private static JsonObject graphics(MinecraftClient client){

        if(graphics!=null)return graphics;JsonObject row=new JsonObject();try{row.addProperty("graphicsMode",client.options.getGraphicsMode().getValue().name());row.addProperty("viewDistance",client.options.getViewDistance().getValue());row.addProperty("simulationDistance",client.options.getSimulationDistance().getValue());row.addProperty("entityDistanceScaling",client.options.getEntityDistanceScaling().getValue());row.addProperty("maxFps",client.options.getMaxFps().getValue());row.addProperty("vsync",client.options.getEnableVsync().getValue());row.addProperty("clouds",client.options.getCloudRenderMode().getValue().name());row.addProperty("perspective",client.options.getPerspective().name());if(com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()){row.addProperty("openGlVendor",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));row.addProperty("openGlRenderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));row.addProperty("openGlVersion",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));}else row.addProperty("openGlRenderer","NOT_MEASURED_NOT_RENDER_THREAD");row.addProperty("gpuDuration","NOT_MEASURED");row.add("optionalIris",irisSnapshot());}catch(RuntimeException failure){row.addProperty("status","NOT_MEASURED_GRAPHICS_QUERY_FAILED");error("client-process",null,null,"graphics_metadata","Cannot collect graphics settings",failure);}graphics=row;return row;

    }

    private static void tick(MinecraftClient client){long now=System.nanoTime();if(session!=null&&now>=deadline)setSession(session,false,0,"all");if(now-lastJournal>=1_000_000_000L){lastJournal=now;drainJfrCloses();flushErrors();}if(!enabled()||now-lastBatch<1_000_000_000L)return;long start=System.nanoTime();try{sendBatch(client,now);}catch(RuntimeException failure){error("client",null,null,"telemetry","Client batch rejected locally",failure);}finally{flushOverheadNs+=System.nanoTime()-start;}}

    private static synchronized void sendBatch(MinecraftClient client,long now){

        if(!ClientPlayNetworking.canSend(BATCH))return;long interval=now-lastBatch;JsonObject data=new JsonObject();data.addProperty("schema","dw-client-diagnostics-v1");data.addProperty("session",session.toString());data.addProperty("side","client");data.addProperty("windowNs",interval);data.addProperty("timestampUtc",java.time.Instant.now().toString());data.addProperty("frameClock","RenderSystem.flipFrame wall interval, includes presentation/wait; GPU duration NOT_MEASURED");JsonObject frames=FRAMES.json();if(FRAMES.observations>0)frames.addProperty("fpsAverage",1_000_000_000D*FRAMES.observations/FRAMES.total);frames.add("instantaneousFpsDistribution",fpsDistribution(FRAMES));data.add("frames",frames);data.add("gcStopTheWorldPauseDurations",GC_PAUSES.json());data.addProperty("gcStopTheWorldSource",jfrStatus);data.add("gcCycleNotificationDurations",GC_CYCLES.json());data.addProperty("gcCycleScope","GC cycle elapsed, may include concurrent phases; not STW pause duration");

        Runtime runtime=Runtime.getRuntime();JsonObject memory=new JsonObject();memory.addProperty("jvmPid",ManagementFactory.getRuntimeMXBean().getPid());memory.addProperty("scope",client.getServer()==null?"client JVM process":"shared integrated client/server JVM process; do not add to server value");memory.addProperty("heapUsedBytes",runtime.totalMemory()-runtime.freeMemory());memory.addProperty("heapCommittedBytes",runtime.totalMemory());memory.addProperty("heapMaxBytes",runtime.maxMemory());memory.addProperty("gcCountDelta",gcCounts()-gcCount);memory.addProperty("gcAggregateMillisDelta",gcTimes()-gcMillis);memory.addProperty("processResidentBytes","NOT_MEASURED");data.add("memory",memory);

        data.addProperty("gpuTiming","NOT_MEASURED; CPU render elapsed does not measure GPU");data.addProperty("allModNetworkBytes","NOT_MEASURED; only explicitly instrumented channels");data.addProperty("loadedClientChunkCount",client.world==null?0:client.world.getChunkManager().getLoadedChunkCount());int entityCount=0;if(client.world!=null)for(var entity:client.world.getEntities()){if(++entityCount>20000)break;}data.addProperty("clientEntitiesLoaded",Math.min(entityCount,20000));data.addProperty("clientEntityCountTruncated",entityCount>20000);data.addProperty("clientChunkHelpersLoaded","NOT_MEASURED_NO_FORCING_CHUNK_SCAN");data.add("wallProvider",GSON.toJsonTree(PrototypeWallModels.snapshot()));JsonObject caches=new JsonObject();caches.addProperty("compositeShapeCacheEntries",CompositeShapes.cacheSize());caches.addProperty("wallJunctionCacheEntries",PrototypeWallModels.junctionCacheSize());data.add("geometryCacheEntries",caches);data.add("graphics",graphics(client));data.add("buffers",snapshot());

        JsonArray timings=new JsonArray();for(var entry:TIMINGS.entrySet()){JsonObject row=entry.getValue().json();row.addProperty("typeChunkSection",entry.getKey());row.addProperty("sampling","1/16 caller-selected invocations; elapsed wall ns unless section explicitly threadCPU");timings.add(row);}data.add("timings",timings);JsonObject wire=new JsonObject();for(var entry:NETWORK.entrySet())wire.add(entry.getKey(),GSON.toJsonTree(entry.getValue()));data.add("networkInstrumentedPacketCountAndPayloadBytes",wire);

        JsonArray events=new JsonArray();EVENTS.forEach(events::add);data.add("events",events);JsonArray renderAcks=new JsonArray();int ackStart=Math.max(0,RENDERED.size()-8),ackIndex=0;for(var row:RENDERED.values())if(ackIndex++>=ackStart)renderAcks.add(row);data.add("placementRenderAcknowledgements",renderAcks);JsonArray errors=new JsonArray();for(var fault:ERRORS.values()){JsonObject row=fault.data.deepCopy();row.addProperty("repeatCount",fault.repeats);errors.add(row);}data.add("errors",errors);

        if(metadata!=null)data.add("runtimeMetadata",metadata);else data.addProperty("runtimeMetadata","PENDING_ASYNC_READ");data.addProperty("diagnosticFlushOverheadNsCumulative",flushOverheadNs);
        BatchPacket prepared=budgetBatch(data,wireTrims);JsonObject wireData=prepared.data();
        var packet=PacketByteBufs.create();packet.writeUuid(session);packet.writeString(prepared.json(),16384);int bytes=packet.readableBytes();ClientPlayNetworking.send(BATCH,packet);
        wireTrims=wireData.get("wireBudgetRowsTrimmed").getAsLong();
        if(BATCH_HISTORY.size()>=256)BATCH_HISTORY.removeFirst();BATCH_HISTORY.add(wireData);
        JsonObject localWindow=new JsonObject();localWindow.addProperty("session",session.toString());localWindow.add("timestampUtc",data.get("timestampUtc"));localWindow.addProperty("windowNs",interval);localWindow.add("timings",timings);localWindow.add("wireBudget",wireData.get("wireBudget"));
        if(LOCAL_TIMING_WINDOWS.size()>=64){LOCAL_TIMING_WINDOWS.removeFirst();droppedLocalTimingWindows++;}LOCAL_TIMING_WINDOWS.add(localWindow);
        lastBatch=now;FRAMES.clear();GC_PAUSES.clear();GC_CYCLES.clear();EVENTS.clear();TIMINGS.clear();NETWORK.clear();network("client","send",BATCH.toString(),bytes);gcCount=gcCounts();gcMillis=gcTimes();

    }

    /** Pure bounded JSON budgeting; scalar JSON is serialized once, final JSON once. */
    private record BatchPacket(JsonObject data,String json){}
    private static final class RowBudget{
        int chars,bytes;long omitted;
        RowBudget(int chars,int bytes){this.chars=chars;this.bytes=bytes;}
        JsonArray select(JsonArray rows,int sectionChars,boolean newestFirst){
            int allowance=Math.min(Math.max(0,sectionChars),chars);ArrayDeque<JsonElement> kept=new ArrayDeque<>();
            for(int step=0;step<rows.size();step++){
                JsonElement row=rows.get(newestFirst?rows.size()-1-step:step);
                if(allowance<=0||chars<=0||bytes<=0){omitted+=rows.size()-step;break;}
                String encoded=GSON.toJson(row);int rowChars=encoded.length()+1,rowBytes=encoded.getBytes(StandardCharsets.UTF_8).length+1;
                if(rowChars>allowance||rowChars>chars||rowBytes>bytes){omitted++;continue;}
                if(newestFirst)kept.addFirst(row);else kept.addLast(row);
                allowance-=rowChars;chars-=rowChars;bytes-=rowBytes;
            }
            JsonArray selected=new JsonArray();kept.forEach(selected::add);return selected;
        }
    }
    private static JsonObject compactRuntime(JsonElement full){
        JsonObject compact=new JsonObject();
        compact.addProperty("fullRuntimeMetadata","LOCAL_CLIENT_ZIP/runtime.json; mod list is not repeated in network batches");
        if(full!=null&&full.isJsonObject()){
            JsonObject source=full.getAsJsonObject();
            for(String key:List.of("productionArtifactSha256","modVersion","minecraftVersion","loaderVersion","javaVersion","processId","processScope"))
                if(source.has(key)&&source.get(key).isJsonPrimitive())compact.add(key,source.get(key));
        }else compact.addProperty("status","PENDING_ASYNC_READ");
        return compact;
    }
    private static void rowCounts(JsonObject counts,String section,JsonArray available,JsonArray retained){
        JsonObject row=new JsonObject();row.addProperty("availableRows",available.size());row.addProperty("retainedRows",retained.size());row.addProperty("omittedRows",available.size()-retained.size());
        if(section.equals("timings")){long availableSamples=0,retainedSamples=0;for(JsonElement item:available)availableSamples+=item.getAsJsonObject().get("measurements").getAsLong();for(JsonElement item:retained)retainedSamples+=item.getAsJsonObject().get("measurements").getAsLong();row.addProperty("availableSampledInvocations",availableSamples);row.addProperty("retainedSampledInvocations",retainedSamples);row.addProperty("omittedSampledInvocations",availableSamples-retainedSamples);}
        counts.add(section,row);
    }
    private static BatchPacket budgetBatch(JsonObject source,long previouslyOmitted){
        JsonObject wire=new JsonObject();Set<String> sections=Set.of("timings","events","errors","placementRenderAcknowledgements");
        for(var field:source.entrySet())if(!sections.contains(field.getKey())&&!field.getKey().equals("runtimeMetadata"))wire.add(field.getKey(),field.getValue());
        wire.add("runtimeMetadata",compactRuntime(source.get("runtimeMetadata")));
        for(String section:sections)wire.add(section,new JsonArray());
        JsonObject counts=new JsonObject();counts.addProperty("policy","Reserved ACK/timing budget; remaining newest events/errors. Full bounded timing windows/session samples stay in local ZIP.");counts.addProperty("wholeJsonSerializations",2);wire.add("wireBudget",counts);
        // The reserve covers final counters/section names before they are populated.
        String scalarJson=GSON.toJson(wire);RowBudget budget=new RowBudget(16384-scalarJson.length()-1400,60000-scalarJson.getBytes(StandardCharsets.UTF_8).length-1400);
        if(budget.chars<0||budget.bytes<0)throw new IllegalArgumentException("Bounded scalar telemetry exceeds packet budget");
        JsonArray acknowledgements=source.getAsJsonArray("placementRenderAcknowledgements"),timings=source.getAsJsonArray("timings"),events=source.getAsJsonArray("events"),errors=source.getAsJsonArray("errors");
        JsonArray keptAcks=budget.select(acknowledgements,Math.min(4000,budget.chars/3),true);wire.add("placementRenderAcknowledgements",keptAcks);rowCounts(counts,"placementRenderAcknowledgements",acknowledgements,keptAcks);
        JsonArray keptTimings=budget.select(timings,Math.max(0,budget.chars-1000),false);wire.add("timings",keptTimings);rowCounts(counts,"timings",timings,keptTimings);
        JsonArray keptEvents=budget.select(events,Math.max(0,budget.chars-500),true);wire.add("events",keptEvents);rowCounts(counts,"events",events,keptEvents);
        JsonArray keptErrors=budget.select(errors,budget.chars,true);wire.add("errors",keptErrors);rowCounts(counts,"errors",errors,keptErrors);
        wire.addProperty("wireBudgetRowsTrimmed",previouslyOmitted+budget.omitted);
        String json=GSON.toJson(wire);if(wire.size()>32||json.length()>16384||json.getBytes(StandardCharsets.UTF_8).length>60000)throw new IllegalArgumentException("Bounded client telemetry exceeds packet budget");
        return new BatchPacket(wire,json);
    }

    private static JsonObject localTimingExport(){
        JsonObject retained=new JsonObject();retained.addProperty("schema","dw-local-client-timing-populations-v1");retained.addProperty("session",session==null?"STOPPED_SEE_SUMMARY_SESSION":session.toString());
        retained.addProperty("scope","Bounded sampled elapsed invocation populations, including rows omitted from wire; nested intervals are not independent CPU costs.");
        retained.addProperty("windowLimit",64);retained.addProperty("windowsRetained",LOCAL_TIMING_WINDOWS.size());retained.addProperty("windowsDropped",droppedLocalTimingWindows);
        JsonArray windows=new JsonArray();LOCAL_TIMING_WINDOWS.forEach(windows::add);retained.add("windows",windows);
        retained.addProperty("sectionLimit",MAX_TIMINGS);retained.addProperty("sampleLimitPerSection",512);retained.addProperty("sectionObservationsDropped",droppedSessionTimingSections);retained.addProperty("windowSectionObservationsDroppedCumulative",droppedTimings);
        JsonArray sections=new JsonArray();for(var entry:SESSION_TIMINGS.entrySet()){JsonObject row=entry.getValue().json();row.addProperty("typeChunkSection",entry.getKey());JsonArray samples=new JsonArray();for(int i=0;i<entry.getValue().count;i++)samples.add(entry.getValue().values[i]);row.add("rawRetainedSampleNs",samples);sections.add(row);}retained.add("sessionSampledTimings",sections);
        return retained;
    }

    private static JsonObject fpsDistribution(Samples intervals){JsonObject row=new JsonObject();row.addProperty("population","Empirical reciprocal of retained positive presentation intervals; nearest-rank quantiles; high FPS corresponds to low frame time");row.addProperty("intervalsObserved",intervals.observations);row.addProperty("retainedIntervals",intervals.count);row.addProperty("droppedIntervals",intervals.observations-intervals.count);double[] fps=new double[intervals.count];int count=0;for(int i=0;i<intervals.count;i++)if(intervals.values[i]>0)fps[count++]=1e9/intervals.values[i];row.addProperty("positiveIntervals",count);if(count==0){row.addProperty("status","NOT_MEASURED");return row;}fps=Arrays.copyOf(fps,count);Arrays.sort(fps);row.addProperty("medianFps",fps[(count-1)/2]);row.addProperty("p95Fps",fps[(int)Math.ceil(count*.95)-1]);row.addProperty("p99Fps",fps[(int)Math.ceil(count*.99)-1]);row.addProperty("timeWeightedFpsAverage",intervals.total>0?1e9*intervals.observations/intervals.total:0);return row;}
    private static JsonObject irisSnapshot(){JsonObject row=new JsonObject();row.addProperty("captureTimeUtc",java.time.Instant.now().toString());row.addProperty("source","Optional public Iris APIs reflected once at first session batch; no Iris dependency or GPU timing");var mod=FabricLoader.getInstance().getModContainer("iris");if(mod.isEmpty()){row.addProperty("status","NOT_MEASURED_IRIS_NOT_LOADED");return row;}row.addProperty("irisVersion",bounded(mod.get().getMetadata().getVersion().getFriendlyString(),128));try{Class<?> iris=Class.forName("net.irisshaders.iris.Iris",false,DwClientDiagnostics.class.getClassLoader());Object config=iris.getMethod("getIrisConfig").invoke(null);row.addProperty("enabled",(boolean)config.getClass().getMethod("areShadersEnabled").invoke(config));row.addProperty("packName",bounded(String.valueOf(iris.getMethod("getCurrentPackName").invoke(null)),256));row.addProperty("fallback",(boolean)iris.getMethod("isFallback").invoke(null));Object manager=iris.getMethod("getPipelineManager").invoke(null);Object pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);row.addProperty("pipelineClass",pipeline==null?"NONE":pipeline.getClass().getName());row.addProperty("status","OBSERVED_IRIS_PUBLIC_API");}catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){row.addProperty("status","NOT_MEASURED_IRIS_PUBLIC_API_UNAVAILABLE");row.addProperty("reason",bounded(unavailable.toString(),512));}return row;}
    private static void prepareMetadata(){if(metadata!=null||metadataPending)return;metadataPending=true;if(!ioSubmit(()->{JsonObject row=new JsonObject();try{var loader=FabricLoader.getInstance();row.addProperty("minecraftVersion",SharedConstants.getGameVersion().getName());row.addProperty("javaVersion",System.getProperty("java.version"));row.addProperty("processId",ProcessHandle.current().pid());row.addProperty("processScope","JVM_PROCESS_SHARED_IF_SAME_PID");row.addProperty("loaderVersion",loader.getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString());row.addProperty("os",System.getProperty("os.name"));JsonArray mods=new JsonArray();loader.getAllMods().stream().sorted(Comparator.comparing(mod->mod.getMetadata().getId())).limit(512).forEach(mod->{JsonObject m=new JsonObject();m.addProperty("id",bounded(mod.getMetadata().getId(),128));m.addProperty("version",bounded(mod.getMetadata().getVersion().getFriendlyString(),256));mods.add(m);});row.add("modVersions",mods);row.addProperty("modVersionListTruncated",loader.getAllMods().size()>512);var own=loader.getModContainer("bloodborne_dw").orElseThrow();row.addProperty("modVersion",own.getMetadata().getVersion().getFriendlyString());var paths=own.getOrigin().getPaths();if(paths.size()==1&&Files.isRegularFile(paths.get(0)))row.addProperty("productionArtifactSha256",artifactSha(paths.get(0)));else row.addProperty("productionArtifactSha256","NOT_MEASURED_DEV_DIRECTORY");}catch(Exception failure){row.addProperty("metadataError",failure.toString());error("client",null,null,"metadata","Cannot collect runtime artifact metadata",failure);}metadata=row;metadataPending=false;},"metadata"))metadataPending=false;}

    private static synchronized void flushErrors(){

        if(journalWriting)return;Map<Fault,Long> versions=new LinkedHashMap<>();List<JsonObject> pending=new ArrayList<>();

        for(var fault:ERRORS.values())if(fault.written!=fault.repeats){JsonObject row=fault.data.deepCopy();row.addProperty("repeatCount",fault.repeats);pending.add(row);versions.put(fault,fault.repeats);}if(pending.isEmpty())return;

        journalWriting=true;if(!ioSubmit(()->{try{Path dir=FabricLoader.getInstance().getGameDir().resolve("logs");Files.createDirectories(dir);Path current=dir.resolve("dreamwalker-client-errors.jsonl");if(Files.exists(current)&&Files.size(current)>1_048_576){for(int i=2;i>=1;i--){Path old=dir.resolve("dreamwalker-client-errors."+i+".jsonl"),next=dir.resolve("dreamwalker-client-errors."+(i+1)+".jsonl");if(Files.exists(old))Files.move(old,next,StandardCopyOption.REPLACE_EXISTING);}Files.move(current,dir.resolve("dreamwalker-client-errors.1.jsonl"),StandardCopyOption.REPLACE_EXISTING);}StringBuilder lines=new StringBuilder();for(JsonObject row:pending)lines.append(GSON.toJson(row)).append('\n');Files.writeString(current,lines.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);synchronized(DwClientDiagnostics.class){versions.forEach((fault,count)->fault.written=Math.max(fault.written,count));}}catch(Exception failure){LOG.error("Cannot write bounded Dreamwalker client error journal; retry is retained and world continues",failure);}finally{journalWriting=false;}},"error-journal"))journalWriting=false;

    }



    private static boolean sourceModel(Identifier id){String path=id.getPath();return path.startsWith("base/source/")||path.startsWith("alt/prototype_")||path.startsWith("roof/")||path.startsWith("tree_proposal/")||path.startsWith("block/wall/base/")||path.startsWith("block/wall/alt/");}

    private static void startJfr(UUID expected){stopJfr();try{if(!FlightRecorder.isAvailable()){jfrStatus="NOT_MEASURED_JFR_UNAVAILABLE";return;}if(!JFR_SLOTS.tryAcquire()){jfrStatus="NOT_MEASURED_JFR_TWO_STREAM_CLOSE_LIMIT";return;}RecordingStream stream;try{stream=new RecordingStream();}catch(RuntimeException failure){JFR_SLOTS.release();throw failure;}jfr=stream;stream.enable("jdk.GCPhasePause").withThreshold(java.time.Duration.ZERO);stream.onEvent("jdk.GCPhasePause",event->{if(enabled()&&expected.equals(session))synchronized(DwClientDiagnostics.class){GC_PAUSES.add(event.getDuration().toNanos());}});stream.onError(failure->{jfrStatus="NOT_MEASURED_JFR_STREAM_ERROR";error("client-process",null,null,"jfr_gc","Cannot record actual GC pause phases",failure);});jfr=stream;jfrStatus="JFR_jdk.GCPhasePause_PROCESS_SCOPE";stream.startAsync();}catch(RuntimeException failure){jfrStatus="NOT_MEASURED_JFR_START_FAILURE";error("client-process",null,null,"jfr_gc","Cannot start GC phase observation",failure);stopJfr();}}

    private static synchronized void stopJfr(){RecordingStream previous=jfr;jfr=null;if(previous!=null)JFR_CLOSES.addLast(previous);drainJfrCloses();}

    private static synchronized void drainJfrCloses(){while(!JFR_CLOSES.isEmpty()){RecordingStream stream=JFR_CLOSES.peekFirst();if(!ioSubmit(()->{try{stream.close();}finally{JFR_SLOTS.release();}},"jfr-close"))return;JFR_CLOSES.removeFirst();}}

    private static boolean ioSubmit(Runnable task,String section){try{IO.execute(()->{long before=threadCpuNs();try{task.run();}finally{long after=threadCpuNs();if(before>=0&&after>=0)IO_CPU.addAndGet(after-before);}});return true;}catch(RejectedExecutionException rejected){IO_REJECTED.incrementAndGet();error("client-process",null,null,"report_queue","Bounded client diagnostics IO queue full: "+section+"; world continues",null);return false;}}

    private static void exportClient(UUID id){if(EXPORT_SUBMITTED.contains(id))return;JsonObject timingDetails=localTimingExport();timingDetails.addProperty("session",id.toString());JsonArray renderDetails=new JsonArray();RENDERED.values().forEach(row->renderDetails.add(row.deepCopy()));JsonObject metadataCopy=metadata==null?new JsonObject():metadata.deepCopy();JsonObject graphicsCopy=graphics==null?new JsonObject():graphics.deepCopy();JsonArray batches=new JsonArray();BATCH_HISTORY.forEach(batches::add);JsonArray faults=new JsonArray();for(var fault:ERRORS.values()){JsonObject row=fault.data.deepCopy();row.addProperty("repeatCount",fault.repeats);faults.add(row);}JsonArray tail=new JsonArray();EVENTS.forEach(tail::add);JsonObject summary=snapshot();summary.addProperty("schema","dw-local-client-diagnostics-v1");summary.addProperty("filter",filter);summary.addProperty("session",id.toString());summary.addProperty("side","client");summary.addProperty("manualVisualAcceptance","NOT_RUN");summary.addProperty("gpuDuration","NOT_MEASURED");summary.addProperty("totalBatchesRetained",batches.size());summary.addProperty("localTimingDetails","local-timings.json retains bounded full windows/session samples including wire omissions");summary.addProperty("fullPlacementRenderAcknowledgements","placement-render-acknowledgements.json retains current-session bounded ACK cache");summary.addProperty("serverRecords","SEPARATE_SERVER_EXPORT_SAME_SESSION_UUID");summary.addProperty("processPid",ManagementFactory.getRuntimeMXBean().getPid());JsonObject partial=new JsonObject();JsonObject partialFrames=FRAMES.json();partialFrames.add("instantaneousFpsDistribution",fpsDistribution(FRAMES));partial.add("frames",partialFrames);partial.add("gcStopTheWorldPauseDurations",GC_PAUSES.json());partial.addProperty("gcSource",jfrStatus);JsonArray times=new JsonArray();for(var entry:TIMINGS.entrySet()){JsonObject row=entry.getValue().json();row.addProperty("typeChunkSection",entry.getKey());times.add(row);}partial.add("timings",times);partial.add("network",GSON.toJsonTree(NETWORK));summary.addProperty("lastPartialWindow","Local partial-window.json retained; not transmitted after authoritative expiry");boolean submitted=ioSubmit(()->{try{Path dir=FabricLoader.getInstance().getGameDir().resolve("diagnostics");Files.createDirectories(dir);Path zip=dir.resolve("dreamwalker-client-"+id+".zip");try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip,StandardOpenOption.CREATE_NEW))){zipEntry(out,"summary.json",GSON.toJson(summary));zipEntry(out,"summary.md",readableSummary(id,batches,partial));zipEntry(out,"runtime.json",GSON.toJson(metadataCopy));zipEntry(out,"local-timings.json",GSON.toJson(timingDetails));zipEntry(out,"placement-render-acknowledgements.json",GSON.toJson(renderDetails));zipEntry(out,"partial-window.json",GSON.toJson(partial));zipEntry(out,"graphics.json",GSON.toJson(graphicsCopy));zipEntry(out,"client-batches.json",GSON.toJson(batches));zipEntry(out,"errors.json",GSON.toJson(faults));zipEntry(out,"tail-events.json",GSON.toJson(tail));zipEntry(out,"README.txt","Client diagnostics linked to server by session UUID. Frame intervals include presentation and waiting; CPU timings do not measure GPU. Truncated sample populations/dropped rows are explicit. local-timings.json retains full bounded sampled windows/session populations beyond network budget. Full mod metadata stays in runtime.json; network batches have compact runtime identity. Runtime observation is not visual acceptance.");}try(var files=Files.list(dir)){List<Path> archives=files.filter(path->path.getFileName().toString().matches("dreamwalker-client-[0-9a-f-]{36}\\.zip")).sorted(Comparator.comparingLong((Path path)->{try{return Files.getLastModifiedTime(path).toMillis();}catch(Exception ignored){return 0;}}).reversed()).toList();for(int i=4;i<archives.size();i++)Files.deleteIfExists(archives.get(i));}}catch(Exception failure){error("client",null,null,"report_export","Client ZIP export failed; world operation continues",failure);}},"export");if(submitted){if(EXPORT_SUBMITTED.size()>=8)EXPORT_SUBMITTED.remove(EXPORT_SUBMITTED.iterator().next());EXPORT_SUBMITTED.add(id);}}

    private static String readableSummary(UUID id,JsonArray batches,JsonObject partial){long frames=0,ns=0;for(JsonElement raw:batches){JsonObject values=raw.getAsJsonObject().getAsJsonObject("frames");frames+=values.get("measurements").getAsLong();ns+=values.get("totalNs").getAsLong();}JsonObject tail=partial.getAsJsonObject("frames");frames+=tail.get("measurements").getAsLong();ns+=tail.get("totalNs").getAsLong();return "# Dreamwalker: клиентская диагностика\n\nСеанс: "+id+". Записано интервалов кадра: "+frames+"; среднее: "+(frames==0?"не измерено":String.format(java.util.Locale.ROOT,"%.3f мс",ns/(frames*1e6)))+". Пакетов-окон сохранено: "+batches.size()+".\n\nЭто интервалы настоящего flipFrame, включая VSync, ожидание и планирование. CPU и GPU различаются; GPU-время не измерено. Квантили, число сохранённых/отброшенных выборок и область процесса указаны в JSON. Память и GC общего PID клиента/сервера не суммируются.\n\nСобытия, фактические модельные цепочки, настройки графики и SHA мода находятся в JSON. Серверный ZIP имеет тот же UUID. Полный NBT не записывается. Этот отчёт не означает визуального одобрения.\n";}

    private static String artifactSha(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var stream=Files.newInputStream(path)){byte[] buffer=new byte[65536];int size;while((size=stream.read(buffer))>=0)digest.update(buffer,0,size);}return HexFormat.of().formatHex(digest.digest());}
    private static void validateFilter(JsonObject value){if(!value.has("mode"))return;String mode=value.get("mode").getAsString();if(!Set.of("all","id","object","area").contains(mode))throw new IllegalArgumentException("Invalid filter mode");if(mode.equals("id")&&!value.get("typeId").getAsString().matches("[0-9]{5}"))throw new IllegalArgumentException("Invalid TEMP filter");if(mode.equals("object")&&value.has("instanceId")&&value.get("instanceId").getAsString().length()>160)throw new IllegalArgumentException("Invalid instance filter");if(mode.equals("object")||mode.equals("area")){JsonArray center=value.getAsJsonArray("center");if(center==null||center.size()!=3)throw new IllegalArgumentException("Invalid filter center");for(JsonElement coordinate:center)if(!Double.isFinite(coordinate.getAsDouble()))throw new IllegalArgumentException("Invalid center number");}if(mode.equals("area")){double radius=value.get("radius").getAsDouble();if(!Double.isFinite(radius)||radius<0||radius>128)throw new IllegalArgumentException("Invalid filter radius");}}
    private static void zipEntry(ZipOutputStream out,String name,String body)throws java.io.IOException{out.putNextEntry(new ZipEntry(name));out.write(body.getBytes(StandardCharsets.UTF_8));out.closeEntry();}

    private static JsonObject base(String type,String instance,BlockPos root){JsonObject row=new JsonObject();row.addProperty("timeUtc",java.time.Instant.now().toString());row.addProperty("operationNumber",++operation);row.addProperty("origin","client");row.addProperty("session",session==null?"NONE":session.toString());row.addProperty("typeId",bounded(type,100));row.addProperty("instanceId",bounded(instance,100));row.addProperty("dimension",MinecraftClient.getInstance().world==null?"NOT_MEASURED":MinecraftClient.getInstance().world.getRegistryKey().getValue().toString());if(root!=null){JsonArray pos=new JsonArray();pos.add(root.getX());pos.add(root.getY());pos.add(root.getZ());row.add("root",pos);}return row;}

    private static JsonObject states(Map<String,?> fields){JsonObject row=new JsonObject();if(fields!=null)for(var field:fields.entrySet()){if(row.size()>=24)break;Object value=field.getValue();String key=bounded(field.getKey(),80);if(value instanceof Number n)row.addProperty(key,n);else if(value instanceof Boolean b)row.addProperty(key,b);else row.addProperty(key,bounded(String.valueOf(value),512));}return row;}

    private static void enqueue(JsonObject row){if(EVENTS.size()>=MAX_EVENTS){EVENTS.removeFirst();droppedEvents++;}EVENTS.add(row);}

    private static boolean matches(String type,BlockPos root){return matches(type,null,root);}

    private static boolean matches(String type,String instance,BlockPos root){

        if(type!=null&&(type.equals("client")||type.equals("client-process")))return true;

        JsonObject selection=parsedFilter;if(!selection.has("mode"))return filter.isBlank()||filter.equals("all")||filter.equals(type);

        String mode=selection.get("mode").getAsString();if(!Set.of("all","id","object","area").contains(mode))return false;

        if(selection.has("dimension")&&MinecraftClient.getInstance().world!=null&&!selection.get("dimension").getAsString().equals(MinecraftClient.getInstance().world.getRegistryKey().getValue().toString()))return false;

        if(mode.equals("all"))return true;if(mode.equals("id"))return selection.has("typeId")&&selection.get("typeId").getAsString().equals(type);

        if(mode.equals("object")&&selection.has("instanceId")&&!selection.get("instanceId").getAsString().isEmpty())return selection.get("instanceId").getAsString().equals(instance);

        if(root==null||!selection.has("center"))return false;JsonArray center=selection.getAsJsonArray("center");double dx=root.getX()-center.get(0).getAsDouble(),dy=root.getY()-center.get(1).getAsDouble(),dz=root.getZ()-center.get(2).getAsDouble();double radius=mode.equals("object")?0:selection.has("radius")?selection.get("radius").getAsDouble():0;return dx*dx+dy*dy+dz*dz<=radius*radius;

    }

    private static long gcCounts(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionCount())).sum();}private static long gcTimes(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}

    private static String bounded(String value,int maximum){if(value==null)return "NONE";return value.length()>maximum?value.substring(0,maximum):value;}

}

