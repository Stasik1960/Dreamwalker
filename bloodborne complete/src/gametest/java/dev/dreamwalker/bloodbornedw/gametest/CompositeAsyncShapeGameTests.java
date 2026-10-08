package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
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
            List<VoxelShape> expected=shapes(w,queries);NbtCompound journal=CompositeLedger.get(w).writeNbt(new NbtCompound());Object pending=CompositeRuntime.diagnosticsMetrics(w).get("pendingOwnerTasks");
            compare(t,expected,worker(w,queries),"published server lighting");
            t.assertTrue(!w.isChunkLoaded(unknown)&&journal.equals(CompositeLedger.get(w).writeNbt(new NbtCompound()))&&pending.equals(CompositeRuntime.diagnosticsMetrics(w).get("pendingOwnerTasks")),"worker does not load chunks, mutate ledger, or schedule owner cleanup");
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
    private static List<VoxelShape> shapes(ServerWorld w,List<Query> queries){List<VoxelShape> out=new ArrayList<>();for(Query q:queries){out.add(q.carrier.getOutlineShape(w,q.pos));out.add(q.carrier.getCollisionShape(w,q.pos));out.add(q.carrier.getCollisionShape(w,q.pos,ShapeContext.absent()));}return List.copyOf(out);}
    private static List<VoxelShape> worker(ServerWorld w,List<Query> queries){FutureTask<List<VoxelShape>> job=new FutureTask<>(()->shapes(w,queries));Thread thread=new Thread(job,"DW native async lighting regression");thread.setDaemon(true);thread.start();try{return job.get(2,TimeUnit.SECONDS);}catch(Exception failure){job.cancel(true);throw new IllegalStateException("Worker shapes required queued server work or failed (server thread did not pump)",failure);}}
    private static void compare(TestContext t,List<VoxelShape> expected,List<VoxelShape> actual,String phase){t.assertTrue(expected.size()==actual.size(),phase+" query count");for(int i=0;i<expected.size();i++)t.assertTrue(!VoxelShapes.matchesAnywhere(expected.get(i),actual.get(i),BooleanBiFunction.NOT_SAME),phase+" query "+i+" actual="+actual.get(i).getBoundingBoxes()+" expected="+expected.get(i).getBoundingBoxes());}
    private static boolean committed(TransactionCore.Result result){return result.outcome()==TransactionCore.Outcome.COMMITTED;}
    private static ServerPlayerEntity player(ServerWorld w){ServerPlayerEntity p=new ServerPlayerEntity(w.getServer(),w,new GameProfile(UUID.randomUUID(),"AsyncShapeTest")){@Override public void sendMessage(Text text,boolean overlay){}};p.getAbilities().allowModifyWorld=true;p.getAbilities().creativeMode=true;p.setPosition(0,200,0);return p;}
    private static void clear(ServerWorld w,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-5,-1,-5),root.add(8,8,5)))w.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(w);}
}
