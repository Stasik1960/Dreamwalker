package dev.dreamwalker.bloodbornerp.object;

import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Scalar-only RP instrumentation. A telemetry failure must not change an object transaction. */
public final class RpDiagnostics {
    private RpDiagnostics() {}
    public static String typeId(Identifier registry) {
        try { var entry=DebugCatalogue.entry(registry);return entry==null?registry.toString():entry.temporaryId(); }
        catch(RuntimeException ignored) { return registry.toString(); }
    }
    public static String typeId(Entity entity) { return typeId(Registries.ENTITY_TYPE.getId(entity.getType())); }
    public static String typeIdForAsset(String asset) { Identifier id=Identifier.tryParse("bloodborne_rp:"+asset);return id==null?"UNASSIGNED":typeId(id); }
    public static boolean enabled(Entity entity) { return entity.getWorld() instanceof ServerWorld world&&enabled(world); }
    public static boolean enabled(ServerWorld world) {try{return DwDiagnostics.enabled(world);}catch(RuntimeException ignored){return false;}}
    public static void event(Entity entity,String action,Map<String,Object> before,Map<String,Object> after,String result,String reason) {
        if(!(entity.getWorld() instanceof ServerWorld world))return;
        event(world,typeId(entity),entity.getUuidAsString(),entity.getBlockPos(),action,before,after,result,reason);
    }
    public static void event(ServerWorld world,String type,String instance,BlockPos root,String action,Map<String,Object> before,Map<String,Object> after,String result,String reason) {
        try { if(DwDiagnostics.enabled(world))DwDiagnostics.record(world,type,instance,root,action,before,after,result,reason); }
        catch(RuntimeException ignored) { /* Instrumentation is outside gameplay state. */ }
    }
    public static void error(Entity entity,String category,String reason,Throwable failure) {
        error(entity.getWorld() instanceof ServerWorld world?world:null,typeId(entity),entity.getUuidAsString(),entity.getBlockPos(),category,reason,failure);
    }
    public static void error(ServerWorld world,String type,String instance,BlockPos root,String category,String reason,Throwable failure) {
        try { DwDiagnostics.error(world,type,instance,root,category,reason,failure); }
        catch(RuntimeException ignored) { /* A failed error journal cannot prevent load/save. */ }
    }
    public static long begin(Entity entity,String section) {
        if(!enabled(entity))return 0;
        try {if(entity.getWorld() instanceof ServerWorld world&&DwDiagnostics.shouldSample(world,typeId(entity),entity.getUuidAsString(),entity.getBlockPos(),section))return System.nanoTime();}catch(RuntimeException ignored){}return 0;
    }
    public static long begin(ServerWorld world,String type,BlockPos root,String section) {try { if(DwDiagnostics.shouldSample(world,type,root,section))return System.nanoTime(); }catch(RuntimeException ignored) {}return 0;}
    public static void finish(Entity entity,String section,long began) {
        if(began==0||!(entity.getWorld() instanceof ServerWorld world))return;
        try {DwDiagnostics.measured(world,typeId(entity),entity.getUuidAsString(),entity.getBlockPos(),section,System.nanoTime()-began);}catch(RuntimeException ignored){}
    }
    public static void finish(ServerWorld world,String type,BlockPos root,String section,long began) {if(began!=0)try { DwDiagnostics.measured(world,type,root,section,System.nanoTime()-began); }catch(RuntimeException ignored) {}}
    public static Map<String,Object> state(RpObjectEntity entity) {
        return Map.of("registry",Registries.ENTITY_TYPE.getId(entity.getType()).toString(),"asset",entity.assetId(),
            "open",entity.isOpen(),"locked",entity.isLocked(),"scale",entity.objectScale(),"yaw",entity.getYaw(),
            "dogsVisible",entity.dogsVisible(),"gatePulseTicks",entity.woodGatePulseTicks(),"verticalOffset",entity.verticalOffset());
    }
    public static void network(String side,String direction,String channel,int payloadBytes) {
        try { DwDiagnostics.network(side,direction,channel,payloadBytes); }catch(RuntimeException ignored) {}
    }
}
