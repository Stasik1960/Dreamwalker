package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

/** A real cold native CHUNK_LOAD callback, not a fake loaded predicate or prepared chunk. */
public final class ShardChunkLoadV127GameTests implements FabricGameTest {
    private static final Map<ServerWorld,Map<Long,Probe>> PROBES=new WeakHashMap<>();
    private static boolean initialized;
    private static final class Probe {
        final ShardedCompositeLedger store;final Cell cell;int callbacks;boolean managerMissing;WorldChunk supplied;
        Map<Cell,List<CompositeData.Contribution>> entries;CompletableFuture<List<CompositeData.Contribution>> worker;Throwable failure;
        Probe(ShardedCompositeLedger store,Cell cell){this.store=store;this.cell=cell;}
    }
    private static void initialize(){if(initialized)return;initialized=true;ServerChunkEvents.CHUNK_LOAD.register((world,actual)->{
        var probe=PROBES.getOrDefault(world,Map.of()).get(actual.getPos().toLong());if(probe==null)return;
        try {
            probe.callbacks++;probe.supplied=actual;probe.managerMissing=world.getChunkManager().getWorldChunk(actual.getPos().x,actual.getPos().z)==null;
            probe.entries=probe.store.loadedChunkEntries(actual);
            probe.worker=CompletableFuture.supplyAsync(()->CompositeShapeSnapshots.contributions(world,CompositeData.pos(probe.cell)));
        }catch(Throwable failure){probe.failure=failure;}
    });}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_sharded_owner_storage",tickLimit=400)
    public void actualColdChunkCallbackPublishesIndexedShardBeforeFullFutureAndHandlesEmptyChunk(TestContext c)throws Exception {
        initialize();var world=c.getWorld();int x=1450000+Math.floorMod(UUID.randomUUID().hashCode(),1000),z=-1450000;var chunk=new ChunkPos(x,z);var emptyChunk=new ChunkPos(x+1,z);
        var root=new Cell(x*16+8,90,z*16+8);var guest=root.add(new Cell(1,0,0));var owner=new Owner(UUID.randomUUID(),"bloodborne_dw:source_bricks",root);
        var payload=new NbtCompound();payload.putUuid("ExactLink",UUID.randomUUID());payload.putLong("ExactCounter",912345678901L);payload.putString("ExactEvent","opaque callback fixture");
        var data=CompositeData.data(CompositeArchitecture.kindBlock("source_bricks").getDefaultState(),payload);
        var full=new Footprint(List.of(new Box(0,0,0,1,1,1)),List.of(new Box(0,0,0,1,1,1)));
        var thin=new Footprint(List.of(new Box(0,.25,0,1,.5,1)),List.of(new Box(0,.25,0,1,.5,1)));
        var rootEntry=new CompositeData.Contribution(owner,data,full);var guestEntry=new CompositeData.Contribution(owner,data,thin);
        var expected=Map.of(root,List.of(rootEntry),guest,List.of(guestEntry));
        var directory=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-shard-native-cold-load").resolve(UUID.randomUUID().toString());Files.createDirectories(directory);
        var saved=new NbtCompound();saved.putInt("Schema",ShardedCompositeLedger.SCHEMA);saved.putInt("ChunkX",x);saved.putInt("ChunkZ",z);var rows=new NbtList();
        for(var row:expected.entrySet()){var value=new NbtCompound();var pos=row.getKey();value.putIntArray("pos",new int[]{pos.x(),pos.y(),pos.z()});value.put("owners",CompositeData.contributions(row.getValue()));rows.add(value);}saved.put("cells",rows);
        NbtIo.writeCompressed(saved,directory.resolve("owners-"+x+"."+z+".nbt").toFile());
        var store=new ShardedCompositeLedger(world,directory,new TreeSet<>(Set.of(chunk.toLong())),()->{});var known=new Probe(store,guest);var empty=new Probe(store,new Cell((x+1)*16+8,90,z*16+8));
        var probes=PROBES.computeIfAbsent(world,ignored->new HashMap<>());probes.put(chunk.toLong(),known);probes.put(emptyChunk.toLong(),empty);
        try {
            c.assertTrue(world.getChunkManager().getWorldChunk(x,z)==null&&world.getChunkManager().getWorldChunk(x+1,z)==null,"Both native chunks are genuinely cold; the fixture has not requested them");
            var actual=world.getChunk(x,z);var actualEmpty=world.getChunk(x+1,z);
            c.assertTrue(known.failure==null&&empty.failure==null,"Actual chunk-load callbacks succeed: "+known.failure+" / "+empty.failure);
            c.assertTrue(known.callbacks==1&&empty.callbacks==1&&known.supplied==actual&&empty.supplied==actualEmpty,"Each callback uses the exact supplied real WorldChunk");
            c.assertTrue(known.managerMissing&&empty.managerMissing,"Actual native CHUNK_LOAD precedes the manager FULL future publication for indexed and unindexed chunks");
            c.assertTrue(known.entries.equals(expected)&&empty.entries.isEmpty(),"Indexed callback retains exact owner UUID/typed NBT/geometry; unknown empty shard remains empty");
            c.assertTrue(known.worker.join().equals(List.of(guestEntry))&&empty.worker.join().isEmpty(),"An actual background geometry worker sees the correct immutable publication, including no phantom unknown-shard cells");
            c.assertTrue((long)store.metrics().get("ownerShardReads")==1&&(int)store.metrics().get("cachedOwnerShards")==1,"Callbacks and worker queries read the known shard once and retain only its one bounded cache entry");
            c.assertTrue(store.at(root).equals(List.of(rootEntry))&&(long)store.metrics().get("ownerShardReads")==1,"After native FULL publication, ordinary exact ledger inspection reuses the same cache without rereading disk");
            boolean immutable=false;try{known.entries.clear();}catch(UnsupportedOperationException expectedFailure){immutable=true;}c.assertTrue(immutable,"Captured lifecycle entries are immutable");
            store.unload(chunk);c.assertTrue((int)store.metrics().get("cachedOwnerShards")==0&&CompositeShapeSnapshots.contributions(world,CompositeData.pos(guest)).isEmpty(),"Normal unload releases the lifecycle shard and worker geometry");
            var report=new JsonObject();report.addProperty("status","PASS_REAL_COLD_NATIVE_CHUNK_LOAD_BEFORE_FULL_PUBLICATION");report.addProperty("indexedAndUnindexedCallbackCount",2);report.addProperty("actualNativeShardReads",1);report.addProperty("workerDiskReads",0);report.addProperty("maxRetainedShardCount",1);report.addProperty("retainedShardCountAfterUnload",0);report.addProperty("ownerUuidTypedNbtCollisionAndSelection","EXACT");
            Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-shard-cold-load-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));c.complete();
        }finally{probes.remove(chunk.toLong());probes.remove(emptyChunk.toLong());if(probes.isEmpty())PROBES.remove(world);store.unload(chunk);CompositeShapeSnapshots.forgetChunk(world,emptyChunk.toLong());}
    }
}
