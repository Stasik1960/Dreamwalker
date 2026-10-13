package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import dev.dreamwalker.bloodbornedw.architecture.NoCollidePlacement;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Ordinary item paths, actual occupied physical guest cells and atomic owners.
 * No scripted conversion privilege, no no-collide lease, no position override. */
public final class UtilityPlacementV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utility_installation",tickLimit=100)
    public void ordinaryBarrierAndBbItemsOverlapGuestCollisionInBothInstallationOrders(TestContext c)throws Exception {
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);
        var first=c.getAbsolutePos(new BlockPos(3,20,3));var second=first.east(8);
        var forced=load(world,List.of(first,second));var block=CompositeArchitecture.kindBlock("source_addon_barrel_0");
        Owner[] owners=new Owner[2];var firstGuest=first.up();var secondGuest=second.up();
        Runnable cleanup=()->{
            for(var guest:List.of(firstGuest,secondGuest))if(DwUtilityBlocks.marker(world.getBlockState(guest)))DwUtilityBlocks.remove(world,guest,player);
            for(var owner:owners)if(owner!=null&&world.getBlockEntity(CompositeData.pos(owner.root())) instanceof CompositeBlockEntity be&&owner.equals(be.resident()))CompositeRuntime.remove(world,owner,player,false);
            CompositeRuntime.drain(world);
            for(var pos:List.of(first.down(),second.down(),firstGuest,secondGuest))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            player.discard();release(world,forced);
        };
        try {
            world.setBlockState(first.down(),Blocks.STONE.getDefaultState());
            c.assertTrue(use(world,player,new ItemStack(block.asItem()),first.down(),Direction.UP,top(first.down())).isAccepted(),"Ordinary registered BB barrel item places the source object");
            c.assertTrue(world.getBlockEntity(first) instanceof CompositeBlockEntity,"Actual normal BB item creates an owned root");
            var root=(CompositeBlockEntity)world.getBlockEntity(first);owners[0]=root.resident();var rootData=root.createNbtWithIdentifyingData();var rootState=world.getBlockState(first);
            var before=List.copyOf(CompositeRuntime.contributions(world,firstGuest));
            c.assertTrue(world.getBlockState(firstGuest).isOf(CompositeArchitecture.CELL)&&!before.isEmpty()&&before.stream().anyMatch(entry->entry.owner().equals(owners[0])&&!entry.shape().collision().isEmpty()),"Target is a genuine helper cell containing this BB object's active physical collision");
            var shape=world.getBlockState(firstGuest).getOutlineShape(world,firstGuest,ShapeContext.of(player));
            c.assertTrue(!shape.isEmpty(),"Actual helper has a selectable upper face for the normal barrier item click");
            var face=new Vec3d(firstGuest.getX()+.5,firstGuest.getY()+shape.getBoundingBox().maxY,firstGuest.getZ()+.5);
            c.assertTrue(use(world,player,new ItemStack(DwUtilityBlocks.BARRIER_ITEM),firstGuest,Direction.UP,face).isAccepted(),"Ordinary DW-barrier item installs directly inside the occupied BB helper collision");
            c.assertTrue(world.getBlockState(firstGuest).isOf(DwUtilityBlocks.BARRIER)&&CompositeRuntime.contributions(world,firstGuest).equals(before),"Marker replaces only the helper carrier, preserving exact independent guest contribution records");
            // Reverse order uses a real stone face below the future guest cell.
            // Remove that temporary support before placing the ordinary BB root.
            world.setBlockState(second,Blocks.STONE.getDefaultState());
            c.assertTrue(use(world,player,new ItemStack(DwUtilityBlocks.BARRIER_ITEM),second,Direction.UP,top(second)).isAccepted(),"Normal DW-barrier item installs before the reverse BB object");
            c.assertTrue(world.getBlockState(secondGuest).isOf(DwUtilityBlocks.BARRIER),"Real normal placement has the expected adjacent-cell destination");
            world.setBlockState(second,Blocks.AIR.getDefaultState());world.setBlockState(second.down(),Blocks.STONE.getDefaultState());
            var markerData=world.getBlockEntity(secondGuest).createNbtWithIdentifyingData();var markerState=world.getBlockState(secondGuest);
            c.assertTrue(CompositeRuntime.contributions(world,secondGuest).isEmpty(),"Reverse barrier starts without guest owners");
            c.assertTrue(use(world,player,new ItemStack(block.asItem()),second.down(),Direction.UP,top(second.down())).isAccepted(),"Ordinary BB item installs through the existing DW-barrier guest cell without a forced-placement lease");
            c.assertTrue(world.getBlockEntity(second) instanceof CompositeBlockEntity,"Reverse normal BB item creates its real independent root");owners[1]=((CompositeBlockEntity)world.getBlockEntity(second)).resident();
            c.waitAndRun(3,()->{
                try {
                    c.assertTrue(world.getBlockState(first).equals(rootState)&&world.getBlockEntity(first) instanceof CompositeBlockEntity be&&owners[0].equals(be.resident())&&rootData.equals(be.createNbtWithIdentifyingData()),"Barrier installation and scheduled cleanup preserve original BB UUID, state and exact typed root data");
                    c.assertTrue(world.getBlockState(firstGuest).isOf(DwUtilityBlocks.BARRIER)&&CompositeRuntime.contributions(world,firstGuest).equals(before),"First barrier and physical BB guests survive actual queued lifecycle processing");
                    c.assertTrue(world.getBlockState(secondGuest).equals(markerState)&&world.getBlockEntity(secondGuest) instanceof CompositeBlockEntity&&stableMarker(markerData).equals(stableMarker(world.getBlockEntity(secondGuest).createNbtWithIdentifyingData())),"Reverse atomic BB handler retains exact marker state, payload, resident, ID and coordinates; only guest owners may be added");
                    var added=CompositeRuntime.contributions(world,secondGuest);
                    c.assertTrue(added.size()==1&&added.get(0).owner().equals(owners[1])&&!added.get(0).shape().collision().isEmpty(),"Reverse barrier cell receives exactly the new BB object's physical guest contribution");
                    c.assertTrue(((CompositeBlockEntity)world.getBlockEntity(secondGuest)).contributions().equals(added),"Marker's typed owner records agree with the independently read guest ledger");
                    c.assertTrue(!owners[0].instanceId().equals(owners[1].instanceId())&&!NoCollidePlacement.armed(player)&&!NoCollidePlacement.ignoringBlocks(),"Both ordinary installations retain independent identity and no privilege state");
                    proof(c,"v127-utility-overlap-native-proof.json","PASS_ORDINARY_UTILITY_INSTALLATION_BOTH_ORDERS");c.complete();
                }catch(Exception failure){throw new IllegalStateException(failure);}finally{cleanup.run();}
            });
        }catch(Exception|Error failure){cleanup.run();throw failure;}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utility_installation",tickLimit=100)
    public void ordinaryDwAndVanillaBarrierItemsRejectOverlapButAcceptAdjacentCells(TestContext c)throws Exception {
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var base=c.getAbsolutePos(new BlockPos(3,20,3));
        var cells=List.of(base,base.east(5),base.east(10),base.east(15));var forced=load(world,cells);
        try {
            int checked=0;
            for(boolean existingDw:List.of(true,false))for(boolean incomingDw:List.of(true,false)){
                var goal=cells.get(checked++);world.setBlockState(goal.down(),Blocks.STONE.getDefaultState());
                var existing=existingDw?DwUtilityBlocks.BARRIER_ITEM:Items.BARRIER;
                var incoming=incomingDw?DwUtilityBlocks.BARRIER_ITEM:Items.BARRIER;
                c.assertTrue(use(world,player,new ItemStack(existing),goal.down(),Direction.UP,top(goal.down())).isAccepted(),"Fixture barrier is itself placed by its ordinary item");
                var before=world.getBlockState(goal);var entity=world.getBlockEntity(goal);var beforeData=entity==null?null:entity.createNbtWithIdentifyingData();
                c.assertTrue(before.isOf(existingDw?DwUtilityBlocks.BARRIER:Blocks.BARRIER),"Clicked real support face puts the barrier in the intended occupied cell");
                c.assertTrue(!use(world,player,new ItemStack(incoming),goal.down(),Direction.UP,top(goal.down())).isAccepted(),"Ordinary "+(incomingDw?"DW":"vanilla")+" barrier refuses overlap with "+(existingDw?"DW":"vanilla")+" barrier");
                c.assertTrue(world.getBlockState(goal).equals(before)&&(beforeData==null?world.getBlockEntity(goal)==null:world.getBlockEntity(goal)!=null&&beforeData.equals(world.getBlockEntity(goal).createNbtWithIdentifyingData())),"Rejected overlap preserves exact existing barrier state and typed data");
                var side=new Vec3d(goal.getX()+1,goal.getY()+.5,goal.getZ()+.5);
                c.assertTrue(use(world,player,new ItemStack(incoming),goal,Direction.EAST,side).isAccepted()&&world.getBlockState(goal.east()).isOf(incomingDw?DwUtilityBlocks.BARRIER:Blocks.BARRIER),"The same normal item accepts a touching adjacent cell, distinguishing overlap from adjacency");
            }
            c.assertTrue(checked==4&&!NoCollidePlacement.armed(player)&&!NoCollidePlacement.ignoringBlocks(),"All four DW/vanilla overlap pairs were checked without no-collide privilege");
            proof(c,"v127-utility-barrier-exclusion-native-proof.json","PASS_FOUR_NORMAL_BARRIER_EXCLUSION_PAIRS_AND_ADJACENCY");c.complete();
        }finally{
            for(var goal:cells)for(var pos:List.of(goal,goal.east(),goal.down()))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            CompositeRuntime.drain(world);player.discard();release(world,forced);
        }
    }
    private static Vec3d top(BlockPos pos){return new Vec3d(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);}
    private static NbtCompound stableMarker(NbtCompound source){var stable=source.copy();stable.remove("owners");return stable;}
    private static ActionResult use(ServerWorld world,ServerPlayerEntity player,ItemStack stack,BlockPos clicked,Direction face,Vec3d point){
        NoCollidePlacement.invalidate(player);player.setStackInHand(Hand.MAIN_HAND,stack);player.refreshPositionAndAngles(point.x,point.y+1,point.z-4,0,0);
        return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(point,face,clicked,false)));
    }
    private static Map<ChunkPos,Boolean> load(ServerWorld world,List<BlockPos> cells){
        Map<ChunkPos,Boolean> forced=new LinkedHashMap<>();for(var pos:cells)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var chunk=new ChunkPos(pos.add(x*16,0,z*16));forced.putIfAbsent(chunk,world.getForcedChunks().contains(chunk.toLong()));world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(chunk.x,chunk.z);
        }return forced;
    }
    private static void release(ServerWorld world,Map<ChunkPos,Boolean> forced){forced.forEach((chunk,wasForced)->{if(!wasForced)world.setChunkForced(chunk.x,chunk.z,false);});}
    private static void proof(TestContext c,String file,String status)throws Exception {var proof=new JsonObject();proof.addProperty("status",status);proof.addProperty("method","Actual registered ItemStack.useOnBlock and native ItemPlacementContext, occupied physical guest cells, atomic handler and queued cleanup");proof.addProperty("conversionPrivilege",false);proof.addProperty("noCollideLease",false);Files.writeString(c.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve(file),new GsonBuilder().setPrettyPrinting().create().toJson(proof));}
}
