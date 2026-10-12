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
            transitionContract(c);
            var world=c.getWorld();var root=c.getAbsolutePos(new BlockPos(3,10,3));UtilityV127GameTests.load(world,root,64);
            com.sk89q.worldedit.world.World adapter=null;
            for(var method:Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethods())if(method.getName().equals("adapt")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(world)&&com.sk89q.worldedit.world.World.class.isAssignableFrom(method.getReturnType()))adapter=(com.sk89q.worldedit.world.World)method.invoke(null,world);
            c.assertTrue(adapter!=null&&adapter.getClass().getMethod("getWorld").invoke(adapter)==world,"Real public Fabric adapter preserves the exact native server world");
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
        /** Compare every cell against WE's unchanged vanilla ImmutableTables,
         * rather than testing the arithmetic implementation against itself. */
        static void transitionContract(TestContext c)throws Exception{
            var type=com.sk89q.worldedit.world.block.BlockTypes.OAK_STAIRS;
            var all=type.getAllStates();Map<Map<com.sk89q.worldedit.registry.state.Property<?>,Object>,com.sk89q.worldedit.world.block.BlockState> map=new LinkedHashMap<>();
            for(var state:all)map.put(state.getStates(),state);
            var field=com.sk89q.worldedit.world.block.BlockState.class.getDeclaredField("states");field.setAccessible(true);
            for(var state:all){var compact=dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditStateTransitions.create(type,state.getStates(),map);int count=0;
                var original=(com.google.common.collect.Table<?,?,?>)field.get(state);
                c.assertTrue(new ArrayList<>(original.columnKeySet()).equals(new ArrayList<>(compact.columnKeySet()))&&new ArrayList<>(original.rowKeySet()).equals(new ArrayList<>(compact.rowKeySet()))&&new ArrayList<>(original.cellSet()).equals(new ArrayList<>(compact.cellSet())),"WorldEdit Table ordered cell/column/row views equal unchanged vanilla ImmutableTable");
                for(var property:type.getProperties())for(Object value:property.getValues()){var actual=compact.get(property,value);var expected=transition(state,property,value);c.assertTrue((actual==null?state:actual)==expected,"WorldEdit immutable neighbor lookup keeps exact state identity");if(actual!=null)count++;}
                c.assertTrue(compact.size()==count&&compact.cellSet().size()==count,"WorldEdit neighbor Table cardinality and views");
                c.assertTrue(compact.get(null,null)==null&&compact.get(type.getProperties().get(0),null)==null,"WorldEdit Table null get returns null");
                for(var cell:compact.cellSet())c.assertTrue(compact.row(cell.getRowKey()).get(cell.getColumnKey())==cell.getValue()&&compact.column(cell.getColumnKey()).get(cell.getRowKey())==cell.getValue(),"WorldEdit neighbor row/column views");
                boolean immutable=false;try{compact.clear();}catch(UnsupportedOperationException expected){immutable=true;}c.assertTrue(immutable,"WorldEdit neighbor graph remains immutable");
            }
            c.assertTrue(!dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditStateTransitions.own(type),"Vanilla WorldEdit state generation is not intercepted");
            System.out.println("V127 WorldEdit shared state transitions: PASS "+dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditStateTransitions.statistics());
        }
        @SuppressWarnings({"rawtypes","unchecked"}) static com.sk89q.worldedit.world.block.BlockState transition(com.sk89q.worldedit.world.block.BlockState state,com.sk89q.worldedit.registry.state.Property property,Object value){return state.with(property,value);}
    }
}
