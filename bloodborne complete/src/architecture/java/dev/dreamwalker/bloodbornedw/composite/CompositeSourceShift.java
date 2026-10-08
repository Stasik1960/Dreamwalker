package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/** Reviewed source anchoring only. SourceShift uses block units in canonical local axes. */
public final class CompositeSourceShift {
    public record Shift(double x,double y,double z){
        public Shift{for(double value:new double[]{x,y,z})if(!Double.isFinite(value)||Math.abs(value)>32)throw new IllegalArgumentException("Source anchoring shift exceeds32 blocks");}
        public Shift rotate(int yaw){double angle=Math.PI*(yaw&7)/4,c=Math.cos(angle),s=Math.sin(angle);return new Shift(x*c-z*s,y,x*s+z*c);}
    }
    private record Key(CompositeSpec spec,int variant,boolean open,int yaw,double mount,String plane,Shift shift){}
    private static final Map<Key,Map<Cell,Footprint>> CACHE=new ConcurrentHashMap<>();
    private CompositeSourceShift(){}
    public static Shift local(NbtCompound payload){
        if(!payload.contains("SourceShift"))return new Shift(0,0,0);
        NbtList values=payload.getList("SourceShift",6);if(values.size()!=3)throw new IllegalArgumentException("Expected three SourceShift doubles");
        return new Shift(values.getDouble(0),values.getDouble(1),values.getDouble(2));
    }
    public static Shift world(BlockState state,NbtCompound payload){return local(payload).rotate(state.get(CompositeRootBlock.ROTATION));}
    public static Map<Cell,Footprint> footprint(CompositeSpec spec,BlockState state,NbtCompound payload){
        Shift local=local(payload);double mount=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);
        if(local.x==0&&local.y==0&&local.z==0)return spec.footprint(state,mount);
        Key key=new Key(spec,state.get(CompositeRootBlock.VARIANT),state.get(CompositeRootBlock.OPEN),state.get(CompositeRootBlock.ROTATION),mount,ThinWindowRootBlock.mount(state).asString(),local);
        Map<Cell,Footprint> known=CACHE.get(key);if(known!=null)return known;
        Shift shift=world(state,payload);TreeMap<Cell,List<Box>> collision=new TreeMap<>(),selection=new TreeMap<>();
        for(var entry:spec.footprint(state,mount).entrySet()){
            distribute(entry.getKey(),entry.getValue().collision(),shift,collision);
            distribute(entry.getKey(),entry.getValue().selection(),shift,selection);
        }
        TreeSet<Cell> cells=new TreeSet<>(collision.keySet());cells.addAll(selection.keySet());cells.add(Cell.ORIGIN);
        if(cells.size()>4096)throw new IllegalArgumentException("Source-shift cell budget exceeded");
        TreeMap<Cell,Footprint> result=new TreeMap<>();for(Cell cell:cells)result.put(cell,new Footprint(List.copyOf(collision.getOrDefault(cell,List.of())),List.copyOf(selection.getOrDefault(cell,List.of()))));
        Map<Cell,Footprint> immutable=Collections.unmodifiableMap(result);if(CACHE.size()<4096)CACHE.putIfAbsent(key,immutable);return immutable;
    }
    private static void distribute(Cell origin,List<Box> boxes,Shift shift,Map<Cell,List<Box>> target){
        for(Box box:boxes){double x0=origin.x()+box.minX()+shift.x,y0=origin.y()+box.minY()+shift.y,z0=origin.z()+box.minZ()+shift.z;
            double x1=origin.x()+box.maxX()+shift.x,y1=origin.y()+box.maxY()+shift.y,z1=origin.z()+box.maxZ()+shift.z;
            for(int x=(int)Math.floor(x0+1e-10);x<Math.ceil(x1-1e-10);x++)for(int y=(int)Math.floor(y0+1e-10);y<Math.ceil(y1-1e-10);y++)for(int z=(int)Math.floor(z0+1e-10);z<Math.ceil(z1-1e-10);z++){
                double a=clamp(x0-x),b=clamp(y0-y),c=clamp(z0-z),d=clamp(x1-x),e=clamp(y1-y),f=clamp(z1-z);
                if(d-a>1e-10&&e-b>1e-10&&f-c>1e-10)target.computeIfAbsent(new Cell(x,y,z),unused->new ArrayList<>()).add(new Box(a,b,c,d,e,f));
            }
        }
    }
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
}
