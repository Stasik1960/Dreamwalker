package dev.dreamwalker.bloodbornedw.composite;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Render-thread-only CUTOUT batches for immutable owner meshes.
 *
 * <p>Owners are collected only after their existing exact visual-bounds test
 * has admitted the block entity. A native 16^3 section is merely the upload
 * unit: it never substitutes a coarser visibility test. Translucent owners do
 * not enter this path and keep their original submission order.</p>
 */
final class CompositeGpuBatch {
    private static final long MAX_GPU_BYTES=192L*1024*1024;
    private static final float MATRIX_EPSILON=.002f;
    private static final RenderLayer LAYER=RenderLayer.getCutout();
    private static final int VERTEX_BYTES=LAYER.getVertexFormat().getVertexSizeByte();
    private static final Map<Long,FrameBatch> QUEUED=new HashMap<>();
    private static final LinkedHashMap<Long,GpuBatch> GPU=new LinkedHashMap<>(128,.75f,true);
    private static final WeakHashMap<CompositeBlockEntity,OwnerSlot> RETAINED=new WeakHashMap<>();
    private static boolean initialized;
    private static long queuedBytes,gpuBytes,uploads,draws,retainedHits;

    private static final class Member {
        final CompositeBlockEntity owner;CompositeWorldMesh.Mesh mesh;CompositeWorldMesh.CacheSignature signature;final int x,y,z;long bytes;
        Member(CompositeBlockEntity owner,CompositeWorldMesh.Mesh mesh,CompositeWorldMesh.CacheSignature signature,int x,int y,int z,long bytes){this.owner=owner;this.mesh=mesh;this.signature=signature;this.x=x;this.y=y;this.z=z;this.bytes=bytes;}
    }
    private static final class FrameBatch {
        final long section;final ArrayList<Member> members=new ArrayList<>();long bytes;
        FrameBatch(long section){this.section=section;}
    }
    private static final class GpuBatch {
        final long section;final VertexBuffer buffer;final WeakReference<CompositeBlockEntity>[] owners;final CompositeWorldMesh.CacheSignature[] signatures;final long[] memberBytes;final long bytes;
        boolean live=true;
        GpuBatch(long section,VertexBuffer buffer,WeakReference<CompositeBlockEntity>[] owners,CompositeWorldMesh.CacheSignature[] signatures,long[] memberBytes,long bytes){this.section=section;this.buffer=buffer;this.owners=owners;this.signatures=signatures;this.memberBytes=memberBytes;this.bytes=bytes;}
        boolean matches(List<Member> members){
            if(owners.length!=members.size())return false;
            var world=MinecraftClient.getInstance().world;if(world==null)return false;
            for(int i=0;i<owners.length;i++){var owner=members.get(i).owner;if(owners[i].get()!=owner||owner.isRemoved()||world.getBlockEntity(owner.getPos())!=owner||signatures[i]!=members.get(i).signature)return false;}
            return true;
        }
    }
    private record OwnerSlot(GpuBatch batch,CompositeWorldMesh.CacheSignature signature,long bytes){}
    private CompositeGpuBatch(){}

    private static void initialize(){
        if(initialized)return;initialized=true;
        WorldRenderEvents.START.register(context->beginFrame());
        // Fabric 3.0.9 documents this point after block entities and other
        // non-translucent submissions, while the translucent world pass is
        // still pending. Direct framebuffer drawing is expected here.
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(CompositeGpuBatch::drawQueued);
        WorldRenderEvents.END.register(context->discardFrame());
    }

