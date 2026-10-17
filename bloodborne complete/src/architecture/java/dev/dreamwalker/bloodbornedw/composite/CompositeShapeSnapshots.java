package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** Published geometry inputs, never a World or BlockEntity reference. Lighting workers
 * must not ask the server to finish the chunk whose lighting they are completing.
 * Mutation batches publish one immutable revision; only touched chunk maps are copied.
 * Storage is bounded by saved ledger cells plus actual attached composite BEs. Queries
 * never insert cells, force chunks, inspect world ownership, or queue cleanup. */
public final class CompositeShapeSnapshots {
    private static final Map<World,Published> WORLDS=new WeakHashMap<>();
    public record Root(UUID token,Owner resident,double offset,BlockPos sourceRoot,boolean decorativePlacement) {}
    private record CellData(List<CompositeData.Contribution> ledger,List<CompositeData.Contribution> entity,Root root,int sourceOpacity) {
        private static final CellData EMPTY=new CellData(List.of(),List.of(),null,0);
        List<CompositeData.Contribution> entries(){return ledger.isEmpty()?entity:ledger;}
        boolean empty(){return ledger.isEmpty()&&entity.isEmpty()&&root==null;}
    }
    private static final class Published {
        volatile Map<Long,Map<Cell,CellData>> chunks=Map.of();
        final Map<Cell,CellData> pending=new HashMap<>();
        int batchDepth;
        long sourceCarrierLightChecks;
        CellData at(Cell cell){return chunks.getOrDefault(chunk(cell),Map.of()).getOrDefault(cell,CellData.EMPTY);}
        synchronized CellData current(Cell cell){return pending.getOrDefault(cell,at(cell));}
        synchronized void change(World world,Cell cell,List<CompositeData.Contribution> ledger,List<CompositeData.Contribution> entity,Root root,boolean changeLedger,boolean changeEntity,boolean batch){
            CellData old=pending.getOrDefault(cell,at(cell));
            var nextLedger=changeLedger?List.copyOf(ledger):old.ledger;var nextEntity=changeEntity?List.copyOf(entity):old.entity;
            pending.put(cell,new CellData(nextLedger,nextEntity,changeEntity?root:old.root,ImportedWholeSourceOpticsV127.publishedOpacity(cell,nextLedger.isEmpty()?nextEntity:nextLedger)));
            if(!batch)flush(world);
        }
        synchronized void retire(World world,Cell cell,UUID token,boolean batch){
            CellData old=pending.getOrDefault(cell,at(cell));
            if(old.root==null||!old.root.token().equals(token))return;
            pending.put(cell,new CellData(old.ledger,List.of(),null,ImportedWholeSourceOpticsV127.publishedOpacity(cell,old.ledger)));if(!batch)flush(world);
        }
        synchronized void flush(World world){
            if(batchDepth>0)return;
            if(pending.isEmpty())return;
            Map<Long,Map<Cell,CellData>> next=new HashMap<>(chunks);
            Map<Long,Map<Cell,CellData>> changed=new HashMap<>();
            List<Cell> sourceLightChanges=new ArrayList<>();
            for(var row:pending.entrySet()){
                CellData old=at(row.getKey()),value=row.getValue();
                if(old.sourceOpacity!=value.sourceOpacity||decorative(old)!=decorative(value))sourceLightChanges.add(row.getKey());
                long key=chunk(row.getKey());Map<Cell,CellData> cells=changed.computeIfAbsent(key,ignored->new HashMap<>(next.getOrDefault(key,Map.of())));
                if(row.getValue().empty())cells.remove(row.getKey());else cells.put(row.getKey(),row.getValue());
            }
            // Both maps are private, newly allocated revision data. No writer
            // retains their mutable reference after publication. Wrapping them
            // keeps the same immutable snapshot without rebuilding MapN's
            // linear-probing table: adjacent packed ChunkPos long keys have
            // heavily colliding hashes (x ^ z), particularly on a city grid.
            for(var row:changed.entrySet())if(row.getValue().isEmpty())next.remove(row.getKey());else next.put(row.getKey(),Collections.unmodifiableMap(row.getValue()));
            chunks=Collections.unmodifiableMap(next);pending.clear();
            // A helper or manual decorative root may retain the identical block
            // state while its published instance opacity changes. Recheck those cells,
            // after the complete immutable revision is visible to light workers.
            if(world instanceof ServerWorld server&&!sourceLightChanges.isEmpty()){
                List<Cell> checks=List.copyOf(sourceLightChanges);
                Runnable recheck=()->{for(var cell:checks){var pos=CompositeData.pos(cell);if(server.isChunkLoaded(pos)){
                    var chunk=server.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);
                    // getOpacity can change with the identical saved block state.
                    // Repair only this loaded column's native direct-sky boundary
                    // before enqueueing ordinary nibble propagation; no full scan.
                    if(chunk!=null)chunk.getChunkSkyLight().isSkyLightAccessible(chunk,pos.getX()&15,pos.getY(),pos.getZ()&15);
                    server.getChunkManager().getLightingProvider().checkBlock(pos);synchronized(this){sourceCarrierLightChecks++;}
                }}};
                // Chunk BE attachment/deserialization may publish on a worker.
                // Publish first; defer all loaded-chunk/lighting access without
                // waiting for the server or forcing the unfinished chunk.
                if(server.getServer().isOnThread())recheck.run();else server.getServer().execute(recheck);
            }
        }
        synchronized void begin(){batchDepth++;}
        synchronized void end(World world){if(batchDepth<=0)throw new IllegalStateException("Unbalanced shape publication batch");batchDepth--;flush(world);}
        synchronized void forgetChunk(long key){
            if(batchDepth>0)throw new IllegalStateException("Cannot evict a shape shard during an atomic publication batch");
            pending.keySet().removeIf(cell->chunk(cell)==key);Map<Long,Map<Cell,CellData>> next=new HashMap<>(chunks);next.remove(key);chunks=Collections.unmodifiableMap(next);
        }
    }
    private static long chunk(Cell cell){return net.minecraft.util.math.ChunkPos.toLong(cell.x()>>4,cell.z()>>4);}
    private static boolean decorative(CellData data){return data.root!=null&&data.root.decorativePlacement();}
    private static synchronized Published cache(World world,boolean create){return create?WORLDS.computeIfAbsent(world,ignored->new Published()):WORLDS.get(world);}
    public static boolean worker(BlockView view){return view instanceof ServerWorld world&&!world.getServer().isOnThread();}
    private static CellData data(BlockView view,BlockPos pos){
        // Vanilla ChunkSkyLight queries its existing WorldChunk with local X/Z.
        // getWorld/getPos are field getters: resolve the same immutable scalar,
        // never chunk contents, BE data, disk or an unfinished chunk future.
        if(pos!=null&&view instanceof WorldChunk chunk)return data(chunk.getWorld(),new BlockPos(chunk.getPos().getStartX()+(pos.getX()&15),pos.getY(),chunk.getPos().getStartZ()+(pos.getZ()&15)));
        if(!(view instanceof World world)||pos==null)return CellData.EMPTY;Published cache=cache(world,false);return cache==null?CellData.EMPTY:worker(view)?cache.at(CompositeData.cell(pos)):cache.current(CompositeData.cell(pos));
    }
    public static List<CompositeData.Contribution> contributions(BlockView view,BlockPos pos){return data(view,pos).entries();}
    /** A light worker reads the already published scalar; no owner NBT, registry,
     * chunk, block entity or persistent state is read during this query. */
    public static int sourceCarrierOpacity(BlockView view,BlockPos pos){
        if(pos!=null&&view instanceof ProjectedBlockView projected)return data(projected.backingWorld(),projected.worldPosition(pos)).sourceOpacity;
        return data(view,pos).sourceOpacity;
    }
    /** Same scalar-only route as source helpers, including projected native
     * model views. No NBT/BE reads or query-created cache entries on workers. */
    public static boolean decorativePlacement(BlockView view,BlockPos pos){
        if(pos!=null&&view instanceof ProjectedBlockView projected)return decorative(data(projected.backingWorld(),projected.worldPosition(pos)));
        return decorative(data(view,pos));
    }
    public static Owner owner(BlockView view,BlockPos pos){CellData data=data(view,pos);if(data.root!=null)return data.root.resident();for(var entry:data.entries())if(entry.owner().root().equals(CompositeData.cell(pos)))return entry.owner();return null;}
    /** Existing attached native roots also have owners before their first height edit. */
    public static Set<Owner> attachedOwners(World world){Published published=cache(world,false);if(published==null)return Set.of();Set<Owner> owners=new HashSet<>();for(var cells:published.chunks.values())for(var data:cells.values())if(data.root()!=null&&data.root().resident()!=null)owners.add(data.root().resident());return Set.copyOf(owners);}
    /** Known replacement UUIDs are rejected. Missing/unloaded root metadata is
     * deferred, just as an unloaded owner is on the authoritative main thread. */
    public static boolean validOrDeferred(BlockView view,Owner owner){Root root=data(view,CompositeData.pos(owner.root())).root;return root==null||owner.equals(root.resident());}
    public static double offset(BlockView view,BlockPos pos){CellData data=data(view,pos);if(data.root!=null)return data.root.offset();for(var entry:data.entries())if(entry.owner().root().equals(CompositeData.cell(pos)))return offset(entry);return 0;}
    public static boolean shiftedSourceBacking(BlockView view,BlockPos pos){CellData data=data(view,pos);if(data.root!=null&&data.root.sourceRoot()!=null)return offset(view,data.root.sourceRoot())!=0;for(var entry:data.entries())if("true".equals(entry.rootData().properties().get("source_clone"))&&offset(entry)!=0)return true;return false;}
    private static double offset(CompositeData.Contribution entry){return CompositeData.nbt(entry.rootData().blockEntityNbt()).getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);}
    public static void ledger(World world,Cell cell,List<CompositeData.Contribution> entries){cache(world,true).change(world,cell,entries,List.of(),null,true,false,CompositeRuntime.writing());}
    public static void ledgerLoaded(World world,Map<Cell,List<CompositeData.Contribution>> cells){Published cache=cache(world,true);synchronized(cache){for(var row:cells.entrySet())cache.change(world,row.getKey(),row.getValue(),List.of(),null,true,false,true);cache.flush(world);}}
    public static void entity(World world,BlockPos pos,UUID token,Owner owner,List<CompositeData.Contribution> entries,NbtCompound payload,BlockPos sourceRoot){
        if(world==null)return;boolean decorative=owner!=null&&owner.registryId().startsWith("bloodborne_dw:source_")&&payload.contains(ImportedSourceLightingV127.DECORATIVE_PLACEMENT,3)&&payload.getInt(ImportedSourceLightingV127.DECORATIVE_PLACEMENT)==1;
        Root root=new Root(token,owner,payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY),sourceRoot==null?null:sourceRoot.toImmutable(),decorative);
        cache(world,true).change(world,CompositeData.cell(pos),List.of(),entries,root,false,true,CompositeRuntime.writing());
    }
    public static void retired(World world,BlockPos pos,UUID token){if(world==null)return;Published cache=cache(world,false);if(cache!=null)cache.retire(world,CompositeData.cell(pos),token,CompositeRuntime.writing());}
    public static void flush(World world){Published cache=cache(world,false);if(cache!=null)cache.flush(world);}
    public static void beginBatch(World world){cache(world,true).begin();}
    public static void endBatch(World world){cache(world,true).end(world);}
    public static synchronized void forget(World world){WORLDS.remove(world);}
    /** Opt-in server shard eviction; worker readers retain an immutable old
     * revision safely until their current query finishes, without disk access. */
    public static void forgetChunk(World world,long chunk){Published cache=cache(world,false);if(cache!=null)cache.forgetChunk(chunk);}
    public static Map<String,Object> metrics(World world){Published cache=cache(world,false);if(cache==null)return Map.of("shapeSnapshotChunks",0,"shapeSnapshotCells",0,"shapeSnapshotPendingCells",0,"shapeSnapshotBatchDepth",0,"sourceCarrierLightChecks",0L);synchronized(cache){var chunks=cache.chunks;return Map.of("shapeSnapshotChunks",chunks.size(),"shapeSnapshotCells",chunks.values().stream().mapToInt(Map::size).sum(),"shapeSnapshotPendingCells",cache.pending.size(),"shapeSnapshotBatchDepth",cache.batchDepth,"sourceCarrierLightChecks",cache.sourceCarrierLightChecks,"shapeSnapshotScope","immutable saved owner cells plus attached composite BE metadata; pending revision cells and nested atomic batch depth; no query-created entries");}}
    private CompositeShapeSnapshots(){}
}
