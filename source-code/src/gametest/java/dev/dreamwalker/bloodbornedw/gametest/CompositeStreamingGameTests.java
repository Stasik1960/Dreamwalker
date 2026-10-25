package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

/** Real loaded blocks and transactions, including malformed saved memberships.
 * These assertions guard the semantic shortcut used for healthy chunk loads. */
public final class CompositeStreamingGameTests implements FabricGameTest {
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_streaming")
    public void healthyOwnersDeduplicateWithoutTransactionsOrPayloadChanges(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            CompositeBlockEntity entity=place(t,w,root);CompositeLoadWork.forget(w);
            NbtCompound before=entity.createNbt(),ledger=CompositeLedger.get(w).writeNbt(new NbtCompound());
            var entry=entity.contributions().get(0);long source=new ChunkPos(root).toLong();
            for(int i=0;i<800;i++)CompositeLoadWork.owner(w,source,entry);
            t.assertTrue(number(w,"pendingOwners")==1&&number(w,"duplicateOwners")==799,"one saved owner is queued once despite repeated helper entries");
            finish(t,w);
            t.assertTrue(number(w,"pruneTransactions")==0&&number(w,"cellChecks")>1,"healthy multicell owner needs no transaction preflight");
            t.assertTrue(number(w,"footprintsBuilt")<number(w,"cellChecks"),"one drain shares the actual root geometry between helper checks");
            Map<Cell,List<CompositeData.Contribution>> claims=new TreeMap<>();
            for(Cell cell:CompositeLedger.get(w).cells())if((cell.x()>>4)==(root.getX()>>4)&&(cell.z()>>4)==(root.getZ()>>4)&&CompositeLedger.get(w).at(cell).stream().anyMatch(c->c.owner().equals(entry.owner())))claims.put(cell,CompositeLedger.get(w).at(cell));
            CompositeLoadWork.chunkCells(w,source,claims);CompositeLoadWork.chunkCells(w,source,claims);
            t.assertTrue(number(w,"pendingChunkBatches")==1&&number(w,"pendingChunkCells")==claims.size(),"saved source chunk replaces duplicate batches without owner fanout");
            for(int i=0;i<200&&number(w,"pendingChunkBatches")>0;i++)CompositeLoadWork.drain(w);
            t.assertTrue(number(w,"pendingChunkCells")==0&&number(w,"chunkCellChecks")==claims.size()&&number(w,"pruneTransactions")==0,"actual chunk path checks each healthy saved claim once without transactions");
            t.assertTrue(before.equals(entity.createNbt())&&ledger.equals(CompositeLedger.get(w).writeNbt(new NbtCompound())),"healthy validation preserves all typed payload and ledger bytes");
            long publications=((Number)CompositeShapeSnapshots.metrics(w).get("shapeSnapshotPublications")).longValue();
            for(int i=0;i<500;i++){entity.setWorld(w);entity.setCachedState(entity.getCachedState());entity.publishShapeSnapshot();}
            t.assertTrue(publications==((Number)CompositeShapeSnapshots.metrics(w).get("shapeSnapshotPublications")).longValue(),"identical attachments and scalar metadata cause no map publication");
            t.complete();
        }finally{clear(w,root);CompositeLoadWork.forget(w);}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_streaming")
    public void loadedUuidReplacementPrunesStaleGuestAndPreservesForeignCarrier(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            clear(w,root);BlockState state=CompositeArchitecture.kindBlock("prototype_wood_window").getDefaultState();UUID id=UUID.randomUUID();
            var object=CompositeRuntime.instance(w,root,state,id,new NbtCompound());
            Cell offset=object.cells().entrySet().stream().filter(row->!row.getKey().equals(Cell.ORIGIN)&&!row.getValue().selection().isEmpty()).map(Map.Entry::getKey).findFirst().orElseThrow();
            BlockPos foreign=root.add(offset.x(),offset.y(),offset.z());w.setBlockState(foreign,Blocks.STRUCTURE_VOID.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            t.assertTrue(CompositeRuntime.place(w,root,state,id,null).outcome()==TransactionCore.Outcome.COMMITTED,"saved owner overlaps real foreign carrier");
            CompositeRuntime.drain(w);CompositeLoadWork.forget(w);CompositeBlockEntity entity=(CompositeBlockEntity)w.getBlockEntity(root);var saved=entity.contributions().get(0);Owner old=entity.resident();
            entity.set(new Owner(UUID.randomUUID(),old.registryId(),old.root()),entity.contributions(),entity.payload());
            CompositeLoadWork.owner(w,new ChunkPos(root).toLong(),saved);finish(t,w);
            t.assertTrue(number(w,"pruneTransactions")>0,"loaded different UUID is proven stale and executes normal atomic prune");
            t.assertTrue(w.getBlockState(foreign).isOf(Blocks.STRUCTURE_VOID)&&CompositeLedger.get(w).at(CompositeData.cell(foreign)).isEmpty(),"stale guest disappears without replacing original foreign block");t.complete();
        }finally{clear(w,root);CompositeLoadWork.forget(w);}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_streaming")
    public void matchingUuidStillChecksCurrentFootprintAndPayloadRevision(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            CompositeBlockEntity entity=place(t,w,root);CompositeLoadWork.forget(w);var saved=entity.contributions().get(0);Owner owner=entity.resident();
            BlockPos helper=CompositeRuntime.instance(w,root,entity.getCachedState(),owner.instanceId(),entity.payload()).cells().keySet().stream().filter(cell->!cell.equals(Cell.ORIGIN)).map(cell->root.add(cell.x(),cell.y(),cell.z())).filter(pos->w.getBlockState(pos).isOf(CompositeArchitecture.CELL)).findFirst().orElseThrow();
            NbtCompound moved=entity.payload();moved.putDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY,20);entity.set(owner,entity.contributions(),moved);
            CompositeLoadWork.owner(w,new ChunkPos(root).toLong(),saved);finish(t,w);
            t.assertTrue(number(w,"pruneTransactions")>0&&CompositeLedger.get(w).at(CompositeData.cell(helper)).isEmpty(),"valid resident with changed geometry does not retain obsolete old helper membership");
            t.assertTrue(entity.resident().equals(owner)&&entity.payload().equals(moved),"cleanup preserves actual moved root authority and typed payload");t.complete();
        }finally{clear(w,root);CompositeLoadWork.forget(w);}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_streaming")
    public void unloadedRootsDeferWithoutDiskLoadsAndUnloadCancelsBudgetedQueue(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            CompositeBlockEntity entity=place(t,w,root);CompositeLoadWork.forget(w);var saved=entity.contributions().get(0);long source=new ChunkPos(root).toLong();Cell far=new Cell(30_000,root.getY(),30_000);
            t.assertTrue(!w.isChunkLoaded(CompositeData.pos(far)),"dependency starts unloaded");
            for(int i=0;i<900;i++)CompositeLoadWork.owner(w,source,new CompositeData.Contribution(new Owner(UUID.randomUUID(),saved.owner().registryId(),far),saved.rootData(),saved.shape()));
            CompositeLoadWork.drain(w);
            t.assertTrue(number(w,"steps")<=256&&number(w,"pendingOwners")==900,"one drain respects hard work cap and retains deferred owners");
            t.assertTrue(!w.isChunkLoaded(CompositeData.pos(far))&&number(w,"pruneTransactions")==0,"helper-first validation neither loads root chunks nor removes deferred geometry");
            CompositeLoadWork.migration(w,source,"cancel-test",()->{throw new AssertionError("unloaded source migration ran");});
            CompositeLoadWork.chunkUnloaded(w,source);
            t.assertTrue(number(w,"pendingOwners")==0&&number(w,"pendingMigrations")==0&&number(w,"deferredRootChunks")==0&&number(w,"cancelledOwners")==900,"last source unload retires owners, dependency index and migrations");
            CompositeLoadWork.drain(w);t.complete();
        }finally{clear(w,root);CompositeLoadWork.forget(w);}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_streaming")
    public void chunkCellDefersUnloadedOwnerAndCancelsItsDependencyOnSourceUnload(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));Cell claim=CompositeData.cell(root.add(4,0,0));
        try{
            CompositeBlockEntity entity=place(t,w,root);CompositeLoadWork.forget(w);var saved=entity.contributions().get(0);long source=new ChunkPos(root).toLong();Cell far=new Cell(30_000,root.getY(),30_000);
            var pending=new CompositeData.Contribution(new Owner(UUID.randomUUID(),saved.owner().registryId(),far),saved.rootData(),saved.shape());
            w.setBlockState(CompositeData.pos(claim),Blocks.STRUCTURE_VOID.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);CompositeLedger.get(w).put(claim,List.of(pending));
            CompositeLoadWork.chunkCells(w,source,Map.of(claim,List.of(pending)));CompositeLoadWork.drain(w);
            t.assertTrue(number(w,"pendingOwners")==0&&number(w,"pendingChunkCells")==0&&number(w,"deferredCellRootChunks")==1&&number(w,"deferredCells")==1,"chunk validates saved cells directly and keeps a loaded-source dependency for missing root");
            t.assertTrue(!w.isChunkLoaded(CompositeData.pos(far))&&CompositeLedger.get(w).at(claim).equals(List.of(pending)),"unloaded owner retains exact guest contribution without disk chunk loading");
            CompositeLoadWork.chunkUnloaded(w,source);
            t.assertTrue(number(w,"deferredCellRootChunks")==0&&number(w,"deferredCells")==0,"source unload releases the deferred cell and root indices");t.complete();
        }finally{CompositeLedger.get(w).put(claim,List.of());clear(w,root);CompositeLoadWork.forget(w);}
    }
    private static long number(ServerWorld w,String key){return ((Number)CompositeRuntime.streamingMetrics(w).get(key)).longValue();}
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_streaming")
    public void nativeChunkDecodeScopesShareImmutableValuesAndRestoreAfterNestedReads(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            CompositeBlockEntity original=place(t,w,root);NbtCompound extra=original.payload();extra.putByteArray("ScopeTypedBytes",new byte[]{-128,0,127});extra.putByte("ScopeTypedByte",(byte)1);original.set(original.resident(),original.contributions(),extra);NbtCompound saved=original.createNbt();byte[] expected=CompositeData.bytes(saved);
            CompositeBlockEntity first=new CompositeBlockEntity(root,original.getCachedState()),second=new CompositeBlockEntity(root,original.getCachedState()),inner=new CompositeBlockEntity(root,original.getCachedState()),restored=new CompositeBlockEntity(root,original.getCachedState()),outside=new CompositeBlockEntity(root,original.getCachedState());
            try(var scope=CompositeData.openDecodeScope()){
                first.readNbt(saved.copy());second.readNbt(saved.copy());var a=first.contributions().get(0);var b=second.contributions().get(0);
                t.assertTrue(a.owner()==b.owner()&&a.rootData()==b.rootData()&&a.shape()==b.shape(),"actual vanilla-style BE reads share only equal immutable graph values inside one chunk scope");
                try(var nested=CompositeData.openDecodeScope()){inner.readNbt(saved.copy());t.assertTrue(inner.contributions().get(0).rootData()!=a.rootData(),"reentrant chunk has an independent bounded decode pool");}
                restored.readNbt(saved.copy());t.assertTrue(restored.contributions().get(0).rootData()==a.rootData(),"closing nested decode restores the outer chunk pool");
            }
            outside.readNbt(saved.copy());t.assertTrue(outside.contributions().get(0).rootData()!=first.contributions().get(0).rootData(),"finished chunk scope retains no process-wide value pool");
            for(var entity:List.of(first,second,inner,restored,outside))t.assertTrue(Arrays.equals(expected,CompositeData.bytes(entity.createNbt())),"scoped native BE read preserves every canonical typed save byte");
            NbtCompound edited=first.payload();edited.putByte("ScopeTypedByte",(byte)7);first.set(first.resident(),first.contributions(),edited);t.assertTrue(second.payload().equals(extra),"mutable per-BE payload stays independent of shared immutable contributions");t.complete();
        }finally{clear(w,root);CompositeLoadWork.forget(w);}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=200,batchId="composite_streaming_border")
    public void stairsDoNotLoadAbsentNeighboursAndCompletedMigrationsReleaseTheirIndex(TestContext t)throws Exception{
        ServerWorld w=t.getWorld();var block=CompositeArchitecture.kindBlock("source_stairs_polished_deepslate_stairs_bottom");BlockState stair=block.getDefaultState();BlockPos far=new BlockPos(30_104,80,30_104);
        t.assertTrue(w.getChunkManager().getWorldChunk(far.getX()>>4,far.getZ()>>4)==null,"far stair connection query begins outside loaded world");
        t.assertTrue(ImportedStairBlock.connected(stair,w,far).equals(ImportedStairShapes.normalize(stair)),"missing neighbours preserve the existing normalized stair pose");
        t.assertTrue(w.getChunkManager().getWorldChunk(far.getX()>>4,far.getZ()>>4)==null,"automatic connection query does not complete absent FULL chunks");
        // Explicit test setup loads a root chunk. Its north/south neighbour
        // need not have a FULL chunk even when terrain dependencies exist.
        int cx=far.getX()>>4,cz=far.getZ()>>4;w.getChunk(cx,cz);Direction facing=ImportedStairShapes.facing(stair);BlockPos root=new BlockPos(cx*16+8,80,cz*16+8);
        root=facing.getAxis()==Direction.Axis.X?new BlockPos(cx*16+(facing==Direction.EAST?15:0),80,cz*16+8):new BlockPos(cx*16+8,80,cz*16+(facing==Direction.SOUTH?15:0));
        BlockPos ahead=root.offset(facing);BlockPos chosen=root;
        try{
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)w.getChunk(cx+dx,cz+dz);
            var placed=CompositeRuntime.place(w,root,stair,UUID.randomUUID(),null,ImportedTypeAliases.markPayload(new NbtCompound()));
            t.assertTrue(placed.outcome()==TransactionCore.Outcome.COMMITTED,"actual imported stair installed at a chunk border: "+placed);
            var entity=(CompositeBlockEntity)w.getBlockEntity(root);Owner owner=entity.resident();
            ImportedCollisionMigration.forget(w);t.assertTrue(ImportedCollisionMigration.pending(w,owner),"saved source owner is initially indexed for compatibility validation");ImportedCollisionMigration.discardOwner(w,owner);
            var worldsField=ImportedCollisionMigration.class.getDeclaredField("WORLDS");worldsField.setAccessible(true);Object index=((Map<?,?>)worldsField.get(null)).get(w);var chunksField=index.getClass().getDeclaredField("chunks");chunksField.setAccessible(true);
            t.assertTrue(((Map<?,?>)chunksField.get(index)).values().stream().noneMatch(values->((Set<?>)values).contains(owner)),"completed owner releases every reverse chunk index immediately");
            var drain=ImportedStairBlock.class.getDeclaredMethod("drain",ServerWorld.class);drain.setAccessible(true);ImportedStairBlock.scheduleNearby(w,root);drain.invoke(null,w);
            // Exercise the loaded-neighbour callback body independently of
            // neighbour notifications. Actual cold load timing is native QA.
            w.setBlockState(ahead,Blocks.STONE_STAIRS.getDefaultState().with(StairsBlock.FACING,facing.rotateYCounterclockwise()).with(StairsBlock.HALF,ImportedStairShapes.half(w.getBlockState(root))),Block.FORCE_STATE|Block.SKIP_DROPS);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(w,w.getChunkManager().getWorldChunk(ahead.getX()>>4,ahead.getZ()>>4));
            for(int i=0;i<30;i++)drain.invoke(null,w);
            t.assertTrue(ImportedStairShapes.shape(w.getBlockState(root))==net.minecraft.block.enums.StairShape.OUTER_LEFT,"arriving border neighbour restores actual automatic corner connection; current="+w.getBlockState(root)+" connected="+ImportedStairBlock.connected(w.getBlockState(root),w,root)+" result="+CompositeRuntime.lastResult()+" queue="+ImportedStairBlock.streamingMetrics(w));t.complete();
        }finally{w.setBlockState(ahead,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);w.setBlockState(chosen,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);CompositeRuntime.drain(w);}
    }
    private static void finish(TestContext t,ServerWorld w){for(int i=0;i<200&&number(w,"readyOwners")>0;i++)CompositeLoadWork.drain(w);t.assertTrue(number(w,"pendingOwners")==0,"loaded owner maintenance completes");}
    private static CompositeBlockEntity place(TestContext t,ServerWorld w,BlockPos root){clear(w,root);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);t.assertTrue(CompositeRuntime.place(w,root,CompositeArchitecture.kindBlock("prototype_wood_window").getDefaultState(),UUID.randomUUID(),null).outcome()==TransactionCore.Outcome.COMMITTED,"actual window installed");CompositeRuntime.drain(w);return (CompositeBlockEntity)w.getBlockEntity(root);}
    private static void clear(ServerWorld w,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-3,-1,-3),root.add(5,8,5)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(w);}
}
