package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;

/** Runs real WorldEdit parser, EditSession, region visitor and undo on Fabric.
 * With the optional mod absent, this class never loads any WorldEdit API type. */
public final class WorldEditV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_worldedit",tickLimit=120)
    public void numericWholeObjectDeleteUndoCopyAndCompact(TestContext c) {
        if(!FabricLoader.getInstance().isModLoaded("worldedit")){System.out.println("V127 WorldEdit native integration: NOT_RUN (optional worldedit mod absent)");c.complete();return;}
        try{WithWorldEdit.run(c);}catch(Exception e){throw new IllegalStateException("Native WorldEdit integration failed",e);}
    }
    private static final class WithWorldEdit {
        static com.sk89q.worldedit.math.BlockVector3 v(BlockPos p){return com.sk89q.worldedit.math.BlockVector3.at(p.getX(),p.getY(),p.getZ());}
        static void run(TestContext c)throws Exception {
            var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,10,3));UtilityV127GameTests.load(world,root,64);
            com.sk89q.worldedit.world.World adapter=null;
            for(var constructor:Class.forName("com.sk89q.worldedit.fabric.FabricWorld").getConstructors())if(constructor.getParameterCount()==1&&constructor.getParameterTypes()[0].isInstance(world))adapter=(com.sk89q.worldedit.world.World)constructor.newInstance(world);
            c.assertTrue(adapter!=null,"Real Fabric WorldEdit world adapter available");
            var we=com.sk89q.worldedit.WorldEdit.getInstance();var context=new com.sk89q.worldedit.extension.input.ParserContext();context.setWorld(adapter);
            var block=CompositeArchitecture.kindBlock("source_dark_arch_door");var number=DebugCatalogue.entry(block.spec.id).temporaryId();
            var pattern=we.getBlockFactory().parseFromInput(number,context);
            try(var edits=we.newEditSessionBuilder().world(adapter).maxBlocks(10000).build()){edits.setBlock(v(root),pattern);edits.flushSession();}
            c.assertTrue(world.getBlockEntity(root) instanceof CompositeBlockEntity,"Five-digit pattern creates a real owned object");
            var be=(CompositeBlockEntity)world.getBlockEntity(root);var owner=be.resident();var object=CompositeRuntime.instance(world,root,world.getBlockState(root),owner.instanceId(),be.payload());
            var offset=object.cells().keySet().stream().filter(p->p.x()!=0||p.y()!=0||p.z()!=0).findFirst().orElseThrow();var helper=root.add(offset.x(),offset.y(),offset.z());
            var mask=we.getMaskFactory().parseFromInput(number,context);
            c.assertTrue(mask.test(v(helper)),"Five-digit mask sees an object through its real helper cell");
            try(var edits=we.newEditSessionBuilder().world(adapter).maxBlocks(10000).build()){
                var clip=edits.getFullBlock(v(helper));
                edits.replaceBlocks(new com.sk89q.worldedit.regions.CuboidRegion(adapter,v(helper),v(helper)),mask,com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());edits.flushSession();
                c.assertTrue(!(world.getBlockEntity(root) instanceof CompositeBlockEntity),"Replacing one touched part removes the complete door");
                try(var undo=we.newEditSessionBuilder().world(adapter).maxBlocks(10000).build()){edits.undo(undo);undo.flushSession();}
                c.assertTrue(world.getBlockEntity(root) instanceof CompositeBlockEntity restored&&owner.equals(restored.resident()),"Undo restores the exact owner UUID and root location");
                var destination=helper.add(32,0,0);edits.setBlock(v(destination),clip);edits.flushSession();var copyRoot=root.add(32,0,0);
                c.assertTrue(world.getBlockEntity(copyRoot) instanceof CompositeBlockEntity copied&&!copied.resident().instanceId().equals(owner.instanceId()),"Clipboard keeps complete pose/payload and allocates an independent copied owner");
            }
            var cube=CompositeArchitecture.kindBlock("source_stone");
            if(cube instanceof ImportedCompactBlock){var at=root.add(0,0,32);var state=cube.getDefaultState().with(ImportedCompactBlock.COMPACT,true);world.setBlockState(at,state);
                var cubeNumber=DebugCatalogue.entry(cube.spec.id).temporaryId();var cubeMask=we.getMaskFactory().parseFromInput(cubeNumber,context);
                c.assertTrue(world.getBlockEntity(at)==null&&cubeMask.test(v(at)),"Dense compact source cube remains native and selectable by numeric ID");
                try(var edits=we.newEditSessionBuilder().world(adapter).maxBlocks(10).build()){edits.replaceBlocks(new com.sk89q.worldedit.regions.CuboidRegion(adapter,v(at),v(at)),cubeMask,com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());edits.flushSession();}
                c.assertTrue(world.getBlockState(at).isAir(),"WorldEdit deletes native compact source cubes");
            }
            for(var at:List.of(root,root.add(32,0,0)))if(world.getBlockEntity(at) instanceof CompositeBlockEntity own) c.assertTrue(CompositeRuntime.remove(world,own.resident(),null,false).outcome()==TransactionCore.Outcome.COMMITTED,"Whole fixture cleanup");
            System.out.println("V127 WorldEdit native parser/whole-delete/undo/clipboard/compact: PASS");c.complete();
        }
    }
}
