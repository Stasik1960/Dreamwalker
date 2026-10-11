package dev.dreamwalker.bloodbornedw.gametest;

import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;

/** Genuine new stair item/neighbor/owner operations; V12 objects are never edited. */
public final class ImportedStairGameTests implements FabricGameTest {
    private static final Direction[] DIRECTIONS={Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH};
    private static final StairShape[] CORNERS={StairShape.OUTER_LEFT,StairShape.OUTER_RIGHT,StairShape.INNER_LEFT,StairShape.INNER_RIGHT};
    private static List<ImportedStairBlock> stairs(){return CompositeArchitecture.blocks().stream().filter(ImportedStairBlock.class::isInstance).map(ImportedStairBlock.class::cast).sorted(Comparator.comparing(block->block.spec.id.toString())).toList();}
    private static ServerPlayerEntity player(ServerWorld world){
        var player=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"NewStairProbe"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){} @Override public void send(Packet<?> packet,PacketCallbacks callbacks){}};
        player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,player);
        player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);player.getAbilities().allowModifyWorld=true;return player;
    }
    private static void load(ServerWorld world,BlockPos root){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var pos=root.add(x,0,z);world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);world.getChunk(pos);}}
    private static Owner place(TestContext t,BlockPos root,BlockState state){
        load(t.getWorld(),root);var payload=new NbtCompound();payload.putInt(ImportedTypeAliases.VERSION_KEY,1);
        var result=CompositeRuntime.place(t.getWorld(),root,state,UUID.randomUUID(),null,payload);
        t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"native stair fixture placement commits: "+result);
        return ((CompositeBlockEntity)t.getWorld().getBlockEntity(root)).resident();
    }
    private static void remove(TestContext t,Owner owner){t.assertTrue(CompositeRuntime.remove(t.getWorld(),owner,null,false).outcome()==TransactionCore.Outcome.COMMITTED,"native stair/ladder cleanup commits");CompositeRuntime.drain(t.getWorld());}
    private static void contract(TestContext t,BlockPos root,Owner owner,StairShape expected){
        var world=t.getWorld();var state=world.getBlockState(root);
        t.assertTrue(state.get(ImportedStairBlock.SHAPE)==expected,"neighbor shape is "+expected+" for "+owner.registryId()+"; actual="+state);
        t.assertTrue(((CompositeBlockEntity)world.getBlockEntity(root)).resident().equals(owner),"corner update retains resident UUID");
        boolean found=false;
        for(var row:CompositeRuntime.contributions(world,root))if(row.owner().equals(owner)){
            found=true;var saved=CompositeData.state(row.rootData());
            t.assertTrue(saved.equals(state),"world state and typed owner contribution stay synchronized");
        }
        t.assertTrue(found,"native stair has a saved owner contribution");
        t.assertTrue(state.get(CompositeRootBlock.VARIANT)==ImportedStairShapes.variant(state),"derived pose agrees with half/shape");
        t.assertTrue(VoxelShapes.matchesAnywhere(state.getCollisionShape(world,root),state.getOutlineShape(world,root),net.minecraft.util.function.BooleanBiFunction.NOT_SAME)==false,"outline exactly follows the simplified physical boxes");
        var picked=CompositeRuntime.pick(world,owner);
        t.assertTrue(picked.isOf(state.getBlock().asItem())&&picked.getOrCreateNbt().getInt(ImportedTypeAliases.VERSION_KEY)==1,"native pick returns the one current installer item");
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=1200,batchId="v124_stair_item")
    public void allNewNativeStairTypesPlaceThroughTheirOneItemWithFourFacingsAndTwoHalves(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(64,96,64));var player=player(world);load(world,root);
        var types=stairs();t.assertTrue(types.size()==19,"reviewed appearance catalogue has19 new functional stair types");
        List<Runnable> cases=new ArrayList<>();
        for(var block:types)for(Direction facing:DIRECTIONS)for(BlockHalf half:BlockHalf.values())cases.add(()->{
            world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            if(half==BlockHalf.TOP)world.setBlockState(root.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            player.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()-8,facing.asRotation(),0);
            var stack=block.art(block.getDefaultState(),new NbtCompound());player.setStackInHand(Hand.MAIN_HAND,stack);
            var clicked=half==BlockHalf.TOP?root.up():root.down();var face=half==BlockHalf.TOP?Direction.DOWN:Direction.UP;
            var hit=new BlockHitResult(Vec3d.ofCenter(clicked).add(0,half==BlockHalf.TOP?-.5:.5,0),face,clicked,false);
            var result=stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
            t.assertTrue(result.isAccepted(),"ordinary native stair item places "+block.spec.id+" "+half+" "+facing+"; "+CompositeRuntime.lastResult());
            var state=world.getBlockState(root);t.assertTrue(state.isOf(block)&&state.get(ImportedStairBlock.HALF)==half&&ImportedStairShapes.facing(state)==facing,"placement chooses clicked half and player facing");
            var owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();contract(t,root,owner,StairShape.STRAIGHT);
            remove(t,owner);world.setBlockState(root.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(root.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        });
        for(int i=0;i<cases.size();i++){var check=cases.get(i);t.runAtTick(i+1,check);}
        t.runAtTick(cases.size()+3,()->{player.discard();t.complete();});
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=6000,batchId="v124_stair_neighbors")
    public void everyAppearanceHalfFacingAndCornerUpdatesOnNeighborRemovalAndReadditionWithMatchingLedger(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(128,96,64));load(world,root);int index=0;
        for(var block:stairs())for(BlockHalf half:BlockHalf.values())for(Direction facing:DIRECTIONS)for(StairShape shape:CORNERS){
            final long start=1L+7L*index++;final Owner[] owner={null};
            boolean outer=shape==StairShape.OUTER_LEFT||shape==StairShape.OUTER_RIGHT;
            boolean left=shape==StairShape.OUTER_LEFT||shape==StairShape.INNER_LEFT;
            var neighbor=root.offset(outer?facing:facing.getOpposite());var other=left?facing.rotateYCounterclockwise():facing.rotateYClockwise();
            var neighborState=Blocks.STONE_STAIRS.getDefaultState().with(StairsBlock.FACING,other).with(StairsBlock.HALF,half);
            t.runAtTick(start,()->{
                var state=block.getDefaultState().with(CompositeRootBlock.ROTATION,ImportedStairShapes.rotation(facing)).with(ImportedStairBlock.HALF,half);
                owner[0]=place(t,root,ImportedStairShapes.normalize(state));contract(t,root,owner[0],StairShape.STRAIGHT);
                world.setBlockState(neighbor,neighborState,Block.NOTIFY_ALL);
            });
            t.runAtTick(start+2,()->{contract(t,root,owner[0],shape);world.setBlockState(neighbor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);});
            t.runAtTick(start+4,()->{contract(t,root,owner[0],StairShape.STRAIGHT);world.setBlockState(neighbor,neighborState,Block.NOTIFY_ALL);});
            t.runAtTick(start+6,()->{contract(t,root,owner[0],shape);remove(t,owner[0]);world.setBlockState(neighbor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);});
        }
        t.runAtTick(index*7L+3,t::complete);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="v124_stair_legacy_pose")
    public void legacyDiagonalCornerPosesAndTrimStateKeepTheirUsefulSimpleBoxes(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(192,96,64));load(world,root);int tick=1;
        for(var block:stairs())for(int rotation:new int[]{1,3,5,7}){
            final int yaw=rotation;t.runAtTick(tick++,()->{
                var state=ImportedStairShapes.fromPose(block.getDefaultState(),1).with(CompositeRootBlock.ROTATION,yaw);
                var owner=place(t,root,state);world.setBlockState(root.east(),Blocks.STONE_STAIRS.getDefaultState(),Block.NOTIFY_ALL);
                t.assertTrue(ImportedStairBlock.connected(state,world,root).equals(state),"45degree legacy corner pose is retained without an invented diagonal neighbor connection");
                contract(t,root,owner,StairShape.INNER_LEFT);remove(t,owner);world.setBlockState(root.east(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            });
        }
        t.runAtTick(tick++,()->{
            var block=CompositeArchitecture.kindBlock("source_stairs_polished_deepslate_stairs_bottom");
            var plain=ImportedStairShapes.fromPose(block.getDefaultState(),0);var trim=ImportedStairShapes.fromPose(block.getDefaultState(),10);
            t.assertTrue(block.spec.pose(trim).parts().size()>block.spec.pose(plain).parts().size(),"90980 authored trim survives as an appearance state of93028");
            t.assertTrue(block.spec.pose(trim).collision().equals(block.spec.pose(plain).collision()),"small decorative trim does not acquire detailed physics");
            var owner=place(t,root,trim);contract(t,root,owner,StairShape.STRAIGHT);
            t.assertTrue("true".equals(CompositeRuntime.pick(world,owner).getOrCreateSubNbt("BlockStateTag").getString("trim")),"copy retains trim appearance state");remove(t,owner);
        });
        t.runAtTick(tick++,()->{
            var block=stairs().get(0);var state=ImportedStairShapes.fromPose(block.getDefaultState(),1).with(ImportedStairBlock.CONNECTIONS,ImportedStairBlock.Connections.MANUAL);
            var owner=place(t,root,state);
            t.assertTrue(ImportedStairBlock.connected(state,world,root).equals(state),"old isolated cardinal corner retains its exact installed appearance in compatibility mode");
            t.assertTrue(ImportedStairBlock.activateAutomatic(state,world,root).get(ImportedStairBlock.SHAPE)==StairShape.STRAIGHT,"explicit activation resumes automatic vanilla-style neighbor shape");
            contract(t,root,owner,StairShape.INNER_LEFT);remove(t,owner);
        });
        t.runAtTick(tick+2,t::complete);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="v124_ladder")
    public void unifiedRungLadderRetainsBothAuthoredHeightsClimbingZonesEightRotationsAndStacking(TestContext t){
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(256,96,64));var block=CompositeArchitecture.kindBlock("source_hold_ladder_02");load(world,root);int tick=1;
        t.assertTrue(block instanceof ImportedLadderBlock,"the canonical rung ladder is the height-state type");
        for(int height:new int[]{0,1})for(int rotation=0;rotation<8;rotation++){
            final int h=height,yaw=rotation;t.runAtTick(tick++,()->{
                var state=ImportedStairShapes.fromPose(block.getDefaultState(),h).with(CompositeRootBlock.ROTATION,yaw);var owner=place(t,root,state);
                var bounds=ImportedLadderBlock.bounds(state);t.assertTrue(bounds.to().y()-bounds.from().y()==(h+1)*16,"height state retains the original climb zone");
                double a=Math.toRadians(yaw*45),x=.5-Math.sin(a)*.5,z=.5+Math.cos(a)*.5;
                var body=new Box(root.getX()+x-.25,root.getY()+h+.1,root.getZ()+z-.25,root.getX()+x+.25,root.getY()+h+.9,root.getZ()+z+.25);
                t.assertTrue(ImportedLadderClimbing.climbing(world,body).isPresent(),"both heights retain climbable rung zones at all8 orientations");
                var picked=CompositeRuntime.pick(world,owner);t.assertTrue(picked.isOf(block.asItem())&&picked.getOrCreateSubNbt("BlockStateTag").getString("height").equals(h==1?"two":"one"),"one canonical installer item copies ladder length");
                remove(t,owner);
            });
        }
        var player=player(world);
        for(int first:new int[]{0,1})for(int second:new int[]{0,1}){
            final int a=first,b=second;t.runAtTick(tick++,()->{
                var firstState=ImportedStairShapes.fromPose(block.getDefaultState(),a);var lower=place(t,root,firstState);
                var upperRoot=root.up(a+1);load(world,upperRoot);
                player.refreshPositionAndAngles(root.getX()+.5,root.getY(),root.getZ()-8,0,0);
                var stack=block.art(ImportedStairShapes.fromPose(block.getDefaultState(),b),new NbtCompound());player.setStackInHand(Hand.MAIN_HAND,stack);
                var topCell=root.up(a);var hit=new BlockHitResult(new Vec3d(root.getX()+.0625,upperRoot.getY(),root.getZ()+.9375),Direction.UP,topCell,false);
                var result=stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
                t.assertTrue(result.isAccepted()&&world.getBlockState(upperRoot).isOf(block),"one/two-cell ladder items stack on the existing upper rung cell: "+a+"→"+b+"; "+CompositeRuntime.lastResult());
                t.assertTrue(world.getBlockState(upperRoot).get(ImportedLadderBlock.HEIGHT)==(b==1?ImportedLadderBlock.Height.TWO:ImportedLadderBlock.Height.ONE),"stacked ladder retains chosen height");
                var upper=((CompositeBlockEntity)world.getBlockEntity(upperRoot)).resident();remove(t,upper);remove(t,lower);
            });
        }
        t.runAtTick(tick+2,()->{player.discard();t.complete();});
    }
}