    static boolean enqueue(CompositeBlockEntity owner,CompositeWorldMesh.Mesh mesh,CompositeWorldMesh.CacheSignature signature,MatrixStack matrices,VertexConsumerProvider consumers){
        // Iris manages extra vertex attributes and a separate shadow pass.
        // Keep those submissions on the existing native BE consumer path.
        if(StaticArchitectureShaderCompat.active())return false;
        initialize();
        var client=MinecraftClient.getInstance();
        if(client.world==null||mesh==null||signature==null||mesh.lights().length==0||mesh.vertices().length!=mesh.lights().length*12)return false;
        // Crumbling/outline/special providers carry render semantics outside
        // the immutable mesh. Preserve their original immediate submission.
        if(consumers!=client.getBufferBuilders().getEntityVertexConsumers())return false;
        Matrix4f position=matrices.peek().getPositionMatrix();Matrix3f normal=matrices.peek().getNormalMatrix();
        var camera=client.gameRenderer.getCamera().getPos();var root=owner.getPos();
        if(!cameraTransform(position,normal,root.getX()-camera.x,root.getY()-camera.y,root.getZ()-camera.z))return false;
        long section=ChunkSectionPos.from(root).asLong();FrameBatch batch=QUEUED.computeIfAbsent(section,FrameBatch::new);
        for(var iterator=batch.members.iterator();iterator.hasNext();){Member member=iterator.next();if(member.owner==owner){
            if(member.signature==signature)return true;
            iterator.remove();batch.bytes-=member.bytes;queuedBytes-=member.bytes;break;
        }}
        long added=(long)mesh.lights().length*VERTEX_BYTES;
        if(added<=0||added>MAX_GPU_BYTES||queuedBytes>MAX_GPU_BYTES-added)return false;
        int sx=ChunkSectionPos.unpackX(section)<<4,sy=ChunkSectionPos.unpackY(section)<<4,sz=ChunkSectionPos.unpackZ(section)<<4;
        batch.members.add(new Member(owner,mesh,signature,root.getX()-sx,root.getY()-sy,root.getZ()-sz,added));batch.bytes+=added;queuedBytes+=added;
        return true;
    }

    static boolean enqueueRetained(CompositeBlockEntity owner,net.minecraft.block.BlockState state,net.minecraft.block.BlockState originalState,net.minecraft.nbt.NbtCompound payload,long revision,int resources,int overlay,MatrixStack matrices,VertexConsumerProvider consumers){
        if(StaticArchitectureShaderCompat.active())return false;
        initialize();var client=MinecraftClient.getInstance();if(client.world==null||consumers!=client.getBufferBuilders().getEntityVertexConsumers())return false;
        OwnerSlot slot=RETAINED.get(owner);if(slot==null||GPU.get(slot.batch.section)!=slot.batch||!slot.signature.matches(state,originalState,payload,revision,resources,overlay)){if(slot!=null)RETAINED.remove(owner);return false;}
        var root=owner.getPos();long section=ChunkSectionPos.from(root).asLong();if(section!=slot.batch.section)return false;
        var camera=client.gameRenderer.getCamera().getPos();if(!cameraTransform(matrices.peek().getPositionMatrix(),matrices.peek().getNormalMatrix(),root.getX()-camera.x,root.getY()-camera.y,root.getZ()-camera.z))return false;
        FrameBatch frame=QUEUED.computeIfAbsent(section,FrameBatch::new);
        for(Member member:frame.members)if(member.owner==owner)return member.signature==slot.signature;
        if(slot.bytes<=0||slot.bytes>MAX_GPU_BYTES||queuedBytes>MAX_GPU_BYTES-slot.bytes)return false;
        int sx=ChunkSectionPos.unpackX(section)<<4,sy=ChunkSectionPos.unpackY(section)<<4,sz=ChunkSectionPos.unpackZ(section)<<4;
        frame.members.add(new Member(owner,null,slot.signature,root.getX()-sx,root.getY()-sy,root.getZ()-sz,slot.bytes));frame.bytes+=slot.bytes;queuedBytes+=slot.bytes;retainedHits++;return true;
    }

    /** Read retained metadata without touching the GPU LRU; enqueueRetained does
     * the access-order update only after exact cached visibility admits it. */
    static CompositeWorldMesh.CacheSignature retainedSignature(CompositeBlockEntity owner,net.minecraft.block.BlockState originalState,net.minecraft.nbt.NbtCompound payload,long revision,int resources,int overlay,MatrixStack matrices,VertexConsumerProvider consumers){
        if(StaticArchitectureShaderCompat.active())return null;
        initialize();var client=MinecraftClient.getInstance();if(client.world==null||owner==null||owner.isRemoved()||client.world.getBlockEntity(owner.getPos())!=owner||consumers!=client.getBufferBuilders().getEntityVertexConsumers())return null;
        OwnerSlot slot=RETAINED.get(owner);if(slot==null||!slot.batch.live||!slot.signature.matchesOriginal(originalState,payload,revision,resources,overlay))return null;
        var root=owner.getPos();if(ChunkSectionPos.from(root).asLong()!=slot.batch.section)return null;var camera=client.gameRenderer.getCamera().getPos();
        return cameraTransform(matrices.peek().getPositionMatrix(),matrices.peek().getNormalMatrix(),root.getX()-camera.x,root.getY()-camera.y,root.getZ()-camera.z)?slot.signature:null;
    }

