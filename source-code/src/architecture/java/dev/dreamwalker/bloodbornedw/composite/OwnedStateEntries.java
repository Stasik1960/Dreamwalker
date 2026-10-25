package dev.dreamwalker.bloodbornedw.composite;

import com.google.common.collect.ImmutableMap;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;

/** Storage sharing only: exact ordered Property and value object identities.
 * Different blocks remain different BlockState/owner objects. All lookup pools
 * are bounded and local to this registration pass; none retain worlds/cells. */
public final class OwnedStateEntries {
    public static final int MAX_LAYOUTS=256,MAX_MAPS=65536,MAX_PROPERTIES=16;
    public record Metrics(long ownedStatesVisited,long mapsReplaced,long valueSlotsEliminated,
                          long identityCellsVerified,long cachedMaps,int sharedLayouts,
                          long unshareableStates,long boundedFallbackStates,
                          long foreignSampleStatesVerified,boolean temporaryCacheReleased) {}
    private static volatile Metrics startup;
    private OwnedStateEntries(){}

    /** Object-array equality deliberately uses ==, never Property.equals. */
    private static final class IdentityVector {
        final Object[] members;final int hash;
        IdentityVector(Object[] members){this.members=members;int result=1;for(Object member:members)result=31*result+System.identityHashCode(member);hash=result;}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof IdentityVector vector)||members.length!=vector.members.length)return false;for(int n=0;n<members.length;n++)if(members[n]!=vector.members[n])return false;return true;}
    }
    private static final class Layout {
        final IdentityVector keys;
        final Map<IdentityVector,ImmutableMap<Property<?>,Comparable<?>>> maps=new HashMap<>();
        Layout(IdentityVector keys){this.keys=keys;}
        boolean matches(ImmutableMap<Property<?>,Comparable<?>> map){if(map.size()!=keys.members.length)return false;int index=0;for(var key:map.keySet())if(key!=keys.members[index++])return false;return true;}
    }
    private record ForeignSample(BlockState state,ImmutableMap<Property<?>,Comparable<?>> entries) {}
    public static Metrics metrics(){return startup;}

    private static boolean owned(Block block){String namespace=Registries.BLOCK.getId(block).getNamespace();return namespace.equals("bloodborne_dw")||namespace.equals("bloodborne_rp");}
    private static IdentityVector layout(ImmutableMap<Property<?>,Comparable<?>> entries){return new IdentityVector(entries.keySet().toArray());}
    private static IdentityVector values(ImmutableMap<Property<?>,Comparable<?>> entries){return new IdentityVector(entries.values().toArray());}
    private static long verify(ImmutableMap<Property<?>,Comparable<?>> old,ImmutableMap<Property<?>,Comparable<?>> shared){
        if(old.size()!=shared.size())throw new IllegalStateException("Owned native property map size changed");
        var left=old.entrySet().iterator();var right=shared.entrySet().iterator();long count=0;
        while(left.hasNext()){var a=left.next();var b=right.next();if(a.getKey()!=b.getKey()||a.getValue()!=b.getValue())throw new IllegalStateException("Owned native property/value object identity or order changed");count++;}
        if(right.hasNext()||!old.equals(shared))throw new IllegalStateException("Owned native immutable property map content changed");return count;
    }
    public static synchronized Metrics optimizeRegistered(){
        // Only layouts shared by multiple owned registry blocks can save maps.
        // A unique large art_bank property keeps its maps without cache overhead.
        var blocks=Registries.BLOCK.getIds().stream().sorted(Comparator.comparing(Object::toString)).map(Registries.BLOCK::get).filter(OwnedStateEntries::owned).toList();
        Map<IdentityVector,Integer> familyCounts=new HashMap<>();
        for(var block:blocks){var entries=block.getDefaultState().getEntries();if(!entries.isEmpty()&&entries.size()<=MAX_PROPERTIES)familyCounts.merge(layout(entries),1,Integer::sum);}
        Map<IdentityVector,Layout> sharedLayouts=new HashMap<>();
        var foreign=new ArrayList<ForeignSample>();
        for(var block:List.of(Blocks.STONE,Blocks.OAK_STAIRS,Blocks.OAK_FENCE))for(var state:block.getStateManager().getStates())foreign.add(new ForeignSample(state,state.getEntries()));
        long visited=0,replaced=0,slots=0,verified=0,cached=0,unshareable=0,fallback=0;
        for(var block:blocks){
            var key=layout(block.getDefaultState().getEntries());
            if(familyCounts.getOrDefault(key,0)<2){long count=block.getStateManager().getStates().size();visited+=count;unshareable+=count;continue;}
            var pool=sharedLayouts.get(key);
            if(pool==null){if(sharedLayouts.size()>=MAX_LAYOUTS){long count=block.getStateManager().getStates().size();visited+=count;fallback+=count;continue;}pool=new Layout(key);sharedLayouts.put(key,pool);}
            for(var state:block.getStateManager().getStates()){
                visited++;var old=state.getEntries();if(!pool.matches(old)){fallback++;continue;}
                var signature=values(old);var shared=pool.maps.get(signature);
                if(shared==null){if(cached>=MAX_MAPS){fallback++;continue;}pool.maps.put(signature,old);cached++;continue;}
                verified+=verify(old,shared);
                if(old!=shared){((StateEntriesAccess)state).bloodborne_dw$sharedEntries(shared);if(state.getEntries()!=shared||state.getBlock()!=block)throw new IllegalStateException("Owned native state owner or property map replacement changed");replaced++;slots+=old.size();}
            }
        }
        for(var sample:foreign)if(sample.state.getEntries()!=sample.entries)throw new IllegalStateException("Foreign native immutable property map was changed");
        var result=new Metrics(visited,replaced,slots,verified,cached,sharedLayouts.size(),unshareable,fallback,foreign.size(),true);
        // Clear every temporary lookup before retaining only numeric metrics.
        sharedLayouts.clear();familyCounts.clear();foreign.clear();
        if(startup==null)startup=result;return result;
    }
}
