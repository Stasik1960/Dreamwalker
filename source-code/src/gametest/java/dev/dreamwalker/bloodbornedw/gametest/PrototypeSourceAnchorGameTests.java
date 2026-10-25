package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Source anchoring differs from normal new-build mounting; foreign support must survive. */
public final class PrototypeSourceAnchorGameTests implements FabricGameTest {
    private static final BlockPos LOCAL=new BlockPos(10,3,10);
    private static NbtCompound shift(double x,double y,double z){NbtCompound tag=new NbtCompound();NbtList values=new NbtList();for(double value:new double[]{x,y,z})values.add(NbtDouble.of(value));tag.put("SourceShift",values);return tag;}
    @GameTest(templateName="bloodborne_dw:tree_test",tickLimit=180,batchId="prototype_source_anchor")
    public void sourceTreeUsesOwnedMelonCellAndPreservesForeignGrassBelow(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();CompositeRootBlock tree=CompositeArchitecture.kindBlock("prototype_tree");
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_ALL);player.setPosition(root.getX()+8,root.getY(),root.getZ()+8);
            NbtCompound payload=shift(0,-1,0);payload.putString("SourceProvenance","Original18-member source assembly; no nonmember support consumed");
            UUID initial=UUID.randomUUID();test.assertTrue(dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstances(Set.of(initial),()->CompositeRuntime.place(world,root,tree.getDefaultState(),initial,player,payload)).outcome()==Outcome.COMMITTED,"explicit initial source compensation places root in consumed lowest melon cell");
            CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(root);Owner owner=entity.resident();
            test.assertTrue(world.getBlockState(root.down()).isOf(Blocks.GRASS_BLOCK),"foreign original grass support remains actual native block");
            test.assertTrue(CompositeRuntime.contributions(world,root.down()).stream().anyMatch(c->c.owner().equals(owner)),"source geometry can share support cell without replacing grass");
            test.assertTrue(CompositeSourceShift.world(world.getBlockState(root),entity.payload()).y()==-1,"render compensation remains exact one block downward");
            NbtCompound saved=entity.createNbtWithIdentifyingData();var restored=net.minecraft.block.entity.BlockEntity.createFromNbt(root,world.getBlockState(root),saved.copy());
            test.assertTrue(restored instanceof CompositeBlockEntity&&saved.equals(restored.createNbtWithIdentifyingData()),"registered block-entity persistence retains source compensation and UUID");
            ItemStack picked=CompositeRuntime.pick(world,owner);NbtCompound art=picked.getSubNbt("CompositePayload");
            test.assertTrue(art==null&&entity.payload().getString("SourceProvenance").equals(payload.getString("SourceProvenance")),"installed typed provenance remains exact; newly picked item excludes all instance provenance/height/link data");
            test.assertTrue(CompositeRuntime.remove(world,owner,player,false).outcome()==Outcome.COMMITTED&&world.getBlockState(root.down()).isOf(Blocks.GRASS_BLOCK),"whole source tree removal preserves foreign support");
            player.setYaw(0);player.setStackInHand(Hand.MAIN_HAND,picked);
            test.assertTrue(picked.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.of(root.down()).add(.5,1,.5),Direction.UP,root.down(),false))).isAccepted(),"picked tree installs normally from its item");
            CompositeBlockEntity rebuilt=(CompositeBlockEntity)world.getBlockEntity(root);
            test.assertTrue(!rebuilt.resident().equals(owner)&&!rebuilt.payload().contains("SourceShift"),"new build has fresh UUID and canonical source-independent seating");test.complete();
        }finally{clear(world,root);player.discard();}
    }
    @GameTest(templateName="bloodborne_dw:tree_test",tickLimit=220,batchId="prototype_source_anchor")
    public void roofFractionalSourceCompensationRotatesOnceAndPersists(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();CompositeRootBlock roof=CompositeArchitecture.kindBlock("prototype_roof");
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.DRIPSTONE_BLOCK.getDefaultState(),Block.NOTIFY_ALL);player.setPosition(root.getX()+8,root.getY(),root.getZ()+8);
            NbtCompound payload=shift(0,-3.0/16,5.0/16);UUID initial=UUID.randomUUID();test.assertTrue(dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstances(Set.of(initial),()->CompositeRuntime.place(world,root,roof.getDefaultState(),initial,player,payload)).outcome()==Outcome.COMMITTED,"source roof seating stores exact fractional compensation for this initial instance");Owner owner=((CompositeBlockEntity)world.getBlockEntity(root)).resident();
            for(int yaw=0;yaw<8;yaw++){
                BlockState next=world.getBlockState(root).with(CompositeRootBlock.ROTATION,yaw);var result=CompositeRuntime.transition(world,owner,next,player);
                test.assertTrue(result.outcome()==(yaw==0?Outcome.COMMITTED:Outcome.REJECTED),"source pose stays retained; a new rotated base penetration into foreign support is rejected without inheriting initial privilege");
                NbtCompound actual=((CompositeBlockEntity)world.getBlockEntity(root)).payload();var offset=CompositeSourceShift.world(next,actual);double angle=yaw*Math.PI/4;
                test.assertTrue(Math.abs(offset.x()+5.0/16*Math.sin(angle))<1e-9&&Math.abs(offset.z()-5.0/16*Math.cos(angle))<1e-9&&offset.y()==-3.0/16,"source compensation receives exactly one global rotation");
                var effective=CompositeSourceShift.footprint(roof.spec,next,actual);test.assertTrue(effective.keySet().stream().anyMatch(cell->cell.y()<0),"intrinsic source roof edge below normalized anchor is retained as a sparse contribution");
                test.assertTrue(world.getBlockState(root.down()).isOf(Blocks.DRIPSTONE_BLOCK),"support is preserved during fractional/source45 transition");
            }
            ItemStack pick=CompositeRuntime.pick(world,owner);test.assertTrue(pick.getSubNbt("CompositePayload")==null||!pick.getSubNbt("CompositePayload").contains("SourceShift"),"new item installation does not inherit migration compensation");
            test.assertTrue(CompositeRuntime.remove(world,owner,player,false).outcome()==Outcome.COMMITTED,"source roof is removed atomically");test.complete();
        }finally{clear(world,root);player.discard();}
    }
    private static void clear(ServerWorld world,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-8,-2,-8),root.add(8,20,8)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);for(var item:world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(root).expand(21),e->true))item.discard();}
}
