package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Bounded, loaded-chunk-only maintenance. Interactive transactions remain
 * synchronous; this queue replaces the repeated full transaction preflight
 * formerly performed for every healthy saved helper on chunk load. */
public final class CompositeLoadWork {
    private static final long BUDGET_NANOS=2_000_000L;
    private static final int MAX_STEPS=256;
    private static final Map<ServerWorld,State> WORLDS=new WeakHashMap<>();
    private record Migration(long chunk,String key,Runnable action){}
    private static final class ChunkWork {
        final ArrayDeque<Cell> cells=new ArrayDeque<>();
        ChunkWork(Collection<Cell> cells){this.cells.addAll(cells);}
    }
    private record RootMemo(BlockState state,CompositeBlockEntity entity,Owner resident,Object payload,Map<Cell,Footprint> footprint){}
    private static final class OwnerWork {
        final CompositeData.Contribution saved;
        final Set<Long> sources=new HashSet<>();
        Iterator<Cell> offsets;
        Long deferredRoot;
        boolean restart;
        OwnerWork(CompositeData.Contribution saved){this.saved=saved;}
    }
    private static final class State {
        final Map<Owner,OwnerWork> owners=new HashMap<>();
        final LinkedHashMap<Owner,OwnerWork> ready=new LinkedHashMap<>();
        final Map<Long,Set<Owner>> sourceChunks=new HashMap<>(),deferredRoots=new HashMap<>();
        final LinkedHashMap<String,Migration> migrations=new LinkedHashMap<>();
        final Map<Long,Set<String>> migrationChunks=new HashMap<>();
        final LinkedHashMap<Long,ChunkWork> chunks=new LinkedHashMap<>();
        final Map<Long,Map<Long,Set<Cell>>> waitingCells=new HashMap<>();
        final Map<Long,Set<Long>> sourceDependencies=new HashMap<>();
        // A drain-local memo is discarded before the next game tick. No world
        // graph or decoded membership is cached for previously visited chunks.
        final Map<Owner,RootMemo> roots=new HashMap<>();
        long queuedOwners,duplicateOwners,completedOwners,cancelledOwners,steps,cellChecks,footprints,prunes,deferred,completedMigrations,maxDrainNanos;
        long pendingCells,queuedChunkBatches,completedChunkBatches,cancelledChunkBatches,chunkCellChecks;
        boolean migrationTurn=true;
        void finish(Owner owner){
            OwnerWork work=owners.remove(owner);ready.remove(owner);if(work==null)return;
            for(long chunk:work.sources)remove(sourceChunks,chunk,owner);
            if(work.deferredRoot!=null)remove(deferredRoots,work.deferredRoot,owner);
        }
    }
    private static <T> void remove(Map<Long,Set<T>> index,long key,T value){Set<T> values=index.get(key);if(values!=null){values.remove(value);if(values.isEmpty())index.remove(key);}}
    private static long chunk(Cell cell){return ChunkPos.toLong(cell.x()>>4,cell.z()>>4);}
    private static void mainThread(ServerWorld world){if(!world.getServer().isOnThread())throw new IllegalStateException("Chunk maintenance requires the server thread");}
    private static State state(ServerWorld world){mainThread(world);return WORLDS.computeIfAbsent(world,ignored->new State());}

