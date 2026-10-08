package dev.dreamwalker.bloodbornedw.architecture.wall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Simple cached collision and independent one-box selection from authored bounds.
 * Cardinal collision uses post/arm rectangles at native wall height1.5; diagonal
 * collision uses one coarse root-cell rectangle. Neither reserves neighbor cells.
 */
final class WallGeometry {
    private final List<double[]> post,low,tall;
    private final VoxelShape[] collision=new VoxelShape[256],outline=new VoxelShape[1296];
    WallGeometry(){
        try(InputStream stream=WallGeometry.class.getResourceAsStream("/bloodborne_dw/prototype-wall.json")){
            if(stream==null)throw new IllegalStateException("Missing original wall geometry");
            JsonObject value=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(value.get("schemaVersion").getAsInt()!=1||value.get("units").getAsInt()!=16)throw new IllegalStateException("Invalid wall geometry schema");
            post=boxes(value.getAsJsonArray("post"));low=boxes(value.getAsJsonArray("low"));tall=boxes(value.getAsJsonArray("tall"));
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot load wall geometry",failure);}
    }
    synchronized VoxelShape collision(int sides,boolean includePost,int yaw){
        int mask=0,value=sides;for(int direction=0;direction<4;direction++){if(value%3!=0)mask|=1<<direction;value/=3;}
        int key=mask|(includePost?16:0)|(yaw<<5);VoxelShape cached=collision[key];if(cached!=null)return cached;
        List<double[]> parts=new ArrayList<>(5);
        if(includePost)for(double[] box:post)parts.add(rotated(box,yaw,1.5));
        for(int direction=0;direction<4;direction++)if((mask&(1<<direction))!=0)for(double[] box:low)parts.add(rotated(box,(yaw+direction*2)&7,1.5));
        VoxelShape result=VoxelShapes.empty();
        if((yaw&1)!=0){if(!parts.isEmpty())result=cuboid(bounds(parts));}
        else{for(double[] box:parts)result=VoxelShapes.union(result,cuboid(box));result=result.simplify();}
        return collision[key]=result;
    }
    synchronized VoxelShape outline(int sides,boolean includePost,int yaw){
        int key=sides+(includePost?81:0)+yaw*162;VoxelShape cached=outline[key];if(cached!=null)return cached;
        List<double[]> parts=new ArrayList<>(5);if(includePost)for(double[] box:post)parts.add(rotated(box,yaw,box[4]));
        int value=sides;for(int direction=0;direction<4;direction++){int shape=value%3;value/=3;if(shape!=0)for(double[] box:shape==2?tall:low)parts.add(rotated(box,(yaw+direction*2)&7,box[4]));}
        return outline[key]=parts.isEmpty()?VoxelShapes.empty():cuboid(bounds(parts));
    }
    private static VoxelShape cuboid(double[] box){return VoxelShapes.cuboid(box[0],box[1],box[2],box[3],box[4],box[5]);}
    private static double[] bounds(List<double[]> parts){double[] result={1,Double.POSITIVE_INFINITY,1,0,Double.NEGATIVE_INFINITY,0};for(double[] box:parts){for(int i=0;i<3;i++){result[i]=Math.min(result[i],box[i]);result[i+3]=Math.max(result[i+3],box[i+3]);}}return result;}
    private static List<double[]> boxes(JsonArray input){
        if(input==null||input.isEmpty()||input.size()>4)throw new IllegalArgumentException("Unexpected wall cuboid budget");List<double[]> result=new ArrayList<>();
        for(var entry:input){JsonArray values=entry.getAsJsonArray();if(values.size()!=6)throw new IllegalArgumentException("Wall cuboid needs six values");double[] box=new double[6];for(int i=0;i<6;i++){box[i]=values.get(i).getAsDouble()/16;if(box[i]<0||box[i]>1)throw new IllegalArgumentException("Wall geometry exceeds one cell");}if(box[0]>=box[3]||box[1]>=box[4]||box[2]>=box[5])throw new IllegalArgumentException("Empty wall cuboid");result.add(box);}return List.copyOf(result);
    }
    private static double[] rotated(double[] box,int yaw,double height){
        double angle=yaw*Math.PI/4,c=Math.cos(angle),s=Math.sin(angle),minX=1,maxX=0,minZ=1,maxZ=0;
        for(double x:new double[]{box[0],box[3]})for(double z:new double[]{box[2],box[5]}){double rx=.5+c*(x-.5)-s*(z-.5),rz=.5+s*(x-.5)+c*(z-.5);minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minZ=Math.min(minZ,rz);maxZ=Math.max(maxZ,rz);}
        return new double[]{clamp(minX),box[1],clamp(minZ),clamp(maxX),height,clamp(maxZ)};
    }
    private static double clamp(double value){if(Math.abs(value)<1e-9)return 0;if(Math.abs(value-1)<1e-9)return 1;return Math.max(0,Math.min(1,value));}
}
