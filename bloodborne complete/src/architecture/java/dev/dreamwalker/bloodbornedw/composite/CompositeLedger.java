package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Foreign carriers retain their real block and real BE. Only owner contributions live here. */
public final class CompositeLedger extends PersistentState {
    private final Map<Cell,List<CompositeData.Contribution>> cells=new TreeMap<>();
    private java.lang.ref.WeakReference<World> boundWorld=new java.lang.ref.WeakReference<>(null);
    private boolean sharded;
    private boolean clientSharded;
    private final Map<Long,Set<Cell>> clientChunks=new HashMap<>();
    private final Set<Long> knownShards=new TreeSet<>();
    private ShardedCompositeLedger shards;
    private static boolean shardEventsRegistered;
    private static final Map<World,CompositeLedger> CLIENT=Collections.synchronizedMap(new WeakHashMap<>());
    public static CompositeLedger get(World world){CompositeLedger ledger=world instanceof ServerWorld server?server.getPersistentStateManager().getOrCreate(CompositeLedger::read,CompositeLedger::new,"bloodborne_dw_composite_owners"):CLIENT.computeIfAbsent(world,unused->new CompositeLedger());if(ledger.boundWorld.get()!=world){ledger.boundWorld=new java.lang.ref.WeakReference<>(world);if(ledger.sharded){if(!(world instanceof ServerWorld server))throw new IllegalStateException("Saved owner shard mode requires a server world");ledger.bindShards(server);}else CompositeShapeSnapshots.ledgerLoaded(world,ledger.cells);}registerShardEvents();return ledger;}
    private void bindShards(ServerWorld world){
        var directory=net.minecraft.world.dimension.DimensionType.getSaveDirectory(world.getRegistryKey(),world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)).resolve("data/bloodborne_dw_owner_shards");
        shards=new ShardedCompositeLedger(world,directory,knownShards,this::markDirty);
    }
    private static synchronized void registerShardEvents(){if(shardEventsRegistered)return;shardEventsRegistered=true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{var ledger=get(world);if(ledger.sharded)ledger.shards.unload(chunk.getPos());});
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world->{var ledger=get(world);if(ledger.sharded)ledger.shards.trimUnloaded();});
    }
    /** Conversion opt-in is permitted only before the first owner is installed.
     * Existing global saves (including the8740-owner V12.6 gallery) remain exact. */
    public static void enableSharding(ServerWorld world){if(!world.getServer().isOnThread())throw new IllegalStateException("Owner shard opt-in needs server thread");var ledger=get(world);if(ledger.sharded)return;if(!ledger.cells.isEmpty())throw new IllegalStateException("Owner sharding requires an empty conversion ledger; existing saves are not rewritten");ledger.sharded=true;ledger.bindShards(world);ledger.markDirty();}
    public static void markClientSharded(World world){if(world instanceof ServerWorld)throw new IllegalArgumentException("Client shard flag requires a client world");var ledger=get(world);if(ledger.clientSharded)return;ledger.clientSharded=true;for(var cell:ledger.cells.keySet())ledger.clientChunks.computeIfAbsent(net.minecraft.util.math.ChunkPos.toLong(cell.x()>>4,cell.z()>>4),ignored->new HashSet<>()).add(cell);}
    public static void forgetClientChunk(World world,net.minecraft.util.math.ChunkPos chunk){var ledger=get(world);if(!ledger.clientSharded)return;for(var cell:ledger.clientChunks.getOrDefault(chunk.toLong(),Set.of()))ledger.cells.remove(cell);ledger.clientChunks.remove(chunk.toLong());CompositeShapeSnapshots.forgetChunk(world,chunk.toLong());}
    public boolean sharded(){return sharded;}
    public void prepareChunk(net.minecraft.util.math.ChunkPos chunk){if(sharded)shards.prepare(chunk);}
    /** The chunk-load event owns this actual chunk even before the FULL lookup is available. */
    public Map<Cell,List<CompositeData.Contribution>> loadedChunkEntries(net.minecraft.world.chunk.WorldChunk actualChunk){
        World world=boundWorld.get();if(actualChunk==null||actualChunk.getWorld()!=world)throw new IllegalArgumentException("Owner load callback chunk belongs to a different world");
        if(sharded)return shards.loadedChunkEntries(actualChunk);var chunk=actualChunk.getPos();Map<Cell,List<CompositeData.Contribution>> result=new TreeMap<>();
        for(var row:cells.entrySet())if((row.getKey().x()>>4)==chunk.x&&(row.getKey().z()>>4)==chunk.z)result.put(row.getKey(),row.getValue());return Map.copyOf(result);
    }
    public void flushShards(){if(sharded)shards.flush();}
    public Set<Long> knownShardChunks(){return sharded?shards.knownChunks():Set.of();}
    public Map<String,Object> shardMetrics(){return sharded?shards.metrics():Map.of("shardedOwnerMode",false,"globalOwnerCells",cells.size());}
    public Map<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner,Map<Cell,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint>> savedShardMemberships(Set<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner> owners){return sharded?shards.savedMemberships(owners):Map.of();}
    public List<CompositeData.Contribution> at(Cell cell){return sharded?shards.at(cell):cells.getOrDefault(cell,List.of());}
    public void put(Cell cell,List<CompositeData.Contribution> entries){if(sharded){shards.put(cell,entries);markDirty();return;}if(entries.isEmpty())cells.remove(cell);else cells.put(cell,List.copyOf(entries));if(clientSharded){long key=net.minecraft.util.math.ChunkPos.toLong(cell.x()>>4,cell.z()>>4);if(entries.isEmpty()){var known=clientChunks.get(key);if(known!=null){known.remove(cell);if(known.isEmpty())clientChunks.remove(key);}}else clientChunks.computeIfAbsent(key,ignored->new HashSet<>()).add(cell);}markDirty();World world=boundWorld.get();if(world!=null)CompositeShapeSnapshots.ledger(world,cell,entries);}
    public Set<Cell> cells(){return sharded?shards.loadedCells():Set.copyOf(cells.keySet());}
    /** Chunk-watch synchronization must not scan or send the whole ledger. */
    public Set<Cell> chunkCells(net.minecraft.util.math.ChunkPos chunk){if(sharded)return shards.chunkCells(chunk);var result=new TreeSet<Cell>();for(var cell:cells.keySet())if((cell.x()>>4)==chunk.x&&(cell.z()>>4)==chunk.z)result.add(cell);return Set.copyOf(result);}
    public static CompositeLedger read(NbtCompound nbt){CompositeLedger state=new CompositeLedger();net.minecraft.util.math.BlockPos current=null;try{NbtList entries=nbt.getList("cells",10);for(int i=0;i<entries.size();i++){NbtCompound row=entries.getCompound(i);int[] p=row.getIntArray("pos");if(p.length!=3)throw new IllegalArgumentException("Invalid composite ledger cell");current=new net.minecraft.util.math.BlockPos(p[0],p[1],p[2]);state.cells.put(new Cell(p[0],p[1],p[2]),CompositeData.contributions(row.getList("owners",10)));}if(nbt.contains("OwnerShardSchema")){if(nbt.getInt("OwnerShardSchema")!=ShardedCompositeLedger.SCHEMA||!state.cells.isEmpty())throw new IllegalArgumentException("Invalid owner shard metadata or mixed global/sharded save");state.sharded=true;for(long key:nbt.getLongArray("OwnerShardChunks"))if(!state.knownShards.add(key))throw new IllegalArgumentException("Repeated owner shard index");}return state;}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Cannot read saved owner cell: "+failure.getMessage(), failure);throw failure;}}
    @Override public NbtCompound writeNbt(NbtCompound nbt){net.minecraft.util.math.BlockPos current=null;try{if(sharded){if(shards==null)throw new IllegalStateException("Unbound sharded owner metadata");shards.flush();nbt.putInt("OwnerShardSchema",ShardedCompositeLedger.SCHEMA);nbt.putLongArray("OwnerShardChunks",knownShards.stream().mapToLong(Long::longValue).toArray());nbt.put("cells",new NbtList());return nbt;}NbtList entries=new NbtList();for(var entry:cells.entrySet()){NbtCompound row=new NbtCompound();Cell p=entry.getKey();current=CompositeData.pos(p);row.putIntArray("pos",new int[]{p.x(),p.y(),p.z()});row.put("owners",CompositeData.contributions(entry.getValue()));entries.add(row);}nbt.put("cells",entries);return nbt;}catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Cannot encode saved owner cell: "+failure.getMessage(), failure);throw failure;}}
}
