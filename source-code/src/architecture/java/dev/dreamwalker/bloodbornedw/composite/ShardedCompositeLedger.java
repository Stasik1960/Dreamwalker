package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.BlockData;
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
    public static final int SCHEMA=1;
    private static final class Shard {
        final Map<Cell,List<CompositeData.Contribution>> cells=new TreeMap<>();boolean dirty;
        final Map<Owner,Map<Cell,Footprint>> membership=new TreeMap<>();
    }
    private final ServerWorld world;
    private final Path directory;
    private final Set<Long> known;
    private final Runnable indexChanged;
    private final Map<Long,Shard> cached=new LinkedHashMap<>(16,.75f,true);
    private long reads,writes,membershipReads,membershipShapes;

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
            Map<Owner,Owner> ownerPool=new HashMap<>();Map<BlockData,BlockData> dataPool=new HashMap<>();Map<Footprint,Footprint> shapePool=new HashMap<>();
            List<Owner> ownerTable=new ArrayList<>();List<BlockData> dataTable=new ArrayList<>();List<Footprint> shapeTable=new ArrayList<>();
            boolean dictionary=n.getBoolean("Dictionary");
            if(dictionary) {
                var owners=n.getList("ownerTable",NbtElement.COMPOUND_TYPE);for(int i=0;i<owners.size();i++)ownerTable.add(CompositeData.owner(owners.getCompound(i)));
                var datas=n.getList("dataTable",NbtElement.COMPOUND_TYPE);for(int i=0;i<datas.size();i++)dataTable.add(CompositeData.data(datas.getCompound(i)));
                var shapes=n.getList("shapeTable",NbtElement.COMPOUND_TYPE);for(int i=0;i<shapes.size();i++){var s=shapes.getCompound(i);shapeTable.add(new Footprint(CompositeData.boxes(s.getList("collision",NbtElement.LIST_TYPE)),CompositeData.boxes(s.getList("selection",NbtElement.LIST_TYPE))));}
            }
            var entries=n.getList("cells",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<entries.size();i++) {
                var row=entries.getCompound(i);int[] p=row.getIntArray("pos");
                if(p.length!=3)throw new IllegalArgumentException("Invalid owner shard cell coordinate");
                var cell=new Cell(p[0],p[1],p[2]);var values=new ArrayList<CompositeData.Contribution>();
                if(dictionary) {
                    var refs=row.getList("refs",NbtElement.INT_ARRAY_TYPE);
                    for(int j=0;j<refs.size();j++){int[] r=((NbtIntArray)refs.get(j)).getIntArray();if(r.length!=3)throw new IllegalArgumentException("Shard dictionary reference");values.add(new CompositeData.Contribution(ownerTable.get(r[0]),dataTable.get(r[1]),shapeTable.get(r[2])));}
                }else values.addAll(CompositeData.contributions(row.getList("owners",NbtElement.COMPOUND_TYPE)));
                for(int j=0;j<values.size();j++){var c=values.get(j);values.set(j,new CompositeData.Contribution(ownerPool.computeIfAbsent(c.owner(),ignored->c.owner()),dataPool.computeIfAbsent(c.rootData(),ignored->c.rootData()),shapePool.computeIfAbsent(c.shape(),ignored->c.shape())));}
                if(chunk(cell)!=key||values.isEmpty()||shard.cells.putIfAbsent(cell,List.copyOf(values))!=null)
                    throw new IllegalArgumentException("Cross-chunk/empty/repeated owner shard cell");
            }
            var owners=n.getList("ownerMembership",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<owners.size();i++) {
                var row=owners.getCompound(i);var owner=dictionary?ownerTable.get(row.getInt("ownerRef")):CompositeData.owner(row.getCompound("owner"));
                if(chunk(owner.root())!=key||shard.membership.containsKey(owner))throw new IllegalArgumentException("Wrong root/repeated saved membership owner");
                var cells=new TreeMap<Cell,Footprint>();var members=row.getList("members",NbtElement.COMPOUND_TYPE);
                if(members.isEmpty()||members.size()>4096)throw new IllegalArgumentException("Saved owner membership budget");
                for(int j=0;j<members.size();j++) {
                    var entry=members.getCompound(j);int[] p=entry.getIntArray("offset");if(p.length!=3)throw new IllegalArgumentException("Saved owner membership offset");
                    var cell=new Cell(p[0],p[1],p[2]);var shape=dictionary?shapeTable.get(entry.getInt("shapeRef")):new Footprint(CompositeData.boxes(entry.getList("collision",NbtElement.LIST_TYPE)),CompositeData.boxes(entry.getList("selection",NbtElement.LIST_TYPE)));
                    if(cells.putIfAbsent(cell,shape)!=null)throw new IllegalArgumentException("Repeated saved owner membership cell");
                }
                shard.membership.put(owner,cells);
            }
            return shard;
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot read composite owner shard "+source,failure);}
    }
    private Shard cached(long key) {
        mainThread();var existing=cached.get(key);if(existing!=null)return existing;
        // Live chunk ownership cannot be evicted to satisfy an arbitrary cache
        // limit: collision and cross-chunk rollback need its authoritative data.
        // Unload callbacks flush and release it; cold inspections stay transient.
        var next=read(key);cached.put(key,next);CompositeShapeSnapshots.ledgerLoaded(world,next.cells);
        ImportedTypeMigration.shardLoaded(world,next.cells);ImportedCollisionMigration.shardLoaded(world,next.cells);
        return next;
    }
    public void prepare(ChunkPos pos){mainThread();long key=pos.toLong();if(known.contains(key))cached(key);}
    /** Fabric supplies the actual FULL chunk before its manager future is published.
     * Do not look that same future up again or read the shard separately per cell. */
    public Map<Cell,List<CompositeData.Contribution>> loadedChunkEntries(net.minecraft.world.chunk.WorldChunk actualChunk){
        mainThread();if(actualChunk==null||actualChunk.getWorld()!=world)throw new IllegalArgumentException("Owner load callback requires the supplied chunk from this world");
        long key=actualChunk.getPos().toLong();return known.contains(key)?Map.copyOf(cached(key).cells):Map.of();
    }
    public List<CompositeData.Contribution> at(Cell cell) {
        mainThread();long key=chunk(cell);
        var existing=cached.get(key);if(existing!=null)return existing.cells.getOrDefault(cell,List.of());
        // The compact-city majority has no owners. Querying an empty loaded
        // chunk must not allocate a shard or publish a snapshot for that chunk.
        if(!known.contains(key))return List.of();
        // Unloaded inspection is a transient bounded read; it never publishes
        // worker geometry for an unloaded chunk or retains a cache entry.
        return (loaded(key)?cached(key):read(key)).cells.getOrDefault(cell,List.of());
    }
    public void put(Cell cell,List<CompositeData.Contribution> values) {
        mainThread();long key=chunk(cell);if(!loaded(key))throw new IllegalStateException("Sharded owner writes require a loaded target chunk");
        if(values.isEmpty()&&!known.contains(key)&&!cached.containsKey(key)){CompositeShapeSnapshots.ledger(world,cell,values);return;}
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
    public Set<Cell> chunkCells(ChunkPos pos){mainThread();long key=pos.toLong();if(!loaded(key))throw new IllegalStateException("Chunk-watch synchronization requires an actually loaded chunk");return known.contains(key)?Set.copyOf(cached(key).cells.keySet()):Set.of();}
    public Set<Long> knownChunks(){mainThread();return Set.copyOf(known);}
    /** Read only the requested complete BEFORE masks from a cold root shard.
     * Inflation stays synchronous. Skip unrelated cell and typed-payload trees,
     * collect only requested owner masks and referenced dictionary shapes from
     * the same file handle. Every owner identity and mask budget is checked.
     * All temporary lookup tables die with this call; nothing publishes or
     * retains geometry for an unloaded chunk. The saved schema is unchanged. */
    private Map<Owner,Map<Cell,Footprint>> readMemberships(long key,Set<Owner> requested) {
        if(!known.contains(key))return Map.of();Path source=file(key);
        if(!Files.isRegularFile(source))throw new IllegalStateException("Missing indexed composite owner shard: "+source);
        try(var input=Files.newByteChannel(source,StandardOpenOption.READ)) {
            var metadata=MembershipNbtReader.metadata(input);NbtCompound n=metadata.tag();reads++;membershipReads++;
            if(n==null||n.getInt("Schema")!=SCHEMA||n.getInt("ChunkX")!=ChunkPos.getPackedX(key)||n.getInt("ChunkZ")!=ChunkPos.getPackedZ(key))
                throw new IllegalArgumentException("Owner shard header mismatch");
            boolean dictionary=n.getBoolean("Dictionary");
            var ownerTags=n.getList("ownerTable",NbtElement.COMPOUND_TYPE);
            Map<Integer,Owner> owners=new HashMap<>();Map<Integer,Footprint> shapes=new HashMap<>();
            Map<Owner,Map<Cell,Footprint>> result=new HashMap<>();Set<Owner> seen=new HashSet<>();
            Map<Integer,Owner> selected=new LinkedHashMap<>();Set<Integer> wantedShapes=new HashSet<>();
            var memberships=n.getList("ownerMembership",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<memberships.size();i++) {
                var row=memberships.getCompound(i);Owner owner;
                if(dictionary) {
                    int ref=row.getInt("ownerRef");if(ref<0||ref>=ownerTags.size())throw new IllegalArgumentException("Shard owner membership reference");
                    owner=owners.computeIfAbsent(ref,ignored->CompositeData.owner(ownerTags.getCompound(ref)));
                }else owner=CompositeData.owner(row.getCompound("owner"));
                if(chunk(owner.root())!=key||!seen.add(owner))throw new IllegalArgumentException("Wrong root/repeated saved membership owner");
                int count=metadata.memberCounts().get(i);
                if(count<=0||count>4096)throw new IllegalArgumentException("Saved owner membership budget");
                if(requested.contains(owner))selected.put(i,owner);
            }
            var selectedRows=MembershipNbtReader.members(input,selected.keySet());
            if(!selected.isEmpty()&&selectedRows.size()!=memberships.size())throw new IllegalArgumentException("Shard membership row bounds");
            if(dictionary)for(int index:selected.keySet()){
                var members=selectedRows.entries().get(index).getList("members",NbtElement.COMPOUND_TYPE);
                for(int j=0;j<members.size();j++)wantedShapes.add(members.getCompound(j).getInt("shapeRef"));
            }
            var shapeTags=dictionary?MembershipNbtReader.shapes(input,wantedShapes):null;
            for(var requestedRow:selected.entrySet()) {
                Owner owner=requestedRow.getValue();NbtList members=selectedRows.entries().get(requestedRow.getKey()).getList("members",NbtElement.COMPOUND_TYPE);
                if(members.size()!=metadata.memberCounts().get(requestedRow.getKey()))throw new IllegalArgumentException("Shard membership row count");
                Map<Cell,Footprint> cells=new TreeMap<>();
                for(int j=0;j<members.size();j++) {
                    var entry=members.getCompound(j);int[] p=entry.getIntArray("offset");if(p.length!=3)throw new IllegalArgumentException("Saved owner membership offset");
                    Footprint shape;
                    if(dictionary) {
                        int ref=entry.getInt("shapeRef");if(ref<0||ref>=shapeTags.size()||!shapeTags.entries().containsKey(ref))throw new IllegalArgumentException("Shard membership shape reference");
                        shape=shapes.get(ref);if(shape==null){var tag=shapeTags.entries().get(ref);shape=new Footprint(CompositeData.boxes(tag.getList("collision",NbtElement.LIST_TYPE)),CompositeData.boxes(tag.getList("selection",NbtElement.LIST_TYPE)));shapes.put(ref,shape);membershipShapes++;}
                    }else shape=new Footprint(CompositeData.boxes(entry.getList("collision",NbtElement.LIST_TYPE)),CompositeData.boxes(entry.getList("selection",NbtElement.LIST_TYPE)));
                    if(cells.putIfAbsent(new Cell(p[0],p[1],p[2]),shape)!=null)throw new IllegalArgumentException("Repeated saved owner membership cell");
                }
                result.put(owner,Collections.unmodifiableMap(cells));
            }
            return result;
        }catch(IOException|RuntimeException failure){throw new IllegalStateException("Cannot read composite owner membership "+source,failure);}
    }
    /** Exact whole BEFORE masks, even if only one occupied chunk has loaded.
     * Root shards are inspected once per input batch; cold reads decode only
     * the requested owners' occupied regions and are never cached. */
    public Map<Owner,Map<Cell,Footprint>> savedMemberships(Set<Owner> owners) {
        mainThread();Map<Long,Set<Owner>> grouped=new HashMap<>();Map<Owner,Map<Cell,Footprint>> result=new HashMap<>();
        for(var owner:owners)grouped.computeIfAbsent(chunk(owner.root()),ignored->new HashSet<>()).add(owner);
        for(var group:grouped.entrySet()) {
            var shard=cached.get(group.getKey());
            if(shard==null)result.putAll(readMemberships(group.getKey(),group.getValue()));
            else for(var owner:group.getValue()) {
                var cells=shard.membership.get(owner);if(cells!=null)result.put(owner,Collections.unmodifiableMap(new TreeMap<>(cells)));
            }
        }
        return Map.copyOf(result);
    }
    private void flush(long key,Shard shard) {
        if(!shard.dirty)return;mainThread();
        if(shard.cells.isEmpty()&&shard.membership.isEmpty()){
            try{Files.deleteIfExists(file(key));}catch(IOException failure){throw new IllegalStateException("Cannot retire empty owner shard "+file(key),failure);}
            if(known.remove(key))indexChanged.run();
            shard.dirty=false;return;
        }
        var n=new NbtCompound();n.putInt("Schema",SCHEMA);
        n.putInt("ChunkX",ChunkPos.getPackedX(key));n.putInt("ChunkZ",ChunkPos.getPackedZ(key));n.putBoolean("Dictionary",true);var entries=new NbtList();
        Map<Owner,Integer> ownerIds=new LinkedHashMap<>();Map<BlockData,Integer> dataIds=new LinkedHashMap<>();Map<Footprint,Integer> shapeIds=new LinkedHashMap<>();
        for(var entry:shard.cells.entrySet()) {
            var row=new NbtCompound();var p=entry.getKey();row.putIntArray("pos",new int[]{p.x(),p.y(),p.z()});
            var refs=new NbtList();for(var c:entry.getValue())refs.add(new NbtIntArray(new int[]{ownerIds.computeIfAbsent(c.owner(),ignored->ownerIds.size()),dataIds.computeIfAbsent(c.rootData(),ignored->dataIds.size()),shapeIds.computeIfAbsent(c.shape(),ignored->shapeIds.size())}));
            row.put("refs",refs);entries.add(row);
        }
        n.put("cells",entries);var owners=new NbtList();
        for(var entry:shard.membership.entrySet()) {
            var row=new NbtCompound();row.putInt("ownerRef",ownerIds.computeIfAbsent(entry.getKey(),ignored->ownerIds.size()));var members=new NbtList();
            for(var member:entry.getValue().entrySet()) {
                var item=new NbtCompound();var p=member.getKey();item.putIntArray("offset",new int[]{p.x(),p.y(),p.z()});
                item.putInt("shapeRef",shapeIds.computeIfAbsent(member.getValue(),ignored->shapeIds.size()));members.add(item);
            }
            row.put("members",members);owners.add(row);
        }
        n.put("ownerMembership",owners);var ownerTable=new NbtList();for(var owner:ownerIds.keySet())ownerTable.add(CompositeData.owner(owner));n.put("ownerTable",ownerTable);
        var dataTable=new NbtList();for(var data:dataIds.keySet())dataTable.add(CompositeData.data(data));n.put("dataTable",dataTable);
        var shapeTable=new NbtList();for(var shape:shapeIds.keySet()){var s=new NbtCompound();s.put("collision",CompositeData.boxes(shape.collision()));s.put("selection",CompositeData.boxes(shape.selection()));shapeTable.add(s);}n.put("shapeTable",shapeTable);
        Path target=file(key),staged=target.resolveSibling(target.getFileName()+".pending");
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
    public Map<String,Object> metrics(){
        mainThread();Set<BlockData> uniqueData=Collections.newSetFromMap(new IdentityHashMap<>());Set<Owner> uniqueOwners=Collections.newSetFromMap(new IdentityHashMap<>());Set<Footprint> uniqueShapes=Collections.newSetFromMap(new IdentityHashMap<>());
        long duplicatedPayloadBytes=0;int refs=0,members=0;
        for(var shard:cached.values()){members+=shard.membership.values().stream().mapToInt(Map::size).sum();for(var values:shard.cells.values())for(var c:values){uniqueData.add(c.rootData());uniqueOwners.add(c.owner());uniqueShapes.add(c.shape());duplicatedPayloadBytes+=c.rootData().blockEntityNbt().length;refs++;}}
        long sharedPayloadBytes=uniqueData.stream().mapToLong(d->d.blockEntityNbt().length).sum();Map<String,Object> result=new LinkedHashMap<>();
        result.put("shardedOwnerMode",true);result.put("knownOwnerShards",known.size());result.put("cachedOwnerShards",cached.size());result.put("cachedOwnerCells",cached.values().stream().mapToInt(s->s.cells.size()).sum());
        result.put("ownerShardReads",reads);result.put("ownerShardWrites",writes);result.put("coldMembershipReads",membershipReads);result.put("coldMembershipShapesDecoded",membershipShapes);result.put("maxCachedOwnerShards",-1);result.put("contributionReferences",refs);result.put("savedMembershipCellReferences",members);
        result.put("cachePolicy","Loaded indexed owner chunks only; unloaded inspections are transient and unload flushes dirty state before release");
        result.put("uniqueSharedRootData",uniqueData.size());result.put("uniqueSharedOwners",uniqueOwners.size());result.put("uniqueSharedShapes",uniqueShapes.size());result.put("typedPayloadBytesWithoutSharing",duplicatedPayloadBytes);result.put("typedPayloadBytesWithSharing",sharedPayloadBytes);
        result.put("byteMetricScope","Exact typed payload array bytes, not a claim about total JVM heap; owner/shape/rootData sharing verified separately");return Map.copyOf(result);
    }
}
