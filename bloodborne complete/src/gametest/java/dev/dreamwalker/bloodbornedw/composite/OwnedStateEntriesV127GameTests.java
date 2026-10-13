package dev.dreamwalker.bloodbornedw.composite;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;

/** Checks every registered owned state's actual owner/key/domain identities,
 * plus shared immutable-map references and native foreign reference invariants. */
public final class OwnedStateEntriesV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="z_v127_state_entries",tickLimit=6000)
    public void ownedImmutableEntriesShareOnlyExactIdentityMapsAndPreserveEveryStateOwner(TestContext context){new Audit(context).step();}
    private static final class IdentitySignature {
        final Object[] items;final int hash;
        IdentitySignature(ImmutableMap<Property<?>,Comparable<?>> map){items=new Object[map.size()*2];int index=0,result=1;for(var row:map.entrySet()){items[index++]=row.getKey();items[index++]=row.getValue();}for(var item:items)result=31*result+System.identityHashCode(item);hash=result;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){if(!(other instanceof IdentitySignature value)||items.length!=value.items.length)return false;for(int n=0;n<items.length;n++)if(items[n]!=value.items[n])return false;return true;}
    }
    private static final class Audit {
        final TestContext context;
        final List<Block> blocks=Registries.BLOCK.getIds().stream().filter(id->id.getNamespace().equals("bloodborne_dw")||id.getNamespace().equals("bloodborne_rp")).map(Registries.BLOCK::get).toList();
        final Map<IdentitySignature,ImmutableMap<Property<?>,Comparable<?>>> defaults=new HashMap<>();
        int blockIndex,stateIndex;long states,propertyKeys,sharedDefaults;List<BlockState> current;
        Audit(TestContext context){this.context=context;var metrics=OwnedStateEntries.metrics();context.assertTrue(metrics!=null&&metrics.mapsReplaced()>100000&&metrics.identityCellsVerified()>=metrics.valueSlotsEliminated(),"Actual startup shared many owned maps after exact old/new key/value identity checks");context.assertTrue(metrics.cachedMaps()<=OwnedStateEntries.MAX_MAPS&&metrics.sharedLayouts()<=OwnedStateEntries.MAX_LAYOUTS&&metrics.temporaryCacheReleased(),"Interner lookup memory is bounded and released after startup");}
        void step(){
            int budget=5000;
            while(budget-->0){
                if(current==null){
                    if(blockIndex==blocks.size()){finish();return;}
                    var block=blocks.get(blockIndex);current=block.getStateManager().getStates();stateIndex=0;
                    var map=block.getDefaultState().getEntries();var previous=defaults.putIfAbsent(new IdentitySignature(map),map);if(previous!=null&&!map.isEmpty()){context.assertTrue(previous==map,"Equal ordered Property/value identities share the actual immutable default map across owned registry blocks");sharedDefaults++;}
                }
                var block=blocks.get(blockIndex);var state=current.get(stateIndex++);context.assertTrue(state.getBlock()==block,"Original native BlockState and its exact owner stay distinct");
                for(var row:state.getEntries().entrySet()){
                    var property=row.getKey();context.assertTrue(block.getStateManager().getProperty(property.getName())==property,"Property keys remain the exact declared manager objects");
                    boolean exactDomainValue=false;for(Object value:property.getValues())if(value==row.getValue()){exactDomainValue=true;break;}
                    context.assertTrue(exactDomainValue,"Property values remain the exact declared domain objects");propertyKeys++;
                }
                states++;if(stateIndex==current.size()){current=null;blockIndex++;}
            }
            context.runAtTick(context.getTick()+1,this::step);
        }
        void finish(){
            context.assertTrue(states==OwnedStateEntries.metrics().ownedStatesVisited()&&sharedDefaults>300,"Proof visits every registered owned state and hundreds of shared family defaults");
            var foreign=new ArrayList<Map.Entry<BlockState,ImmutableMap<Property<?>,Comparable<?>>>>();for(var block:List.of(Blocks.STONE,Blocks.OAK_STAIRS,Blocks.OAK_FENCE))for(var state:block.getStateManager().getStates())foreign.add(Map.entry(state,state.getEntries()));
            var repeated=OwnedStateEntries.optimizeRegistered();for(var sample:foreign)context.assertTrue(sample.getKey().getEntries()==sample.getValue(),"Actual repeat pass leaves foreign native immutable-map object references untouched");
            context.assertTrue(repeated.mapsReplaced()==0,"Repeat optimization is idempotent for every owned state map");
            var proof=new JsonObject();proof.addProperty("status","PASS_EXHAUSTIVE_NATIVE_PROPERTY_MAP_IDENTITIES");proof.addProperty("ownedStates",states);proof.addProperty("declaredPropertyKeyAndValueIdentityChecks",propertyKeys);proof.addProperty("sharedRegistryDefaults",sharedDefaults);proof.addProperty("foreignMapReferencesVerifiedOnRepeat",foreign.size());proof.addProperty("idempotentRepeat",true);proof.add("startupStorageMetrics",new com.google.gson.Gson().toJsonTree(OwnedStateEntries.metrics()));
            try{Files.writeString(context.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-state-entries-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception failure){throw new IllegalStateException(failure);}
            context.complete();
        }
    }
}
