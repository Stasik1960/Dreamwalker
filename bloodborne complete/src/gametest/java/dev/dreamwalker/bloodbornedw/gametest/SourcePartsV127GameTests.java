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
    /** Read the independent persisted source-part contract. A source-only
     * optics alias returns its exact visual manual pose (which may be pose1),
     * whereas an incomplete construction returns its declared whole item. */
    private record ExpectedInstallation(String registry,int pose){}
    private static ExpectedInstallation expectedInstallation(String registry,int pose){
        if(registry.equals("bloodborne_dw:source_original_tree"))return new ExpectedInstallation(registry,0);
        Map<String,Integer> declared=new HashMap<>();
        try(var reader=new java.io.InputStreamReader(SourcePartsV127GameTests.class.getResourceAsStream("/bloodborne_dw/imported-source-parts-v127.json"),java.nio.charset.StandardCharsets.UTF_8)){
            for(var raw:com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("parts")){var row=raw.getAsJsonObject();declared.put(row.get("registryId").getAsString()+"/"+row.get("variant").getAsInt(),row.get("defaultInstallationPose").getAsInt());}
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        Set<Integer> seen=new HashSet<>();int current=pose;
        while(declared.containsKey(registry+"/"+current)){if(!seen.add(current))throw new IllegalArgumentException("Circular source installer contract "+registry);current=declared.get(registry+"/"+current);}
        // A declared old manual pose can itself be an approved construction
        // component. Independently compose its existing V126 whole-item recipe;
        // retain a selected completed pose when several whole poses are offered.
        try(var reader=new java.io.InputStreamReader(SourcePartsV127GameTests.class.getResourceAsStream("/bloodborne_dw/imported-assembly-v126.json"),java.nio.charset.StandardCharsets.UTF_8)){
            var doc=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();var redirects=doc.getAsJsonObject("hiddenPartRedirects");String target=redirects.has(registry)?redirects.get(registry).getAsString():registry;List<Integer> active=new ArrayList<>();
            for(var raw:doc.getAsJsonArray("recipes")){var recipe=raw.getAsJsonObject();if(recipe.get("primaryRegistryId").getAsString().equals(target))active.add(recipe.get("installationPose").getAsInt());}
            if(!target.equals(registry))current=active.isEmpty()?0:active.get(0);else if(!active.isEmpty()&&!active.contains(current))current=active.get(0);
            return new ExpectedInstallation(target,current);
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
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
            var picked=CompositeRuntime.pick(world,owner);var tag=picked.getSubNbt("BlockStateTag");var installation=expectedInstallation(block.spec.id.toString(),pose);
            c.assertTrue(picked.isOf(net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier(installation.registry())))&&tag!=null&&Integer.parseInt(tag.getString("variant"))+(tag.contains("art_bank",8)?16*Integer.parseInt(tag.getString("art_bank")):0)==installation.pose(),"Hidden source piece picks one completed installation item: "+block.spec.id+"/"+pose+" expected="+installation+" picked="+picked);
            c.assertTrue(picked.getSubNbt("CompositePayload")==null&&picked.getSubNbt("BlockEntityTag")==null,"Source UUID and private settings cannot enter the installation item");
            c.assertTrue(CompositeRuntime.remove(world,owner,player,false).outcome()==TransactionCore.Outcome.COMMITTED,"Whole source-only owner removes normally");CompositeRuntime.drain(world);checked++;
        }
        int declared;
        try(var reader=new java.io.InputStreamReader(SourcePartsV127GameTests.class.getResourceAsStream("/bloodborne_dw/imported-source-parts-v127.json"),java.nio.charset.StandardCharsets.UTF_8)){
            var parts=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("parts");Set<String> unique=new HashSet<>();
            for(var raw:parts){var row=raw.getAsJsonObject();unique.add(row.get("registryId").getAsString()+"/"+row.get("variant").getAsInt());}
            c.assertTrue(unique.size()==parts.size(),"No repeated persisted source-part contract");declared=unique.size();
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        c.assertTrue(checked==declared+12,"Every declared source compatibility state and12 unchanged tree pieces is verified separately");player.discard();c.complete();
    }
}
