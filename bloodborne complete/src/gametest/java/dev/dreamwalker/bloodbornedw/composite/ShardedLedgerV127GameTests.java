package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;

/** Native NBT/disk/chunk lifecycle and rollback-shaped storage transactions.
 * Uses its own fresh QA shard directory; the test world's ordinary global
 * ownership ledger and unrelated working objects are never converted. */
public final class ShardedLedgerV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="final_sharded_empty_chunks")
    public void loadedEmptyChunksDoNotAllocateOwnerCachesOrWriteEmptyShards(TestContext t){
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(2048,140,2048));
        Path directory=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("final-shard-empty-fixtures").resolve(UUID.randomUUID().toString());
        Set<Long> known=new TreeSet<>();AtomicInteger indexChanges=new AtomicInteger();
        var store=new ShardedCompositeLedger(world,directory,known,indexChanges::incrementAndGet);
        int baseX=pos.getX()>>4,baseZ=pos.getZ()>>4;
        for(int x=0;x<5;x++)for(int z=0;z<5;z++){
            var chunk=world.getChunk(baseX+x,baseZ+z);
            t.assertTrue(world.getChunkManager().getWorldChunk(baseX+x,baseZ+z)!=null,"Native fixture chunk is really FULL and loaded");
            for(int cell=0;cell<4096;cell++){
                var empty=new Cell((baseX+x)*16+(cell&15),140+(cell>>8),(baseZ+z)*16+((cell>>4)&15));
                t.assertTrue(store.at(empty).isEmpty(),"An unindexed loaded chunk has no owner contributions");
            }
            store.put(new Cell(chunk.getPos().getStartX(),140,chunk.getPos().getStartZ()),List.of());
        }
        store.flush();
        t.assertTrue((int)store.metrics().get("cachedOwnerShards")==0&&known.isEmpty()&&indexChanges.get()==0,"102400 loaded empty-cell reads and25 empty removals allocate no cached/indexed shards");
        t.assertTrue((long)store.metrics().get("ownerShardReads")==0&&(long)store.metrics().get("ownerShardWrites")==0&&!java.nio.file.Files.exists(directory),"Empty native palette queries perform zero owner disk IO");
        t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400,batchId="v127_sharded_owner_storage")
    public void nativeShardFlushEvictReopenRetainsCrossChunkMembershipAndRollback(TestContext t) {
        var world=t.getWorld();var absolute=t.getAbsolutePos(new BlockPos(128,140,0));
        int chunkX=absolute.getX()>>4,chunkZ=absolute.getZ()>>4;world.getChunk(chunkX,chunkZ);world.getChunk(chunkX+1,chunkZ);
        var root=new Cell(chunkX*16+15,absolute.getY(),chunkZ*16+5);var other=new Cell(root.x()+1,root.y(),root.z());
        var owner=new Owner(UUID.randomUUID(),"bloodborne_dw:source_bricks",root);
        var unrelated=new Owner(UUID.randomUUID(),"bloodborne_dw:prototype_double_door",root.add(new Cell(0,2,0)));
        var payload=new NbtCompound();payload.putUuid("Linked",UUID.randomUUID());payload.putString("Event","opaque-event");payload.putInt("ImportedTypeVersion",1);
        var data=CompositeData.data(Registries.BLOCK.get(new Identifier(owner.registryId())).getDefaultState(),payload);
        var full=new Footprint(List.of(new Box(0,0,0,1,1,1)),List.of(new Box(0,0,0,1,1,1)));
        var thin=new Footprint(List.of(new Box(0,.25,0,1,.5,1)),List.of(new Box(0,.25,0,1,.5,1)));
        var rootContribution=new CompositeData.Contribution(owner,data,full);var sideContribution=new CompositeData.Contribution(owner,data,thin);
        var foreign=new CompositeData.Contribution(unrelated,CompositeData.data(Registries.BLOCK.get(new Identifier(unrelated.registryId())).getDefaultState(),payload.copy()),full);
        Path directory=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-shard-native-fixtures").resolve(UUID.randomUUID().toString());
        Set<Long> keys=new TreeSet<>();AtomicInteger indexChanges=new AtomicInteger();
        var store=new ShardedCompositeLedger(world,directory,keys,indexChanges::incrementAndGet);
        try {
            store.put(root,List.of(rootContribution));store.put(other,List.of(sideContribution,foreign));
            store.put(unrelated.root(),List.of(foreign));
            t.assertTrue(keys.size()==2&&indexChanges.get()>=2,"Compact index contains two chunkkeys, not every cell/UUID");
            t.assertTrue(store.chunkCells(new ChunkPos(chunkX,chunkZ)).equals(Set.of(root,unrelated.root()))&&store.chunkCells(new ChunkPos(chunkX+1,chunkZ)).equals(Set.of(other)),"Watched-chunk synchronization contains only that chunk's cells");
            var expected=new TreeMap<Cell,Footprint>();expected.put(Cell.ORIGIN,full);expected.put(new Cell(1,0,0),thin);
            t.assertTrue(store.savedMemberships(Set.of(owner)).get(owner).equals(expected),"Root shard records the entire cross-chunk authoritative BEFORE");
            // Same storage sequence used by native transaction rollback: alter
            // one owner's shape, then restore the exact saved contribution list.
            var before=store.at(other);store.put(other,List.of(new CompositeData.Contribution(owner,data,full),foreign));store.put(other,before);
            t.assertTrue(store.at(other).equals(before)&&store.savedMemberships(Set.of(owner)).get(owner).equals(expected),"Rollback restores contribution membership and root index, preserving foreign owner");
            store.flush();long reads=(long)store.metrics().get("ownerShardReads");
            var worker=CompletableFuture.supplyAsync(()->CompositeShapeSnapshots.contributions(world,CompositeData.pos(other))).join();
            t.assertTrue(worker.equals(before)&&(long)store.metrics().get("ownerShardReads")==reads,"Lighting-worker shape query reads immutable publication with zero disk IO");
            store.unload(new ChunkPos(chunkX,chunkZ));store.unload(new ChunkPos(chunkX+1,chunkZ));
            t.assertTrue((int)store.metrics().get("cachedOwnerShards")==0,"Unload flushes and releases every cached shard");
            t.assertTrue(CompositeShapeSnapshots.contributions(world,CompositeData.pos(other)).isEmpty(),"Unloaded shard has no retained worker geometry");
            var reopened=new ShardedCompositeLedger(world,directory,new TreeSet<>(keys),()->{});
            reopened.prepare(new ChunkPos(chunkX+1,chunkZ));
            t.assertTrue(reopened.at(other).equals(before),"Actual compressed NBT reopening preserves both owner UUIDs, rootData typed payloads and exact boxes");
            t.assertTrue(reopened.savedMemberships(Set.of(owner)).get(owner).equals(expected),"Helper-first reopening sees complete saved root footprint while root shard is not cached");
            t.assertTrue((int)reopened.metrics().get("cachedOwnerShards")==1,"Whole membership inspection does not retain the uncached root shard");
            reopened.prepare(new ChunkPos(chunkX,chunkZ));t.assertTrue(reopened.at(root).equals(List.of(rootContribution)),"Root chunk reopening restores precise contribution");
            reopened.put(other,List.of(foreign));t.assertTrue(reopened.savedMemberships(Set.of(owner)).get(owner).equals(Map.of(Cell.ORIGIN,full)),"Removal updates only the removed owner's membership");
            t.assertTrue(reopened.at(other).equals(List.of(foreign)),"Unrelated actual UUID/NBT/collision remains after another owner's removal");
            reopened.flush();reopened.unload(new ChunkPos(chunkX,chunkZ));reopened.unload(new ChunkPos(chunkX+1,chunkZ));
            t.complete();
        } finally {
            store.unload(new ChunkPos(chunkX,chunkZ));store.unload(new ChunkPos(chunkX+1,chunkZ));
            CompositeShapeSnapshots.forgetChunk(world,ChunkPos.toLong(chunkX,chunkZ));CompositeShapeSnapshots.forgetChunk(world,ChunkPos.toLong(chunkX+1,chunkZ));
        }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_sharded_owner_storage")
    public void legacyLedgerNbtRemainsExactAndMixedModeIsRejected(TestContext t) {
        var legacy=new NbtCompound();legacy.put("cells",new NbtList());
        var state=CompositeLedger.read(legacy.copy());t.assertTrue(!state.sharded()&&state.writeNbt(new NbtCompound()).equals(legacy),"Ordinary V12/V12.6 global format is byte-semantic identical, no mode flag injected");
        var metadata=new NbtCompound();metadata.putInt("OwnerShardSchema",1);metadata.putLongArray("OwnerShardChunks",new long[]{ChunkPos.toLong(-3,5),ChunkPos.toLong(9,-7)});metadata.put("cells",new NbtList());
        t.assertTrue(CompositeLedger.read(metadata).sharded(),"Explicit persisted opt-in metadata recognized");
        var duplicate=metadata.copy();duplicate.putLongArray("OwnerShardChunks",new long[]{1,1});boolean rejected=false;
        try{CompositeLedger.read(duplicate);}catch(IllegalArgumentException expected){rejected=true;}
        t.assertTrue(rejected,"Corrupt repeated shard-key metadata is diagnosed, never silently discarded");t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400,batchId="v127_sharded_owner_storage")
    public void thousandNativeTransientShardReadsHaveNoResidentOrSnapshotGrowth(TestContext t) throws Exception {
        var world=t.getWorld();Path directory=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-shard-native-stress").resolve(UUID.randomUUID().toString());
        java.nio.file.Files.createDirectories(directory);Set<Long> known=new TreeSet<>();
        var data=CompositeData.data(Registries.BLOCK.get(new Identifier("bloodborne_dw:source_bricks")).getDefaultState(),new NbtCompound());
        var full=new Footprint(List.of(new Box(0,0,0,1,1,1)),List.of(new Box(0,0,0,1,1,1)));
        long snapshotCells=((Number)CompositeShapeSnapshots.metrics(world).get("shapeSnapshotCells")).longValue();
        List<Cell> cells=new ArrayList<>();List<CompositeData.Contribution> expected=new ArrayList<>();
        // These distant coordinates are inspected without generating/requesting
        // Minecraft chunks. Only fresh QA native compressed NBT files are made.
        for(int i=0;i<1000;i++) {
            int x=1500000+i,z=-1500000;long key=ChunkPos.toLong(x,z);known.add(key);var cell=new Cell(x*16+8,64,z*16+8);
            var owner=new Owner(UUID.randomUUID(),"bloodborne_dw:source_bricks",cell);var c=new CompositeData.Contribution(owner,data,full);
            var n=new NbtCompound();n.putInt("Schema",1);n.putInt("ChunkX",x);n.putInt("ChunkZ",z);var rows=new NbtList();var row=new NbtCompound();
            row.putIntArray("pos",new int[]{cell.x(),cell.y(),cell.z()});row.put("owners",CompositeData.contributions(List.of(c)));rows.add(row);n.put("cells",rows);
            var membership=new NbtList();var object=new NbtCompound();object.put("owner",CompositeData.owner(owner));var members=new NbtList();var member=new NbtCompound();member.putIntArray("offset",new int[]{0,0,0});member.put("collision",CompositeData.boxes(full.collision()));member.put("selection",CompositeData.boxes(full.selection()));members.add(member);object.put("members",members);membership.add(object);n.put("ownerMembership",membership);
            NbtIo.writeCompressed(n,directory.resolve("owners-"+x+"."+z+".nbt").toFile());cells.add(cell);expected.add(c);
        }
        var store=new ShardedCompositeLedger(world,directory,known,()->{});int maxResident=0,maxCells=0;
        for(int i=0;i<1000;i++) {
            t.assertTrue(store.at(cells.get(i)).equals(List.of(expected.get(i))),"Native transient shard preserves exact owner/typed rootData/shape "+i);
            var metrics=store.metrics();maxResident=Math.max(maxResident,(int)metrics.get("cachedOwnerShards"));maxCells=Math.max(maxCells,(int)metrics.get("cachedOwnerCells"));
        }
        t.assertTrue((long)store.metrics().get("ownerShardReads")==1000&&known.size()==1000,"All1000 indexed chunks read from actual native files");
        t.assertTrue(maxResident==0&&maxCells==0&&store.loadedCells().isEmpty(),"Visited count1000 has zero monotonic resident shard/cell growth; unloaded reads remain transient");
        t.assertTrue(((Number)CompositeShapeSnapshots.metrics(world).get("shapeSnapshotCells")).longValue()==snapshotCells,"No unloaded shape snapshots published or retained during stress traversal");
        var proof=new NbtCompound();proof.putInt("knownShards",known.size());proof.putInt("maxResidentShards",maxResident);proof.putInt("maxResidentCells",maxCells);proof.putLong("actualNativeFileReads",(long)store.metrics().get("ownerShardReads"));
        NbtIo.writeCompressed(proof,directory.resolve("stress-result.nbt").toFile());t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="v127_sharded_owner_storage")
    public void repeatedTypedPayloadsDecodeAsSharedObjectsAndDictionaryBytes(TestContext t) throws Exception {
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(192,140,0));int x=pos.getX()>>4,z=pos.getZ()>>4;world.getChunk(x,z);
        var root=new Cell(x*16+2,pos.getY(),z*16+2);var owner=new Owner(UUID.randomUUID(),"bloodborne_dw:source_bricks",root);
        var payload=new NbtCompound();payload.putString("LargeTypedSetting","repeated opaque author event setting ".repeat(256));payload.putUuid("Link",UUID.randomUUID());
        var data=CompositeData.data(Registries.BLOCK.get(new Identifier(owner.registryId())).getDefaultState(),payload);
        var full=new Footprint(List.of(new Box(0,0,0,1,1,1)),List.of(new Box(0,0,0,1,1,1)));var c=new CompositeData.Contribution(owner,data,full);
        Path directory=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-shard-native-sharing").resolve(UUID.randomUUID().toString());Set<Long> known=new TreeSet<>();var store=new ShardedCompositeLedger(world,directory,known,()->{});
        for(int dx=0;dx<8;dx++)for(int dz=0;dz<8;dz++)store.put(root.add(new Cell(dx,0,dz)),List.of(c));
        store.flush();store.unload(new ChunkPos(x,z));var reopened=new ShardedCompositeLedger(world,directory,new TreeSet<>(known),()->{});reopened.prepare(new ChunkPos(x,z));
        try {
            var first=reopened.at(root).get(0);for(int dx=0;dx<8;dx++)for(int dz=0;dz<8;dz++){var entry=reopened.at(root.add(new Cell(dx,0,dz))).get(0);t.assertTrue(entry.rootData()==first.rootData()&&entry.owner()==first.owner()&&entry.shape()==first.shape(),"Shared owner/rootData/shape references after actual native dictionary decoding");}
            var metrics=reopened.metrics();long without=(long)metrics.get("typedPayloadBytesWithoutSharing"),with=(long)metrics.get("typedPayloadBytesWithSharing");
            t.assertTrue(without==with*64&&(int)metrics.get("uniqueSharedRootData")==1,"Measured exact typed payload arrays64copies→1, without claiming total JVM heap");
            var saved=NbtIo.readCompressed(directory.resolve("owners-"+x+"."+z+".nbt").toFile());
            t.assertTrue(saved.getBoolean("Dictionary")&&saved.getList("dataTable",NbtElement.COMPOUND_TYPE).size()==1,"Native disk format stores full typed rootData once per shard, not per occupied cell");
            t.assertTrue(saved.getList("ownerTable",NbtElement.COMPOUND_TYPE).size()==1&&saved.getList("shapeTable",NbtElement.COMPOUND_TYPE).size()==1,"Owner and fitted shape dictionaries are compact and exact");t.complete();
        } finally {reopened.unload(new ChunkPos(x,z));CompositeShapeSnapshots.forgetChunk(world,ChunkPos.toLong(x,z));}
    }
}
