package dev.dreamwalker.bloodbornedw.diagnostics;

import com.google.gson.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.*;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.zip.*;
import jdk.jfr.consumer.RecordingStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.*;

/** Optional bounded diagnostics; never an authority for object state or placement. */
public final class DwDiagnostics {
    public static final Identifier SESSION_CHANNEL=new Identifier("bloodborne_dw","diagnostics_session");
    public static final Identifier CLIENT_BATCH_CHANNEL=new Identifier("bloodborne_dw","diagnostics_client_batch");
    public static final Identifier ACCEPT_CHANNEL=new Identifier("bloodborne_dw","diagnostics_accept");
    public static final int CLIENT_MAX_CHARS=16384,CLIENT_MAX_BYTES=60000;
    private static final Logger LOG=LoggerFactory.getLogger("DreamwalkerDiagnostics");
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final Map<MinecraftServer,State> STATES=new ConcurrentHashMap<>();
    private static final Map<String,Function<ServerWorld,Map<String,Object>>> PROVIDERS=new ConcurrentHashMap<>();
    private static final LinkedHashMap<String,Map<String,Object>> ERRORS=new LinkedHashMap<>();
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"Dreamwalker-diagnostics-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final Path OUTPUT=Path.of("dreamwalker-diagnostics").toAbsolutePath().normalize();
    private static volatile boolean initialized; private static volatile Map<String,Object> environment=Map.of("status","METADATA_PENDING");
    private static volatile boolean errorsDirty; private static long lastErrorFlushNs;
    private DwDiagnostics(){}
    public record Filter(String mode,String typeId,String instanceId,String dimension,BlockPos center,double radius){
        public Filter {mode=Objects.requireNonNull(mode);typeId=typeId==null?"":typeId;instanceId=instanceId==null?"":instanceId;dimension=dimension==null?"":dimension;center=center==null?BlockPos.ORIGIN:center.toImmutable();
            if(!Set.of("all","id","object","area").contains(mode)||!typeId.isEmpty()&&!typeId.matches("[0-9]{5}")||instanceId.length()>128||!Double.isFinite(radius)||radius<0||radius>128)throw new IllegalArgumentException("Invalid diagnostic filter");}
        public static Filter all(ServerWorld world){return new Filter("all","","",world.getRegistryKey().getValue().toString(),BlockPos.ORIGIN,0);}
        Map<String,Object> report(){return Map.of("mode",mode,"typeId",typeId,"instanceId",instanceId,"dimension",dimension,"center",List.of(center.getX(),center.getY(),center.getZ()),"radius",radius);}
        boolean accepts(ServerWorld world,String type,String instance,BlockPos root){
            if(world==null||!dimension.equals(world.getRegistryKey().getValue().toString()))return false;
            return switch(mode){case "id"->typeId.equals(type);case "object"->instanceId.isEmpty()?root!=null&&root.equals(center):instanceId.equals(instance);case "area"->root!=null&&root.getSquaredDistance(center)<=radius*radius;default->true;};
        }
    }
    private static final class State {
        volatile Session active; Session last,tickSession; long tickStartNs; long serial;
        LongConsumer reviewTickObserver;
        final DiagnosticBuffers.Ring<Map<String,Object>> snapshots=new DiagnosticBuffers.Ring<>(128);
        final DiagnosticBuffers.Ring<Map<String,Object>> lifecycle=new DiagnosticBuffers.Ring<>(128);
    }
    private static final class Session {
        final UUID id=UUID.randomUUID();final long startNs=System.nanoTime(),deadlineNs;final Instant started=Instant.now();final ServerWorld world;final Filter filter;final int seconds;
        final DiagnosticBuffers.Ring<Map<String,Object>> events=new DiagnosticBuffers.Ring<>(2048),marks=new DiagnosticBuffers.Ring<>(64),metrics=new DiagnosticBuffers.Ring<>(600),clientBatches=new DiagnosticBuffers.Ring<>(256);
        final DiagnosticBuffers.Durations ticks=new DiagnosticBuffers.Durations(4096),gcPauses=new DiagnosticBuffers.Durations(256),overhead=new DiagnosticBuffers.Durations(1024);
        final Map<String,DiagnosticBuffers.Durations> measures=new LinkedHashMap<>();final Map<String,Long> network=new LinkedHashMap<>(),samples=new LinkedHashMap<>();final Map<UUID,Long> clientLast=new HashMap<>();final Set<UUID> participants=new HashSet<>();
        long operation,measureGroupsDropped,networkKeysDropped,sampleKeysDropped,clientRejected,endNs;String stopReason="ACTIVE";volatile String jfrStatus="NOT_MEASURED";RecordingStream jfr;
        Session(ServerWorld world,int seconds,Filter filter){this.world=world;this.seconds=seconds;this.filter=filter;deadlineNs=startNs+seconds*1_000_000_000L;}
        void startJfr(){try{jfr=new RecordingStream();jfr.enable("jdk.GCPhasePause").withoutThreshold();jfr.onEvent("jdk.GCPhasePause",event->gcPauses.add(event.getDuration().toNanos()));jfr.onError(error->jfrStatus="NOT_MEASURED_JFR_ERROR:"+error.getClass().getSimpleName());jfr.startAsync();jfrStatus="JFR_jdk.GCPhasePause_ACTUAL_PAUSE_DURATION";}catch(Throwable error){jfrStatus="NOT_MEASURED_JFR_UNAVAILABLE:"+error.getClass().getSimpleName();if(jfr!=null)try{jfr.close();}catch(Exception ignored){}jfr=null;}}
        void close(){if(jfr!=null){try{jfr.close();}catch(Exception ignored){}jfr=null;}}
    }
    public static synchronized void initialize(){
        if(initialized)return;initialized=true;
        IO.execute(()->environment=environment());
        ServerTickEvents.END_SERVER_TICK.register(DwDiagnostics::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{lifecycle(handler.player.getServerWorld(),"player_join",handler.player.getBlockPos(),Map.of("playerUuid",handler.player.getUuid().toString()));State state=STATES.get(server);Session s=state==null?null:state.active;if(s!=null&&handler.player.getServerWorld()==s.world){sendSession(handler.player,s,true);record(s.world,"","",handler.player.getBlockPos(),"player_join",Map.of(),Map.of("playerUuid",handler.player.getUuid().toString()),"OBSERVED","No chunk or entity reads in join callback");}});
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->record(world,"","",chunk.getPos().getStartPos(),"chunk_load",Map.of(),Map.of("chunkX",chunk.getPos().x,"chunkZ",chunk.getPos().z),"OBSERVED","Coordinates only; object inspection deferred to tick"));
        registerMetrics("rp",world->Map.of("loadedCustomIndex",dev.dreamwalker.bloodbornerp.object.RpObjectIndex.metrics(world),"sourceGeometryCache",dev.dreamwalker.bloodbornerp.object.RpObjectGeometry.cacheMetrics(),"lampProtocol",dev.dreamwalker.bloodbornerp.lamp.LampService.diagnosticMetrics()));
        registerMetrics("mechanisms",dev.dreamwalker.bloodbornedw.link.MechanismLinks::diagnosticsMetrics);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->stop(server,"SERVER_STOPPING"));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{State state=STATES.remove(server);if(state!=null&&state.active!=null)state.active.close();});
        ServerPlayNetworking.registerGlobalReceiver(CLIENT_BATCH_CHANNEL,(server,player,handler,buf,sender)->{
            try{if(buf.readableBytes()>CLIENT_MAX_BYTES+32)return;UUID id=buf.readUuid();String value=buf.readString(CLIENT_MAX_CHARS);if(buf.isReadable()||value.getBytes(StandardCharsets.UTF_8).length>CLIENT_MAX_BYTES)return;server.execute(()->clientBatch(player,id,value));}
            catch(RuntimeException invalid){error(player.getServerWorld(),"","",player.getBlockPos(),"CLIENT_TELEMETRY_INVALID","Malformed bounded diagnostics batch",invalid);}
        });
    }
    public static boolean enabled(ServerWorld world){if(world==null)return false;State state=STATES.get(world.getServer());Session s=state==null?null:state.active;return s!=null&&s.world==world&&System.nanoTime()<s.deadlineNs;}
    public static boolean shouldSample(ServerWorld world,String typeId,BlockPos root,String section){
        return shouldSample(world,typeId,"",root,section);
    }
    public static boolean shouldSample(ServerWorld world,String typeId,String instanceId,BlockPos root,String section){
        if(!enabled(world))return false;Session s=STATES.get(world.getServer()).active;if(!s.filter.accepts(world,typeId,instanceId,root))return false;
        String key=key(typeId,root,section);synchronized(s){if(!s.samples.containsKey(key)&&s.samples.size()>=128){s.sampleKeysDropped++;return false;}long count=s.samples.merge(key,1L,Long::sum);return (count&31)==1;}
    }
    public static void measured(ServerWorld world,String typeId,BlockPos root,String section,long elapsedNs){
        measured(world,typeId,"",root,section,elapsedNs);
    }
    public static void measured(ServerWorld world,String typeId,String instanceId,BlockPos root,String section,long elapsedNs){
        if(!enabled(world)||elapsedNs<0)return;long own=System.nanoTime();Session s=STATES.get(world.getServer()).active;if(!s.filter.accepts(world,typeId,instanceId,root))return;String key=key(typeId,root,section);
        synchronized(s){DiagnosticBuffers.Durations durations=s.measures.get(key);if(durations==null){if(s.measures.size()>=128){s.measureGroupsDropped++;return;}durations=new DiagnosticBuffers.Durations(256);s.measures.put(key,durations);}durations.add(elapsedNs);}s.overhead.add(System.nanoTime()-own);
    }
    private static String key(String type,BlockPos root,String section){return DiagnosticBuffers.shortText(type,32)+"|"+(root==null?"NO_CHUNK":(root.getX()>>4)+","+(root.getZ()>>4))+"|"+DiagnosticBuffers.shortText(section,96);}
    public static void record(ServerWorld world,String typeId,String instanceId,BlockPos root,String action,Map<String,Object> before,Map<String,Object> after,String result,String reason){
        if(!enabled(world))return;Session s=STATES.get(world.getServer()).active;boolean global=action!=null&&(action.startsWith("session_")||action.startsWith("world_save_")||action.equals("player_join")||action.equals("chunk_load"));if(!global&&!s.filter.accepts(world,typeId,instanceId,root))return;long own=System.nanoTime();
        Map<String,Object> row=new LinkedHashMap<>();row.put("sessionId",s.id.toString());row.put("time",Instant.now().toString());row.put("elapsedNs",own-s.startNs);synchronized(s){row.put("operation",++s.operation);}row.put("side","server");row.put("typeId",DiagnosticBuffers.shortText(typeId,32));row.put("instanceId",DiagnosticBuffers.shortText(instanceId,160));row.put("dimension",world.getRegistryKey().getValue().toString());row.put("root",position(root));row.put("action",DiagnosticBuffers.shortText(action,96));row.put("before",DiagnosticBuffers.safeMap(before));row.put("after",DiagnosticBuffers.safeMap(after));row.put("result",DiagnosticBuffers.shortText(result,64));row.put("reason",DiagnosticBuffers.shortText(reason,512));s.events.add(row);
        row.put("scope",global?"GLOBAL_LIFECYCLE":"MATCHED_OBJECT_FILTER");
        if("place".equals(action)&&("COMMITTED".equals(result)||"SUCCESS".equals(result)))accepted(s,row);
        s.overhead.add(System.nanoTime()-own);
    }
    public static void error(ServerWorld world,String typeId,String instanceId,BlockPos root,String category,String reason,Throwable failure){
        String dimension=world==null?"UNKNOWN_BOOTSTRAP":world.getRegistryKey().getValue().toString();String text=DiagnosticBuffers.shortText(reason,512);String key=dimension+"|"+typeId+"|"+instanceId+"|"+position(root)+"|"+category+"|"+text;boolean first;
        synchronized(ERRORS){Map<String,Object> row=ERRORS.get(key);first=row==null;if(first){if(ERRORS.size()==256)ERRORS.remove(ERRORS.keySet().iterator().next());row=new LinkedHashMap<>();row.put("typeId",DiagnosticBuffers.shortText(typeId,32));row.put("instanceId",DiagnosticBuffers.shortText(instanceId,128));row.put("root",position(root));row.put("dimension",dimension);row.put("category",DiagnosticBuffers.shortText(category,96));row.put("reason",text);row.put("firstTime",Instant.now().toString());row.put("repeats",0L);if(failure!=null){row.put("exception",failure.getClass().getName());row.put("exceptionMessage",DiagnosticBuffers.shortText(failure.getMessage(),256));row.put("stack",Arrays.stream(failure.getStackTrace()).limit(16).map(StackTraceElement::toString).toList());}ERRORS.put(key,row);}row.put("lastTime",Instant.now().toString());row.put("repeats",((Number)row.get("repeats")).longValue()+1);errorsDirty=true;}
        if(first)LOG.warn("DW diagnostics {} type={} instance={} root={} dimension={}: {}",category,typeId,instanceId,root,dimension,text);
        record(world,typeId,instanceId,root,"error:"+category,Map.of(),Map.of(),"ERROR",text);
        // Bootstrap failures may prevent the first server tick. Still batch a local journal.
        if(world==null&&first){List<Map<String,Object>> rows=errors();try{IO.execute(()->writeErrorJournal(rows));}catch(RejectedExecutionException busy){/* Dirty flag remains for later ticks. */}}
    }
    public static void network(String side,String direction,String channel,int payloadBytes){
        if(payloadBytes<0)return;for(State state:STATES.values()){Session s=state.active;if(s==null||System.nanoTime()>=s.deadlineNs)continue;String key=DiagnosticBuffers.shortText(side,16)+"|"+DiagnosticBuffers.shortText(direction,16)+"|"+DiagnosticBuffers.shortText(channel,128);synchronized(s){if(!s.network.containsKey(key)&&s.network.size()>=128){s.networkKeysDropped++;continue;}s.network.merge(key,(long)payloadBytes,Long::sum);}}
    }
    public static void registerMetrics(String name,Function<ServerWorld,Map<String,Object>> provider){if(PROVIDERS.size()>=8&&!PROVIDERS.containsKey(name))throw new IllegalArgumentException("Diagnostics provider limit");PROVIDERS.put(DiagnosticBuffers.shortText(name,64),Objects.requireNonNull(provider));}
    public static UUID start(ServerWorld world,int seconds,Filter filter){
        long own=System.nanoTime();
        if(seconds<1||seconds>600)throw new IllegalArgumentException("Diagnostic duration must be1..600 seconds");State state=STATES.computeIfAbsent(world.getServer(),s->new State());if(state.active!=null)stop(world.getServer(),"REPLACED_BY_OPERATOR");Session session=new Session(world,seconds,filter);state.active=session;state.last=session;session.startJfr();
        for(ServerPlayerEntity player:world.getPlayers())sendSession(player,session,true);record(world,"","",null,"session_start",Map.of(),Map.of("seconds",seconds,"filter",filter.report()),"STARTED","");session.overhead.add(System.nanoTime()-own);return session.id;
    }
    public static boolean stop(MinecraftServer server,String reason){long own=System.nanoTime();State state=STATES.get(server);Session s=state==null?null:state.active;if(s==null)return false;s.endNs=System.nanoTime();s.stopReason=DiagnosticBuffers.shortText(reason,128);Map<String,Object> end=new LinkedHashMap<>();end.put("sessionId",s.id.toString());end.put("time",Instant.now().toString());end.put("elapsedNs",s.endNs-s.startNs);end.put("operation",++s.operation);end.put("side","server");end.put("scope","GLOBAL_LIFECYCLE");end.put("typeId","");end.put("instanceId","");end.put("root",List.of());end.put("dimension",s.world.getRegistryKey().getValue().toString());end.put("action","session_stop");end.put("before",Map.of("enabled",true));end.put("after",Map.of("enabled",false));end.put("result","STOPPED");end.put("reason",s.stopReason);s.events.add(end);state.active=null;s.close();for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())if(s.participants.contains(player.getUuid()))sendSession(player,s,false);s.overhead.add(System.nanoTime()-own);return true;}
    /** Called at actual MinecraftServer.tick HEAD and RETURN, including all Fabric END callbacks. */
    public static void serverTickStart(MinecraftServer server){State state=STATES.get(server);if(state==null)return;state.tickSession=state.active;state.tickStartNs=state.tickSession==null&&state.reviewTickObserver==null?0:System.nanoTime();}
    public static void serverTickEnd(MinecraftServer server){State state=STATES.get(server);if(state!=null&&state.tickStartNs!=0){long elapsed=System.nanoTime()-state.tickStartNs;if(state.tickSession!=null)state.tickSession.ticks.add(elapsed);if(state.reviewTickObserver!=null)state.reviewTickObserver.accept(elapsed);state.tickSession=null;state.tickStartNs=0;}}
    /** Optional QA-only comparison observer. No observer or nanoTime call exists on the ordinary disabled path. */
    public static void reviewTickObserver(MinecraftServer server,LongConsumer observer){State state=STATES.computeIfAbsent(server,s->new State());state.reviewTickObserver=observer;}
    public static void saved(MinecraftServer server,String phase,boolean flush,boolean force){for(ServerWorld world:server.getWorlds()){Map<String,Object> fields=Map.of("flush",flush,"force",force);lifecycle(world,"world_save_"+phase,null,fields);record(world,"","",null,"world_save_"+phase,Map.of(),fields,"OBSERVED","No world state read in save hook");}}
    private static void lifecycle(ServerWorld world,String action,BlockPos root,Map<String,Object> fields){State state=STATES.computeIfAbsent(world.getServer(),s->new State());state.lifecycle.add(Map.of("time",Instant.now().toString(),"action",action,"dimension",world.getRegistryKey().getValue().toString(),"root",position(root),"fields",DiagnosticBuffers.safeMap(fields)));}
    private static void accepted(Session session,Map<String,Object> row){
        String compact=new Gson().toJson(row);if(compact.length()>8192||compact.getBytes(StandardCharsets.UTF_8).length>24000)return;
        for(ServerPlayerEntity player:session.world.getPlayers())if(player.networkHandler!=null&&session.participants.contains(player.getUuid())&&ServerPlayNetworking.canSend(player,ACCEPT_CHANNEL)){
            PacketByteBuf buf=PacketByteBufs.create();buf.writeUuid(session.id);buf.writeString(String.valueOf(row.get("instanceId")),160);buf.writeString(compact,8192);network("server","out",ACCEPT_CHANNEL.toString(),buf.readableBytes());ServerPlayNetworking.send(player,ACCEPT_CHANNEL,buf);
        }
    }
    private static void sendSession(ServerPlayerEntity player,Session s,boolean active){
        if(player.networkHandler==null||!ServerPlayNetworking.canSend(player,SESSION_CHANNEL))return;
        PacketByteBuf buf=PacketByteBufs.create();buf.writeUuid(s.id);buf.writeBoolean(active);buf.writeInt(active?(int)Math.max(0,Math.ceil((s.deadlineNs-System.nanoTime())/1e9)):0);buf.writeString(JSON.toJson(s.filter.report()),1024);s.participants.add(player.getUuid());network("server","out",SESSION_CHANNEL.toString(),buf.readableBytes());ServerPlayNetworking.send(player,SESSION_CHANNEL,buf);
    }
    private static void clientBatch(ServerPlayerEntity player,UUID id,String raw){
        State state=STATES.get(player.getServer());Session s=state==null?null:state.active;if(s==null||!s.id.equals(id)||!s.participants.contains(player.getUuid())||player.getServerWorld()!=s.world||System.nanoTime()>=s.deadlineNs)return;
        long now=System.nanoTime(),last=s.clientLast.getOrDefault(player.getUuid(),0L);if(now-last<900_000_000L){s.clientRejected++;return;}
        try{JsonObject value=JsonParser.parseString(raw).getAsJsonObject();if(value.size()>32){s.clientRejected++;return;}s.clientLast.put(player.getUuid(),now);s.clientBatches.add(Map.of("sessionId",id.toString(),"playerUuid",player.getUuid().toString(),"receivedNs",now-s.startNs,"utf8PayloadBytes",raw.getBytes(StandardCharsets.UTF_8).length,"batch",value));network("server","in",CLIENT_BATCH_CHANNEL.toString(),raw.getBytes(StandardCharsets.UTF_8).length+16);}
        catch(RuntimeException malformed){s.clientRejected++;error(s.world,"","",player.getBlockPos(),"CLIENT_TELEMETRY_INVALID","Bounded diagnostics JSON is not an object",malformed);}
    }
    private static void tick(MinecraftServer server){
        State state=STATES.get(server);Session s=state==null?null:state.active;long own=System.nanoTime();
        if(s!=null){if(System.nanoTime()>=s.deadlineNs)stop(server,"AUTO_DURATION_EXPIRED");else if(server.getTicks()%20==0)s.metrics.add(worldMetrics(s.world));s.overhead.add(System.nanoTime()-own);}
        if(errorsDirty&&own-lastErrorFlushNs>=1_000_000_000L){lastErrorFlushNs=own;List<Map<String,Object>> rows=errors();try{IO.execute(()->writeErrorJournal(rows));errorsDirty=false;}catch(RejectedExecutionException busy){/* Keep dirty; bounded queue retries next tick. */}}
    }
    private static Map<String,Object> worldMetrics(ServerWorld world){
        Map<String,Object> result=new LinkedHashMap<>(processMetrics());result.put("dimension",world.getRegistryKey().getValue().toString());int total=0,rp=0;boolean truncated=false;
        for(var entity:world.iterateEntities()){if(++total>20000){truncated=true;break;}if(entity instanceof dev.dreamwalker.bloodbornerp.object.RpObjectEntity)rp++;}result.put("loadedRpObjectsAllTypes",rp);result.put("loadedEntityCountScanned",Math.min(total,20000));result.put("loadedEntityScanTruncated",truncated);
        for(var provider:PROVIDERS.entrySet())try{result.put(provider.getKey(),DiagnosticBuffers.safeMap(provider.getValue().apply(world)));}catch(RuntimeException failure){result.put(provider.getKey(),"NOT_MEASURED_PROVIDER_ERROR");error(world,"","",null,"METRICS_PROVIDER",provider.getKey(),failure);}return result;
    }
    private static Map<String,Object> processMetrics(){
        var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();Map<String,Object> result=new LinkedHashMap<>();result.put("processId",ProcessHandle.current().pid());result.put("processScope","JVM_PROCESS_SHARED_IF_SAME_PID");result.put("heapUsedBytes",heap.getUsed());result.put("heapCommittedBytes",heap.getCommitted());result.put("heapMaxBytes",heap.getMax());long collections=0,time=0;for(var gc:ManagementFactory.getGarbageCollectorMXBeans()){if(gc.getCollectionCount()>=0)collections+=gc.getCollectionCount();if(gc.getCollectionTime()>=0)time+=gc.getCollectionTime();}result.put("gcCollectionCountProcessCumulative",collections);result.put("gcMxCollectionTimeMsProcessCumulative",time);result.put("gcMxScope","AGGREGATE_COLLECTION_DURATION_NOT_STW_PAUSE");result.put("diagnosticIoQueueSize",IO.getQueue().size());result.put("diagnosticIoActiveWorkers",IO.getActiveCount());result.put("diagnosticIoQueueMaximum",4);result.put("gpuTime","NOT_MEASURED");result.put("blockCpuPercent","NOT_MEASURED_NOT_DERIVABLE_FROM_FPS");return result;
    }
    public static Map<String,Object> status(MinecraftServer server){State state=STATES.get(server);Session s=state==null?null:state.active!=null?state.active:state.last;Map<String,Object> out=new LinkedHashMap<>();out.put("enabled",state!=null&&state.active!=null);out.put("sessionId",s==null?"NONE":s.id.toString());out.put("stopReason",s==null?"NEVER_STARTED":s.stopReason);out.put("events",s==null?Map.of():s.events.counts());out.put("snapshots",state==null?Map.of():state.snapshots.counts());out.put("errorKeys",errors().size());out.put("diagnosticIoQueueSize",IO.getQueue().size());out.put("diagnosticIoActiveWorkers",IO.getActiveCount());out.put("diagnosticIoQueueMaximum",4);if(s!=null){out.put("filter",s.filter.report());out.put("ticks",s.ticks.report());out.put("clientBatches",s.clientBatches.counts());out.put("remainingSeconds",Math.max(0,(s.deadlineNs-System.nanoTime())/1e9));}return out;}
    public static void mark(ServerWorld world,String note){State state=STATES.get(world.getServer());Session s=state==null?null:state.active;if(s!=null)s.marks.add(Map.of("time",Instant.now().toString(),"elapsedNs",System.nanoTime()-s.startNs,"note",DiagnosticBuffers.shortText(note,512)));}
    public static Map<String,Object> snapshot(ServerWorld world,PlayerEntity player,BlockPos root,Map<String,Object> extra){try{return storeSnapshot(world,DwDiagnosticSnapshots.block(world,player,root),extra);}catch(RuntimeException failure){error(world,"","",root,"OBJECT_SNAPSHOT","Loaded object snapshot failed; no object edits",failure);return storeSnapshot(world,Map.of("root",position(root),"result","NOT_MEASURED_CORRUPT_OBJECT"),extra);}}
    public static Map<String,Object> snapshot(ServerWorld world,PlayerEntity player,dev.dreamwalker.bloodbornerp.object.RpObjectEntity target,Map<String,Object> extra){try{return storeSnapshot(world,DwDiagnosticSnapshots.entity(world,player,target),extra);}catch(RuntimeException failure){error(world,"",target.getUuid().toString(),target.getBlockPos(),"OBJECT_SNAPSHOT","Loaded RP snapshot failed; no object edits",failure);return storeSnapshot(world,Map.of("instanceId",target.getUuid().toString(),"result","NOT_MEASURED_CORRUPT_OBJECT"),extra);}}
    private static Map<String,Object> storeSnapshot(ServerWorld world,Map<String,Object> value,Map<String,Object> extra){State state=STATES.computeIfAbsent(world.getServer(),s->new State());Map<String,Object> row=new LinkedHashMap<>(value);row.put("time",Instant.now().toString());row.put("sessionId",state.active==null?"NO_ACTIVE_SESSION":state.active.id.toString());row.put("annotation",DiagnosticBuffers.safeMap(extra));state.snapshots.add(row);return Collections.unmodifiableMap(row);}
    public static int snapshotCommand(ServerCommandSource source,String note){return DwDiagnosticSnapshots.command(source,note);}
    public static int snapshotSelected(ServerCommandSource source,String note){return snapshotCommand(source,note);}
    public static LiteralArgumentBuilder<ServerCommandSource> commandTree(){return DwDiagnosticCommands.tree();}
    static String json(Object value){return JSON.toJson(value);}
    static List<Integer> position(BlockPos root){return root==null?List.of():List.of(root.getX(),root.getY(),root.getZ());}
    public static List<Map<String,Object>> errors(){synchronized(ERRORS){return ERRORS.values().stream().map(LinkedHashMap::new).map(m->(Map<String,Object>)m).toList();}}
    public static CompletableFuture<Path> export(MinecraftServer server){return export(server,OUTPUT);}
    public static CompletableFuture<Path> export(MinecraftServer server,Path directory){
        State state=STATES.get(server);Session s=state==null?null:state.active!=null?state.active:state.last;Map<String,Object> session=new LinkedHashMap<>(status(server));session.put("schemaVersion",1);session.put("side","server");session.put("environment",environment);session.put("processScope","JVM_PROCESS_SHARED_IF_SAME_PID; do not sum same PID client and server heap/GC");
        Map<String,Object> measurements=new LinkedHashMap<>();List<Map<String,Object>> events=s==null?List.of():s.events.copy(),clients=s==null?List.of():s.clientBatches.copy(),snapshots=state==null?List.of():state.snapshots.copy(),marks=s==null?List.of():s.marks.copy();
        if(s!=null){session.put("started",s.started.toString());session.put("secondsRequested",s.seconds);session.put("elapsedNs",(s.endNs==0?System.nanoTime():s.endNs)-s.startNs);session.put("clientTelemetry",clients.isEmpty()?"NOT_MEASURED_NO_CLIENT_BATCHES":"BOUNDED_CLIENT_BATCHES_PRESENT");session.put("remoteClientLocalZip","NOT_AVAILABLE_ON_SERVER; same UUID independent local client export");session.put("clientRejected",s.clientRejected);measurements.put("serverTicks",s.ticks.report());measurements.put("observedTps",s.ticks.count/Math.max(.001,((s.endNs==0?System.nanoTime():s.endNs)-s.startNs)/1e9));measurements.put("diagnosticsOwnSynchronousOverhead",s.overhead.report());measurements.put("diagnosticsIoThreadCpuNs","NOT_MEASURED; asynchronous report writing excluded from synchronous hooks");measurements.put("gcActualPausesStatus",s.jfrStatus);measurements.put("gcActualPauses",s.gcPauses.report());measurements.put("processSamples",s.metrics.copy());measurements.put("processSamplesRetention",s.metrics.counts());synchronized(s){Map<String,Object> sections=new LinkedHashMap<>();s.measures.forEach((k,v)->sections.put(k,v.report()));measurements.put("ownCodeSampledSections",sections);measurements.put("sampleGateCalls",new LinkedHashMap<>(s.samples));measurements.put("sampleStrategy","FIRST_THEN_EVERY_32_CALLS_NO_TOTAL_CPU_EXTRAPOLATION");measurements.put("measurementGroupsDropped",s.measureGroupsDropped);measurements.put("sampleKeysDropped",s.sampleKeysDropped);measurements.put("networkChannelPayloadBytes",new LinkedHashMap<>(s.network));measurements.put("networkKeysDropped",s.networkKeysDropped);}}
        measurements.put("serverTickScope","MinecraftServer.tick HEAD to RETURN, including Fabric END callbacks; incomplete edge ticks explicitly omitted");measurements.put("diagnosticsOwnOverheadScope","PARTIAL_INCLUSIVE_API_HOOK_SAMPLES; includes session/JFR start-stop, record, measured, END metrics; nested timings may overlap, not unique process CPU; actual off/on QA tick comparison separate");measurements.put("allModNetworkBytes","NOT_MEASURED; instrumented custom payload channels only; native chunk/DataTracker excluded");measurements.put("networkScope","Only instrumented mod custom-channel payload bytes; framing/TCP/IP bytes NOT_MEASURED");measurements.put("dataTrackerNetworkBytes","NOT_MEASURED");measurements.put("vanillaChunkNetworkBytes","NOT_MEASURED");measurements.put("gcCaptureScope","JFR pause events delivered by active RecordingStream; not a guarantee of exhaustive process pauses");session.put("lifecyclePrelude",state==null?List.of():state.lifecycle.copy());session.put("lifecyclePreludeRetention",state==null?Map.of():state.lifecycle.counts());session.put("lifecyclePreludeScope","Bounded128 join/save markers, even before detailed session; no off-session object events or tick metrics");session.put("snapshotScope","Bounded128 manual snapshots, each row tagged by active session or NO_ACTIVE_SESSION");
        String name="diagnostics-"+(s==null?UUID.randomUUID():s.id)+"-"+System.currentTimeMillis()+".zip";List<Map<String,Object>> errorRows=errors();
        try{return CompletableFuture.supplyAsync(()->{try{Files.createDirectories(directory);Path target=directory.resolve(name);try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))){entry(zip,"summary.md",summary(session));entry(zip,"session.json",json(session));entry(zip,"events.jsonl",events.stream().map(v->new Gson().toJson(v)).reduce("",(a,b)->a+b+"\n"));entry(zip,"measurements.json",json(measurements));entry(zip,"object-snapshots.json",json(snapshots));entry(zip,"marks.json",json(marks));entry(zip,"errors.json",json(errorRows));entry(zip,"client-batches.json",json(clients));entry(zip,"environment.json",json(environment));}rotate(directory,"diagnostics-",8);return target;}catch(Exception failure){error(null,"","",null,"REPORT_IO","Diagnostic ZIP export failed without changing world: "+failure.getClass().getSimpleName(),failure);return null;}},IO);}catch(RejectedExecutionException busy){error(null,"","",null,"REPORT_IO","Diagnostic export queue full (maximum4); world continues",null);return CompletableFuture.completedFuture(null);}
    }
    private static void entry(ZipOutputStream zip,String name,String value)throws IOException{ZipEntry entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private static String summary(Map<String,Object> session){
        StringBuilder out=new StringBuilder("# Dreamwalker diagnostics session\n\n");
        out.append("Session UUID: ").append(session.getOrDefault("sessionId","NONE")).append("\n");
        out.append("Capture: ").append(Boolean.TRUE.equals(session.get("enabled"))?"ACTIVE / PARTIAL":"STOPPED / NO ACTIVE RECORDING").append("; reason: ").append(session.getOrDefault("stopReason","NOT_MEASURED")).append("\n");
        out.append("Started: ").append(session.getOrDefault("started","NOT_MEASURED")).append("; requested duration: ").append(session.getOrDefault("secondsRequested","NOT_MEASURED")).append(" seconds\n");
        Object elapsed=session.get("elapsedNs");out.append("Observed duration: ").append(elapsed instanceof Number n?String.format(Locale.ROOT,"%.3f seconds",n.doubleValue()/1e9):"NOT_MEASURED").append("\n\n");
        Map<?,?> events=session.get("events") instanceof Map<?,?> values?values:Map.of();
        out.append("Events: ").append(Objects.toString(events.get("allCount"),"NOT_MEASURED")).append(" observed, ").append(Objects.toString(events.get("retained"),"NOT_MEASURED")).append(" retained, ").append(Objects.toString(events.get("dropped"),"NOT_MEASURED")).append(" dropped.\n");
        out.append("Distinct error keys: ").append(session.getOrDefault("errorKeys","NOT_MEASURED")).append("; repeated occurrences are counted in errors.json.\n");
        Map<?,?> snapshots=session.get("snapshots") instanceof Map<?,?> values?values:Map.of();out.append("Object snapshots: ").append(Objects.toString(snapshots.get("retained"),"NOT_MEASURED")).append(" retained; full NBT is never captured.\n\n");
        Map<?,?> ticks=session.get("ticks") instanceof Map<?,?> values?values:Map.of();Object mean=ticks.get("allMeanNs"),p95=ticks.get("p95Ns"),p99=ticks.get("p99Ns");
        out.append("Server ticks: ").append(Objects.toString(ticks.get("allCount"),"NOT_MEASURED")).append(" observed; ticks over 50 ms: ").append(Objects.toString(ticks.get("allCountOver50ms"),"NOT_MEASURED")).append(".\n");
        out.append("Tick mean / p95 / p99: ").append(mean instanceof Number n?String.format(Locale.ROOT,"%.3f ms",n.doubleValue()/1e6):"NOT_MEASURED").append(" / ").append(p95 instanceof Number n?String.format(Locale.ROOT,"%.3f ms",n.doubleValue()/1e6):"NOT_MEASURED").append(" / ").append(p99 instanceof Number n?String.format(Locale.ROOT,"%.3f ms",n.doubleValue()/1e6):"NOT_MEASURED").append(".\n");
        out.append("Mean/count cover observed ticks; p95/p99 use only the retained window. Tick intervals include Fabric END callbacks. Own hook timings can overlap; they are not unique process CPU cost.\n\n");
        out.append("Client telemetry: ").append(session.getOrDefault("clientTelemetry","NOT_MEASURED_NO_CLIENT_BATCHES")).append(". Graphics settings and frame intervals are in client-batches.json and the separate local client ZIP with the same UUID.\n");
        out.append("GPU timing: NOT_MEASURED. CPU render submission and FPS do not determine a block's GPU time or exact load percentage.\n");
        out.append("All-mod / vanilla chunk / DataTracker / TCP traffic: NOT_MEASURED. Network counters include only instrumented custom-channel payload bytes.\n");
        out.append("JFR reports actual observed GC pauses when available; MXBean collection time is not an STW pause measurement. Client and integrated server may share one PID: do not add their heap/GC values.\n\n");
        Map<?,?> environment=session.get("environment") instanceof Map<?,?> values?values:Map.of();out.append("Production version: ").append(Objects.toString(environment.get("productionVersion"),"NOT_MEASURED")).append("; SHA256: ").append(Objects.toString(environment.get("productionArtifactSha256"),"NOT_MEASURED")).append(".\n");
        out.append("Minecraft/Java/Fabric/mod versions are in environment.json; detailed measurements, retention and unavailable data are in measurements.json. Normal placement refusals are events, not errors. This capture is not manual visual acceptance.\n");
        return out.toString();
    }
    private static void writeErrorJournal(List<Map<String,Object>> rows){try{Files.createDirectories(OUTPUT);Path path=OUTPUT.resolve("errors-current.json");byte[] bytes=json(Map.of("schemaVersion",1,"boundedErrorKeys",256,"errors",rows)).getBytes(StandardCharsets.UTF_8);if(Files.exists(path)&&Files.size(path)+bytes.length>1_048_576){for(int n=3;n>=1;n--){Path from=OUTPUT.resolve(n==1?"errors-current.json":"errors-"+(n-1)+".json"),to=OUTPUT.resolve("errors-"+n+".json");if(Files.exists(from))Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}Files.write(path,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}catch(IOException failure){LOG.warn("DW diagnostics error journal unavailable: {}",failure.getClass().getSimpleName());}}
    private static void rotate(Path directory,String prefix,int keep)throws IOException{try(var files=Files.list(directory)){List<Path> paths=files.filter(p->p.getFileName().toString().startsWith(prefix)&&p.getFileName().toString().endsWith(".zip")).sorted(Comparator.comparingLong((Path p)->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0;}}).reversed()).toList();for(int n=keep;n<paths.size();n++)Files.deleteIfExists(paths.get(n));}}
    private static Map<String,Object> environment(){Map<String,Object> result=new LinkedHashMap<>();result.put("javaVersion",System.getProperty("java.version"));result.put("javaVm",System.getProperty("java.vm.name"));result.put("processId",ProcessHandle.current().pid());result.put("processScope","JVM_PROCESS_SHARED_IF_SAME_PID");result.put("side","server");result.put("graphics","NOT_AVAILABLE_SERVER; see client batch metadata");List<Map<String,String>> mods=new ArrayList<>();for(var mod:FabricLoader.getInstance().getAllMods())mods.add(Map.of("id",mod.getMetadata().getId(),"version",mod.getMetadata().getVersion().getFriendlyString()));result.put("modVersions",mods);result.put("minecraftVersion",FabricLoader.getInstance().getModContainer("minecraft").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("NOT_MEASURED"));result.put("loaderVersion",FabricLoader.getInstance().getModContainer("fabricloader").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("NOT_MEASURED"));result.put("productionArtifactSha256","NOT_MEASURED_NON_JAR_ORIGIN");FabricLoader.getInstance().getModContainer("bloodborne_dw").ifPresent(mod->{result.put("productionVersion",mod.getMetadata().getVersion().getFriendlyString());for(Path p:mod.getOrigin().getPaths())if(Files.isRegularFile(p)&&p.toString().endsWith(".jar"))try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=Files.newInputStream(p)){byte[] buffer=new byte[65536];int size;while((size=stream.read(buffer))>=0)digest.update(buffer,0,size);}result.put("productionArtifactSha256",HexFormat.of().formatHex(digest.digest()));break;}catch(Exception e){result.put("productionArtifactSha256","NOT_MEASURED_IO_ERROR");}});return Collections.unmodifiableMap(result);}
}
