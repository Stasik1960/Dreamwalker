package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.*;

/** Actual server-worker queries while the server thread deliberately does not pump tasks.
 * A hidden FULL-chunk request cannot complete and fails the bounded deadline. */
public final class CompositeAsyncShapeGameTests implements FabricGameTest {
    private record Query(BlockPos pos,BlockState carrier) {}
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_async_shapes")
    public void workerLightingQueriesMatchRootHelperForeignAndMountedNativeWithoutChunkLoads(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8)),nativeRoot=root.add(6,0,0);ServerPlayerEntity p=player(w);
        try{
            clear(w,root);var block=CompositeArchitecture.kindBlock("prototype_wood_window");BlockState state=block.getDefaultState();UUID id=UUID.randomUUID();
            var object=CompositeRuntime.instance(w,root,state,id,new NbtCompound());
            Cell foreign=object.cells().entrySet().stream().filter(row->!row.getKey().equals(Cell.ORIGIN)&&!row.getValue().selection().isEmpty()).map(Map.Entry::getKey).findFirst().orElseThrow();
            BlockPos carrier=root.add(foreign.x(),foreign.y(),foreign.z());w.setBlockState(carrier,Blocks.STRUCTURE_VOID.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);
            w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            t.assertTrue(committed(CompositeRuntime.place(w,root,state,id,null)),"real window placement keeps noncolliding foreign carrier");
            BlockPos helper=object.cells().entrySet().stream().filter(row->!row.getKey().equals(Cell.ORIGIN)&&!row.getKey().equals(foreign)&&!row.getValue().selection().isEmpty()).map(row->root.add(row.getKey().x(),row.getKey().y(),row.getKey().z())).filter(pos->w.getBlockState(pos).isOf(CompositeArchitecture.CELL)).findFirst().orElseThrow();
            w.setBlockState(nativeRoot.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            var ladder=PrototypeArchitecture.LADDERS.get(0);w.setBlockState(nativeRoot,ladder.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true),Block.NOTIFY_ALL);
            t.assertTrue(VerticalMount.setOffset(w,nativeRoot,2,p),"native ladder shifted through real owner transaction: "+CompositeRuntime.lastResult());
            BlockPos wall=root.add(6,0,-4);w.setBlockState(wall.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);w.setBlockState(wall,PrototypeWallArchitecture.WALL.getDefaultState(),Block.NOTIFY_ALL);
            t.assertTrue(VerticalMount.setOffset(w,wall,.125,p),"native wall fractional shift retains cached worker geometry: "+CompositeRuntime.lastResult());
            BlockPos visual=root.add(-4,0,-3),physical=visual.south();BlockState hive=Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.FACING,Direction.NORTH).with(BeehiveBlock.HONEY_LEVEL,1),oldLadder=Blocks.LADDER.getDefaultState().with(LadderBlock.FACING,Direction.SOUTH);
            w.setBlockState(visual,hive,Block.NOTIFY_ALL);w.setBlockState(physical,oldLadder,Block.NOTIFY_ALL);
            t.assertTrue(SourceLadderRuntime.install(w,new SourceLadderRuntime.Installation(visual,physical,hive,oldLadder,w.getBlockEntity(visual).createNbtWithId(),null,0,PrototypeLadderBlock.Profile.BASE,UUID.randomUUID()),p).committed(),"actual source pair installed");
            t.assertTrue(VerticalMount.setOffset(w,physical,3,p),"source ladder and backing move through actual transaction: "+CompositeRuntime.lastResult());
            BlockPos unknown=new BlockPos(30_000,root.getY(),30_000);t.assertTrue(!w.isChunkLoaded(unknown),"foreign query chunk starts unloaded");
            List<Query> queries=new ArrayList<>();for(BlockPos pos:List.of(root,helper,carrier,nativeRoot,nativeRoot.up(2),wall,wall.up(),physical,visual,visual.up(3)))queries.add(new Query(pos,w.getBlockState(pos)));
            queries.add(new Query(unknown,Blocks.STONE.getDefaultState()));
            List<VoxelShape> expected=shapes(w,queries);NbtCompound journal=CompositeLedger.get(w).writeNbt(new NbtCompound());int pending=pendingOwnerTasks(w);
            compare(t,expected,worker(w,queries),"published server lighting");
            t.assertTrue(!w.isChunkLoaded(unknown)&&journal.equals(CompositeLedger.get(w).writeNbt(new NbtCompound()))&&pending==pendingOwnerTasks(w),"worker does not load chunks, mutate ledger, or schedule owner cleanup");
            t.assertTrue(w.getBlockState(carrier).isOf(Blocks.STRUCTURE_VOID),"foreign carrier remains the actual original block");t.complete();
        }finally{clear(w,root);p.discard();}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_async_shapes")
    public void savedDeferredOwnersKnownUuidReplacementsAndRollbackPublishExactShapeRevisions(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            clear(w,root);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);BlockState state=CompositeArchitecture.kindBlock("prototype_wood_window").getDefaultState();UUID uuid=UUID.randomUUID();
            t.assertTrue(committed(CompositeRuntime.place(w,root,state,uuid,null)),"initial committed owner");CompositeBlockEntity be=(CompositeBlockEntity)w.getBlockEntity(root);Owner owner=be.resident();NbtCompound saved=be.createNbt(),journal=CompositeLedger.get(w).writeNbt(new NbtCompound());
            List<Query> queries=CompositeRuntime.instance(w,root,state,uuid,be.payload()).cells().keySet().stream().map(cell->root.add(cell.x(),cell.y(),cell.z())).map(pos->new Query(pos,w.getBlockState(pos))).toList();List<VoxelShape> initial=shapes(w,queries);
            Map<Cell,List<CompositeData.Contribution>> ledger=new HashMap<>();for(Cell cell:CompositeLedger.get(w).cells())ledger.put(cell,CompositeLedger.get(w).at(cell));
            // Saved ledger publication alone precedes BE attachment during spawn lighting.
            CompositeShapeSnapshots.forget(w);CompositeShapeSnapshots.ledgerLoaded(w,ledger);compare(t,initial,worker(w,queries),"saved deferred roots without attached metadata");
            be.readNbt(saved.copy());compare(t,initial,worker(w,queries),"BE typed read/attach publication");
            be.set(new Owner(UUID.randomUUID(),owner.registryId(),owner.root()),be.contributions(),be.payload());
            List<VoxelShape> stale=worker(w,queries);for(VoxelShape shape:stale)t.assertTrue(shape.isEmpty(),"known different resident UUID rejects stale prior-owner contributions");
            be.readNbt(saved.copy());BlockState opened=state.with(CompositeRootBlock.OPEN,true);ObjectInstance proposed=CompositeRuntime.instance(w,root,opened,uuid,be.payload());
            boolean[] observed={false};FabricCompositeWorld failing=new FabricCompositeWorld(w,Map.of(owner,proposed)){
                int count;
                @Override public boolean writeSilently(Cell cell,CellSnapshot next){boolean result=super.writeSilently(cell,next);if(++count==2){
                    // A chunk-attachment publication on another thread must not
                    // flush the main thread's partially written transaction.
                    // Capture the BE on the server, never resolve it on the worker.
                    CompositeBlockEntity current=(CompositeBlockEntity)w.getBlockEntity(root);FutureTask<Void> attach=new FutureTask<>(()->{current.publishShapeSnapshot();return null;});Thread thread=new Thread(attach,"DW concurrent BE shape publication regression");thread.setDaemon(true);thread.start();
                    try{attach.get(2,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException("Concurrent publication failed",failure);}
                    compare(t,initial,worker(w,queries),"worker sees last committed revision after concurrent publication during partial writes");observed[0]=true;return false;
                }return result;}
            };
            var result=CompositeRuntime.execute(failing,CompositeRuntime.core().transitionPayload(failing,owner,proposed,List.of()));
            t.assertTrue(observed[0]&&result.outcome()==TransactionCore.Outcome.ROLLED_BACK,"injected second actual write fails and restores all snapshots: "+result);
            t.assertTrue(saved.equals(w.getBlockEntity(root).createNbt())&&journal.equals(CompositeLedger.get(w).writeNbt(new NbtCompound())),"rollback preserves full typed BE and ownership ledger");compare(t,initial,worker(w,queries),"rollback publishes exact old revision");
            t.assertTrue(committed(CompositeRuntime.transition(w,owner,opened,null)),"actual open transition commits after rollback");List<Query> next=queries.stream().map(q->new Query(q.pos,w.getBlockState(q.pos))).toList();compare(t,shapes(w,next),worker(w,next),"new committed state publication");
            t.complete();
        }finally{clear(w,root);}
    }
    @GameTest(templateName="bloodborne_dw:window_test",tickLimit=120,batchId="composite_async_shapes")
    public void equalContributionSharingPreservesRootHelperPayloadAndWorkerGeometry(TestContext t){
        ServerWorld w=t.getWorld();BlockPos root=t.getAbsolutePos(new BlockPos(8,4,8));
        try{
            clear(w,root);w.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            BlockState state=CompositeArchitecture.kindBlock("prototype_wood_window").getDefaultState();UUID uuid=UUID.randomUUID();
            t.assertTrue(committed(CompositeRuntime.place(w,root,state,uuid,null)),"owner placed for contribution sharing regression");
            CompositeBlockEntity be=(CompositeBlockEntity)w.getBlockEntity(root);NbtCompound extra=be.payload();
            extra.putByteArray("SharingBytes",new byte[]{-128,0,127});extra.putByte("SharingTyped",(byte)1);be.set(be.resident(),be.contributions(),extra);
            BlockPos helper=CompositeRuntime.instance(w,root,state,uuid,be.payload()).cells().keySet().stream().filter(cell->!cell.equals(Cell.ORIGIN)).map(cell->root.add(cell.x(),cell.y(),cell.z())).filter(pos->w.getBlockState(pos).isOf(CompositeArchitecture.CELL)).findFirst().orElseThrow();
            CompositeBlockEntity helperBe=(CompositeBlockEntity)w.getBlockEntity(helper);
            be.readNbt(be.createNbt());helperBe.readNbt(helperBe.createNbt());Owner resident=be.resident();
            List<CompositeData.Contribution> original=be.contributions(),originalHelper=helperBe.contributions(),shared=CompositeLedger.get(w).at(CompositeData.cell(root)),helperShared=CompositeLedger.get(w).at(CompositeData.cell(helper));
            t.assertTrue(original!=shared&&original.equals(shared)&&originalHelper!=helperShared&&originalHelper.equals(helperShared),"saved BEs begin with independent graphs equal to their ledger lists");
            List<Query> queries=List.of(new Query(root,state),new Query(helper,w.getBlockState(helper)));List<VoxelShape> initial=shapes(w,queries);
            byte[] rootBytes=CompositeData.bytes(be.createNbt()),helperBytes=CompositeData.bytes(helperBe.createNbt());NbtCompound journal=CompositeLedger.get(w).writeNbt(new NbtCompound());
            // Expose entity graph publication independently of the equal ledger
            // graph, which normally wins queries and would hide stale BE refs.
            CompositeShapeSnapshots.beginBatch(w);try{CompositeShapeSnapshots.ledger(w,CompositeData.cell(root),List.of());CompositeShapeSnapshots.ledger(w,CompositeData.cell(helper),List.of());}finally{CompositeShapeSnapshots.endBatch(w);}
            t.assertTrue(workerContribution(w,root)==original.get(0)&&workerContribution(w,helper)==originalHelper.get(0),"worker snapshot initially retains each independently decoded BE graph");
            Object lightChecks=CompositeShapeSnapshots.metrics(w).get("sourceCarrierLightChecks");
            CompositeShapeSnapshots.beginBatch(w);
            try{
                t.assertTrue(be.shareEqualContributions(shared)&&be.contributions()==shared,"root adopts the existing immutable equal ledger list by reference");
                t.assertTrue(helperBe.resident()==null&&helperBe.shareEqualContributions(helperShared)&&helperBe.contributions()==helperShared,"helper adopts an equal graph while retaining its helper role");
                t.assertTrue(workerContribution(w,root)==original.get(0)&&workerContribution(w,helper)==originalHelper.get(0),"equal-ref replacement remains unpublished until its atomic batch completes");
            }finally{CompositeShapeSnapshots.endBatch(w);}
            t.assertTrue(workerContribution(w,root)==shared.get(0)&&workerContribution(w,helper)==helperShared.get(0),"completed publication releases both old equal BE graphs from worker snapshots");
            t.assertTrue(lightChecks.equals(CompositeShapeSnapshots.metrics(w).get("sourceCarrierLightChecks")),"equal graph sharing does not schedule source light checks");
            t.assertTrue(!be.shareEqualContributions(shared)&&!helperBe.shareEqualContributions(helperShared),"sharing the already installed lists is a no-op");
            t.assertTrue(be.resident()==resident&&be.payload().equals(extra)&&helperBe.resident()==null&&Arrays.equals(rootBytes,CompositeData.bytes(be.createNbt()))&&Arrays.equals(helperBytes,CompositeData.bytes(helperBe.createNbt())),"sharing preserves resident/helper authority and every typed BE byte");
            // Exercise the registered callback body with actual already FULL
            // chunks as well as the direct sharing primitive above. A real
            // disk unload/reload and its callback timing remain separate QA.
            be.readNbt(be.createNbt());helperBe.readNbt(helperBe.createNbt());Owner callbackResident=be.resident();
            t.assertTrue(be.contributions()!=shared&&be.contributions().equals(shared)&&helperBe.contributions()!=helperShared&&helperBe.contributions().equals(helperShared),"BE decode recreates independent equal graphs before the actual chunk callback body");
            var rootChunk=w.getChunkManager().getWorldChunk(root.getX()>>4,root.getZ()>>4);var helperChunk=w.getChunkManager().getWorldChunk(helper.getX()>>4,helper.getZ()>>4);
            t.assertTrue(rootChunk!=null&&helperChunk!=null,"callback receives the existing loaded WorldChunks");
            CompositeRuntime.chunkLoaded(w,rootChunk);if(helperChunk!=rootChunk)CompositeRuntime.chunkLoaded(w,helperChunk);
            t.assertTrue(be.contributions()==shared&&helperBe.contributions()==helperShared&&be.resident()==callbackResident&&helperBe.resident()==null,"chunk callback canonicalizes both attached root and helper graphs without changing their authority");
            t.assertTrue(Arrays.equals(rootBytes,CompositeData.bytes(be.createNbt()))&&Arrays.equals(helperBytes,CompositeData.bytes(helperBe.createNbt()))&&workerContribution(w,root)==shared.get(0)&&workerContribution(w,helper)==helperShared.get(0),"callback body preserves every typed BE byte and publishes canonical entity references");
            var first=shared.get(0);NbtCompound changedPayload=CompositeData.nbt(first.rootData().blockEntityNbt());changedPayload.putByte("SharingDifference",(byte)1);
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(first.owner(),first.rootData().withNbt(CompositeData.bytes(changedPayload)),first.shape())),"different root payload");
            Map<String,String> changedProperties=new TreeMap<>(first.rootData().properties());changedProperties.put("sharing_test","changed");
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(first.owner(),first.rootData().withProperties(changedProperties),first.shape())),"different root state properties");
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(first.owner(),new CellSnapshot.BlockData("test:different",first.rootData().properties(),first.rootData().blockEntityNbt()),first.shape())),"different root registry ID");
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(new Owner(UUID.randomUUID(),first.owner().registryId(),first.owner().root()),first.rootData(),first.shape())),"different owner UUID");
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(new Owner(first.owner().instanceId(),first.owner().registryId(),first.owner().root().add(new Cell(1,0,0))),first.rootData(),first.shape())),"different owner root");
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(new Owner(first.owner().instanceId(),"test:different",first.owner().root()),first.rootData(),first.shape())),"different owner registry ID");
            Footprint changedSelection=new Footprint(first.shape().collision(),first.shape().selection().isEmpty()?List.of(new Box(0,0,0,1,1,1)):List.of());
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(first.owner(),first.rootData(),changedSelection)),"different selection geometry");
            Footprint changedCollision=new Footprint(first.shape().collision().isEmpty()?List.of(new Box(0,0,0,1,1,1)):List.of(),first.shape().selection());
            rejectsSharing(t,be,replaceFirst(shared,new CompositeData.Contribution(first.owner(),first.rootData(),changedCollision)),"different collision geometry");
            rejectsSharing(t,be,List.copyOf(shared.subList(1,shared.size())),"different contribution count");
            t.assertTrue(journal.equals(CompositeLedger.get(w).writeNbt(new NbtCompound())),"sharing never changes the authoritative ownership ledger");
            compare(t,initial,worker(w,queries),"equal list sharing republishes unchanged worker geometry");
            CompositeShapeSnapshots.beginBatch(w);try{CompositeShapeSnapshots.ledger(w,CompositeData.cell(root),shared);CompositeShapeSnapshots.ledger(w,CompositeData.cell(helper),helperShared);}finally{CompositeShapeSnapshots.endBatch(w);}
            t.complete();
        }finally{clear(w,root);}
    }
    private static List<CompositeData.Contribution> replaceFirst(List<CompositeData.Contribution> entries,CompositeData.Contribution replacement){List<CompositeData.Contribution> changed=new ArrayList<>(entries);changed.set(0,replacement);return List.copyOf(changed);}
    /** Current production keeps this server-thread map private, without a
     * diagnosticsMetrics API. Read its existing size only; never mutate it. */
    private static int pendingOwnerTasks(ServerWorld world){try{var field=CompositeRuntime.class.getDeclaredField("PENDING");field.setAccessible(true);Object tasks=((Map<?,?>)field.get(null)).get(world);return tasks instanceof Map<?,?> pending?pending.size():0;}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot inspect current pending owner work",failure);}}
    private static void rejectsSharing(TestContext t,CompositeBlockEntity entity,List<CompositeData.Contribution> different,String reason){
        List<CompositeData.Contribution> original=entity.contributions();Owner resident=entity.resident();byte[] before=CompositeData.bytes(entity.createNbt());
        t.assertTrue(!entity.shareEqualContributions(different)&&entity.contributions()==original&&entity.resident()==resident&&Arrays.equals(before,CompositeData.bytes(entity.createNbt())),reason+" cannot replace the resident contribution graph or typed BE data");
    }
    private static CompositeData.Contribution workerContribution(ServerWorld w,BlockPos pos){FutureTask<CompositeData.Contribution> job=new FutureTask<>(()->CompositeShapeSnapshots.contributions(w,pos).get(0));Thread thread=new Thread(job,"DW shared BE graph publication regression");thread.setDaemon(true);thread.start();try{return job.get(2,TimeUnit.SECONDS);}catch(Exception failure){job.cancel(true);throw new IllegalStateException("Shared graph publication failed",failure);}}
    private static List<VoxelShape> shapes(ServerWorld w,List<Query> queries){List<VoxelShape> out=new ArrayList<>();for(Query q:queries){out.add(q.carrier.getOutlineShape(w,q.pos));out.add(q.carrier.getCollisionShape(w,q.pos));out.add(q.carrier.getCollisionShape(w,q.pos,ShapeContext.absent()));}return List.copyOf(out);}
    private static List<VoxelShape> worker(ServerWorld w,List<Query> queries){FutureTask<List<VoxelShape>> job=new FutureTask<>(()->shapes(w,queries));Thread thread=new Thread(job,"DW native async lighting regression");thread.setDaemon(true);thread.start();try{return job.get(2,TimeUnit.SECONDS);}catch(Exception failure){job.cancel(true);throw new IllegalStateException("Worker shapes required queued server work or failed (server thread did not pump)",failure);}}
    private static void compare(TestContext t,List<VoxelShape> expected,List<VoxelShape> actual,String phase){t.assertTrue(expected.size()==actual.size(),phase+" query count");for(int i=0;i<expected.size();i++)t.assertTrue(!VoxelShapes.matchesAnywhere(expected.get(i),actual.get(i),BooleanBiFunction.NOT_SAME),phase+" query "+i+" actual="+actual.get(i).getBoundingBoxes()+" expected="+expected.get(i).getBoundingBoxes());}
    private static boolean committed(TransactionCore.Result result){return result.outcome()==TransactionCore.Outcome.COMMITTED;}
    private static ServerPlayerEntity player(ServerWorld w){ServerPlayerEntity p=new ServerPlayerEntity(w.getServer(),w,new GameProfile(UUID.randomUUID(),"AsyncShapeTest")){@Override public void sendMessage(Text text,boolean overlay){}};p.getAbilities().allowModifyWorld=true;p.getAbilities().creativeMode=true;p.setPosition(0,200,0);return p;}
    private static void clear(ServerWorld w,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-5,-1,-5),root.add(8,8,5)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(w);}
}
