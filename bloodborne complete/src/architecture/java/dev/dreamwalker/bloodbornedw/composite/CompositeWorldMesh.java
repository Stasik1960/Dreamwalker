package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.joml.*;

/** Reuse vanilla's world lighting/AO, but bake it only when the world changes.
 * Static owner meshes have no per-frame world queries or quad allocations.
 * Sky light stays native: day/night changes are applied by Minecraft's lightmap.
 */
public final class CompositeWorldMesh {
    private static final long MAX_BYTES=96L*1024*1024;
    // A logical root/helper is ownership metadata, not another opaque cube
    // inside its own custom model. Real neighbouring blocks still shade it.
    // Converted whole assemblies retain their explicit native shadow cells.
    private static final AtomicLong LIGHT_REVISION=new AtomicLong();
    private static final AtomicLong SECTION_SEQUENCE=new AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<Long,Long> SECTIONS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Long,Long> COLORS=new HashMap<>();
    private static final LinkedHashMap<CompositeBlockEntity,Cached> CACHE=new LinkedHashMap<>(256,.75f,true);
    private static ClientWorld world;
    private static long bytes;
    private static long bakedOwners,bakedVertices;
    private static int resources;
    private record Cached(BlockState state,BlockState originalState,NbtCompound payload,long revision,int resources,int overlay,Mesh mesh,long[] sections,long colorChunk,long colorStamp){
        boolean current(){if(colorChunk!=Long.MIN_VALUE&&COLORS.getOrDefault(colorChunk,0L)!=colorStamp)return false;for(int i=0;i<sections.length;i+=2)if(SECTIONS.getOrDefault(sections[i],0L)!=sections[i+1])return false;return true;}
    }
    private record Mesh(float[] vertices,int[] lights,int overlay){long bytes(){return vertices.length*4L+lights.length*4L;}}
    private CompositeWorldMesh(){}
    public static void changed(){LIGHT_REVISION.incrementAndGet();}
    public static void sectionChanged(long section){SECTIONS.put(section,SECTION_SEQUENCE.incrementAndGet());}
    public static void chunkLoaded(ClientWorld loadedWorld,ChunkPos chunk){
        // Packet block data replaces whole sections without updateListeners.
        // Include previously missing neighbours, whose cached stamp was zero.
        for(int y=loadedWorld.getBottomSectionCoord();y<loadedWorld.getTopSectionCoord();y++)
            sectionChanged(ChunkSectionPos.asLong(chunk.x,y,chunk.z));
    }
    public static void chunkColorsChanged(ChunkPos chunk){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)COLORS.put(ChunkPos.toLong(chunk.x+x,chunk.z+z),SECTION_SEQUENCE.incrementAndGet());}
    public static void reload(){resources++;CACHE.clear();bytes=0;changed();}
    public static void disconnect(){CACHE.clear();SECTIONS.clear();COLORS.clear();bytes=0;world=null;changed();}
    public static void forgetChunk(ChunkPos pos){
        var it=CACHE.entrySet().iterator();while(it.hasNext()){
            var entry=it.next();var cached=entry.getValue();boolean affected=new ChunkPos(entry.getKey().getPos()).equals(pos);
            for(int i=0;!affected&&i<cached.sections.length;i+=2)
                affected=ChunkSectionPos.unpackX(cached.sections[i])==pos.x&&ChunkSectionPos.unpackZ(cached.sections[i])==pos.z;
            if(affected){bytes-=cached.mesh.bytes();it.remove();}
        }
        // Eviction also handles an initial dependency captured at stamp zero:
        // deleting a missing version alone would leave zero equal to zero.
        SECTIONS.keySet().removeIf(key->ChunkSectionPos.unpackX(key)==pos.x&&ChunkSectionPos.unpackZ(key)==pos.z);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)COLORS.remove(ChunkPos.toLong(pos.x+x,pos.z+z));
    }
    public static Map<String,Long> metrics(){return Map.of("cachedOwners",(long)CACHE.size(),"cachedBytes",bytes,"cacheByteLimit",MAX_BYTES,"lightRevision",LIGHT_REVISION.get(),"sectionRevisions",(long)SECTIONS.size(),"bakedOwners",bakedOwners,"bakedVertices",bakedVertices);}

    static void render(CompositeBlockEntity entity,CompositeRootBlock block,BlockState state,NbtCompound payload,
                       MatrixStack matrices,VertexConsumerProvider consumers,int overlay){
        var client=MinecraftClient.getInstance();if(client.world==null)return;
        if(world!=client.world){CACHE.clear();SECTIONS.clear();COLORS.clear();bytes=0;world=client.world;}
        long revision=LIGHT_REVISION.get();var known=CACHE.get(entity);
        var originalState=entity.getCachedState();var originalPayload=entity.sourceRenderPayload();
        if(known==null||known.state!=state||known.originalState!=originalState||known.payload!=originalPayload||known.revision!=revision||known.resources!=resources||known.overlay!=overlay||!known.current()){
            Map<Long,Long> dependencies=new HashMap<>();var mesh=bake(client,entity,block,state,payload,overlay,dependencies);
            bakedOwners++;bakedVertices+=mesh.lights.length;
            if(known!=null)bytes-=known.mesh.bytes();
            long[] stamps=new long[dependencies.size()*2];int stamp=0;for(var section:dependencies.entrySet()){stamps[stamp++]=section.getKey();stamps[stamp++]=section.getValue();}
            long colorChunk=RemainingSourceTint.biomeTinted(block)?new ChunkPos(entity.getPos()).toLong():Long.MIN_VALUE;
            known=new Cached(state,originalState,originalPayload,revision,resources,overlay,mesh,stamps,colorChunk,COLORS.getOrDefault(colorChunk,0L));CACHE.put(entity,known);bytes+=mesh.bytes();
            var iterator=CACHE.entrySet().iterator();
            while(bytes>MAX_BYTES&&CACHE.size()>1&&iterator.hasNext()){var old=iterator.next();bytes-=old.getValue().mesh.bytes();iterator.remove();}
        }
        var consumer=consumers.getBuffer(block.spec.translucent?RenderLayer.getTranslucent():RenderLayer.getCutout());
        var position=matrices.peek().getPositionMatrix();var normal=matrices.peek().getNormalMatrix();
        float[] v=known.mesh.vertices;int[] lights=known.mesh.lights;
        float lastNx=Float.NaN,lastNy=Float.NaN,lastNz=Float.NaN,transformedNx=0,transformedNy=0,transformedNz=0;
        // Positions are relative to the owner cell. Camera translation is applied
        // once here; neither geometry nor lighting depends on the camera.
        for(int n=0,i=0;n<v.length;n+=12,i++){
            float x=v[n],y=v[n+1],z=v[n+2],nx=v[n+9],ny=v[n+10],nz=v[n+11];
            // Baked face vertices share a normal. The matrix stays fixed for
            // this owner draw; varying/custom normals still take the exact
            // original transform. No geometry, culling or lighting is changed.
            if(nx!=lastNx||ny!=lastNy||nz!=lastNz){
                transformedNx=normal.m00()*nx+normal.m10()*ny+normal.m20()*nz;
                transformedNy=normal.m01()*nx+normal.m11()*ny+normal.m21()*nz;
                transformedNz=normal.m02()*nx+normal.m12()*ny+normal.m22()*nz;
                lastNx=nx;lastNy=ny;lastNz=nz;
            }
            consumer.vertex(position.m00()*x+position.m10()*y+position.m20()*z+position.m30(),
                            position.m01()*x+position.m11()*y+position.m21()*z+position.m31(),
                            position.m02()*x+position.m12()*y+position.m22()*z+position.m32(),
                            v[n+3],v[n+4],v[n+5],v[n+6],v[n+7],v[n+8],overlay,lights[i],
                            transformedNx,transformedNy,transformedNz);
        }
    }
    private static Mesh bake(MinecraftClient client,CompositeBlockEntity entity,CompositeRootBlock block,BlockState state,NbtCompound payload,int overlay,Map<Long,Long> dependencies){
        var recorded=new Recorder(overlay,RemainingSourceTint.color(block,client,entity.getPos()));var matrices=new MatrixStack();var shift=CompositeSourceShift.world(state,payload);
        matrices.translate(shift.x(),entity.mountY()+shift.y(),shift.z());
        rotate(matrices,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
        if(ThinWindowRootBlock.mount(state)!=ThinWindowRootBlock.Mount.VERTICAL){
            rotate(matrices,.5,.5,.5,ThinWindowRootBlock.mount(state)==ThinWindowRootBlock.Mount.FLOOR?90:-90,true);
            rotate(matrices,.5,.5,.5,block.spec.variant(state).mountAlignmentYaw(),false);
        }
        var random=net.minecraft.util.math.random.Random.create(42);
        for(var part:ImportedStairLegacyArt.selectedParts(block,state,payload)){
            matrices.push();matrices.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
            rotate(matrices,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
            rotate(matrices,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
            rotate(matrices,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
            var model=client.getBakedModelManager().getModel(part.model());
            if(block.spec.id.getPath().startsWith("source_")&&part.model().getPath().startsWith("remaining/source_emissive/")){
                int tint=RemainingSourceTint.color(block,client,entity.getPos());
                client.getBlockRenderManager().getModelRenderer().render(matrices.peek(),recorded,state,model,((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay);
            }else{
                var carrier=ImportedSourceLightingV127.renderCarrier(block.spec,state);
                var view=new PartView(client.world,entity.getPos(),matrices.peek(),dependencies,
                    block.spec.id.getPath().startsWith("source_")&&payload.getInt(ImportedWholeSourceOpticsV127.CONTEXT)!=1?entity.resident():null);
                // The model's axes must also rotate its neighbour/light queries.
                // Keeping a world-space matrix but unrotated lighting breaks doors
                // and mounted panes. Culling is intentionally false for BER parts.
                // Native source offsets were already preserved in SourceShift.
                // Cancel the renderer's automatic offset, avoiding a second move.
                matrices.push();var offset=carrier.getModelOffset(view,BlockPos.ORIGIN);matrices.translate(-offset.x,-offset.y,-offset.z);
                client.getBlockRenderManager().getModelRenderer().render(view,model,carrier,BlockPos.ORIGIN,matrices,recorded,false,random,42,overlay);matrices.pop();
            }
            matrices.pop();
        }
        return recorded.finish();
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}

    /** A source part's local block neighbourhood, projected onto real world axes. */
    private static final class PartView implements BlockRenderView,ProjectedBlockView {
        private final ClientWorld delegate;private final BlockPos root;private final Matrix4f transform;private final Matrix3f normals;private final Map<Long,Long> dependencies;
        private final dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner singleSourceOwner;
        PartView(ClientWorld delegate,BlockPos root,MatrixStack.Entry matrix,Map<Long,Long> dependencies,dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner singleSourceOwner){this.delegate=delegate;this.root=root;this.dependencies=dependencies;this.singleSourceOwner=singleSourceOwner;transform=new Matrix4f(matrix.getPositionMatrix());normals=new Matrix3f(matrix.getNormalMatrix());}
        @Override public World backingWorld(){return delegate;}
        @Override public BlockPos worldPosition(BlockPos pos){return at(pos);}
        private BlockPos at(BlockPos pos){var point=new Vector3f(pos.getX()+.5f,pos.getY()+.5f,pos.getZ()+.5f);transform.transformPosition(point);var result=BlockPos.ofFloored(root.getX()+point.x,root.getY()+point.y,root.getZ()+point.z);long section=ChunkSectionPos.from(result).asLong();dependencies.putIfAbsent(section,SECTIONS.getOrDefault(section,0L));return result;}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return delegate.getBlockEntity(at(pos));}
        @Override public BlockState getBlockState(BlockPos pos){
            var point=at(pos);var actual=delegate.getBlockState(point);
            // Self masking is disabled for owners with explicit whole-source
            // optics. Any published opaque helper here therefore also belongs
            // to a foreign whole assembly, whose real shadow must survive.
            if(actual.getBlock() instanceof CompositeCellBlock&&CompositeShapeSnapshots.sourceCarrierOpacity(delegate,point)==15){
                var carrier=ImportedWholeSourceOpticsV127.opaqueRenderCarrier();if(carrier!=null)return carrier;
            }
            if(singleSourceOwner!=null){
                // A source helper can share a real neighbouring native/root
                // block. Preserve that block's shadow; ignore only our own
                // synthetic carrier, never another object's resident root.
                if(actual.getBlock() instanceof CompositeCellBlock
                    ||dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.marker(actual)
                    ||point.equals(CompositeData.pos(singleSourceOwner.root())))
                    for(var contribution:CompositeShapeSnapshots.contributions(delegate,point))if(contribution.owner().equals(singleSourceOwner))return net.minecraft.block.Blocks.AIR.getDefaultState();
            }
            if(actual.getBlock() instanceof InvisibleSourceCarrierV127)return InvisibleSourceCarrierV127.source(actual);
            if(actual.getBlock() instanceof SourceFallbackCarrierV1271)return SourceFallbackCarrierV1271.source(actual);
            if(actual.getBlock() instanceof CompositeRootBlock block)return ImportedSourceLightingV127.renderCarrier(block.spec,actual);
            return actual;
        }
        @Override public FluidState getFluidState(BlockPos pos){return delegate.getFluidState(at(pos));}
        @Override public int getHeight(){return delegate.getHeight();}
        @Override public int getBottomY(){return delegate.getBottomY();}
        @Override public LightingProvider getLightingProvider(){return delegate.getLightingProvider();}
        @Override public int getLightLevel(LightType type,BlockPos pos){return delegate.getLightLevel(type,at(pos));}
        @Override public int getColor(BlockPos pos,ColorResolver resolver){return delegate.getColor(at(pos),resolver);}
        @Override public float getBrightness(Direction face,boolean shade){var direction=new Vector3f(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());normals.transform(direction);return delegate.getBrightness(Direction.getFacing(direction.x,direction.y,direction.z),shade);}
    }
    private static final class Recorder implements VertexConsumer {
        private float[] data=new float[768];private int[] light=new int[64];private int size;private final int overlay;
        private final float sourceRed,sourceGreen,sourceBlue;
        private float x,y,z,r=1,g=1,b=1,a=1,u,v,nx,ny,nz;private int packed;
        Recorder(int overlay,int tint){this.overlay=overlay;sourceRed=((tint>>16)&255)/255f;sourceGreen=((tint>>8)&255)/255f;sourceBlue=(tint&255)/255f;}
        @Override public void quad(MatrixStack.Entry entry,BakedQuad quad,float[] brightness,float r,float g,float b,int[] lights,int overlay,boolean useColor){
            VertexConsumer.super.quad(entry,quad,brightness,quad.hasColor()?sourceRed:r,quad.hasColor()?sourceGreen:g,quad.hasColor()?sourceBlue:b,lights,overlay,useColor);
        }
        Mesh finish(){return new Mesh(Arrays.copyOf(data,size*12),Arrays.copyOf(light,size),overlay);}
        @Override public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int packed,float nx,float ny,float nz){
            if(size==light.length){light=Arrays.copyOf(light,size*2);data=Arrays.copyOf(data,size*24);}
            int n=size*12;data[n]=x;data[n+1]=y;data[n+2]=z;data[n+3]=r;data[n+4]=g;data[n+5]=b;data[n+6]=a;data[n+7]=u;data[n+8]=v;data[n+9]=nx;data[n+10]=ny;data[n+11]=nz;light[size++]=packed;
        }
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=(float)x;this.y=(float)y;this.z=(float)z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){this.r=r/255f;this.g=g/255f;this.b=b/255f;this.a=a/255f;return this;}
        @Override public VertexConsumer texture(float u,float v){this.u=u;this.v=v;return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){packed=u|(v<<16);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        @Override public void next(){vertex(x,y,z,r,g,b,a,u,v,overlay,packed,nx,ny,nz);}
        @Override public void fixedColor(int r,int g,int b,int a){color(r,g,b,a);}
        @Override public void unfixColor(){}
    }
}