    /** Vanilla 1.20.1 supplies R_camera, then the BE dispatcher post-multiplies
     * T(root-camera). Any scale, shear, perspective or extra renderer transform
     * is uncertain and deliberately retains the old per-owner draw. */
    private static boolean cameraTransform(Matrix4f p,Matrix3f n,double x,double y,double z){
        if(!near(p.m03(),0)||!near(p.m13(),0)||!near(p.m23(),0)||!near(p.m33(),1))return false;
        float[] a={p.m00(),p.m01(),p.m02(),p.m10(),p.m11(),p.m12(),p.m20(),p.m21(),p.m22()};
        float[] b={n.m00(),n.m01(),n.m02(),n.m10(),n.m11(),n.m12(),n.m20(),n.m21(),n.m22()};
        for(int i=0;i<a.length;i++)if(!Float.isFinite(a[i])||!near(a[i],b[i]))return false;
        if(!unit(a[0],a[1],a[2])||!unit(a[3],a[4],a[5])||!unit(a[6],a[7],a[8])
            ||!near(dot(a[0],a[1],a[2],a[3],a[4],a[5]),0)
            ||!near(dot(a[0],a[1],a[2],a[6],a[7],a[8]),0)
            ||!near(dot(a[3],a[4],a[5],a[6],a[7],a[8]),0))return false;
        double tx=p.m00()*x+p.m10()*y+p.m20()*z,ty=p.m01()*x+p.m11()*y+p.m21()*z,tz=p.m02()*x+p.m12()*y+p.m22()*z;
        return Double.isFinite(tx)&&Double.isFinite(ty)&&Double.isFinite(tz)
            &&Math.abs(p.m30()-tx)<=.01&&Math.abs(p.m31()-ty)<=.01&&Math.abs(p.m32()-tz)<=.01;
    }
    private static boolean unit(float x,float y,float z){return near(x*x+y*y+z*z,1);}
    private static float dot(float x,float y,float z,float X,float Y,float Z){return x*X+y*Y+z*Z;}
    private static boolean near(float value,float expected){return Float.isFinite(value)&&Math.abs(value-expected)<=MATRIX_EPSILON;}

    private static double distanceSquared(long section,net.minecraft.util.math.Vec3d camera){double x=(ChunkSectionPos.unpackX(section)<<4)+8-camera.x,y=(ChunkSectionPos.unpackY(section)<<4)+8-camera.y,z=(ChunkSectionPos.unpackZ(section)<<4)+8-camera.z;return x*x+y*y+z*z;}

    private static void beginFrame(){QUEUED.clear();queuedBytes=0;}
    private static void discardFrame(){QUEUED.clear();queuedBytes=0;}

    private static void drawQueued(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context){
        if(QUEUED.isEmpty())return;
        RenderSystem.assertOnRenderThread();
        ArrayList<FrameBatch> batches=new ArrayList<>(QUEUED.values());
        var viewCamera=context.camera().getPos();
        batches.sort(Comparator.comparingDouble((FrameBatch batch)->distanceSquared(batch.section,viewCamera)).thenComparingLong(batch->batch.section));
        try{
            ArrayList<FrameBatch> fallback=new ArrayList<>();
            LAYER.startDrawing();
            try{
                var shader=RenderSystem.getShader();
                if(shader==null)fallback.addAll(batches);
                else for(FrameBatch frame:batches){
                    frame.members.sort(Comparator.comparingLong(member->member.owner.getPos().asLong()));
                    GpuBatch batch=GPU.get(frame.section);
                    if(batch==null||!batch.matches(frame.members))batch=upload(frame,batch);
                    if(batch==null){fallback.add(frame);continue;}
                    int x=ChunkSectionPos.unpackX(frame.section)<<4,y=ChunkSectionPos.unpackY(frame.section)<<4,z=ChunkSectionPos.unpackZ(frame.section)<<4;
                    var camera=context.camera().getPos();
                    Matrix4f model=new Matrix4f(context.matrixStack().peek().getPositionMatrix()).translate((float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z));
                    batch.buffer.bind();batch.buffer.draw(model,context.projectionMatrix(),shader);draws++;
                }
            }finally{
                VertexBuffer.unbind();LAYER.endDrawing();
            }
            if(!fallback.isEmpty())drawFallback(context,fallback);
        }finally{discardFrame();}
    }

