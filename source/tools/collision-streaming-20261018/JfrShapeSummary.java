import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/** Streaming, bounded-memory diagnostics; no game classes are loaded. */
public final class JfrShapeSummary {
    record Interval(double start,double end,String phase,long deliveries){}
    static final List<Interval> intervals=new ArrayList<>();
    static final LinkedHashMap<String,Predicate<String>> targets=new LinkedHashMap<>();
    static final Map<String,Stats> phases=new TreeMap<>();
    static final Map<String,Long> eventTypes=new TreeMap<>();
    static final class Stats {
        long execution,nativeSamples,allocationEvents,weightedBytes,truncatedExecution,truncatedAllocation;
        long first=Long.MAX_VALUE,last=Long.MIN_VALUE,gcCount;double gcSeconds,gcMax;
        final Map<String,Long> execTargets=new TreeMap<>(),allocTargets=new TreeMap<>(),targetAllocEvents=new TreeMap<>(),self=new HashMap<>(),inclusiveMod=new HashMap<>(),allocClasses=new HashMap<>();
        void sample(String type,List<String> stack,boolean truncated,long weight,String allocated,long time){
            first=Math.min(first,time);last=Math.max(last,time);
            if(type.equals("jdk.ExecutionSample")){
                execution++;if(truncated)truncatedExecution++;
                if(!stack.isEmpty())count(self,stack.get(0),1);
                for(String m:new HashSet<>(stack))if(m.contains("dreamwalker"))count(inclusiveMod,m,1);
                for(var target:targets.entrySet())if(stack.stream().anyMatch(target.getValue()))count(execTargets,target.getKey(),1);
            }else if(type.equals("jdk.NativeMethodSample"))nativeSamples++;
            else{
                allocationEvents++;weightedBytes+=weight;if(truncated)truncatedAllocation++;
                count(allocClasses,allocated,weight);
                for(var target:targets.entrySet())if(stack.stream().anyMatch(target.getValue())){count(allocTargets,target.getKey(),weight);count(targetAllocEvents,target.getKey(),1);}
            }
        }
        Map<String,Object> json(){
            var result=new LinkedHashMap<String,Object>();result.put("executionSamples",execution);result.put("nativeSamples",nativeSamples);result.put("allocationSampleEvents",allocationEvents);result.put("weightedAllocationBytes",weightedBytes);result.put("truncatedExecutionStacks",truncatedExecution);result.put("truncatedAllocationStacks",truncatedAllocation);
            var values=new LinkedHashMap<String,Object>();for(String target:targets.keySet())values.put(target,Map.of("executionSamplePresence",execTargets.getOrDefault(target,0L),"allocationSampleEvents",targetAllocEvents.getOrDefault(target,0L),"weightedAllocationBytes",allocTargets.getOrDefault(target,0L)));
            result.put("targets",values);result.put("topSelfMethods",top(self,25));result.put("topModInclusiveMethods",top(inclusiveMod,45));result.put("topAllocatedClassesByWeight",top(allocClasses,25));result.put("gcPauses",Map.of("count",gcCount,"seconds",gcSeconds,"maximumSeconds",gcMax));return result;
        }
    }
    static void count(Map<String,Long> map,String key,long n){map.merge(key,n,Long::sum);}
    static Stats stats(String phase,String thread){return phases.computeIfAbsent(phase+"|"+thread,k->new Stats());}
    static void prefix(String label,String prefix){targets.put(label,s->s.startsWith(prefix));}
    static void method(String label,String method){targets.put(label,s->s.equals(method));}
    static void targets(){
        String c="dev.dreamwalker.bloodbornedw.composite.";
        method("CompositeOwnerNbt.read",c+"CompositeOwnerNbt.read");method("CompositeOwnerNbt.packedBytes",c+"CompositeOwnerNbt.packedBytes");
        method("CompositeData.savedContributions",c+"CompositeData.savedContributions");method("DecodePool.savedContributions",c+"CompositeData$DecodePool.savedContributions");method("DecodePool.contributions",c+"CompositeData$DecodePool.contributions");method("DecodePool.contribution",c+"CompositeData$DecodePool.contribution");
        for(String m:List.of("boxes","nbt","bytes","writeCanonical","contributions"))method("CompositeData."+m,c+"CompositeData."+m);
        for(String m:List.of("exact","buildExact"))method("ImportedPhysicalShapes."+m,c+"ImportedPhysicalShapes."+m);
        for(String cls:List.of("ImportedPhysicalShapes","StaticArchitectureCatalog","CompositeShapeSnapshots","CompositeWorldMesh","CompositeTerrain","StaticArchitectureGeometry","CompositeNetworking","ImportedCollisionMigration","ImportedTypeMigration","ImportedStairBlock","CompositeLoadWork"))prefix(cls+".*",c+cls);
        method("ShardedCompositeLedger.read",c+"ShardedCompositeLedger.read");
        prefix("VanillaVoxelShapes.*","net.minecraft.class_259.");targets.put("VanillaVoxelShapes.union",s->s.equals("net.minecraft.class_259.method_1084")||s.equals("net.minecraft.class_259.method_17786")||s.equals("net.minecraft.util.shape.VoxelShapes.union"));
        prefix("VanillaChunkBuilder.*","net.minecraft.class_846");prefix("VanillaNbtIo.*","net.minecraft.class_2507.");prefix("VanillaChunkSerializer.*","net.minecraft.class_2852.");prefix("ClientPlayNetworkHandler.*","net.minecraft.class_634.");prefix("BufferBuilder.*","net.minecraft.class_287.");prefix("OpenGL.*","org.lwjgl.opengl.");
    }
    static String thread(RecordedThread thread){if(thread==null)return "UNKNOWN";return thread.getJavaThreadId()==1?"Render thread":thread.getJavaName()!=null?thread.getJavaName():thread.getOSName();}
    static String group(String thread){if(thread.equals("Server thread")||thread.equals("Render thread"))return thread;if(thread.startsWith("Worker-Main-"))return "Worker-Main-*";if(thread.startsWith("IO-Worker-"))return "IO-Worker-*";if(thread.startsWith("Netty"))return "Netty-*";return "Other";}
    static List<String> stack(RecordedEvent e){var out=new ArrayList<String>();if(e.getStackTrace()!=null)for(var f:e.getStackTrace().getFrames()){var m=f.getMethod();out.add(m.getType().getName()+"."+m.getName());}return out;}
    static List<Map<String,Object>> top(Map<String,Long> c,int limit){return c.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(limit).map(e->Map.<String,Object>of("name",e.getKey(),"value",e.getValue())).toList();}
    static String esc(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    static String json(Object o){if(o==null)return "null";if(o instanceof String s)return esc(s);if(o instanceof Number||o instanceof Boolean)return o.toString();if(o instanceof Map<?,?> m)return m.entrySet().stream().map(e->esc(e.getKey().toString())+":"+json(e.getValue())).collect(Collectors.joining(",","{","}"));if(o instanceof Iterable<?> a){var parts=new ArrayList<String>();for(Object x:a)parts.add(json(x));return "["+String.join(",",parts)+"]";}return esc(o.toString());}
    public static void main(String[] args)throws Exception{
        Path jfr=Path.of(args[0]),tsv=Path.of(args[1]),out=Path.of(args[2]);targets();
        for(String line:Files.readAllLines(tsv)){String[] v=line.split("\t");intervals.add(new Interval(Double.parseDouble(v[0]),Double.parseDouble(v[1]),v[2],Long.parseLong(v[3])));}
        long first=Long.MAX_VALUE,last=Long.MIN_VALUE;long read=0;
        try(var recording=new RecordingFile(jfr)){
            while(recording.hasMoreEvents()){
                var e=recording.readEvent();read++;String type=e.getEventType().getName();count(eventTypes,type,1);long time=e.getStartTime().toEpochMilli();first=Math.min(first,time);last=Math.max(last,time);
                if(!Set.of("jdk.ExecutionSample","jdk.NativeMethodSample","jdk.ObjectAllocationSample","jdk.GCPhasePause").contains(type))continue;
                Interval phase=null;for(var p:intervals)if(time>=p.start&&time<p.end){phase=p;break;}if(phase==null)continue;
                var phaseNames=new ArrayList<String>();phaseNames.add(phase.phase);if(!phase.phase.equals("facade"))phaseNames.add("TRAVEL_TOTAL");if(time>=phase.start+500&&time<phase.end-500){phaseNames.add(phase.phase+"_CORE_500MS");if(!phase.phase.equals("facade"))phaseNames.add("TRAVEL_TOTAL_CORE_500MS");}
                if(type.equals("jdk.GCPhasePause")){for(String p:phaseNames){var s=stats(p,"ALL");double d=e.getDuration().toNanos()/1e9;s.gcCount++;s.gcSeconds+=d;s.gcMax=Math.max(s.gcMax,d);}continue;}
                String thread=thread(type.equals("jdk.ExecutionSample")||type.equals("jdk.NativeMethodSample")?e.getThread("sampledThread"):e.getThread());String grouped=group(thread);
                var stack=stack(e);boolean truncated=e.getStackTrace()!=null&&e.getStackTrace().isTruncated();long weight=type.equals("jdk.ObjectAllocationSample")?e.getLong("weight"):0;String allocated=type.equals("jdk.ObjectAllocationSample")?e.getClass("objectClass").getName():"";
                for(String p:phaseNames){stats(p,"ALL").sample(type,stack,truncated,weight,allocated,time);stats(p,grouped).sample(type,stack,truncated,weight,allocated,time);}
            }
        }
        var result=new LinkedHashMap<String,Object>();result.put("status","JFR_PHASE_TARGET_ANALYSIS_COMPLETE");result.put("jfr",jfr.toString());result.put("units","Execution sample presence is not elapsed time or invocation count. A target matches at most once per stack, but different targets overlap and must not be summed. Allocation weights are JFR estimates of cumulative bytes, not exact allocations, live heap or RSS. Zero samples do not prove zero execution. Native samples are reported separately. Phase UTC boundaries are approximate; see phase-provenance.json. CORE trims 500ms from each phase boundary.");
        result.put("eventsRead",read);result.put("recordingFirstEventEpochMs",first);result.put("recordingLastEventEpochMs",last);result.put("eventCounts",eventTypes);var results=new LinkedHashMap<String,Object>();for(var entry:phases.entrySet())results.put(entry.getKey(),entry.getValue().json());result.put("phaseThreads",results);var coverage=new ArrayList<Map<String,Object>>();for(var p:intervals)coverage.add(Map.of("phase",p.phase,"estimatedStartEpochMs",p.start,"estimatedEndEpochMs",p.end,"recordingCoversInterval",first<=p.start&&last>=p.end,"actualDeliveredChunks",p.deliveries));result.put("coverage",coverage);Files.writeString(out,json(result)+"\n");
    }
}
