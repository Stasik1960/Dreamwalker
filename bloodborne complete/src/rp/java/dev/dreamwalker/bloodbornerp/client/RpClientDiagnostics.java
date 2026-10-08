package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics;
import dev.dreamwalker.bloodbornerp.object.RpDiagnostics;
import java.util.Map;
import net.minecraft.entity.Entity;

/** Client CPU/resource evidence, never a world mutation or a claimed GPU/visual measurement. */
public final class RpClientDiagnostics {
    private static final ThreadLocal<java.util.Map<Long,Long>> CPU_START=ThreadLocal.withInitial(java.util.HashMap::new);
    private RpClientDiagnostics() {}
    public static boolean enabled(){try{return DwClientDiagnostics.enabled();}catch(RuntimeException ignored){return false;}}
    public static long begin(Entity entity,String section){if(!enabled())return 0;try{if(DwClientDiagnostics.shouldSample(RpDiagnostics.typeId(entity),entity.getUuidAsString(),entity.getBlockPos(),section)){long started=System.nanoTime();var values=CPU_START.get();if(values.size()>=8)values.clear();values.put(started,DwClientDiagnostics.threadCpuNs());return started;}}catch(RuntimeException ignored){}return 0;}
    public static void finish(Entity entity,String section,long started){if(started!=0)try{Long cpuStart=CPU_START.get().remove(started);long elapsed=System.nanoTime()-started,cpuEnd=DwClientDiagnostics.threadCpuNs();DwClientDiagnostics.measured(RpDiagnostics.typeId(entity),entity.getBlockPos(),section,elapsed);if(cpuStart!=null&&cpuStart>=0&&cpuEnd>=cpuStart)DwClientDiagnostics.measured(RpDiagnostics.typeId(entity),entity.getBlockPos(),section+".thread_cpu",cpuEnd-cpuStart);}catch(RuntimeException ignored){}}
    public static void record(Entity entity,String action,Map<String,?> before,Map<String,?> after,String reason){try{DwClientDiagnostics.record(RpDiagnostics.typeId(entity),entity.getUuidAsString(),entity.getBlockPos(),action,before,after,"OBSERVED",reason);}catch(RuntimeException ignored){}}
    public static void error(Entity entity,String category,String reason,Throwable failure){try{DwClientDiagnostics.error(entity==null?"RP_PROTOCOL":RpDiagnostics.typeId(entity),entity==null?null:entity.getUuidAsString(),entity==null?null:entity.getBlockPos(),category,reason,failure);}catch(RuntimeException ignored){}}
    public static void network(String direction,String channel,int payloadBytes){try{DwClientDiagnostics.network("client",direction,channel,payloadBytes);}catch(RuntimeException ignored){}}
}
