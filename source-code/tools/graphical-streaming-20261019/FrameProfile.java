import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Stream two native recordings against the same measured presentation spans.
 * Reuses the existing JfrShapeSummary target/statistics implementation. */
public final class FrameProfile {
    record Frame(String phase,long end,long duration){long start(){return end-duration;}}
    static final class Stall {
        final Frame frame;
        long clientSamples,serverSamples,clientMain,serverMain,clientAllocation,serverAllocation;
        long clientGc,serverGc,clientWait,serverWait;
        final Map<String,Long> clientSelf=new HashMap<>(),serverSelf=new HashMap<>(),clientMod=new HashMap<>(),serverMod=new HashMap<>();
        Stall(Frame f){frame=f;}
        Map<String,Object> json(){return Map.ofEntries(Map.entry("phase",frame.phase),Map.entry("startEpochNs",frame.start()),Map.entry("endEpochNs",frame.end),Map.entry("durationMs",frame.duration/1e6),Map.entry("clientGcOverlapMs",clientGc/1e6),Map.entry("serverGcOverlapMs",serverGc/1e6),Map.entry("clientMainWaitOverlapMs",clientWait/1e6),Map.entry("serverMainWaitOverlapMs",serverWait/1e6),Map.entry("clientSamples",clientSamples),Map.entry("serverSamples",serverSamples),Map.entry("clientMainSamples",clientMain),Map.entry("serverMainSamples",serverMain),Map.entry("clientAllocationWeight",clientAllocation),Map.entry("serverAllocationWeight",serverAllocation),Map.entry("clientTopMainSelf",JfrShapeSummary.top(clientSelf,6)),Map.entry("serverTopMainSelf",JfrShapeSummary.top(serverSelf,6)),Map.entry("clientTopMainMod",JfrShapeSummary.top(clientMod,8)),Map.entry("serverTopMainMod",JfrShapeSummary.top(serverMod,8)));}
    }
    static final List<Frame> frames=new ArrayList<>();static final List<Stall> stalls=new ArrayList<>();
    static final Map<String,long[]> ranges=new LinkedHashMap<>();
    static final Map<String,JfrShapeSummary.Stats> stats=new LinkedHashMap<>();
    static final Map<String,Object> metadata=new LinkedHashMap<>();
    static final Map<String,Map<String,Long>> nativeSelf=new LinkedHashMap<>();
    static final List<Map<String,Object>> mainWaits=new ArrayList<>();
    static long ns(Instant t){return t.getEpochSecond()*1_000_000_000L+t.getNano();}
    static String phase(long t){for(var e:ranges.entrySet())if(t>=e.getValue()[0]&&t<=e.getValue()[1])return e.getKey();return null;}
    static int stallIndex(long t){int lo=0,hi=stalls.size();while(lo<hi){int mid=(lo+hi)>>>1;if(stalls.get(mid).frame.end<t)lo=mid+1;else hi=mid;}return lo<stalls.size()&&stalls.get(lo).frame.start()<=t?lo:-1;}
    static JfrShapeSummary.Stats get(String key){return stats.computeIfAbsent(key,k->new JfrShapeSummary.Stats());}
    static String role(String side,RecordedThread t){if(t==null)return "UNKNOWN";String n=t.getJavaName();if(side.equals("client")&&(t.getJavaThreadId()==1||"Render thread".equals(n)))return "MAIN";if(side.equals("server")&&"Server thread".equals(n))return "MAIN";if(n==null)return "UNKNOWN";if(n.startsWith("Netty"))return "Netty-*";if(n.startsWith("Worker-Main-")||n.contains("Chunk Render"))return "Worker-*";if(n.startsWith("IO-Worker-"))return "IO-*";return n;}
    static void sample(String side,Path file)throws Exception {
        long first=Long.MAX_VALUE,last=Long.MIN_VALUE,read=0;Map<String,Long> types=new HashMap<>();
        try(var recording=new RecordingFile(file)){
            while(recording.hasMoreEvents()){
                var e=recording.readEvent();read++;String type=e.getEventType().getName();JfrShapeSummary.count(types,type,1);long t=ns(e.getStartTime());first=Math.min(first,t);last=Math.max(last,t);
                String p=phase(t);
                if(type.equals("jdk.GCPhasePause")){
                    long end=t+e.getDuration().toNanos();
                    if(p!=null){for(String name:p.equals("facade")?List.of(p):List.of(p,"TRAVEL")){var s=get(side+"|"+name+"|ALL");s.gcCount++;s.gcSeconds+=e.getDuration().toNanos()/1e9;s.gcMax=Math.max(s.gcMax,e.getDuration().toNanos()/1e9);}}
                    for(var s:stalls){long overlap=Math.max(0,Math.min(end,s.frame.end)-Math.max(t,s.frame.start()));if(side.equals("client"))s.clientGc+=overlap;else s.serverGc+=overlap;}
                    continue;
                }
                boolean exec=type.equals("jdk.ExecutionSample"),nativeSample=type.equals("jdk.NativeMethodSample"),allocation=type.equals("jdk.ObjectAllocationSample");
                if(!exec&&!nativeSample&&!allocation){
                    if(p!=null&&e.getDuration().toNanos()>50_000_000&&Set.of("jdk.ThreadPark","jdk.JavaMonitorEnter","jdk.JavaMonitorWait","jdk.FileRead","jdk.SocketRead","jdk.ThreadSleep").contains(type)&&role(side,e.getThread()).equals("MAIN")){
                        var stack=JfrShapeSummary.stack(e);long end=t+e.getDuration().toNanos();
                        if(mainWaits.size()<4096)mainWaits.add(Map.of("side",side,"phase",p,"event",type,"startEpochNs",t,"endEpochNs",end,"ms",e.getDuration().toNanos()/1e6,"stack",stack.stream().limit(20).toList()));
                        for(var s:stalls){long overlap=Math.max(0,Math.min(end,s.frame.end)-Math.max(t,s.frame.start()));if(side.equals("client"))s.clientWait+=overlap;else s.serverWait+=overlap;}
                    }continue;
                }
                if(p==null)continue;
                RecordedThread thread=exec||nativeSample?e.getThread("sampledThread"):e.getThread();String role=role(side,thread);
                var stack=JfrShapeSummary.stack(e);boolean truncated=e.getStackTrace()!=null&&e.getStackTrace().isTruncated();long weight=allocation?e.getLong("weight"):0;String allocated=allocation?e.getClass("objectClass").getName():"";
                if(nativeSample&&!stack.isEmpty())JfrShapeSummary.count(nativeSelf.computeIfAbsent(side+"|"+p+"|"+role,k->new HashMap<>()),stack.get(0),1);
                for(String name:p.equals("facade")?List.of(p):List.of(p,"TRAVEL")){
                    get(side+"|"+name+"|ALL").sample(type,stack,truncated,weight,allocated,t/1_000_000);get(side+"|"+name+"|"+role).sample(type,stack,truncated,weight,allocated,t/1_000_000);
                }
                int index=stallIndex(t);if(index<0)continue;var s=stalls.get(index);
                if(allocation){if(side.equals("client"))s.clientAllocation+=weight;else s.serverAllocation+=weight;}
                if(!exec)continue;
                if(side.equals("client"))s.clientSamples++;else s.serverSamples++;
                if(role.equals("MAIN")){
                    if(side.equals("client"))s.clientMain++;else s.serverMain++;
                    if(!stack.isEmpty())JfrShapeSummary.count(side.equals("client")?s.clientSelf:s.serverSelf,stack.get(0),1);
                    for(String m:new HashSet<>(stack))if(m.contains("dreamwalker"))JfrShapeSummary.count(side.equals("client")?s.clientMod:s.serverMod,m,1);
                }
            }
        }
        metadata.put(side,Map.of("jfr",file.toString(),"firstEventEpochNs",first,"lastEventEpochNs",last,"eventsRead",read,"eventCounts",types,"coversTravel",ranges.containsKey("tower")&&ranges.containsKey("brick")&&first<=ranges.get("tower")[0]&&last>=ranges.get("brick")[1]));
    }
    public static void main(String[] args)throws Exception {
        Path folder=Path.of(args[0]);JfrShapeSummary.targets();String c="dev.dreamwalker.bloodbornedw.composite.";
        for(String cls:List.of("CompositeClient","CompositeTerrainUpdates","CompositeBlockEntity","CompositeNetworking","CompositeLedger","CompositeShapeSnapshots","StaticArchitectureGeometry","CompositeOwnerUpdates"))JfrShapeSummary.prefix(cls+".*",c+cls);
        JfrShapeSummary.method("ShardedCompositeLedger.readMemberships",c+"ShardedCompositeLedger.readMemberships");
        JfrShapeSummary.prefix("LithiumHashPalette.*","me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette.");
        JfrShapeSummary.method("CompositeBlockEntity.applySyncedRoot",c+"CompositeBlockEntity.applySyncedRoot");
        for(String line:Files.readAllLines(folder.resolve("client/client-frames.csv"))){if(line.startsWith("phase,"))continue;String[] v=line.split(",");Frame f=new Frame(v[0],Long.parseLong(v[1]),Long.parseLong(v[2]));frames.add(f);var range=ranges.computeIfAbsent(f.phase,k->new long[]{f.start(),f.end});range[0]=Math.min(range[0],f.start());range[1]=Math.max(range[1],f.end);if(f.duration>100_000_000)stalls.add(new Stall(f));}
        for(String side:List.of("client","server")){
            Path chosen=null;
            for(String suffix:List.of(".jfr","-partial.jfr","-emergency.jfr")){Path p=folder.resolve(side+"/"+side+suffix);if(Files.exists(p)&&Files.size(p)>0){chosen=p;break;}}
            if(chosen!=null)sample(side,chosen);else if(side.equals("client"))throw new IllegalStateException("Missing native client recording");
            else metadata.put(side,Map.of("status","RECORDING_UNAVAILABLE_AFTER_WATCHDOG","coversTravel",false));
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("status","NATIVE_FRAME_JFR_CORRELATION_COMPLETE");result.put("metadata",metadata);result.put("phaseRangesEpochNs",ranges.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->List.of(e.getValue()[0],e.getValue()[1]))));
        Map<String,Object> samples=new LinkedHashMap<>();for(var e:stats.entrySet())samples.put(e.getKey(),e.getValue().json());result.put("phaseThreads",samples);result.put("stallsOver100Ms",stalls.stream().map(Stall::json).toList());result.put("mainThreadWaitsOver50Ms",mainWaits);
        Map<String,Object> nativeTops=new LinkedHashMap<>();for(var e:nativeSelf.entrySet())nativeTops.put(e.getKey(),JfrShapeSummary.top(e.getValue(),15));result.put("nativeTopSelfByPhase",nativeTops);
        result.put("limitations","Frame spans use bounded native flipFrame timestamps anchored to UTC; see each phase clockAnchorUncertaintyNs. GC overlap is temporal correlation. Execution samples are neither invocation counts nor wall-time shares. Allocation weights estimate temporary byte turnover; nested categories overlap. Main-thread wait spans can overlap GC and are not additive.");
        Files.writeString(folder.resolve("frame-profile.json"),JfrShapeSummary.json(result)+"\n");
    }
}
