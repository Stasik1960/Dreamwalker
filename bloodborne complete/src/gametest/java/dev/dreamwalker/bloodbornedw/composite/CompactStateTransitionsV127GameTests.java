package dev.dreamwalker.bloodbornedw.composite;

import com.google.common.collect.*;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.mixin.StateTransitionAccess;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.state.property.*;
import net.minecraft.test.*;
import net.minecraft.util.WorldSavePath;
import net.minecraft.registry.Registries;

/** Independent native neighbor graph lookup from each block's actual complete
 * StateManager, and original Guava null-inclusive/mutable Table contracts. */
public final class CompactStateTransitionsV127GameTests implements FabricGameTest {
    @SuppressWarnings("unchecked")private static Table<Property<?>,Comparable<?>,BlockState> table(BlockState state){return (Table<Property<?>,Comparable<?>,BlockState>)(Table<?,?,?>)((StateTransitionAccess)state).bloodborne_dw$transitions();}
    @SuppressWarnings({"unchecked","rawtypes"})private static BlockState with(BlockState state,Property<?> property,Comparable<?> value){return state.with((Property)property,(Comparable)value);}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_state_storage",tickLimit=6000)
    public void everyOwnedNativeStateAndPropertyTransitionPreservesExactIdentity(TestContext t){new Audit(t).step();}
    private static final class Audit {
        final TestContext t;final List<Block> blocks=Registries.BLOCK.getIds().stream().filter(id->id.getNamespace().equals("bloodborne_dw")||id.getNamespace().equals("bloodborne_rp")).map(Registries.BLOCK::get).toList();
        int blockIndex,stateIndex;long ownedStates,originalPrototypeStates,transitions;
        Map<Map<Property<?>,Comparable<?>>,BlockState> independent;List<BlockState> states;
        Audit(TestContext t){this.t=t;}
        void step(){
            int budget=1000;
            while(budget-->0){
                if(states==null){
                    if(blockIndex>=blocks.size()){finish();return;}
                    var block=blocks.get(blockIndex);states=block.getStateManager().getStates();stateIndex=0;
                    independent=new HashMap<>();for(var state:states)independent.put(state.getEntries(),state);
                }
                var block=blocks.get(blockIndex);var state=states.get(stateIndex++);var actual=table(state);
                {
                    var id=Registries.BLOCK.getId(block);
                    t.assertTrue(actual.isEmpty()||actual instanceof CompactStateTransitions<?>,"Every actual inefficient owned native table uses bounded compact layout "+id);
                    ownedStates++;if(block instanceof CompositeRootBlock composite&&!composite.spec.id.getPath().startsWith("source_"))originalPrototypeStates++;
                    for(var property:state.getProperties())for(var value:property.getValues()){
                        var expectedEntries=new HashMap<Property<?>,Comparable<?>>(state.getEntries());expectedEntries.put(property,value);
                        var expected=independent.get(expectedEntries);t.assertTrue(expected!=null,"Independent complete native state graph contains every property value");
                        t.assertTrue(with(state,property,value)==expected,"Native .with retains exact original neighbor identity "+id+"/"+property.getName());
                        t.assertTrue(actual.get(property,value)==(Objects.equals(state.getEntries().get(property),value)?null:expected),"Native transition table preserves changed-neighbor/null-current semantics");transitions++;
                    }
                }
                if(stateIndex==states.size()){blockIndex++;states=null;independent=null;}
            }
            t.runAtTick(t.getTick()+1,this::step);
        }
        void finish(){
            t.assertTrue(ownedStates>700000&&originalPrototypeStates==6656,"Full owned registry cohort and every original prototype composite state independently inspected");
            t.assertTrue(table(Blocks.OAK_STAIRS.getDefaultState()) instanceof ArrayTable<?,?,?>,"Vanilla state table remains actual Guava ArrayTable");
            var proof=new JsonObject();proof.addProperty("status","PASS_EXHAUSTIVE_NATIVE_STATE_IDENTITIES");proof.addProperty("ownedStates",ownedStates);proof.addProperty("originalCompositePrototypeStates",originalPrototypeStates);proof.addProperty("independentPropertyTransitions",transitions);
            proof.add("storageMetrics",new Gson().toJsonTree(CompactStateTransitions.metrics()));
            try{Files.writeString(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-state-storage-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception e){throw new IllegalStateException(e);}
            t.complete();
        }
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_state_storage",tickLimit=100)
    public void nativeGuavaTableViewsNullCellsOrderAndMutationContractsMatch(TestContext t){
        var nativeTable=table(CompositeArchitecture.kindBlock("source_bricks").getDefaultState());
        var original=ArrayTable.create(nativeTable);var compact=CompactStateTransitions.compact(original);
        t.assertTrue(compact instanceof CompactStateTransitions<?>,"Detached original Guava table converted for contract inspection");same(t,original,compact);
        var r=original.rowKeyList().get(0);var c=original.columnKeyList().get(0);var replacement=Blocks.DIAMOND_BLOCK.getDefaultState();
        var oldLive=original.row(r).entrySet().iterator().next();var newLive=compact.row(r).entrySet().iterator().next();
        t.assertTrue(original.put(r,c,replacement)==compact.put(r,c,replacement),"Normal put returns exact prior value");
        t.assertTrue(oldLive.getValue()==newLive.getValue(),"Previously obtained entries observe live subsequent cell writes");same(t,original,compact);
        original.row(r).put(c,null);compact.row(r).put(c,null);same(t,original,compact);
        Property<?> foreignRow=null;Comparable<?> foreignColumn=null;
        for(var row:original.rowKeyList())for(var column:original.columnKeyList())if(!row.getValues().contains(column)){foreignRow=row;foreignColumn=column;break;}
        t.assertTrue(foreignRow!=null,"Native table contains null foreign-domain Cartesian cells");
        original.put(foreignRow,foreignColumn,replacement);compact.put(foreignRow,foreignColumn,replacement);same(t,original,compact);
        original.column(foreignColumn).put(foreignRow,null);compact.column(foreignColumn).put(foreignRow,null);same(t,original,compact);
        var all=HashBasedTable.<Property<?>,Comparable<?>,BlockState>create();all.put(r,c,replacement);original.putAll(all);compact.putAll(all);same(t,original,compact);
        t.assertTrue(compact.get(null,null)==null&&!compact.contains(null,null)&&!compact.containsRow(null)&&!compact.containsColumn(null),"Null lookup matches original Guava contract");
        sameFailure(t,()->original.clear(),()->compact.clear());sameFailure(t,()->original.remove(r,c),()->compact.remove(r,c));
        sameFailure(t,()->original.row(null),()->compact.row(null));sameFailure(t,()->original.column(null),()->compact.column(null));
        sameFailure(t,()->original.put(null,c,replacement),()->compact.put(null,c,replacement));
        sameFailure(t,()->original.row(r).put(null,replacement),()->compact.row(r).put(null,replacement));
        sameFailure(t,()->original.rowMap().put(r,Map.of()),()->compact.rowMap().put(r,Map.of()));
        sameFailure(t,()->original.rowKeySet().remove(IntProperty.of("absent",0,1)),()->compact.rowKeySet().remove(IntProperty.of("absent",0,1)));
        sameFailure(t,()->original.cellSet().iterator().remove(),()->compact.cellSet().iterator().remove());
        t.assertTrue(nativeTable.get(foreignRow,foreignColumn)==null,"Mutation tests never changed an attached native state table");t.complete();
    }
    private static void same(TestContext t,Table<Property<?>,Comparable<?>,BlockState> a,Table<Property<?>,Comparable<?>,BlockState> b){
        t.assertTrue(a.size()==b.size()&&a.isEmpty()==b.isEmpty(),"Null-inclusive Cartesian size");
        t.assertTrue(new ArrayList<>(a.rowKeySet()).equals(new ArrayList<>(b.rowKeySet()))&&new ArrayList<>(a.columnKeySet()).equals(new ArrayList<>(b.columnKeySet())),"Original row/column iteration order");
        t.assertTrue(new ArrayList<>(a.cellSet()).equals(new ArrayList<>(b.cellSet()))&&new ArrayList<>(a.values()).equals(new ArrayList<>(b.values())),"Original null-inclusive cell/value iteration order");
        t.assertTrue(a.equals(b)&&b.equals(a)&&a.hashCode()==b.hashCode()&&a.toString().equals(b.toString()),"Symmetric table equality/hash/string including null cells");
        t.assertTrue(a.rowMap().equals(b.rowMap())&&a.columnMap().equals(b.columnMap())&&a.containsValue(null)==b.containsValue(null),"Every row/column/value view includes original null cells");
        for(var row:a.rowKeySet())for(var column:a.columnKeySet())t.assertTrue(a.contains(row,column)==b.contains(row,column)&&a.get(row,column)==b.get(row,column),"Every actual original Guava cell's key presence and neighbor reference");
    }
    private static void sameFailure(TestContext t,Runnable original,Runnable compact){Class<?> a=exception(original),b=exception(compact);t.assertTrue(a!=null&&a==b,"Mutation/invalid-key exception type matches original Guava: "+a+" vs "+b);}
    private static Class<?> exception(Runnable operation){try{operation.run();return null;}catch(RuntimeException failure){return failure.getClass();}}
}