    /** A failed native upload must not turn an accepted enqueue into a hole. */
    private static void drawFallback(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context,List<FrameBatch> batches){
        var immediate=MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();var consumer=immediate.getBuffer(LAYER);var camera=context.camera().getPos();
        Matrix4f position=context.matrixStack().peek().getPositionMatrix();Matrix3f normal=context.matrixStack().peek().getNormalMatrix();
        for(FrameBatch frame:batches){
            int sx=ChunkSectionPos.unpackX(frame.section)<<4,sy=ChunkSectionPos.unpackY(frame.section)<<4,sz=ChunkSectionPos.unpackZ(frame.section)<<4;
            for(Member member:frame.members){if(!resolve(member))continue;float baseX=(float)(sx+member.x-camera.x),baseY=(float)(sy+member.y-camera.y),baseZ=(float)(sz+member.z-camera.z);float[] v=member.mesh.vertices();int[] lights=member.mesh.lights();
                for(int n=0,i=0;n<v.length;n+=12,i++){
                    float x=baseX+v[n],y=baseY+v[n+1],z=baseZ+v[n+2],nx=v[n+9],ny=v[n+10],nz=v[n+11];
                    consumer.vertex(position.m00()*x+position.m10()*y+position.m20()*z+position.m30(),position.m01()*x+position.m11()*y+position.m21()*z+position.m31(),position.m02()*x+position.m12()*y+position.m22()*z+position.m32(),v[n+3],v[n+4],v[n+5],v[n+6],v[n+7],v[n+8],member.mesh.overlay(),lights[i],normal.m00()*nx+normal.m10()*ny+normal.m20()*nz,normal.m01()*nx+normal.m11()*ny+normal.m21()*nz,normal.m02()*nx+normal.m12()*ny+normal.m22()*nz);
                }
            }
        }
        immediate.draw(LAYER);
    }

    private static GpuBatch upload(FrameBatch frame,GpuBatch previous){
        long actualBytes=0;for(var iterator=frame.members.iterator();iterator.hasNext();){var member=iterator.next();if(!resolve(member)){iterator.remove();continue;}actualBytes+=member.bytes;}
        frame.bytes=actualBytes;if(frame.members.isEmpty()){if(previous!=null){GPU.remove(frame.section);gpuBytes-=previous.bytes;retire(previous);previous.buffer.close();}return null;}
        if(frame.bytes>MAX_GPU_BYTES)return null;
        if(previous!=null){GPU.remove(frame.section);gpuBytes-=previous.bytes;retire(previous);previous.buffer.close();}
        evictFor(frame.bytes);
        if(gpuBytes>MAX_GPU_BYTES-frame.bytes)return null;
        BufferBuilder builder=new BufferBuilder(Math.max(256,Math.min(LAYER.getExpectedBufferSize(),(int)Math.min(Integer.MAX_VALUE,frame.bytes))));
        builder.begin(LAYER.getDrawMode(),LAYER.getVertexFormat());
        for(Member member:frame.members){
            float[] vertices=member.mesh.vertices();int[] lights=member.mesh.lights();
            for(int n=0,i=0;n<vertices.length;n+=12,i++)builder.vertex(member.x+vertices[n],member.y+vertices[n+1],member.z+vertices[n+2],vertices[n+3],vertices[n+4],vertices[n+5],vertices[n+6],vertices[n+7],vertices[n+8],member.mesh.overlay(),lights[i],vertices[n+9],vertices[n+10],vertices[n+11]);
        }
        VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
        try{buffer.bind();buffer.upload(builder.end());}
        catch(RuntimeException failure){buffer.close();return null;}
        finally{VertexBuffer.unbind();}
        @SuppressWarnings("unchecked") WeakReference<CompositeBlockEntity>[] owners=(WeakReference<CompositeBlockEntity>[])new WeakReference<?>[frame.members.size()];
        CompositeWorldMesh.CacheSignature[] signatures=new CompositeWorldMesh.CacheSignature[frame.members.size()];long[] memberBytes=new long[frame.members.size()];
        for(int i=0;i<owners.length;i++){var member=frame.members.get(i);owners[i]=new WeakReference<>(member.owner);signatures[i]=member.signature;memberBytes[i]=member.bytes;}
        GpuBatch result=new GpuBatch(frame.section,buffer,owners,signatures,memberBytes,frame.bytes);GPU.put(frame.section,result);gpuBytes+=frame.bytes;
        for(int i=0;i<owners.length;i++){var owner=owners[i].get();if(owner!=null)RETAINED.put(owner,new OwnerSlot(result,signatures[i],memberBytes[i]));}
        uploads++;return result;
    }

