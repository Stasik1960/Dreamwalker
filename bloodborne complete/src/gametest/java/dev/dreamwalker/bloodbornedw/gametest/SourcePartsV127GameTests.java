package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;

/** New source-only saved pieces have their own exact-state contract; ordinary
 * builder tests still cover all previous offered poses and complete items. */
public final class SourcePartsV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v127_source_parts")
    public void hiddenSourceFragmentsStayExactAndPickOneCompleteInstaller(TestContext c){
        var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(6144,96,64));var player=UtilityV127GameTests.player(world);UtilityV127GameTests.load(world,root,64);int checked=0;
        for(var block:CompositeArchitecture.blocks())for(int pose=0;pose<block.spec.variants.size();pose++){
            if(!ImportedSourcePartsV127.part(block,pose)&&!(block instanceof SourceOriginalTreeBlock&&pose!=0))continue;
            var state=block.getDefaultState().with(CompositeRootBlock.VARIANT,pose%16).with(CompositeRootBlock.ROTATION,2);var bank=ImportedArtworkBlock.bankProperty(state);if(bank!=null)state=state.with(bank,pose/16);
            world.setBlockState(root.down(),Blocks.STONE.getDefaultState());UUID id=UUID.randomUUID();var payload=ImportedTypeAliases.markPayload(null);
            c.assertTrue(CompositeRuntime.place(world,root,state,id,player,payload).outcome()==TransactionCore.Outcome.COMMITTED,"Exact saved source-only pose installs: "+block.spec.id+"/"+pose);
            c.assertTrue(world.getBlockState(root).equals(state),"Source-only state is not rewritten to the builder appearance");var be=(CompositeBlockEntity)world.getBlockEntity(root);var owner=be.resident();var instance=CompositeRuntime.instance(world,root,state,id,be.payload());
            for(var offset:instance.cells().keySet())for(var row:CompositeRuntime.contributions(world,root.add(offset.x(),offset.y(),offset.z())))if(row.owner().equals(owner))c.assertTrue(CompositeSpec.poseVariant(CompositeData.state(row.rootData()))==pose,"Source helpers retain exact declared pose");
            var picked=CompositeRuntime.pick(world,owner);var tag=picked.getSubNbt("BlockStateTag");int installation=ImportedAssemblyV126.installationPose(block.spec.id.getPath());
            c.assertTrue(picked.isOf(block.asItem())&&tag!=null&&Integer.parseInt(tag.getString("variant"))+(tag.contains("art_bank",8)?16*Integer.parseInt(tag.getString("art_bank")):0)==installation,"Hidden source piece picks one completed installation item");
            c.assertTrue(picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"Source UUID and private settings cannot enter the installation item");
            c.assertTrue(CompositeRuntime.remove(world,owner,player,false).outcome()==TransactionCore.Outcome.COMMITTED,"Whole source-only owner removes normally");CompositeRuntime.drain(world);checked++;
        }
        c.assertTrue(checked==30,"All18 source compatibility states (including exact lantern emission alias) and12 tree pieces are verified separately");player.discard();c.complete();
    }
}