    public static void migration(ServerWorld world,long sourceChunk,String key,Runnable action){
        State state=state(world);if(state.migrations.putIfAbsent(key,new Migration(sourceChunk,key,action))==null)
            state.migrationChunks.computeIfAbsent(sourceChunk,ignored->new HashSet<>()).add(key);
    }
    /** A saved chunk already enumerates every claimed cell. Revalidating those
     * cells directly avoids owner-to-full-footprint fanout and hundreds of
     * thousands of redundant queued owner/source sets in a dense district. */
    public static void chunkCells(ServerWorld world,long sourceChunk,Map<Cell,List<CompositeData.Contribution>> entries){
        State state=state(world);ChunkWork old=state.chunks.remove(sourceChunk);if(old!=null)state.pendingCells-=old.cells.size();
        cancelDependencies(state,sourceChunk);
        if(entries.isEmpty())return;
        var cells=new ArrayList<>(entries.keySet());Collections.sort(cells);
        state.chunks.put(sourceChunk,new ChunkWork(cells));state.pendingCells+=cells.size();state.queuedChunkBatches++;
    }
    private static void cancelDependencies(State state,long source){
        Set<Long> roots=state.sourceDependencies.remove(source);if(roots==null)return;
        for(long root:roots){var waiting=state.waitingCells.get(root);if(waiting!=null){waiting.remove(source);if(waiting.isEmpty())state.waitingCells.remove(root);}}
    }
    private static void deferCell(State state,long root,long source,Cell cell){
        state.waitingCells.computeIfAbsent(root,ignored->new HashMap<>()).computeIfAbsent(source,ignored->new HashSet<>()).add(cell);
        state.sourceDependencies.computeIfAbsent(source,ignored->new HashSet<>()).add(root);
    }
    public static void owner(ServerWorld world,long sourceChunk,CompositeData.Contribution saved){
        State state=state(world);Owner owner=saved.owner();OwnerWork work=state.owners.get(owner);
        if(work==null){work=new OwnerWork(saved);state.owners.put(owner,work);state.ready.put(owner,work);state.queuedOwners++;}
        else state.duplicateOwners++;
        if(work.sources.add(sourceChunk)){
            state.sourceChunks.computeIfAbsent(sourceChunk,ignored->new HashSet<>()).add(owner);
            if(work.offsets!=null)work.restart=true;
        }
    }
    /** Wakes helper-first work even when the arriving root chunk has no ledger
     * entry for a root that was externally removed. Never resolves its future. */
    public static void chunkLoaded(ServerWorld world,long chunk){
        State state=WORLDS.get(world);if(state==null)return;mainThread(world);
        Set<Owner> waiting=state.deferredRoots.remove(chunk);
        if(waiting!=null)for(Owner owner:waiting){OwnerWork work=state.owners.get(owner);if(work!=null){work.deferredRoot=null;work.offsets=null;work.restart=false;state.ready.putIfAbsent(owner,work);}}
        var cells=state.waitingCells.remove(chunk);
        if(cells!=null)for(var row:cells.entrySet()){
            remove(state.sourceDependencies,row.getKey(),chunk);
            if(world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(row.getKey()),ChunkPos.getPackedZ(row.getKey()))==null)continue;
            ChunkWork work=state.chunks.computeIfAbsent(row.getKey(),ignored->new ChunkWork(List.of()));work.cells.addAll(row.getValue());state.pendingCells+=row.getValue().size();
        }
    }
    public static void chunkUnloaded(ServerWorld world,long chunk){
        State state=WORLDS.get(world);if(state==null)return;mainThread(world);
        ChunkWork batch=state.chunks.remove(chunk);if(batch!=null){state.pendingCells-=batch.cells.size();state.cancelledChunkBatches++;}cancelDependencies(state,chunk);
        Set<String> migrations=state.migrationChunks.remove(chunk);if(migrations!=null)for(String key:migrations)state.migrations.remove(key);
        Set<Owner> owners=state.sourceChunks.remove(chunk);if(owners==null)return;
        for(Owner owner:List.copyOf(owners)){OwnerWork work=state.owners.get(owner);if(work!=null){work.sources.remove(chunk);if(work.sources.isEmpty()){state.finish(owner);state.cancelledOwners++;}}}
        state.roots.clear();
    }
    public static void changed(ServerWorld world){State state=WORLDS.get(world);if(state!=null)state.roots.clear();}
    public static void forget(ServerWorld world){mainThread(world);WORLDS.remove(world);}

    /** Null is an unloaded/deferred root; an empty footprint is proven stale.
     * This performs the same registry/resident/geometry checks as inspectOwner,
     * without serializing a transaction BlockData for every helper cell. */
    private static Map<Cell,Footprint> footprint(ServerWorld world,State state,Owner owner){
        var pos=CompositeData.pos(owner.root());var loaded=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
        if(loaded==null)return null;
        BlockState actual=loaded.getBlockState(pos);
        if(!VerticalMount.isArchitecture(actual)||!net.minecraft.registry.Registries.BLOCK.getId(actual.getBlock()).toString().equals(owner.registryId()))return Map.of();
        if(!(loaded.getBlockEntity(pos) instanceof CompositeBlockEntity entity)||!owner.equals(entity.resident()))return Map.of();
        RootMemo memo=state.roots.get(owner);Object payload=entity.cleanupPayloadRevision();
        if(memo!=null&&memo.state==actual&&memo.entity==entity&&memo.resident==entity.resident()&&memo.payload==payload)return memo.footprint;
        Map<Cell,Footprint> cells=entity.cleanupFootprint(world);state.footprints++;
        state.roots.put(owner,new RootMemo(actual,entity,entity.resident(),payload,cells));return cells;
    }
    private static boolean needsPrune(ServerWorld world,State state,Cell cell){return needsPrune(world,state,cell,null);}
    private static boolean needsPrune(ServerWorld world,State state,Cell cell,Long source){
        var pos=CompositeData.pos(cell);var entries=CompositeLedger.get(world).at(cell);
        if(entries.isEmpty()){
            if(!world.getBlockState(pos).isOf(CompositeArchitecture.CELL))return false;
            if(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity)entries=entity.contributions();
            if(entries.isEmpty())return true;
        }
        boolean stale=false;
        for(var entry:entries){Owner owner=entry.owner();if(ImportedCollisionMigration.pending(world,owner))continue;
            Map<Cell,Footprint> cells=footprint(world,state,owner);
            if(cells==null&&source!=null)deferCell(state,chunk(owner.root()),source,cell);
            if(cells!=null&&!cells.containsKey(cell.subtract(owner.root())))stale=true;
        }
        return stale;
    }
    /** One cell is a scheduling unit; a real prune remains an atomic existing
     * transaction, so a budget boundary never exposes half of a write set. */
    private static void stepOwner(ServerWorld world,State state,Owner owner,OwnerWork work){
        if(ImportedCollisionMigration.pending(world,owner)){state.finish(owner);state.completedOwners++;return;}
        if(world.getChunkManager().getWorldChunk(owner.root().x()>>4,owner.root().z()>>4)==null){
            long rootChunk=chunk(owner.root());work.deferredRoot=rootChunk;
            state.deferredRoots.computeIfAbsent(rootChunk,ignored->new HashSet<>()).add(owner);state.deferred++;return;
        }
        if(work.offsets==null){
            Map<Cell,Footprint> saved=CompositeLedger.get(world).savedShardMemberships(Set.of(owner)).get(owner);
            if(saved==null){
                BlockState source=CompositeData.state(work.saved.rootData());
                if(!VerticalMount.isArchitecture(source)){state.finish(owner);state.completedOwners++;return;}
                var payload=CompositeData.nbt(work.saved.rootData().blockEntityNbt());
                saved=source.getBlock() instanceof CompositeRootBlock block?CompositeSourceShift.footprint(block.spec,source,payload):VerticalMount.nativeFootprint(world,CompositeData.pos(owner.root()),source,payload);
            }
            work.offsets=saved.keySet().iterator();
        }
        if(work.offsets.hasNext()){
            Cell cell=owner.root().add(work.offsets.next());
            if(world.isChunkLoaded(CompositeData.pos(cell))){state.cellChecks++;
                if(needsPrune(world,state,cell)){
                    state.prunes++;var access=new FabricCompositeWorld(world,Map.of());
                    CompositeRuntime.execute(access,CompositeRuntime.core().pruneStale(access,cell));
                }
            }
        }
        if(work.offsets.hasNext())state.ready.put(owner,work);
        else if(work.restart){work.offsets=null;work.restart=false;state.ready.put(owner,work);}
        else{state.finish(owner);state.completedOwners++;}
    }
    public static void drain(ServerWorld world){
        mainThread(world);State state=WORLDS.get(world);if(state==null)return;
        long started=System.nanoTime();int processed=0;
        try{
            while(processed<MAX_STEPS&&(processed==0||System.nanoTime()-started<BUDGET_NANOS)){
                if(state.ready.isEmpty()&&state.chunks.isEmpty()&&state.migrations.isEmpty())break;
                boolean migration=!state.migrations.isEmpty()&&((state.ready.isEmpty()&&state.chunks.isEmpty())||state.migrationTurn);state.migrationTurn=!state.migrationTurn;
                if(migration){
                    var iterator=state.migrations.entrySet().iterator();Migration next=iterator.next().getValue();iterator.remove();remove(state.migrationChunks,next.chunk,next.key);
                    if(world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(next.chunk),ChunkPos.getPackedZ(next.chunk))!=null)next.action.run();
                    state.completedMigrations++;
                }else if(!state.chunks.isEmpty()){
                    var iterator=state.chunks.entrySet().iterator();var row=iterator.next();long source=row.getKey();ChunkWork work=row.getValue();iterator.remove();
                    if(world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(source),ChunkPos.getPackedZ(source))==null){state.pendingCells-=work.cells.size();cancelDependencies(state,source);state.cancelledChunkBatches++;}
                    else{
                        // A small consecutive quantum reuses root geometry for
                        // neighbouring saved helper cells, with a budget check
                        // after each atomic prune/validation.
                        int quantum=0;
                        do{
                            Cell cell=work.cells.removeFirst();state.pendingCells--;state.cellChecks++;state.chunkCellChecks++;
                            if(needsPrune(world,state,cell,source)){state.prunes++;var access=new FabricCompositeWorld(world,Map.of());CompositeRuntime.execute(access,CompositeRuntime.core().pruneStale(access,cell));}
                            processed++;state.steps++;quantum++;
                        }while(!work.cells.isEmpty()&&quantum<32&&processed<MAX_STEPS&&System.nanoTime()-started<BUDGET_NANOS);
                        if(!work.cells.isEmpty())state.chunks.put(source,work);else state.completedChunkBatches++;
                        continue;
                    }
                }else{
                    var iterator=state.ready.entrySet().iterator();var next=iterator.next();Owner owner=next.getKey();OwnerWork work=next.getValue();iterator.remove();
                    stepOwner(world,state,owner,work);
                }
                processed++;state.steps++;
            }
        }finally{state.roots.clear();state.maxDrainNanos=Math.max(state.maxDrainNanos,System.nanoTime()-started);}
    }
    public static Map<String,Object> metrics(ServerWorld world){
        State state=WORLDS.get(world);Map<String,Object> out=new LinkedHashMap<>();
        out.put("budgetNanos",BUDGET_NANOS);out.put("maxStepsPerDrain",MAX_STEPS);
        out.put("pendingOwners",state==null?0:state.owners.size());out.put("readyOwners",state==null?0:state.ready.size());out.put("deferredRootChunks",state==null?0:state.deferredRoots.size());out.put("pendingMigrations",state==null?0:state.migrations.size());
        out.put("pendingChunkBatches",state==null?0:state.chunks.size());out.put("pendingChunkCells",state==null?0L:state.pendingCells);out.put("deferredCellRootChunks",state==null?0:state.waitingCells.size());
        out.put("deferredCells",state==null?0:state.waitingCells.values().stream().flatMap(m->m.values().stream()).mapToInt(Set::size).sum());
        out.put("queuedChunkBatches",state==null?0L:state.queuedChunkBatches);out.put("completedChunkBatches",state==null?0L:state.completedChunkBatches);out.put("cancelledChunkBatches",state==null?0L:state.cancelledChunkBatches);out.put("chunkCellChecks",state==null?0L:state.chunkCellChecks);
        out.put("queuedOwners",state==null?0L:state.queuedOwners);out.put("duplicateOwners",state==null?0L:state.duplicateOwners);out.put("completedOwners",state==null?0L:state.completedOwners);out.put("cancelledOwners",state==null?0L:state.cancelledOwners);
        out.put("steps",state==null?0L:state.steps);out.put("cellChecks",state==null?0L:state.cellChecks);out.put("footprintsBuilt",state==null?0L:state.footprints);out.put("pruneTransactions",state==null?0L:state.prunes);out.put("deferredOwners",state==null?0L:state.deferred);out.put("completedMigrations",state==null?0L:state.completedMigrations);out.put("maxDrainNanos",state==null?0L:state.maxDrainNanos);
        return Collections.unmodifiableMap(out);
    }
    private CompositeLoadWork(){}
}
