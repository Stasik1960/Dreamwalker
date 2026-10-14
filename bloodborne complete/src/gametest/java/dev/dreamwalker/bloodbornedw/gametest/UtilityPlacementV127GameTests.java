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
import net.minecraft.world.RaycastContext;

/** Ordinary item paths, actual occupied physical guest cells and atomic owners.
 * No scripted conversion privilege, no no-collide lease, no position override. */
public final class UtilityPlacementV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1274_held_overlap",tickLimit=100)
    public void realOutlineRaysRepeatedlyPlaceOneSurvivalStackInOwnedCollisionUntilItIsEmpty(TestContext c){
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var first=c.getAbsolutePos(new BlockPos(19200,96,256));var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");
        var roots=List.of(first,first.east(),first.west(),first.west(2));var forced=load(world,roots);var owners=new java.util.ArrayList<Owner>();
        try{
            for(var root:roots)world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
            c.assertTrue(use(world,player,new ItemStack(block.asItem()),first.down(),Direction.UP,top(first.down())).isAccepted(),"Registered normal item creates the first independent owner");
            var original=(CompositeBlockEntity)world.getBlockEntity(first);var originalOwner=original.resident();var originalPayload=original.payload();var originalState=world.getBlockState(first);owners.add(originalOwner);
            var stack=new ItemStack(block.asItem(),3);player.setStackInHand(Hand.MAIN_HAND,stack);
            c.assertTrue(world.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2),"bb no-collide")==1,"Only one real command arms the actual stack");
            player.getAbilities().creativeMode=false; // Exercise real item decrement without changing or moving the stack.
            for(int i=1;i<roots.size();i++){
                var goal=roots.get(i);double x=i==1?.875:.125;var point=new Vec3d(goal.getX()+x,goal.getY(),goal.getZ()+.5);player.refreshPositionAndAngles(point.x,point.y+5,point.z-4,0,0);
                var hit=world.raycast(new RaycastContext(point.add(0,4,0),point.add(0,-.01,0),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                c.assertTrue(hit.getBlockPos().equals(goal.down())&&hit.getSide()==Direction.UP,"Actual floor ray is visible beside the previous object's occupied part of the same target cell");
                c.assertTrue(!CompositeRuntime.contributions(world,goal).isEmpty(),"Incoming root target already contains genuine independent owned geometry");
                c.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"Held permission permits overlapping success number "+i+" without another command");
                var installed=(CompositeBlockEntity)world.getBlockEntity(goal);owners.add(installed.resident());
                c.assertTrue(stack.getCount()==3-i&&NoCollidePlacement.armed(player)==(i<3)&&!NoCollidePlacement.ignoringBlocks(),"Survival count changes preserve the lease until the held stack is exhausted");
                c.assertTrue(world.getBlockState(first).equals(originalState)&&((CompositeBlockEntity)world.getBlockEntity(first)).resident().equals(originalOwner)&&((CompositeBlockEntity)world.getBlockEntity(first)).payload().equals(originalPayload),"Independent original owner/state/typed payload are preserved while guest membership changes");
            }
            c.assertTrue(!stack.hasNbt(),"Repeated overlap never writes item privilege NBT");c.complete();
        }finally{NoCollidePlacement.invalidate(player);for(var owner:owners)if(world.getBlockEntity(CompositeData.pos(owner.root())) instanceof CompositeBlockEntity entity&&owner.equals(entity.resident()))CompositeRuntime.remove(world,owner,player,false);CompositeRuntime.drain(world);for(var root:roots)world.setBlockState(root.down(),Blocks.AIR.getDefaultState());player.discard();release(world,forced);}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1274_marker_surface",tickLimit=100)
    public void markerItemsUseActualAimedHelperTopAndNeverTheReplaceableCarrierBase(TestContext c){
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var base=c.getAbsolutePos(new BlockPos(20000,96,256));var forced=load(world,List.of(base,base.east(8)));var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");
        var owners=new java.util.ArrayList<Owner>();var markers=new java.util.ArrayList<BlockPos>();
        try{
            int i=0;
            for(var item:List.of(DwUtilityBlocks.BARRIER_ITEM,DwUtilityBlocks.LIGHT_ITEM)){
                var root=base.east(i++*8);world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
                c.assertTrue(use(world,player,new ItemStack(block.asItem()),root.down(),Direction.UP,top(root.down())).isAccepted(),"Normal wide source object creates a two-block selectable volume");
                var original=(CompositeBlockEntity)world.getBlockEntity(root);owners.add(original.resident());var originalData=original.createNbtWithIdentifyingData();var helper=root.up();var helperBefore=List.copyOf(CompositeRuntime.contributions(world,helper));
                var stack=new ItemStack(item);player.setStackInHand(Hand.MAIN_HAND,stack);var point=new Vec3d(root.getX()+.5,root.getY()+2,root.getZ()+.5);player.refreshPositionAndAngles(point.x,point.y+6,point.z-4,0,0);
                var hit=world.raycast(new RaycastContext(point.add(0,4,0),point.add(0,-.01,0),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                c.assertTrue(hit.getBlockPos().equals(helper)&&hit.getSide()==Direction.UP&&hit.getPos().squaredDistanceTo(point)<1e-12,"Actual outline ray selects the upper helper face rather than its lower carrier corner");
                c.assertTrue(new net.minecraft.item.ItemPlacementContext(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).getBlockPos().equals(helper),"The old early canReplace path would have sunk the marker into the helper");
                c.assertTrue(stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"Marker places without any command");var marker=root.up(2);markers.add(marker);
                c.assertTrue(world.getBlockState(marker).isOf(item.getBlock())&&world.getBlockState(helper).isOf(CompositeArchitecture.CELL)&&CompositeRuntime.contributions(world,helper).equals(helperBefore),"Native marker cell begins at the actual aimed top and leaves the lower helper geometry intact");
                c.assertTrue(originalData.equals(world.getBlockEntity(root).createNbtWithIdentifyingData())&&!NoCollidePlacement.armed(player),"Original root typed NBT is retained and markers never carry a lease");
            }
            c.complete();
        }finally{for(var marker:markers)DwUtilityBlocks.remove(world,marker,player);for(var owner:owners)CompositeRuntime.remove(world,owner,player,false);CompositeRuntime.drain(world);for(int i=0;i<2;i++)world.setBlockState(base.east(i*8).down(),Blocks.AIR.getDefaultState());player.discard();release(world,forced);}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_utility_installation",tickLimit=100)
    public void ordinaryBarrierAndBbItemsOverlapGuestCollisionInBothInstallationOrders(TestContext c)throws Exception {
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);
        var first=c.getAbsolutePos(new BlockPos(3,20,3));var second=first.east(8);
        var forced=load(world,List.of(first,second));var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");
        Owner[] owners=new Owner[2];var firstGuest=first.east();var secondGuest=second.east();
        Runnable cleanup=()->{
            for(var guest:List.of(firstGuest,secondGuest))if(DwUtilityBlocks.marker(world.getBlockState(guest)))DwUtilityBlocks.remove(world,guest,player);
            for(var owner:owners)if(owner!=null&&world.getBlockEntity(CompositeData.pos(owner.root())) instanceof CompositeBlockEntity be&&owner.equals(be.resident()))CompositeRuntime.remove(world,owner,player,false);
            CompositeRuntime.drain(world);
            for(var pos:List.of(first.down(),second.down(),firstGuest.down(),secondGuest.down(),firstGuest,secondGuest))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            player.discard();release(world,forced);
        };
        try {
            world.setBlockState(first.down(),Blocks.STONE.getDefaultState());
            c.assertTrue(use(world,player,new ItemStack(block.asItem()),first.down(),Direction.UP,top(first.down())).isAccepted(),"Ordinary registered NEW wide masonry item places the source object");
            c.assertTrue(world.getBlockEntity(first) instanceof CompositeBlockEntity,"Actual normal BB item creates an owned root");
            var root=(CompositeBlockEntity)world.getBlockEntity(first);owners[0]=root.resident();var rootData=root.createNbtWithIdentifyingData();var rootState=world.getBlockState(first);
            var before=List.copyOf(CompositeRuntime.contributions(world,firstGuest));
            c.assertTrue(world.getBlockState(firstGuest).isOf(CompositeArchitecture.CELL)&&!before.isEmpty()&&before.stream().anyMatch(entry->entry.owner().equals(owners[0])&&!entry.shape().collision().isEmpty()),"Target is a genuine helper cell containing this BB object's active physical collision");
            world.setBlockState(firstGuest.down(),Blocks.STONE.getDefaultState());
            c.assertTrue(useFloorRay(c,player,new ItemStack(DwUtilityBlocks.BARRIER_ITEM),firstGuest).isAccepted(),"Ordinary DW-barrier item installs on a real visible floor ray inside the adjacent BB helper collision");
            c.assertTrue(world.getBlockState(firstGuest).isOf(DwUtilityBlocks.BARRIER)&&CompositeRuntime.contributions(world,firstGuest).equals(before),"Marker replaces only the helper carrier, preserving exact independent guest contribution records");
            // Reverse order uses a real stone face below the future guest cell.
            // Remove that temporary support before placing the ordinary BB root.
            world.setBlockState(secondGuest.down(),Blocks.STONE.getDefaultState());
            c.assertTrue(useFloorRay(c,player,new ItemStack(DwUtilityBlocks.BARRIER_ITEM),secondGuest).isAccepted(),"Normal DW-barrier item installs before the reverse BB object");
            c.assertTrue(world.getBlockState(secondGuest).isOf(DwUtilityBlocks.BARRIER),"Real normal placement has the expected adjacent-cell destination");
            world.setBlockState(second.down(),Blocks.STONE.getDefaultState());
            var markerData=world.getBlockEntity(secondGuest).createNbtWithIdentifyingData();var markerState=world.getBlockState(secondGuest);
            c.assertTrue(CompositeRuntime.contributions(world,secondGuest).isEmpty(),"Reverse barrier starts without guest owners");
            c.assertTrue(use(world,player,new ItemStack(block.asItem()),second.down(),Direction.UP,top(second.down())).isAccepted(),"Ordinary BB item installs through the existing DW-barrier guest cell without a forced-placement lease");
            c.assertTrue(world.getBlockEntity(second) instanceof CompositeBlockEntity,"Reverse normal BB item creates its real independent root");owners[1]=((CompositeBlockEntity)world.getBlockEntity(second)).resident();
            c.waitAndRun(3,()->{
                try {
                    c.assertTrue(world.getBlockState(first).equals(rootState)&&world.getBlockEntity(first) instanceof CompositeBlockEntity be&&owners[0].equals(be.resident())&&rootData.equals(be.createNbtWithIdentifyingData()),"Barrier installation and scheduled cleanup preserve original BB UUID, state and exact typed root data");
                    c.assertTrue(world.getBlockState(firstGuest).isOf(DwUtilityBlocks.BARRIER)&&CompositeRuntime.contributions(world,firstGuest).equals(before),"First barrier and physical BB guests survive actual queued lifecycle processing");
                    c.assertTrue(world.getBlockState(secondGuest).equals(markerState)&&world.getBlockEntity(secondGuest) instanceof CompositeBlockEntity&&markerData.equals(world.getBlockEntity(secondGuest).createNbtWithIdentifyingData()),"Reverse atomic BB handler retains the exact foreign marker state and all typed native NBT; guest membership lives in the separate owner ledger");
                    var added=CompositeRuntime.contributions(world,secondGuest);
                    c.assertTrue(added.size()==1&&added.get(0).owner().equals(owners[1])&&!added.get(0).shape().collision().isEmpty(),"Reverse barrier cell receives exactly the new BB object's physical guest contribution");
                    c.assertTrue(dev.dreamwalker.bloodbornedw.composite.CompositeShapeSnapshots.contributions(world,secondGuest).equals(added),"Published immutable worker geometry contains the exact authoritative physical guest ledger while the foreign marker NBT remains intact");
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
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v1273_light_installation",tickLimit=100)
    public void ordinaryLightItemOverlapsPhysicalGuestInBothOrdersWithoutACommand(TestContext c){
        var world=c.getWorld();var player=UtilityV127GameTests.player(world);var root=c.getAbsolutePos(new BlockPos(3,20,3));var guest=root.east();var forced=load(world,List.of(root));var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");
        try{
            for(boolean lightFirst:List.of(false,true)){
                world.setBlockState(root.down(),Blocks.STONE.getDefaultState());world.setBlockState(guest.down(),Blocks.STONE.getDefaultState());
                if(lightFirst)c.assertTrue(useFloorRay(c,player,new ItemStack(DwUtilityBlocks.LIGHT_ITEM),guest).isAccepted(),"Real light item installs before architecture");
                c.assertTrue(use(world,player,new ItemStack(block.asItem()),root.down(),Direction.UP,top(root.down())).isAccepted(),"Ordinary architecture installs through a light's guest cell");
                var entity=(CompositeBlockEntity)world.getBlockEntity(root);var owner=entity.resident();var rootData=entity.createNbtWithIdentifyingData();var contributions=List.copyOf(CompositeRuntime.contributions(world,guest));
                c.assertTrue(!contributions.isEmpty()&&contributions.stream().anyMatch(e->!e.shape().collision().isEmpty()),"The test cell has genuine independently owned physical collision");
                if(!lightFirst)c.assertTrue(useFloorRay(c,player,new ItemStack(DwUtilityBlocks.LIGHT_ITEM),guest).isAccepted(),"Real visible floor ray places light into the adjacent physical helper");
                CompositeRuntime.drain(world);
                c.assertTrue(world.getBlockState(guest).isOf(DwUtilityBlocks.LIGHT)&&world.getBlockState(guest).getLuminance()==14&&CompositeRuntime.contributions(world,guest).equals(contributions),"Light keeps brightness14 and exact independent guest geometry in both orders");
                c.assertTrue(rootData.equals(world.getBlockEntity(root).createNbtWithIdentifyingData())&&!NoCollidePlacement.armed(player),"The source root UUID/typed data are unchanged and no command privilege is used");
                DwUtilityBlocks.remove(world,guest,player);CompositeRuntime.remove(world,owner,player,false);CompositeRuntime.drain(world);world.setBlockState(root.down(),Blocks.AIR.getDefaultState());world.setBlockState(guest.down(),Blocks.AIR.getDefaultState());
            }
            c.complete();
        }finally{player.discard();release(world,forced);}
    }
    private static Vec3d top(BlockPos pos){return new Vec3d(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);}
    private static ActionResult useFloorRay(TestContext c,ServerPlayerEntity player,ItemStack stack,BlockPos goal){
        NoCollidePlacement.invalidate(player);player.setStackInHand(Hand.MAIN_HAND,stack);var point=new Vec3d(goal.getX()+.875,goal.getY(),goal.getZ()+.5);player.refreshPositionAndAngles(point.x,point.y+5,point.z-4,0,0);
        // The source object's helper fills only0..0.59375 of this neighboring cell.
        // Its visible outer floor strip is selectable even though a full marker overlaps that guest volume.
        var hit=c.getWorld().raycast(new RaycastContext(point.add(0,4,0),point.add(0,-.01,0),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
        c.assertTrue(hit.getBlockPos().equals(goal.down())&&hit.getSide()==Direction.UP,"Native ray reaches the exposed floor strip beside existing owned geometry");
        return stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
    }
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
