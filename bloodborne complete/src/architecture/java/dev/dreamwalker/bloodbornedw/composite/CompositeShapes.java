package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.shape.*;

/** Bounded 16^3 occupancy. Each outward approximation is less than1/16 per face. */
public final class CompositeShapes {
    private static final int GRID=16,MAX_CACHE=8192;
    private static final Map<List<Box>,VoxelShape> CACHE=new ConcurrentHashMap<>();
    private static final double[] AXES=new double[GRID+1];
    static{for(int i=0;i<=GRID;i++)AXES[i]=i/(double)GRID;}
    public static VoxelShape of(List<Box> boxes){
        if(boxes.isEmpty())return VoxelShapes.empty();
        VoxelShape cached=CACHE.get(boxes);if(cached!=null)return cached;
        BitSetVoxelSet voxels=new BitSetVoxelSet(GRID,GRID,GRID);
        for(Box box:boxes){int x0=lower(box.minX()),y0=lower(box.minY()),z0=lower(box.minZ()),x1=upper(box.maxX()),y1=upper(box.maxY()),z1=upper(box.maxZ());for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)voxels.set(x,y,z);}
        VoxelShape shape=voxels.isEmpty()?VoxelShapes.empty():new GridShape(voxels);
        if(CACHE.size()>=MAX_CACHE)CACHE.clear();
        VoxelShape raced=CACHE.putIfAbsent(List.copyOf(boxes),shape);return raced==null?shape:raced;
    }
    public static int cacheSize(){return CACHE.size();}
    private static int lower(double coordinate){return Math.max(0,Math.min(GRID,(int)Math.floor(coordinate*GRID+1e-8)));}
    private static int upper(double coordinate){return Math.max(0,Math.min(GRID,(int)Math.ceil(coordinate*GRID-1e-8)));}
    private static final class GridShape extends ArrayVoxelShape{private GridShape(BitSetVoxelSet voxels){super(voxels,AXES,AXES,AXES);}}
    private CompositeShapes(){}
}
