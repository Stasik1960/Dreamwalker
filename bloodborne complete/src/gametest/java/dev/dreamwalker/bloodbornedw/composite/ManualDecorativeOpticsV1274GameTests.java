package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.gametest.UtilityV127GameTests;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

/** Actual server light arrays, not only a getOpacity return value. The source
 * conversion path is deliberately unflagged; only the real hand placement opts in. */
public final class ManualDecorativeOpticsV1274GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v1274_manual_optics",tickLimit=600)
    public void realHandRoof90282RestoresActualFloorSkyLightAndPreservesUnflaggedSourceAndTypedPayload(TestContext t){new FloorProbe(t).start();}

    private static final class FloorProbe {
        final TestContext t;final ServerWorld world;
        final net.minecraft.server.network.ServerPlayerEntity player;
        final BlockPos hand,source;final CompositeRootBlock block;
        final Map<ChunkPos,Boolean> forced=new LinkedHashMap<>();
        final List<Owner> owners=new ArrayList<>();final JsonObject proof=new JsonObject();
        Owner handOwner,sourceOwner;NbtCompound manualPayload;boolean closed;
        FloorProbe(TestContext t){this.t=t;world=t.getWorld();player=UtilityV127GameTests.player(world);hand=t.getAbsolutePos(new BlockPos(24000,100,256));source=hand.east(12);block=CompositeArchitecture.kindBlock("source_birch_planks");}
        void start(){
            try{
                for(var root:List.of(hand,source)){
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                        var chunk=new ChunkPos(root.add(dx*16,0,dz*16));forced.putIfAbsent(chunk,world.getForcedChunks().contains(chunk.toLong()));world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(chunk.x,chunk.z);
                    }
                    for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.setBlockState(root.add(x,-2,z),Blocks.STONE.getDefaultState());
                }
                await(()->sky(hand)==15&&sky(hand.down())==15&&sky(source)==15&&sky(source.down())==15,this::install,"Initial actual empty floor columns receive full native sky light");
            }catch(RuntimeException|Error failure){cleanup();throw failure;}
        }
        int sky(BlockPos pos){return world.getLightLevel(LightType.SKY,pos);}
        void install(){
            for(var root:List.of(hand,source))world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
            var stack=new ItemStack(block.asItem());player.setStackInHand(Hand.MAIN_HAND,stack);player.refreshPositionAndAngles(hand.getX()+.5,hand.getY()+6,hand.getZ()-4,0,0);
            var point=new Vec3d(hand.getX()+.5,hand.getY(),hand.getZ()+.5);
            var hit=world.raycast(new RaycastContext(point.add(0,4,0),point.add(0,-.01,0),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
            t.assertTrue(hit.getBlockPos().equals(hand.down())&&hit.getSide()==Direction.UP,"Actual outline ray selects the temporary stone top");
            t.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"The actual registered 90282 item places normally, without no-collide");
            var entity=(CompositeBlockEntity)world.getBlockEntity(hand);handOwner=entity.resident();owners.add(handOwner);manualPayload=entity.payload();
            t.assertTrue(manualPayload.contains(ImportedSourceLightingV127.DECORATIVE_PLACEMENT,3)&&manualPayload.getInt(ImportedSourceLightingV127.DECORATIVE_PLACEMENT)==1,"Only the new hand placement explicitly opts into decorative opacity");
            var typed=new NbtCompound();typed.putByteArray("bytes",new byte[]{1,-4,7});typed.putIntArray("ints",new int[]{5,-6,7});typed.putLongArray("longs",new long[]{9,Long.MAX_VALUE});typed.putShort("short",(short)12);manualPayload.put("TypedOpticsProbe",typed);
            committed(CompositeRuntime.transitionPayload(world,handOwner,world.getBlockState(hand),manualPayload,null),"Typed payload is attached without changing physical geometry");
            var converted=ImportedTypeAliases.markPayload(null);converted.put("TypedOpticsProbe",typed.copy());
            committed(CompositeRuntime.place(world,source,block.getDefaultState(),UUID.randomUUID(),null,converted),"The unchanged source/converter transaction installs without a decorative flag");
            sourceOwner=((CompositeBlockEntity)world.getBlockEntity(source)).resident();owners.add(sourceOwner);
            for(var root:List.of(hand,source))world.setBlockState(root.down(),Blocks.AIR.getDefaultState());
            t.assertTrue(world.getBlockState(hand).getOpacity(world,hand)==0&&world.getBlockState(source).getOpacity(world,source)==Blocks.BIRCH_PLANKS.getDefaultState().getOpacity(world,source),"Hand opacity is zero while unflagged conversion keeps exact source opacity15");
            var handState=world.getBlockState(hand);int worker=CompletableFuture.supplyAsync(()->handState.getOpacity(world,hand)).join();
            t.assertTrue(worker==0,"A worker reads the already published scalar without owner NBT or synchronous chunk access");
            await(()->sky(hand)==15&&sky(hand.down())==15&&sky(source)==0&&sky(source.down())==14,this::revoke,"Real sky arrays illuminate the floor below manual decoration and retain the source cube shadow");
        }
        void revoke(){
            proof.addProperty("manualRootSky",sky(hand));proof.addProperty("manualFloorSurfaceSky",sky(hand.down()));proof.addProperty("convertedRootSky",sky(source));proof.addProperty("convertedFloorSurfaceSky",sky(source.down()));
            var state=world.getBlockState(hand);var without=manualPayload.copy();without.remove(ImportedSourceLightingV127.DECORATIVE_PLACEMENT);
            long before=((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue();
            committed(CompositeRuntime.transitionPayload(world,handOwner,state,without,null),"Removing only the opt-in succeeds");
            t.assertTrue(world.getBlockState(hand).equals(state)&&((CompositeBlockEntity)world.getBlockEntity(hand)).payload().equals(without)&&((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue()>before,"Identical-state payload change preserves all typed data and queues native light recheck");
            await(()->sky(hand)==0&&sky(hand.down())==14,this::restore,"Removing the flag alone restores the actual original cube shadow");
        }
        void restore(){
            committed(CompositeRuntime.transitionPayload(world,handOwner,world.getBlockState(hand),manualPayload,null),"Restore the manual decorative flag without geometry edits");
            await(()->sky(hand)==15&&sky(hand.down())==15,this::rejectAndRemove,"Restored instance optics clear the actual floor shadow again");
        }
        void rejectAndRemove(){
            var before=world.getBlockEntity(hand).createNbtWithIdentifyingData();var state=world.getBlockState(hand);
            var rejected=CompositeRuntime.place(world,hand,state,UUID.randomUUID(),null,ImportedTypeAliases.markPayload(null));
            t.assertTrue(rejected.outcome()==TransactionCore.Outcome.REJECTED&&before.equals(world.getBlockEntity(hand).createNbtWithIdentifyingData()),"Rejected replacement leaves the exact resident UUID and typed payload including optics untouched");
            t.assertTrue(((CompositeBlockEntity)world.getBlockEntity(source)).payload().getCompound("TypedOpticsProbe").equals(manualPayload.getCompound("TypedOpticsProbe"))&&!((CompositeBlockEntity)world.getBlockEntity(source)).payload().contains(ImportedSourceLightingV127.DECORATIVE_PLACEMENT),"The converted object's typed payload remains unchanged and never gains a flag");
            committed(CompositeRuntime.remove(world,handOwner,null,false),"Remove the manual decoration");committed(CompositeRuntime.remove(world,sourceOwner,null,false),"Remove the original source object");CompositeRuntime.drain(world);
            await(()->sky(hand)==15&&sky(hand.down())==15&&sky(source)==15&&sky(source.down())==15,this::finish,"Actual native sky arrays restore both floor columns after owner removal");
        }
        void finish(){
            try{proof.addProperty("status","PASS_ACTUAL_NATIVE_SKY_FLOOR_ARRAYS_MANUAL_90282_AND_UNFLAGGED_CONVERSION_WITH_TYPED_PAYLOAD");proof.addProperty("identicalStateOpacityToggle",true);proof.addProperty("removedFloorSky",sky(hand.down()));proof.addProperty("conversionSourceOpacityPreserved",true);proof.addProperty("workerGetOpacityUsesImmutablePublication",true);Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v1274-manual-decorative-optics-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));t.complete();}
            catch(Exception failure){throw new IllegalStateException(failure);}finally{cleanup();}
        }
        void committed(TransactionCore.Result result,String reason){t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,reason+": "+result.reason());}
        void await(BooleanSupplier ready,Runnable next,String reason){poll(ready,next,reason,t.getTick()+100);}
        void poll(BooleanSupplier ready,Runnable next,String reason,long deadline){t.runAtTick(t.getTick()+1,()->{try{if(ready.getAsBoolean()){next.run();return;}t.assertTrue(t.getTick()<deadline,reason+"; actual hand="+sky(hand)+"/"+sky(hand.down())+", source="+sky(source)+"/"+sky(source.down()));poll(ready,next,reason,deadline);}catch(RuntimeException|Error failure){cleanup();throw failure;}});}
        void cleanup(){
            if(closed)return;closed=true;
            for(var owner:owners)if(world.getBlockEntity(CompositeData.pos(owner.root())) instanceof CompositeBlockEntity entity&&owner.equals(entity.resident()))CompositeRuntime.remove(world,owner,null,false);
            CompositeRuntime.drain(world);for(var root:List.of(hand,source)){world.setBlockState(root.down(),Blocks.AIR.getDefaultState());for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.setBlockState(root.add(x,-2,z),Blocks.AIR.getDefaultState());}
            player.discard();forced.forEach((chunk,original)->{if(!original)world.setChunkForced(chunk.x,chunk.z,false);});
        }
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v1274_manual_optics_scope",tickLimit=150)
    public void compactPaletteAndOriginalV12RemainNativeWhileOwnedCompactSubclassHonorsOnlyTypedOptIn(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(26000,100,256));UtilityV127GameTests.load(world,root,8);var block=CompositeArchitecture.kindBlock("source_bricks");var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,2);var payload=ImportedTypeAliases.markPayload(null);UUID uuid=UUID.randomUUID();Owner owner=null;
        try{
            t.assertTrue(block instanceof ImportedCompactBlock&&!ImportedCompactBlock.compact(state),"Exercise the owned path of the actual compact-capable subclass");
            var flagged=ImportedSourceLightingV127.manualPlacementPayload(block.spec,state,payload.copy());
            var before=CompositeRuntime.instance(world,root,state,uuid,payload);var after=CompositeRuntime.instance(world,root,state,uuid,flagged);t.assertTrue(before.cells().equals(after.cells()),"Decorative optics do not alter selection, collision or occupied membership");
            var result=CompositeRuntime.place(world,root,state,uuid,null,flagged);t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"Owned compact-capable root installs with explicit manual optics");owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();
            t.assertTrue(state.getOpacity(world,root)==0,"Owned subclass delegates instance opacity to CompositeRootBlock");
            var compact=state.with(ImportedCompactBlock.COMPACT,true);t.assertTrue(ImportedCompactBlock.compact(compact)&&compact.getOpacity(world,root)==15,"Native compact palette remains fully opaque even at a position containing a flagged owned snapshot");
            var entity=(CompositeBlockEntity)world.getBlockEntity(root);var resident=owner;var entries=entity.contributions();var token=UUID.randomUUID();long checks=((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue();
            CompletableFuture.runAsync(()->CompositeShapeSnapshots.entity(world,root,token,resident,entries,payload,null)).join();
            t.assertTrue(state.getOpacity(world,root)==15&&((Number)CompositeShapeSnapshots.metrics(world).get("sourceCarrierLightChecks")).longValue()==checks,"Worker chunk-style publication completes without waiting for main or reading/mutating its chunk/light provider");
            entity.publishShapeSnapshot();t.assertTrue(state.getOpacity(world,root)==0,"The actual attached BE restores its publication token and typed flag before further transactions");
            var malformed=flagged.copy();malformed.putString(ImportedSourceLightingV127.DECORATIVE_PLACEMENT,"1");
            t.assertTrue(CompositeRuntime.transitionPayload(world,owner,state,malformed,null).outcome()==TransactionCore.Outcome.COMMITTED&&state.getOpacity(world,root)==15,"Only NBT INT1 opts in; malformed numeric-looking text preserves native opacity");
            for(var old:CompositeArchitecture.blocks())if(!old.spec.id.getPath().startsWith("source_")){var untouched=new NbtCompound();t.assertTrue(ImportedSourceLightingV127.manualPlacementPayload(old.spec,old.getDefaultState(),untouched)==untouched&&untouched.isEmpty(),"Every original V12 placement remains outside the new-only optics policy");}
            var compactPayload=ImportedTypeAliases.markPayload(null);t.assertTrue(ImportedSourceLightingV127.manualPlacementPayload(block.spec,compact,compactPayload)==compactPayload&&!compactPayload.contains(ImportedSourceLightingV127.DECORATIVE_PLACEMENT),"Compact native placement never gains decorative instance data");t.complete();
        }finally{if(owner!=null)CompositeRuntime.remove(world,owner,null,false);CompositeRuntime.drain(world);}
    }
}
