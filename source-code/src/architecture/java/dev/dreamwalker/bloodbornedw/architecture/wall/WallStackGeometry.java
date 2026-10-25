package dev.dreamwalker.bloodbornedw.architecture.wall;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Bounded cache of native wall seam assignments, preserving the complete collider union. */
final class WallStackGeometry {
    private static final int LIMIT=4096;
    // VoxelShape uses identity equality: no state/material Cartesian product or
    // allocation of copied quads/boxes. Dynamic foreign walls are also bounded.
    private record Pair(VoxelShape own,VoxelShape lower,VoxelShape cap) {}
    private static final Map<Pair,VoxelShape> CACHE=new LinkedHashMap<>(64,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Pair,VoxelShape> eldest){return size()>LIMIT;}
    };
    private WallStackGeometry() {}
    static synchronized int cacheSize(){return CACHE.size();}
    static synchronized VoxelShape of(VoxelShape own,VoxelShape lower,VoxelShape cap) {
        if(own.isEmpty()||(lower.isEmpty()&&cap.isEmpty()))return own;
        return CACHE.computeIfAbsent(new Pair(own,lower,cap),pair->{
            VoxelShape actual=pair.lower.isEmpty()?pair.own:VoxelShapes.combineAndSimplify(pair.own,pair.lower.offset(0,-1,0),BooleanBiFunction.ONLY_FIRST);
            return pair.cap.isEmpty()?actual:VoxelShapes.combineAndSimplify(actual,pair.cap.offset(0,1,0),BooleanBiFunction.ONLY_FIRST);
        });
    }
}
