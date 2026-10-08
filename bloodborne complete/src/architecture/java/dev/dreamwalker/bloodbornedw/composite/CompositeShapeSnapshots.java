package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Published geometry inputs, never a World or BlockEntity reference. Lighting workers
 * must not ask the server to finish the chunk whose lighting they are completing.
 * Mutation batches publish one immutable revision; only touched chunk maps are copied.
 * Storage is bounded by saved ledger cells plus actual attached composite BEs. Queries
 * never insert cells, force chunks, inspect world ownership, or queue cleanup. */
public final class CompositeShapeSnapshots {
    private static final Map<World,Published> WORLDS=new WeakHashMap<>();
    public record Root(UUID token,Owner resident,double offset,BlockPos sourceRoot) {}
    private record CellData(List<CompositeData.Contribution> ledger,List<CompositeData.Contribution> entity,Root root) {
        private static final CellData EMPTY=new CellData(List.of(),List.of(),null);
        List<CompositeData.Contribution> entries(){return ledger.isEmpty()?entity:ledger;}
        boolean empty(){return ledger.isEmpty()&&entity.isEmpty()&&root==null;}
    }
    private static final class Published {
        volatile Map<Long,Map<Cell,CellData>> chunks=Map.of();
        final Map<Cell,CellData> pending=new HashMap<>();
        int batchDepth;
        CellData at(Cell cell){return chunks.getOrDefault(chunk(cell),Map.of()).getOrDefault(cell,CellData.EMPTY);}
        synchronized CellData current(Cell cell){return pending.getOrDefault(cell,at(cell));}
        synchronized void change(Cell cell,List<CompositeData.Contribution> ledger,List<CompositeData.Contribution> entity,Root root,boolean changeLedger,boolean changeEntity,boolean batch){
            CellData old=pending.getOrDefault(cell,at(cell));
            pending.put(cell,new CellData(changeLedger?List.copyOf(ledger):old.ledger,changeEntity?List.copyOf(entity):old.entity,changeEntity?root:old.root));
            if(!batch)flush();
        }
        synchronized void retire(Cell cell,UUID token,boolean batch){
            CellData old=pending.getOrDefault(cell,at(cell));
            if(old.root==null||!old.root.token().equals(token))return;
            pending.put(cell,new CellData(old.ledger,List.of(),null));if(!batch)flush();
        }
        synchronized void flush(){
            if(batchDepth>0)return;
            if(pending.isEmpty())return;
            Map<Long,Map<Cell,CellData>> next=new HashMap<>(chunks);
            Map<Long,Map<Cell,CellData>> changed=new HashMap<>();
            for(var row:pending.entrySet()){
                long key=chunk(row.getKey());Map<Cell,CellData> cells=changed.computeIfAbsent(key,ignored->new HashMap<>(next.getOrDefault(key,Map.of())));
                if(row.getValue().empty())cells.remove(row.getKey());else cells.put(row.getKey(),row.getValue());
            }
            for(var row:changed.entrySet())if(row.getValue().isEmpty())next.remove(row.getKey());else next.put(row.getKey(),Map.copyOf(row.getValue()));
            chunks=Map.copyOf(next);pending.clear();
        }
        synchronized void begin(){batchDepth++;}
        synchronized void end(){if(batchDepth<=0)throw new IllegalStateException("Unbalanced shape publication batch");batchDepth--;flush();}
    }
    private static long chunk(Cell cell){return net.minecraft.util.math.ChunkPos.toLong(cell.x()>>4,cell.z()>>4);}
    private static synchronized Published cache(World world,boolean create){return create?WORLDS.computeIfAbsent(world,ignored->new Published()):WORLDS.get(world);}
    public static boolean worker(BlockView view){return view instanceof ServerWorld world&&!world.getServer().isOnThread();}
    private static CellData data(BlockView view,BlockPos pos){if(!(view instanceof World world)||pos==null)return CellData.EMPTY;Published cache=cache(world,false);return cache==null?CellData.EMPTY:worker(view)?cache.at(CompositeData.cell(pos)):cache.current(CompositeData.cell(pos));}
    public static List<CompositeData.Contribution> contributions(BlockView view,BlockPos pos){return data(view,pos).entries();}
    public static Owner owner(BlockView view,BlockPos pos){CellData data=data(view,pos);if(data.root!=null)return data.root.resident();for(var entry:data.entries())if(entry.owner().root().equals(CompositeData.cell(pos)))return entry.owner();return null;}
    /** Known replacement UUIDs are rejected. Missing/unloaded root metadata is
     * deferred, just as an unloaded owner is on the authoritative main thread. */
    public static boolean validOrDeferred(BlockView view,Owner owner){Root root=data(view,CompositeData.pos(owner.root())).root;return root==null||owner.equals(root.resident());}
    public static double offset(BlockView view,BlockPos pos){CellData data=data(view,pos);if(data.root!=null)return data.root.offset();for(var entry:data.entries())if(entry.owner().root().equals(CompositeData.cell(pos)))return offset(entry);return 0;}
    public static boolean shiftedSourceBacking(BlockView view,BlockPos pos){CellData data=data(view,pos);if(data.root!=null&&data.root.sourceRoot()!=null)return offset(view,data.root.sourceRoot())!=0;for(var entry:data.entries())if("true".equals(entry.rootData().properties().get("source_clone"))&&offset(entry)!=0)return true;return false;}
    private static double offset(CompositeData.Contribution entry){return CompositeData.nbt(entry.rootData().blockEntityNbt()).getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);}
    public static void ledger(World world,Cell cell,List<CompositeData.Contribution> entries){cache(world,true).change(cell,entries,List.of(),null,true,false,CompositeRuntime.writing());}
    public static void ledgerLoaded(World world,Map<Cell,List<CompositeData.Contribution>> cells){Published cache=cache(world,true);synchronized(cache){for(var row:cells.entrySet())cache.change(row.getKey(),row.getValue(),List.of(),null,true,false,true);cache.flush();}}
    public static void entity(World world,BlockPos pos,UUID token,Owner owner,List<CompositeData.Contribution> entries,NbtCompound payload,BlockPos sourceRoot){
        if(world==null)return;Root root=new Root(token,owner,payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY),sourceRoot==null?null:sourceRoot.toImmutable());
        cache(world,true).change(CompositeData.cell(pos),List.of(),entries,root,false,true,CompositeRuntime.writing());
    }
    public static void retired(World world,BlockPos pos,UUID token){if(world==null)return;Published cache=cache(world,false);if(cache!=null)cache.retire(CompositeData.cell(pos),token,CompositeRuntime.writing());}
    public static void flush(World world){Published cache=cache(world,false);if(cache!=null)cache.flush();}
    public static void beginBatch(World world){cache(world,true).begin();}
    public static void endBatch(World world){cache(world,true).end();}
    public static synchronized void forget(World world){WORLDS.remove(world);}
    public static Map<String,Object> metrics(World world){Published cache=cache(world,false);if(cache==null)return Map.of("shapeSnapshotChunks",0,"shapeSnapshotCells",0,"shapeSnapshotPendingCells",0,"shapeSnapshotBatchDepth",0);synchronized(cache){var chunks=cache.chunks;return Map.of("shapeSnapshotChunks",chunks.size(),"shapeSnapshotCells",chunks.values().stream().mapToInt(Map::size).sum(),"shapeSnapshotPendingCells",cache.pending.size(),"shapeSnapshotBatchDepth",cache.batchDepth,"shapeSnapshotScope","immutable saved owner cells plus attached composite BE metadata; pending revision cells and nested atomic batch depth; no query-created entries");}}
    private CompositeShapeSnapshots(){}
}
