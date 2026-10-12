package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/** Opt-in storage for a fresh whole-map conversion. No PersistentState per
 * chunk is registered: that manager would retain every visited shard forever.
 * Geometry queries on lighting workers still use CompositeShapeSnapshots only.
 * Existing global-ledger worlds never instantiate this backend. */
public final class ShardedCompositeLedger {
    public static final int SCHEMA=1,MAX_CACHED_SHARDS=1024;
    private static final class Shard {
        final Map<Cell,List<CompositeData.Contribution>> cells=new TreeMap<>();boolean dirty;
        final Map<Owner,Map<Cell,Footprint>> membership=new TreeMap<>();
    }
    private final ServerWorld world;
    private final Path directory;
    private final Set<Long> known;
    private final Runnable indexChanged;
    private final Map<Long,Shard> cached=new LinkedHashMap<>(16,.75f,true);
    private long reads,writes;

    ShardedCompositeLedger(ServerWorld world,Path directory,Set<Long> known,Runnable indexChanged) {
        this.world=Objects.requireNonNull(world);this.directory=directory.toAbsolutePath().normalize();
        this.known=Objects.requireNonNull(known);this.indexChanged=Objects.requireNonNull(indexChanged);
    }
    private void mainThread(){if(!world.getServer().isOnThread())throw new IllegalStateException("Sharded owner disk access is restricted to the authoritative server thread");}
    private static long chunk(Cell cell){return ChunkPos.toLong(cell.x()>>4,cell.z()>>4);}
    private Path file(long key){return directory.resolve("owners-"+ChunkPos.getPackedX(key)+"."+ChunkPos.getPackedZ(key)+".nbt");}
    private boolean loaded(long key){return world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key),ChunkPos.getPackedZ(key))!=null;}
    private Shard read(long key) {
        var shard=new Shard();Path source=file(key);
        if(!known.contains(key))return shard;
        if(!Files.isRegularFile(source))throw new IllegalStateException("Missing indexed composite owner shard: "+source);
        try {
            NbtCompound n=NbtIo.readCompressed(source.toFile());reads++;
            if(n==null||n.getInt("Schema")!=SCHEMA||n.getInt("ChunkX")!=ChunkPos.getPackedX(key)||n.getInt("ChunkZ")!=ChunkPos.getPackedZ(key))
                throw new IllegalArgumentException("Owner shard header mismatch");
            var entries=n.getList("cells",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<entries.size();i++) {
                var row=entries.getCompound(i);int[] p=row.getIntArray("pos");
                if(p.length!=3)throw new IllegalArgumentException("Invalid owner shard cell coordinate");
                var cell=new Cell(p[0],p[1],p[2]);var values=CompositeData.contributions(row.getList("owners",NbtElement.COMPOUND_TYPE));
                if(chunk(cell)!=key||values.isEmpty()||shard.cells.putIfAbsent(cell,List.copyOf(values))!=null)
                    throw new IllegalArgumentException("Cross-chunk/empty/repeated owner shard cell");
            }
            var owners=n.getList("ownerMembership",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<owners.size();i++) {
                var row=owners.getCompound(i);var owner=CompositeData.owner(row.getCompound("owner"));
                if(chunk(owner.root())!=key||shard.membership.containsKey(owner))throw new IllegalArgumentException("Wrong root/repeated saved membership owner");
                var cells=new TreeMap<Cell,Footprint>();var members=row.getList("members",NbtElement.COMPOUND_TYPE);
                if(members.isEmpty()||members.size()>4096)throw new IllegalArgumentException("Saved owner membership budget");
                for(int j=0;j<members.size();j++) {
                    var entry=members.getCompound(j);int[] p=entry.getIntArray("offset");if(p.length!=3)throw new IllegalArgumentException("Saved owner membership offset");
                    var cell=new Cell(p[0],p[1],p[2]);var shape=new Footprint(CompositeData.boxes(entry.getList("collision",NbtElement.LIST_TYPE)),CompositeData.boxes(entry.getList("selection",NbtElement.LIST_TYPE)));
                    if(cells.putIfAbsent(cell,shape)!=null)throw new IllegalArgumentException("Repeated saved owner membership cell");
                }
                shard.membership.put(owner,cells);
            }
            return shard;
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot read composite owner shard "+source,failure);}
    }
    private Shard cached(long key) {
        mainThread();var existing=cached.get(key);if(existing!=null)return existing;
        trimUnloaded();
        if(cached.size()>=MAX_CACHED_SHARDS)throw new IllegalStateException("Loaded composite owner shard budget exceeded; reduce simultaneous loaded chunks before continuing conversion");
        var next=read(key);cached.put(key,next);CompositeShapeSnapshots.ledgerLoaded(world,next.cells);
        ImportedTypeMigration.shardLoaded(world,next.cells);ImportedCollisionMigration.shardLoaded(world,next.cells);
        return next;
    }
    public void prepare(ChunkPos pos){mainThread();long key=pos.toLong();if(known.contains(key))cached(key);}
    public List<CompositeData.Contribution> at(Cell cell) {
        mainThread();long key=chunk(cell);
        // Unloaded inspection is a transient bounded read; it never publishes
        // worker geometry for an unloaded chunk or retains a cache entry.
        return (loaded(key)?cached(key):read(key)).cells.getOrDefault(cell,List.of());
    }
    public void put(Cell cell,List<CompositeData.Contribution> values) {
        mainThread();long key=chunk(cell);if(!loaded(key))throw new IllegalStateException("Sharded owner writes require a loaded target chunk");
        var shard=cached(key);
        var previous=new HashMap<Owner,Footprint>();var next=new HashMap<Owner,Footprint>();
        for(var entry:shard.cells.getOrDefault(cell,List.of()))previous.put(entry.owner(),entry.shape());
        for(var entry:values)if(next.putIfAbsent(entry.owner(),entry.shape())!=null)throw new IllegalArgumentException("Repeated owner contribution at shard cell");
        var touched=new HashSet<Owner>(previous.keySet());touched.addAll(next.keySet());
        for(var owner:touched)if(!Objects.equals(previous.get(owner),next.get(owner))) {
            long rootKey=chunk(owner.root());if(!loaded(rootKey))throw new IllegalStateException("Membership edits require the owning root chunk loaded");
            var rootShard=cached(rootKey);var footprint=rootShard.membership.computeIfAbsent(owner,ignored->new TreeMap<>());
            var offset=cell.subtract(owner.root());
            if(next.containsKey(owner))footprint.put(offset,next.get(owner));else footprint.remove(offset);
            if(footprint.isEmpty())rootShard.membership.remove(owner);
            rootShard.dirty=true;if(known.add(rootKey))indexChanged.run();
        }
        if(values.isEmpty())shard.cells.remove(cell);else shard.cells.put(cell,List.copyOf(values));
        shard.dirty=true;
        if(known.add(key))indexChanged.run();
        CompositeShapeSnapshots.ledger(world,cell,values);
    }
    public Set<Cell> loadedCells(){mainThread();var cells=new HashSet<Cell>();for(var shard:cached.values())cells.addAll(shard.cells.keySet());return Set.copyOf(cells);}
    public Set<Long> knownChunks(){mainThread();return Set.copyOf(known);}
    /** Exact whole BEFORE masks, even if only one occupied chunk has loaded.
     * Root shards are read once per input batch and not retained when unloaded. */
    public Map<Owner,Map<Cell,Footprint>> savedMemberships(Set<Owner> owners) {
        mainThread();Map<Long,Shard> inspected=new HashMap<>();Map<Owner,Map<Cell,Footprint>> result=new HashMap<>();
        for(var owner:owners) {
            long rootKey=chunk(owner.root());var shard=inspected.computeIfAbsent(rootKey,key->cached.containsKey(key)?cached.get(key):read(key));
            var cells=shard.membership.get(owner);if(cells!=null)result.put(owner,Collections.unmodifiableMap(new TreeMap<>(cells)));
        }
        return Map.copyOf(result);
    }
    private void flush(long key,Shard shard) {
        if(!shard.dirty)return;mainThread();var n=new NbtCompound();n.putInt("Schema",SCHEMA);
        n.putInt("ChunkX",ChunkPos.getPackedX(key));n.putInt("ChunkZ",ChunkPos.getPackedZ(key));var entries=new NbtList();
        for(var entry:shard.cells.entrySet()) {
            var row=new NbtCompound();var p=entry.getKey();row.putIntArray("pos",new int[]{p.x(),p.y(),p.z()});
            row.put("owners",CompositeData.contributions(entry.getValue()));entries.add(row);
        }
        n.put("cells",entries);var owners=new NbtList();
        for(var entry:shard.membership.entrySet()) {
            var row=new NbtCompound();row.put("owner",CompositeData.owner(entry.getKey()));var members=new NbtList();
            for(var member:entry.getValue().entrySet()) {
                var item=new NbtCompound();var p=member.getKey();item.putIntArray("offset",new int[]{p.x(),p.y(),p.z()});
                item.put("collision",CompositeData.boxes(member.getValue().collision()));item.put("selection",CompositeData.boxes(member.getValue().selection()));members.add(item);
            }
            row.put("members",members);owners.add(row);
        }
        n.put("ownerMembership",owners);Path target=file(key),staged=target.resolveSibling(target.getFileName()+".pending");
        try {
            Files.createDirectories(directory);NbtIo.writeCompressed(n,staged.toFile());
            try{Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING);}
            shard.dirty=false;writes++;
        }catch(IOException failure){throw new IllegalStateException("Cannot flush composite owner shard "+target,failure);}
    }
    public void flush(){mainThread();for(var entry:cached.entrySet())flush(entry.getKey(),entry.getValue());}
    public void unload(ChunkPos pos) {
        mainThread();if(CompositeRuntime.writing())return;
        long key=pos.toLong();var shard=cached.get(key);if(shard==null)return;
        flush(key,shard);cached.remove(key);CompositeShapeSnapshots.forgetChunk(world,key);
        ImportedTypeMigration.shardUnloaded(world,key);ImportedCollisionMigration.shardUnloaded(world,key);
    }
    public void trimUnloaded() {
        mainThread();if(CompositeRuntime.writing())return;
        for(var key:List.copyOf(cached.keySet()))if(!loaded(key))unload(new ChunkPos(key));
    }
    public Map<String,Object> metrics(){mainThread();return Map.of("shardedOwnerMode",true,"knownOwnerShards",known.size(),"cachedOwnerShards",cached.size(),
        "cachedOwnerCells",cached.values().stream().mapToInt(s->s.cells.size()).sum(),"ownerShardReads",reads,"ownerShardWrites",writes,"maxCachedOwnerShards",MAX_CACHED_SHARDS);}
}
