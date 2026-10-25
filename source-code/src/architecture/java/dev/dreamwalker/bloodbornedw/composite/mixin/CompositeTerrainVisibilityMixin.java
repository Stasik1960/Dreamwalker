package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeTerrainUpdates;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native occlusion traversal knows only 16-cube sections. A model can extend
 * from an unreached root section into a reached one. Append that native section
 * when its expanded bounds overlap a visible reached section; depth, buffers,
 * sorting and rebuilding remain entirely vanilla. */
@Mixin(WorldRenderer.class)
public abstract class CompositeTerrainVisibilityMixin {
    @Shadow private BuiltChunkStorage chunks;
    @Shadow @Final private ObjectArrayList<Object> chunkInfos;
    @Unique private static Constructor<?> dw$terrainInfoConstructor;

    @Inject(method="applyFrustum",at=@At("TAIL"))
    private void dw$visibleOverhangs(Frustum frustum,CallbackInfo callback){
        if(chunks==null||chunkInfos.isEmpty())return;
        Set<Long> reached=new HashSet<>();
        for(Object info:chunkInfos){BlockPos p=((CompositeTerrainChunkInfoAccess)info).terrainChunk().getOrigin();reached.add(ChunkSectionPos.asLong(p.getX()>>4,p.getY()>>4,p.getZ()>>4));}
        long tested=0,added=0;
        for(var chunk:((CompositeTerrainChunkStorageAccess)chunks).terrainChunks()){
            BlockPos p=chunk.getOrigin();long key=ChunkSectionPos.asLong(p.getX()>>4,p.getY()>>4,p.getZ()>>4);
            if(reached.contains(key)||!chunk.shouldBuild())continue;
            Box bounds=chunk.getBoundingBox();tested++;
            if(bounds.minX>=p.getX()&&bounds.minY>=p.getY()&&bounds.minZ>=p.getZ()&&bounds.maxX<=p.getX()+16&&bounds.maxY<=p.getY()+16&&bounds.maxZ<=p.getZ()+16)continue;
            if(!frustum.isVisible(bounds)||!dw$touchesReached(bounds,reached))continue;
            chunkInfos.add(dw$nativeInfo(chunk));added++;
        }
        if(added>0){var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();chunkInfos.sort(Comparator.comparingDouble(info->{var p=((CompositeTerrainChunkInfoAccess)info).terrainChunk().getOrigin();double x=p.getX()+8-camera.x,y=p.getY()+8-camera.y,z=p.getZ()+8-camera.z;return x*x+y*y+z*z;}));}
        CompositeTerrainUpdates.visibility(tested,added);
    }
    @Unique private static boolean dw$touchesReached(Box bounds,Set<Long> reached){
        int x0=MathHelper.floor(bounds.minX)>>4,x1=MathHelper.floor(Math.nextDown(bounds.maxX))>>4;
        int y0=MathHelper.floor(bounds.minY)>>4,y1=MathHelper.floor(Math.nextDown(bounds.maxY))>>4;
        int z0=MathHelper.floor(bounds.minZ)>>4,z1=MathHelper.floor(Math.nextDown(bounds.maxZ))>>4;
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)if(reached.contains(ChunkSectionPos.asLong(x,y,z)))return true;
        return false;
    }
    @Unique private Object dw$nativeInfo(ChunkBuilder.BuiltChunk chunk){
        try{
            // Resolve from an existing native object, so production remapping
            // does not depend on a private class name string. Class metadata is
            // shared once; no per-owner or per-world registry is introduced.
            if(dw$terrainInfoConstructor==null){dw$terrainInfoConstructor=chunkInfos.get(0).getClass().getDeclaredConstructor(ChunkBuilder.BuiltChunk.class,Direction.class,int.class);dw$terrainInfoConstructor.setAccessible(true);}
            return dw$terrainInfoConstructor.newInstance(chunk,null,0);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot append a native overhanging terrain section",failure);}
    }
}
