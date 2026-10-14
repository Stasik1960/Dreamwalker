package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.gametest.UtilityV127GameTests;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.util.WorldSavePath;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class SourceWholeOpticsV127GameTests implements FabricGameTest {
    private record LocalView(net.minecraft.world.World backingWorld,BlockPos origin) implements ProjectedBlockView {
        @Override public BlockPos worldPosition(BlockPos localPosition){return localPosition.add(origin);}
        @Override public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos){return backingWorld.getBlockEntity(worldPosition(pos));}
        @Override public net.minecraft.block.BlockState getBlockState(BlockPos pos){return backingWorld.getBlockState(worldPosition(pos));}
        @Override public net.minecraft.fluid.FluidState getFluidState(BlockPos pos){return backingWorld.getFluidState(worldPosition(pos));}
        @Override public int getHeight(){return backingWorld.getHeight();}
        @Override public int getBottomY(){return backingWorld.getBottomY();}
    }
    private static NbtCompound payload(double shift,boolean enabled){
        var payload=ImportedTypeAliases.markPayload(null);var list=new NbtList();list.add(NbtDouble.of(0));list.add(NbtDouble.of(shift));list.add(NbtDouble.of(0));payload.put("SourceShift",list);
        if(enabled)payload.putInt(ImportedWholeSourceOpticsV127.CONTEXT,1);return payload;
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_whole_source_optics",tickLimit=400)
    public void exactConvertedCarrierShadowsPreserveEmptyPhysicsAndHandV12Guard(TestContext t){
        var world=t.getWorld();int index=0;
        var sourceCarrier=ImportedWholeSourceOpticsV127.opaqueRenderCarrier();
        t.assertTrue(sourceCarrier!=null&&sourceCarrier.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE,BlockPos.ORIGIN)
            &&sourceCarrier.getAmbientOcclusionLightLevel(net.minecraft.world.EmptyBlockView.INSTANCE,BlockPos.ORIGIN)
                ==net.minecraft.block.Blocks.STONE.getDefaultState().getAmbientOcclusionLightLevel(net.minecraft.world.EmptyBlockView.INSTANCE,BlockPos.ORIGIN)
            &&sourceCarrier.getLuminance()==0&&sourceCarrier.getFluidState().isEmpty(),"All 17 consumed source offsets share independently validated native opaque-cube renderer semantics");
        for(String path:List.of("source_original_tree","source_large_grille_gate")){
            var block=CompositeArchitecture.kindBlock(path);var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,0).with(CompositeRootBlock.ROTATION,index==0?0:2);
            var root=t.getAbsolutePos(new BlockPos(32+32*index,30,0));UtilityV127GameTests.load(world,root,12);double shift=index++==0?-1:-.8125;
            var ordinary=payload(shift,false);var flagged=payload(shift,true);UUID uuid=UUID.randomUUID();var before=CompositeRuntime.instance(world,root,state,uuid,ordinary);var after=CompositeRuntime.instance(world,root,state,uuid,flagged);
            for(var entry:before.cells().entrySet())t.assertTrue(entry.getValue().equals(after.cells().get(entry.getKey())),"Original physical and selection boxes remain exact at every existing cell");
            var carriers=ImportedWholeSourceOpticsV127.carriers(block.spec,state,flagged);t.assertTrue(carriers.size()==(path.equals("source_original_tree")?16:1),"Exactly the measured finite carrier count");
            long lightChecks=((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue();
            for(var cell:carriers.keySet()){
                t.assertTrue(after.cells().containsKey(cell),"Consumed source carrier is now an actual owned cell");
                if(!before.cells().containsKey(cell))t.assertTrue(after.cells().get(cell).equals(Footprint.EMPTY),"Added shadow cell has zero collision and zero selection");
            }
            var result=SourceConversionScope.initialInstances(Set.of(uuid),()->CompositeRuntime.place(world,root,state,uuid,null,flagged));
            t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"Initial flagged source object installs atomically: "+result.reason());
            var owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();
            t.assertTrue(((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue()>lightChecks,"Changed NEW helper opacity enqueues a real light-engine check after atomic publication");
            for(var entry:carriers.entrySet()){
                var pos=CompositeData.pos(owner.root().add(entry.getKey()));t.assertTrue(world.getBlockState(pos).isOf(CompositeArchitecture.CELL),"Carrier is a shared helper, not an extra solid cube");
                t.assertTrue(world.getBlockState(pos).getOpacity(world,pos)==entry.getValue(),"Published real helper opacity equals the exact native source value");
                var localView=new LocalView(world,root);var localPos=pos.subtract(root);
                t.assertTrue(localView.getBlockState(localPos).getOpacity(localView,localPos)==entry.getValue(),"Model-local projected view reads the same published source-helper opacity at its actual world position");
                int worker=CompletableFuture.supplyAsync(()->CompositeArchitecture.CELL.getDefaultState().getOpacity(world,pos)).join();t.assertTrue(worker==entry.getValue(),"Lighting worker reads the immutable already published opacity");
                int projectedWorker=CompletableFuture.supplyAsync(()->CompositeShapeSnapshots.sourceCarrierOpacity(localView,localPos)).join();t.assertTrue(projectedWorker==entry.getValue(),"Projected worker lookup retains the real world's immutable snapshot without world loading");
            }
            t.assertTrue(!block.art(state,flagged).getOrCreateNbt().contains(ImportedWholeSourceOpticsV127.CONTEXT),"Creative hand art does not acquire a conversion-only carrier context");
            BlockPos replacement=null;CompositeRootBlock inserted=null;
            if(path.equals("source_original_tree")){
                var offset=carriers.keySet().iterator().next();replacement=CompositeData.pos(owner.root().add(offset));inserted=CompositeArchitecture.kindBlock("source_bricks");
                var hand=inserted.art(inserted.getDefaultState().with(CompositeRootBlock.VARIANT,2),ImportedTypeAliases.markPayload(null));var player=UtilityV127GameTests.player(world);player.setStackInHand(Hand.MAIN_HAND,hand);
                var usage=new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(replacement),Direction.UP,replacement,false));var placement=new ItemPlacementContext(usage);
                t.assertTrue(placement.getBlockPos().equals(replacement)&&placement.canPlace(),"Real hand context selects the empty shadow helper itself instead of an adjacent cell");
                t.assertTrue(hand.useOnBlock(usage).isAccepted()&&world.getBlockState(replacement).isOf(inserted),"An ordinary mod block can be placed by hand into zero-physics source-shadow membership");
                t.assertTrue(CompositeRuntime.contributions(world,replacement).stream().anyMatch(entry->entry.owner().equals(owner)),"Hand placement keeps the original tree's guest binding for independent removal");
            }
            if(path.equals("source_large_grille_gate")){
                var offset=carriers.keySet().iterator().next();var pos=CompositeData.pos(owner.root().add(offset));var helperState=world.getBlockState(pos);long prior=((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue();
                t.assertTrue(CompositeRuntime.transitionPayload(world,owner,state,ordinary,null).outcome()==TransactionCore.Outcome.COMMITTED&&world.getBlockState(pos).equals(helperState)&&helperState.getOpacity(world,pos)==0,"Removing only the source context changes native opacity while the exact helper block state remains identical");
                t.assertTrue(((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue()>prior,"An identical-state opacity change still queues the real lighting provider");
                t.assertTrue(CompositeRuntime.transitionPayload(world,owner,state,flagged,null).outcome()==TransactionCore.Outcome.COMMITTED&&helperState.getOpacity(world,pos)==15,"Exact source context can be restored without physics or helper-state changes");
            }
            t.assertTrue(CompositeRuntime.remove(world,owner,null,false).outcome()==TransactionCore.Outcome.COMMITTED,"Converted whole object removal succeeds");
            for(var cell:carriers.keySet()){var pos=CompositeData.pos(owner.root().add(cell));t.assertTrue(CompositeRuntime.contributions(world,pos).stream().noneMatch(entry->entry.owner().equals(owner))&&CompositeArchitecture.CELL.getDefaultState().getOpacity(world,pos)==0,"Removal releases only that owner's shadow memberships");}
            if(replacement!=null){t.assertTrue(world.getBlockState(replacement).isOf(inserted),"Removing the tree preserves the independently hand-placed ordinary block");world.setBlockState(replacement,net.minecraft.block.Blocks.AIR.getDefaultState());}
        }
        var marked=payload(-1,true);
        for(var block:CompositeArchitecture.blocks())if(!block.spec.id.getPath().startsWith("source_"))t.assertTrue(ImportedWholeSourceOpticsV127.carriers(block.spec,block.getDefaultState(),marked).isEmpty(),"Original V12 cannot enter the NEW carrier optics rules");
        t.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_whole_source_optics",tickLimit=300)
    public void nativeCarrierShardReopenRetainsContextAndIndependentOriginalOwner(TestContext t)throws Exception{
        var world=t.getWorld();var absolute=t.getAbsolutePos(new BlockPos(256,140,256));UtilityV127GameTests.load(world,absolute,4);var root=CompositeData.cell(absolute);var cell=root.add(new Cell(0,3,0));var owner=new Owner(UUID.randomUUID(),"bloodborne_dw:source_original_tree",root);
        var state=CompositeArchitecture.kindBlock("source_original_tree").getDefaultState().with(CompositeRootBlock.VARIANT,0).with(CompositeRootBlock.ROTATION,0);var data=CompositeData.data(state,payload(-1,true));
        var original=new Owner(UUID.randomUUID(),"bloodborne_dw:prototype_double_door",root.add(new Cell(1,0,0)));var oldData=CompositeData.data(CompositeArchitecture.kindBlock("prototype_double_door").getDefaultState(),payload(-1,true));
        var newEntry=new CompositeData.Contribution(owner,data,Footprint.EMPTY);var oldEntry=new CompositeData.Contribution(original,oldData,Footprint.EMPTY);
        var directory=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-carrier-native-shard").resolve(UUID.randomUUID().toString());Set<Long> keys=new TreeSet<>();var chunk=new ChunkPos(cell.x()>>4,cell.z()>>4);var store=new ShardedCompositeLedger(world,directory,keys,()->{});
        try{
            store.put(cell,List.of(oldEntry));t.assertTrue(CompositeShapeSnapshots.sourceCarrierOpacity(world,CompositeData.pos(cell))==0,"Flagged original V12 helper remains completely unchanged");
            store.put(cell,List.of(oldEntry,newEntry));t.assertTrue(CompositeShapeSnapshots.sourceCarrierOpacity(world,CompositeData.pos(cell))==15,"NEW guest publishes the source shadow without changing original ownership");
            store.flush();store.unload(chunk);t.assertTrue(CompositeShapeSnapshots.sourceCarrierOpacity(world,CompositeData.pos(cell))==0,"Unload discards the published shadow scalar with its shard");
            var reopened=new ShardedCompositeLedger(world,directory,new TreeSet<>(keys),()->{});reopened.prepare(chunk);var saved=reopened.at(cell);
            t.assertTrue(saved.equals(List.of(oldEntry,newEntry))&&CompositeData.nbt(saved.stream().filter(entry->entry.owner().equals(owner)).findFirst().orElseThrow().rootData().blockEntityNbt()).getInt(ImportedWholeSourceOpticsV127.CONTEXT)==1,"Actual compressed shard reopening retains typed context, UUID and exact empty shape");
            long reads=(long)reopened.metrics().get("ownerShardReads");t.assertTrue(CompletableFuture.supplyAsync(()->CompositeShapeSnapshots.sourceCarrierOpacity(world,CompositeData.pos(cell))).join()==15&&(long)reopened.metrics().get("ownerShardReads")==reads,"Reopened lighting worker reads zero native disk IO");
            reopened.put(cell,List.of(oldEntry));t.assertTrue(CompositeShapeSnapshots.sourceCarrierOpacity(world,CompositeData.pos(cell))==0&&reopened.at(cell).equals(List.of(oldEntry)),"Removing NEW source guest restores the exact unrelated original membership");reopened.flush();reopened.unload(chunk);
        }finally{store.unload(chunk);CompositeShapeSnapshots.forgetChunk(world,chunk.toLong());}
        t.complete();
    }
}
