package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Model-sized wooden floors. Small outer-cell overhangs are cropped after
 * instance rotation and translation, including overhangs beyond the root cell. */
public final class WoodFloorCollision {
    private static final Map<String,List<ImportedCollisionMath.Box>> BOXES=load();
    private static Map<String,List<ImportedCollisionMath.Box>> load(){
        try(var in=WoodFloorCollision.class.getResourceAsStream("/bloodborne_dw/wood-floor-collision.json")){
            if(in==null)throw new IllegalStateException("Missing floor geometry");
            var rows=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonArray();var result=new HashMap<String,List<ImportedCollisionMath.Box>>();
            for(var raw:rows){var row=raw.getAsJsonObject();var values=row.getAsJsonArray("bounds");var a=values.get(0).getAsJsonArray();var b=values.get(1).getAsJsonArray();result.put(row.get("source").getAsString()+"/"+row.get("variant").getAsInt(),List.of(new ImportedCollisionMath.Box(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble())));}
            return Map.copyOf(result);
        }catch(Exception failure){throw new IllegalStateException("Cannot read wooden floor geometry",failure);}
    }
    public static boolean supports(CompositeSpec spec,BlockState state){return BOXES.containsKey(spec.id+"/"+CompositeSpec.poseVariant(state));}
    /** A flat panel has a thin body below its surface. Native support ending at
     * that same surface is contact, so check only the still-exposed volume.
     * The installed collision and owner membership keep the complete panel. */
    public static List<net.minecraft.util.math.Box> placementBoxes(ServerWorld world,List<net.minecraft.util.math.Box> boxes){
        var result=new ArrayList<net.minecraft.util.math.Box>();
        for(var box:boxes){
            if(box.getYLength()>.0625+1e-8){result.add(box);continue;}
            List<net.minecraft.util.math.Box> remaining=new ArrayList<>();remaining.add(box);
            for(int x=(int)Math.floor(box.minX+1e-8)-1;x<(int)Math.ceil(box.maxX-1e-8)+1;x++)
                for(int y=(int)Math.floor(box.minY+1e-8)-1;y<(int)Math.ceil(box.maxY-1e-8)+1;y++)
                    for(int z=(int)Math.floor(box.minZ+1e-8)-1;z<(int)Math.ceil(box.maxZ-1e-8)+1;z++){
                        var pos=new BlockPos(x,y,z);if(!world.isChunkLoaded(pos))continue;var state=world.getBlockState(pos);
                        if(state.getBlock() instanceof CompositeRootBlock||state.isOf(CompositeArchitecture.CELL)||StaticArchitectureCatalog.contains(state))continue;
                        for(var local:CompositeRuntime.nativeCollision(state,world,pos,ShapeContext.absent()).getBoundingBoxes()){
                            var support=local.offset(pos);if(Math.abs(support.maxY-box.maxY)>1e-8)continue;
                            var next=new ArrayList<net.minecraft.util.math.Box>();
                            for(var piece:remaining)subtractContact(piece,support,next);remaining=next;
                        }
                    }
            result.addAll(remaining);
        }
        return List.copyOf(result);
    }
    private static void subtractContact(net.minecraft.util.math.Box piece,net.minecraft.util.math.Box support,List<net.minecraft.util.math.Box> result){
        if(!piece.intersects(support)){result.add(piece);return;}
        double x0=Math.max(piece.minX,support.minX),x1=Math.min(piece.maxX,support.maxX),y0=Math.max(piece.minY,support.minY),y1=Math.min(piece.maxY,support.maxY),z0=Math.max(piece.minZ,support.minZ),z1=Math.min(piece.maxZ,support.maxZ);
        positive(result,piece.minX,piece.minY,piece.minZ,x0,piece.maxY,piece.maxZ);
        positive(result,x1,piece.minY,piece.minZ,piece.maxX,piece.maxY,piece.maxZ);
        positive(result,x0,piece.minY,piece.minZ,x1,y0,piece.maxZ);
        positive(result,x0,y1,piece.minZ,x1,piece.maxY,piece.maxZ);
        positive(result,x0,y0,piece.minZ,x1,y1,z0);
        positive(result,x0,y0,z1,x1,y1,piece.maxZ);
    }
    private static void positive(List<net.minecraft.util.math.Box> result,double x0,double y0,double z0,double x1,double y1,double z1){
        if(x1-x0>1e-8&&y1-y0>1e-8&&z1-z0>1e-8)result.add(new net.minecraft.util.math.Box(x0,y0,z0,x1,y1,z1));
    }
    public static Map<Cell,Footprint> footprint(CompositeSpec spec,BlockState state,double mount,double x,double y,double z){
        var result=new TreeMap<Cell,Footprint>();
        for(var box:ImportedCollisionMath.transform(BOXES.get(spec.id+"/"+CompositeSpec.poseVariant(state)),state.get(CompositeRootBlock.ROTATION),mount,false,x,y,z)){
            double[] a={box.x0(),box.y0(),box.z0()},b={box.x1(),box.y1(),box.z1()};
            for(int axis:new int[]{0,2}){
                double low=Math.ceil(a[axis])-a[axis],high=b[axis]-Math.floor(b[axis]);
                if(a[axis]<0&&low>1e-8&&low<.5-1e-8)a[axis]=Math.ceil(a[axis]);
                if(b[axis]>1&&high>1e-8&&high<.5-1e-8)b[axis]=Math.floor(b[axis]);
            }
            for(int X=(int)Math.floor(a[0]+1e-8);X<Math.ceil(b[0]-1e-8);X++)for(int Y=(int)Math.floor(a[1]+1e-8);Y<Math.ceil(b[1]-1e-8);Y++)for(int Z=(int)Math.floor(a[2]+1e-8);Z<Math.ceil(b[2]-1e-8);Z++){
                var value=new Box(Math.max(0,a[0]-X),Math.max(0,a[1]-Y),Math.max(0,a[2]-Z),Math.min(1,b[0]-X),Math.min(1,b[1]-Y),Math.min(1,b[2]-Z));
                result.put(new Cell(X,Y,Z),new Footprint(List.of(value),List.of(value)));
            }
        }
        result.putIfAbsent(Cell.ORIGIN,new Footprint(List.of(),List.of()));return Collections.unmodifiableMap(result);
    }
}
