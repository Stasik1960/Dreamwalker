package dev.dreamwalker.bloodbornedw.composite;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Surface coordinates only for diagonal GENERAL source faces whose native
 * flat branch samples their own opaque root. Directional/cull-face lists use
 * a native neighbour even for diagonal geometry, and must keep that lighting.
 * This changes only the light query, never vertices, UVs, tint, shade or AO.
 */
@Environment(EnvType.CLIENT)
public final class SourceFlatLight {
    private static final float EPS=.0001f;
    private static final float OUTWARD=.001f;
    private static final Sample NATIVE=new Sample(false,0,0,0,List.of());
    // Baked quads are immutable resources, not world positions or instances.
    private static final Map<BakedQuad,Sample> SAMPLES=Collections.synchronizedMap(new IdentityHashMap<>());
    // Only raw resource-manager models are accepted here, never the per-root
    // visible/occlusion wrapper. Source JSON models have fixed face lists.
    private static final Map<BakedModel,Set<BakedQuad>> ROOT_QUADS=Collections.synchronizedMap(new IdentityHashMap<>());
    public record Point(float x,float y,float z){}
    public record Sample(boolean projected,float x,float y,float z,List<Point> corners){}
    private SourceFlatLight(){}
    public static void reload(){SAMPLES.clear();ROOT_QUADS.clear();}
    public static Set<BakedQuad> rootQuads(BakedModel raw,BlockState carrier){
        return ROOT_QUADS.computeIfAbsent(raw,ignored->{
            Set<BakedQuad> quads=Collections.newSetFromMap(new IdentityHashMap<>());
            quads.addAll(raw.getQuads(carrier,null,net.minecraft.util.math.random.Random.create(42)));
            return Collections.unmodifiableSet(quads);
        });
    }
    public static Sample sample(BakedQuad quad){return SAMPLES.computeIfAbsent(quad,SourceFlatLight::calculate);}
    public static BlockPos worldSample(BakedQuad quad,BlockPos root,Matrix4f transform){
        var sample=sample(quad);if(!sample.projected())return null;
        var point=transform.transformPosition(new Vector3f(sample.x(),sample.y(),sample.z()));
        return BlockPos.ofFloored(root.getX()+point.x,root.getY()+point.y,root.getZ()+point.z);
    }
    public static BlockPos[] worldCorners(BakedQuad quad,BlockPos root,Matrix4f transform){
        var corners=sample(quad).corners();var result=new BlockPos[corners.size()];
        for(int i=0;i<result.length;i++){
            var corner=corners.get(i);var point=transform.transformPosition(new Vector3f(corner.x(),corner.y(),corner.z()));
            result[i]=BlockPos.ofFloored(root.getX()+point.x,root.getY()+point.y,root.getZ()+point.z);
        }
        return result;
    }
    static boolean outsideRoot(BlockPos[] corners,BlockPos root){
        if(corners.length!=4)return false;
        for(var corner:corners)if(corner.equals(root))return false;
        return true;
    }
    /** Average the two native lightmap channels independently, including zero.
     * No brightest-neighbour selection, floor, emission or AO change. */
    static int averageLight(int[] lights){
        if(lights.length!=4)throw new IllegalArgumentException("Four exterior samples required");
        int block=0,sky=0;for(int light:lights){block+=light&65535;sky+=(light>>>16)&65535;}
        return (block/4)|((sky/4)<<16);
    }
    private static Sample calculate(BakedQuad quad){return calculate(quad.getVertexData(),quad.getFace());}
    /** Package-visible for focused geometry/lighting regression checks. */
    static Sample calculate(int[] data,Direction face){
        if(data.length%4!=0||data.length/4<3)return NATIVE;
        int stride=data.length/4;var points=new Vector3f[4];var center=new Vector3f();
        boolean plane=true;float coordinate=0;
        int axis=face.getAxis()==Direction.Axis.X?0:face.getAxis()==Direction.Axis.Y?1:2;
        for(int i=0;i<4;i++){
            var point=new Vector3f(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));
            if(!point.isFinite())return NATIVE;
            points[i]=point;center.add(point);
            if(i==0)coordinate=point.get(axis);else plane&=Math.abs(point.get(axis)-coordinate)<=EPS;
        }
        // A planar cardinal face can legitimately use the native neighbour,
        // including an extended/inset source face. Only a diagonal general
        // face conclusively takes vanilla's root sample (dimensions flag 0=false).
        if(plane)return NATIVE;
        var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
        if(normal.lengthSquared()<1e-12f)return NATIVE;
        normal.normalize();
        if(normal.dot(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ())<0)normal.negate();
        center.mul(.25f);
        // A thin diagonal can cross its opaque carrier while all four corners
        // are outside it (the authored basalt trim). Inset the samples by 1%
        // to avoid shared vertex boundaries; keep the real geometric normal.
        var corners=new java.util.ArrayList<Point>(4);
        for(var vertex:points){var p=new Vector3f(vertex).lerp(center,.01f).fma(OUTWARD,normal);corners.add(new Point(p.x,p.y,p.z));}
        center.fma(OUTWARD,normal);
        return new Sample(true,center.x,center.y,center.z,List.copyOf(corners));
    }
}
