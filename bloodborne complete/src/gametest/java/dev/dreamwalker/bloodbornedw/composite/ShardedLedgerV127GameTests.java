package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
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
            t.assertTrue(keys.size()==2&&indexChanges.get()>=2,"Compact index contains two chunkkeys, not every cell/UUID");
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
}
