package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornerp.RpConfig;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Delayed lever pulses act on one shared real target state, never on an item's art or a reused position. */
public final class MechanismLinks {
    public enum Kind { RP, ARCHITECTURE }
    public enum Availability { LOADED, UNLOADED, STALE }
    public record TargetRef(Kind kind,String dimension,UUID instance,long root,String registryId){
        public TargetRef{Objects.requireNonNull(kind);Objects.requireNonNull(instance);if(Identifier.tryParse(dimension)==null||registryId.length()>128)throw new IllegalArgumentException("Invalid mechanism target");}
        public String key(){return kind.name()+"|"+dimension+"|"+instance;}
        public NbtCompound write(){NbtCompound tag=new NbtCompound();tag.putString("Kind",kind.name());tag.putString("Dimension",dimension);tag.putUuid("Instance",instance);tag.putLong("Root",root);tag.putString("Registry",registryId);return tag;}
        public static TargetRef read(NbtCompound tag){return new TargetRef(Kind.valueOf(tag.getString("Kind")),tag.getString("Dimension"),tag.getUuid("Instance"),tag.getLong("Root"),tag.getString("Registry"));}
        public static TargetRef rp(RpObjectEntity entity){return new TargetRef(Kind.RP,entity.getWorld().getRegistryKey().getValue().toString(),entity.getUuid(),entity.getBlockPos().asLong(),entity.assetId());}
    }
    public record Status(Availability availability,boolean open,boolean locked,boolean pulseOnly){public Status(Availability availability,boolean open,boolean locked){this(availability,open,locked,false);}}
    public interface ArchitectureBridge {Status inspect(ServerWorld world,TargetRef target);boolean apply(ServerWorld world,TargetRef target,boolean open);}
    private static ArchitectureBridge architecture;
    private static boolean initialized;
    private static final Map<MinecraftServer,Set<UUID>> IMPORT_PENDING=new WeakHashMap<>();
    private static final Map<MinecraftServer,Integer> RETRY_OFFSET=new WeakHashMap<>();
    private MechanismLinks(){}
    public static void architectureBridge(ArchitectureBridge bridge){architecture=Objects.requireNonNull(bridge);}
    public static void initialize(){
        if(initialized)return;initialized=true;
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof RpObjectEntity rp)IMPORT_PENDING.computeIfAbsent(world.getServer(),s->new LinkedHashSet<>()).add(rp.getUuid());});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof RpObjectEntity rp&&entity.getRemovalReason()!=null&&entity.getRemovalReason().shouldDestroy())removed(rp);});
        ServerTickEvents.END_SERVER_TICK.register(server->{Set<UUID> loaded=IMPORT_PENDING.remove(server);if(loaded!=null)for(UUID id:loaded)for(ServerWorld world:server.getWorlds()){Entity entity=world.getEntity(id);if(entity instanceof RpObjectEntity rp){loaded(rp);break;}}if(server.getTicks()%20==0)retryPending(server,256);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{IMPORT_PENDING.remove(server);RETRY_OFFSET.remove(server);});
    }
    public static void loaded(RpObjectEntity entity){
        if(!(entity.getWorld() instanceof ServerWorld world))return;MechanismState state=MechanismState.get(world.getServer());
        if(entity.isMechanism()){
            if(!entity.links().isEmpty()||state.levers.containsKey(entity.getUuid()))lever(state,entity);for(UUID id:entity.links()){
                Entity target=world.getEntity(id);TargetRef ref=target instanceof RpObjectEntity rp?TargetRef.rp(rp):new TargetRef(Kind.RP,world.getRegistryKey().getValue().toString(),id,entity.getBlockPos().asLong(),"");
                if(!(target instanceof RpObjectEntity rp)||rp.canBeLinked())link(entity,ref);
            }
        }
        String key=TargetRef.rp(entity).key();MechanismState.Target target=state.targets.get(key);
        if(target!=null&&entity.canBeLinked()&&!state.removed.contains(key)){
            if(!target.ref.registryId().isEmpty()&&!target.ref.registryId().equals(entity.assetId())){forgetTarget(state,key);return;}
            target.ref=TargetRef.rp(entity);MechanismRules.retry(world.getServer(),256);target.pulseOnly=entity.isPulseOnlyMechanism();if(target.pending)apply(world.getServer(),state,target);state.markDirty();
        }
    }
    public static boolean linkRp(RpObjectEntity source,RpObjectEntity target){return target.canBeLinked()&&source.getWorld()==target.getWorld()&&link(source,TargetRef.rp(target));}
    public static boolean unlinkRp(RpObjectEntity source,UUID target){return unlink(source,new TargetRef(Kind.RP,source.getWorld().getRegistryKey().getValue().toString(),target,0,"").key());}
    public static boolean link(RpObjectEntity source,TargetRef ref){
        if(!(source.getWorld() instanceof ServerWorld world)||!source.isMechanism()||!ref.dimension().equals(world.getRegistryKey().getValue().toString()))return false;
        MechanismState state=MechanismState.get(world.getServer());MechanismState.Lever lever=lever(state,source);
        if(lever==null)return false;
        if(state.removed.contains(ref.key())||lever.targets.contains(ref.key())||lever.targets.size()>=RpConfig.INSTANCE.maxMechanismLinks||state.targets.size()>=MechanismState.LIMIT&&!state.targets.containsKey(ref.key()))return false;
        Status current=inspect(world.getServer(),ref);if(current.availability()==Availability.STALE)return false;
        MechanismState.Target target=state.targets.computeIfAbsent(ref.key(),k->new MechanismState.Target(ref));
        target.pulseOnly=current.pulseOnly()||ref.kind()==Kind.RP&&ref.registryId().equals("wood_gate");
        if(!target.known&&current.availability()==Availability.LOADED){target.desired=current.open();target.known=true;}
        lever.targets.add(ref.key());state.markDirty();MechanismRules.legacyLinked(source,ref);event(world,ref,"link_create",Map.of("sourceLever",source.getUuid().toString()),Map.of("desired",target.desired,"pending",target.pending),"COMMITTED","");return true;
    }
    public static boolean unlink(RpObjectEntity source,String key){if(!(source.getWorld() instanceof ServerWorld world)||!source.isMechanism())return false;MechanismState state=MechanismState.get(world.getServer());MechanismState.Lever lever=state.levers.get(source.getUuid());if(lever==null||!lever.targets.remove(key))return false;MechanismState.Target target=state.targets.get(key);if(target!=null)event(world,target.ref,"link_remove",Map.of("sourceLever",source.getUuid().toString()),Map.of(),"COMMITTED","");pruneUnused(state,key);state.markDirty();MechanismRules.legacyUnlinked(source,key);return true;}
    /** Called once when the existing 70 tick RP lever delay expires. */
    public static void pulse(RpObjectEntity source){
        if(!(source.getWorld() instanceof ServerWorld world)||!source.isMechanism())return;loaded(source);MechanismRules.pulse(source);
    }
    public static void noteInitiator(RpObjectEntity source,net.minecraft.server.network.ServerPlayerEntity player){MechanismRules.noteInitiator(source,player);}
    public static boolean applyEffect(MinecraftServer server,TargetRef ref,boolean open,boolean pulse){
        ServerWorld world=world(server,ref.dimension());if(world==null)return false;
        if(ref.kind()==Kind.RP){Entity entity=world.getEntity(ref.instance());return entity instanceof RpObjectEntity rp&&(pulse?rp.pulseFromMechanism():rp.setOpen(open));}
        return !pulse&&architecture!=null&&architecture.apply(world,ref,open);
    }
    public static int retryPending(MinecraftServer server,int limit){
        if(limit<=0)return 0;int delivered=MechanismRules.retry(server,limit);MechanismState state=MechanismState.get(server);List<MechanismState.Target> pending=state.targets.values().stream().filter(t->t.pending).toList();if(pending.isEmpty()){RETRY_OFFSET.remove(server);return delivered;}
        int start=Math.floorMod(RETRY_OFFSET.getOrDefault(server,0),pending.size()),count=Math.min(limit,pending.size());for(int i=0;i<count;i++)apply(server,state,pending.get((start+i)%pending.size()));RETRY_OFFSET.put(server,(start+count)%pending.size());return count;
    }
    private static void apply(MinecraftServer server,MechanismState state,MechanismState.Target target){
        Status status=inspect(server,target.ref);if(status.availability()==Availability.STALE){forgetTarget(state,target.ref.key());return;}if(status.availability()!=Availability.LOADED||status.locked())return;
        if(!target.known&&!target.pulseOnly){target.desired=status.open()^target.unresolvedFlip;target.known=true;target.unresolvedFlip=false;state.markDirty();}
        ServerWorld world=world(server,target.ref.dimension());boolean accepted;
        if(target.ref.kind()==Kind.RP){Entity entity=world.getEntity(target.ref.instance());accepted=entity instanceof RpObjectEntity rp&&(target.pulseOnly?target.pendingPulses>0&&rp.pulseFromMechanism():rp.setOpen(target.desired));}
        else accepted=architecture!=null&&architecture.apply(world,target.ref,target.desired);
        event(world,target.ref,"link_effect",Map.of("open",status.open()),Map.of("desired",target.desired,"pulseOnly",target.pulseOnly,"pendingPulses",target.pendingPulses),accepted?"COMMITTED":"DEFERRED",accepted?"Actual target accepted effect":"Target deferred desired effect; pending retained");
        if(accepted){if(target.pulseOnly)target.pendingPulses--;target.pending=target.pulseOnly&&target.pendingPulses>0;pruneUnused(state,target.ref.key());state.markDirty();}
    }
    public static Status inspect(MinecraftServer server,TargetRef ref){
        ServerWorld world=world(server,ref.dimension());boolean sample=dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.enabled(world)&&dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.shouldSample(world,typeId(ref),ref.instance().toString(),BlockPos.fromLong(ref.root()),"mechanism_target_lookup");long began=sample?System.nanoTime():0;
        try{return inspectStatus(world,ref);}finally{if(sample)dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.measured(world,typeId(ref),ref.instance().toString(),BlockPos.fromLong(ref.root()),"mechanism_target_lookup",System.nanoTime()-began);}
    }
    private static Status inspectStatus(ServerWorld world,TargetRef ref){
        if(world==null)return new Status(Availability.UNLOADED,false,false);
        if(ref.kind()==Kind.ARCHITECTURE)return architecture==null?new Status(Availability.UNLOADED,false,false):architecture.inspect(world,ref);
        Entity entity=world.getEntity(ref.instance());if(entity==null||entity.isRemoved()&&(entity.getRemovalReason()==null||!entity.getRemovalReason().shouldDestroy()))return new Status(Availability.UNLOADED,false,false);
        if(entity.isRemoved())return new Status(Availability.STALE,false,false);
        if(!(entity instanceof RpObjectEntity rp)||!rp.canBeLinked()||!ref.registryId().isEmpty()&&!ref.registryId().equals(rp.assetId()))return new Status(Availability.STALE,false,false);
        return new Status(Availability.LOADED,rp.isOpen(),rp.isLocked(),rp.isPulseOnlyMechanism());
    }
    public static List<TargetRef> targets(RpObjectEntity source){if(!(source.getWorld() instanceof ServerWorld world))return List.of();loaded(source);MechanismState state=MechanismState.get(world.getServer());MechanismState.Lever lever=state.levers.get(source.getUuid());return MechanismRules.list(world.getServer()).stream().filter(r->r.sources.containsKey(source.getUuid())).flatMap(r->r.targets.values().stream()).distinct().toList();}
    public record SourceRef(UUID instance,String dimension,long root,String asset){}
    public static List<SourceRef> sources(MinecraftServer server,TargetRef target){return MechanismRules.list(server).stream().filter(r->r.targets.containsKey(target.key())).flatMap(r->r.sources.values().stream()).distinct().map(ref->new SourceRef(ref.instance(),ref.dimension(),ref.root(),ref.registryId())).toList();}
    public static boolean pending(MinecraftServer server,TargetRef ref){MechanismState.Target target=MechanismState.get(server).targets.get(ref.key());return target!=null&&target.pending||MechanismRules.pending(server,ref);}
    public static void removed(RpObjectEntity entity){if(!(entity.getWorld() instanceof ServerWorld world))return;MechanismRules.removed(world.getServer(),TargetRef.rp(entity));MechanismState state=MechanismState.get(world.getServer());MechanismState.Lever lever=state.levers.remove(entity.getUuid());if(lever!=null)for(String key:lever.targets)pruneUnused(state,key);forgetTarget(state,TargetRef.rp(entity).key());state.markDirty();}
    public static void removedArchitecture(MinecraftServer server,TargetRef ref){MechanismRules.removed(server,ref);forgetTarget(MechanismState.get(server),ref.key());}
    private static MechanismState.Lever lever(MechanismState state,RpObjectEntity source){if(!state.levers.containsKey(source.getUuid())&&state.levers.size()>=MechanismState.LIMIT)return null;return state.levers.computeIfAbsent(source.getUuid(),id->{state.markDirty();return new MechanismState.Lever(id,source.getWorld().getRegistryKey().getValue().toString(),source.getBlockPos().asLong(),source.assetId());});}
    private static void pruneUnused(MechanismState state,String key){MechanismState.Target target=state.targets.get(key);if(target!=null&&!target.pending&&state.levers.values().stream().noneMatch(lever->lever.targets.contains(key)))state.targets.remove(key);}
    private static void forgetTarget(MechanismState state,String key){state.targets.remove(key);for(MechanismState.Lever lever:state.levers.values())lever.targets.remove(key);if(state.removed.size()<MechanismState.LIMIT)state.removed.add(key);state.markDirty();}
    private static ServerWorld world(MinecraftServer server,String dimension){return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(dimension)));}
    private static void event(ServerWorld world,TargetRef ref,String action,Map<String,Object> before,Map<String,Object> after,String result,String reason){
        if(!dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.enabled(world))return;String type=typeId(ref);BlockPos root=BlockPos.fromLong(ref.root());if(ref.kind()==Kind.ARCHITECTURE&&world.isChunkLoaded(root)){var entry=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(world.getBlockState(root));if(entry!=null)type=entry.temporaryId();}dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.record(world,type,ref.instance().toString(),root,action,before,after,result,reason);
    }
    private static String typeId(TargetRef ref){Identifier registry=Identifier.tryParse(ref.kind()==Kind.RP?"bloodborne_rp:"+(ref.registryId().isEmpty()?"unknown":ref.registryId()):ref.registryId());if(registry==null)return "UNASSIGNED";var entry=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(registry);return entry==null?"UNASSIGNED":entry.temporaryId();}
    public static Map<String,Object> diagnosticsMetrics(ServerWorld world){MechanismState state=MechanismState.observed(world.getServer());return state==null?Map.of("status","NOT_MEASURED_GRAPH_NOT_LOADED"):Map.of("levers",state.levers.size(),"targets",state.targets.size(),"pendingTargets",state.targets.values().stream().filter(t->t.pending).count(),"pendingPulseEffects",state.targets.values().stream().mapToLong(t->t.pendingPulses).sum(),"pendingImports",IMPORT_PENDING.getOrDefault(world.getServer(),Set.of()).size(),"scope","server-wide loaded persistent mechanism graph; no load triggered");}
}
