package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Full offered catalogue through the normal ItemStack.useOnBlock path, no conversion privilege. */
public final class ManualCatalogueV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_catalogue",tickLimit=4000)
    public void everyOfferedPlacementItemCanBePlacedNormally(TestContext c){
        var cases=UnifiedCreativeCatalogue.canonicalItems().stream().filter(NoCollidePlacement::isModPlacement).toList();
        var p=UtilityV127GameTests.player(c.getWorld());var root=c.getAbsolutePos(new BlockPos(3,12,3));var report=new JsonArray();
        UtilityV127GameTests.load(c.getWorld(),root,24);
        checkNext(c,p,root,cases,0,report);
    }
    private static void checkNext(TestContext c,ServerPlayerEntity player,BlockPos root,List<ItemStack> cases,int index,JsonArray report){
        if(index>=cases.size()){
            var result=new JsonObject();result.addProperty("schema","dreamwalker-v127-manual-catalogue");result.addProperty("workingPlacementItems",cases.size());result.add("cases",report);
            long failed=report.asList().stream().filter(v->!v.getAsJsonObject().get("pass").getAsBoolean()).count();result.addProperty("failures",failed);result.addProperty("status",failed==0?"PASS":"FAIL");
            try{var path=Path.of("../v127-manual-placement.json");Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new IllegalStateException(e);}
            c.assertTrue(failed==0,"Every offered manual placement item works; failures="+failed+", detailed v127-manual-placement.json");c.complete();return;
        }
        var world=c.getWorld();ItemStack stack=cases.get(index).copy();NoCollidePlacement.invalidate(player);
        player.setStackInHand(Hand.MAIN_HAND,stack);player.refreshPositionAndAngles(root.getX()+.5,root.getY()+10,root.getZ()-18,0,0);
        if(stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof CompositeRootBlock block){
            var state=block.getDefaultState();var tag=stack.getSubNbt("BlockStateTag");
            if(tag!=null)for(var property:block.getStateManager().getProperties())if(tag.contains(property.getName(),8))state=apply(state,property,tag.getString(property.getName()));
            for(var offset:block.spec.footprint(state,0).keySet())UtilityV127GameTests.load(world,root.add(offset.x(),offset.y(),offset.z()),2);
        }
        world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        Set<UUID> before=new HashSet<>();world.iterateEntities().forEach(e->before.add(e.getUuid()));
        ActionResult action=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.down(),false)));
        var row=new JsonObject();row.addProperty("registryId",Registries.ITEM.getId(stack.getItem()).toString());row.addProperty("name",stack.getName().getString());row.addProperty("itemTag",String.valueOf(cases.get(index).getNbt()));row.addProperty("result",action.toString());row.addProperty("pass",action.isAccepted());
        if(!action.isAccepted())row.addProperty("lastTransaction",String.valueOf(CompositeRuntime.lastResult()));report.add(row);
        if(world.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null)CompositeRuntime.remove(world,own.resident(),player,false);
        else if(DwUtilityBlocks.marker(world.getBlockState(root)))DwUtilityBlocks.remove(world,root,player);
        else world.setBlockState(root,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        var created=new ArrayList<RpObjectEntity>();world.iterateEntities().forEach(e->{if(e instanceof RpObjectEntity rp&&!before.contains(e.getUuid()))created.add(rp);});created.forEach(RpObjectEntity::removeObject);
        world.setBlockState(root.down(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        c.runAtTick(index+2,()->checkNext(c,player,root,cases,index+1,report));
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state,net.minecraft.state.property.Property<T> property,String value){return property.parse(value).map(v->state.with(property,v)).orElse(state);}
}
