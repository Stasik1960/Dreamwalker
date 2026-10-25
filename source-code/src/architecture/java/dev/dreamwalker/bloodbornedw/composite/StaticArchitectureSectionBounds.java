package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/** Only an existing native section under compilation retains this one flag;
 * there is no root/position index or per-world mesh registry. */
@Environment(EnvType.CLIENT)
public final class StaticArchitectureSectionBounds {
    public interface Section {void staticArchitectureBounds(BlockPos expectedOrigin,Box bounds);}
    private static final class Build {
        final BlockPos origin;Box bounds;double minX,minY,minZ,maxX,maxY,maxZ;
        Build(BlockPos origin){this.origin=origin.toImmutable();bounds=nativeBox(origin);minX=origin.getX();minY=origin.getY();minZ=origin.getZ();maxX=minX+16;maxY=minY+16;maxZ=minZ+16;}
        void vertex(double x,double y,double z){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return;x+=origin.getX();y+=origin.getY();z+=origin.getZ();minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
    }
    private static final ThreadLocal<Build> BUILD=new ThreadLocal<>();
    private StaticArchitectureSectionBounds(){}
    public static void begin(BlockPos origin){BUILD.set(new Build(origin));}
    public static Box nativeBox(BlockPos origin){return new Box(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+16,origin.getY()+16,origin.getZ()+16);}
    public static Box include(Box bounds,BlockPos cell,StaticArchitectureCatalog.Recipe recipe){
        if(recipe.resident()<0)return bounds;
        return bounds.union(recipe.fragments().get(recipe.resident()).pose().visualBounds().offset(cell));
    }
    public static void root(BlockPos cell,StaticArchitectureCatalog.Recipe recipe){Build build=BUILD.get();if(build!=null)build.bounds=include(build.bounds,cell,recipe);}
    public static void end(Section section){Build build=BUILD.get();BUILD.remove();if(build!=null)section.staticArchitectureBounds(build.origin,build.bounds.union(new Box(build.minX,build.minY,build.minZ,build.maxX,build.maxY,build.maxZ)));}
    /** Before first compilation, use the same emitted-model bounds cache that
     * the old BER already used. The compiled section records its actual vertices. */
    public static Box includeOwned(Box bounds,BlockPos cell,BlockState state,Object data){
        if(!CompositeTerrain.at(state,cell))return bounds;
        if(data instanceof CompositeTerrain.Snapshot snapshot&&snapshot.owner()!=null&&snapshot.state()==state){
            if(!snapshot.meshable())return bounds;
            var block=(CompositeRootBlock)state.getBlock();var payload=snapshot.payload();
            if(block.spec.simplePhysicalCollision&&ImportedTypeAliases.needsMigration(state,payload)){payload=ImportedTypeAliases.transformPayload(state,payload);state=ImportedTypeAliases.target(state);block=(CompositeRootBlock)state.getBlock();}
            var local=CompositeVisualBoundsV127.localBounds(block,state,payload);
            if(local.isPresent()){var shift=CompositeSourceShift.world(state,payload);return bounds.union(local.get().offset(cell.getX()+shift.x(),cell.getY()+payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY)+shift.y(),cell.getZ()+shift.z()));}
        }
        // Unknown/custom resource adapters must compile before culling them.
        return bounds.union(new Box(cell.getX()-64,cell.getY()-64,cell.getZ()-64,cell.getX()+65,cell.getY()+65,cell.getZ()+65));
    }
    public static VertexConsumer record(VertexConsumer target){Build build=BUILD.get();return build==null?target:new BoundsConsumer(target,build);}
    private static final class BoundsConsumer implements VertexConsumer {
        private final VertexConsumer target;private final Build build;private final Vector3f point=new Vector3f();
        BoundsConsumer(VertexConsumer target,Build build){this.target=target;this.build=build;}
        @Override public void quad(MatrixStack.Entry matrix,BakedQuad quad,float[] brightness,float r,float g,float b,int[] lights,int overlay,boolean useColor){
            int[] data=quad.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){int offset=i*stride;point.set(Float.intBitsToFloat(data[offset]),Float.intBitsToFloat(data[offset+1]),Float.intBitsToFloat(data[offset+2])).mulPosition(matrix.getPositionMatrix());build.vertex(point.x,point.y,point.z);}target.quad(matrix,quad,brightness,r,g,b,lights,overlay,useColor);
        }
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){build.vertex(x,y,z);target.vertex(x,y,z,r,g,b,a,u,v,overlay,light,nx,ny,nz);}
        @Override public VertexConsumer vertex(double x,double y,double z){build.vertex(x,y,z);target.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){target.color(r,g,b,a);return this;}
        @Override public VertexConsumer texture(float u,float v){target.texture(u,v);return this;}
        @Override public VertexConsumer overlay(int u,int v){target.overlay(u,v);return this;}
        @Override public VertexConsumer light(int u,int v){target.light(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){target.normal(x,y,z);return this;}
        @Override public void next(){target.next();}
        @Override public void fixedColor(int r,int g,int b,int a){target.fixedColor(r,g,b,a);}
        @Override public void unfixColor(){target.unfixColor();}
    }
}