    private static boolean resolve(Member member){
        var client=MinecraftClient.getInstance();if(client.world==null||member.owner.isRemoved()||client.world.getBlockEntity(member.owner.getPos())!=member.owner)return false;
        if(member.mesh!=null)return true;var resolved=CompositeWorldMesh.resolveMesh(member.owner,member.signature);
        if(resolved==null)resolved=CompositeWorldMesh.resolveCurrentMesh(member.owner,member.signature.overlay());
        if(resolved==null)return false;
        member.mesh=resolved.mesh();member.signature=resolved.signature();member.bytes=(long)member.mesh.lights().length*VERTEX_BYTES;
        return member.bytes>0&&member.mesh.vertices().length==member.mesh.lights().length*12;
    }

    private static void evictFor(long incoming){
        Iterator<Map.Entry<Long,GpuBatch>> iterator=GPU.entrySet().iterator();
        while(gpuBytes>MAX_GPU_BYTES-incoming&&iterator.hasNext()){
            GpuBatch batch=iterator.next().getValue();iterator.remove();gpuBytes-=batch.bytes;retire(batch);batch.buffer.close();
        }
    }

    private static void unlink(GpuBatch batch){
        for(var ownerRef:batch.owners){var owner=ownerRef.get();if(owner!=null){var link=RETAINED.get(owner);if(link!=null&&link.batch==batch)RETAINED.remove(owner);}}
    }
    private static void retire(GpuBatch batch){batch.live=false;unlink(batch);}

    static void forgetChunk(ChunkPos pos){
        var queued=QUEUED.entrySet().iterator();while(queued.hasNext()){var entry=queued.next();if(inChunk(entry.getKey(),pos)||entry.getValue().members.stream().anyMatch(member->dependsOn(member.signature,pos))){queuedBytes-=entry.getValue().bytes;queued.remove();}}
        ArrayList<VertexBuffer> closing=new ArrayList<>();var iterator=GPU.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();long section=entry.getKey();
            if(inChunk(section,pos)||Arrays.stream(entry.getValue().signatures).anyMatch(signature->dependsOn(signature,pos))){iterator.remove();gpuBytes-=entry.getValue().bytes;retire(entry.getValue());closing.add(entry.getValue().buffer);}
        }
        close(closing);
    }
    private static boolean inChunk(long section,ChunkPos pos){return ChunkSectionPos.unpackX(section)==pos.x&&ChunkSectionPos.unpackZ(section)==pos.z;}
    private static boolean dependsOn(CompositeWorldMesh.CacheSignature signature,ChunkPos pos){for(int i=0;i<signature.sections().length;i+=2)if(inChunk(signature.sections()[i],pos))return true;return false;}

    static void clear(){
        ArrayList<VertexBuffer> closing=new ArrayList<>();for(GpuBatch batch:GPU.values()){batch.live=false;closing.add(batch.buffer);}
        GPU.clear();RETAINED.clear();QUEUED.clear();gpuBytes=0;queuedBytes=0;close(closing);
    }
    private static void close(List<VertexBuffer> buffers){
        if(buffers.isEmpty())return;
        if(RenderSystem.isOnRenderThreadOrInit())for(VertexBuffer buffer:buffers)buffer.close();
        else for(VertexBuffer buffer:buffers)RenderSystem.recordRenderCall(buffer::close);
    }
    static long uploads(){return uploads;}static long draws(){return draws;}static long bytes(){return gpuBytes;}static long limit(){return MAX_GPU_BYTES;}static long retainedHits(){return retainedHits;}
}
