package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.fabricmc.api.*;
import net.minecraft.block.*;
import net.minecraft.client.render.model.*;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.AffineTransformation;
import org.apache.commons.lang3.tuple.Pair;

/** Exact ordered graph sharing AFTER every actual child bake. Entries belong
 * to the parsed unbaked model, never to a global registry or resource cache. */
@Environment(EnvType.CLIENT)
public final class MultipartModelCoreCache {
    private static final LongAdder BUILT=new LongAdder(),REUSED=new LongAdder();
    private MultipartModelCoreCache(){}
    public record Entry(AffineTransformation rotation,boolean uvLock,List<Pair<Predicate<BlockState>,BakedModel>> graph,BakedModel model){}
    public static boolean same(Entry entry,List<Pair<Predicate<BlockState>,BakedModel>> graph,ModelBakeSettings settings){
        if(entry==null||entry.rotation()!=settings.getRotation()||entry.uvLock()!=settings.isUvLocked()||entry.graph().size()!=graph.size())return false;
        for(int i=0;i<graph.size();i++){var old=entry.graph().get(i);var now=graph.get(i);if(old.getLeft()!=now.getLeft()||old.getRight()!=now.getRight())return false;}
        return true;
    }
    public static Entry entry(List<Pair<Predicate<BlockState>,BakedModel>> graph,ModelBakeSettings settings,BakedModel model,StateManager<Block,BlockState> manager){
        if(model instanceof MultipartStateCacheData data)data.dreamwalker$ownedStates(manager);
        BUILT.increment();return new Entry(settings.getRotation(),settings.isUvLocked(),List.copyOf(graph),model);
    }
    public static BakedModel reuse(Entry entry){REUSED.increment();return entry.model();}
    public static Map<String,Long> metrics(){return Map.of("builtExactOwnedGraphs",BUILT.sum(),"reusedExactOwnedGraphs",REUSED.sum(),"maximumEntriesPerParsedModel",1L);}
    /** Vanilla only calls get/put here. Bits are completed before put and are
     * never mutated afterwards. Registered-domain validation occurs on cold
     * writes; the hot get does not scan the Cartesian state list. */
    public static final class OwnedStates extends AbstractMap<BlockState,BitSet>{
        private final StateManager<Block,BlockState> manager;
        private final ConcurrentHashMap<BlockState,BitSet> values=new ConcurrentHashMap<>();
        public OwnedStates(StateManager<Block,BlockState> manager){this.manager=manager;}
        @Override public BitSet get(Object key){return key instanceof BlockState state&&state.getBlock()==manager.getOwner()?values.get(state):null;}
        @Override public BitSet put(BlockState state,BitSet bits){return state.getBlock()==manager.getOwner()&&manager.getStates().contains(state)?values.put(state,bits):null;}
        @Override public int size(){return values.size();}
        @Override public Set<Map.Entry<BlockState,BitSet>> entrySet(){return Collections.unmodifiableMap(values).entrySet();}
    }
}
