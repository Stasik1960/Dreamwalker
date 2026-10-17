package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BasicBakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** NEW-source visibility only: bounds of the actual emitted baked vertices.
 * No physical/selection geometry, owner UUID, coordinates or world enter the cache. */
public final class CompositeVisualBoundsV127 {
    private static final int LIMIT=2048;
    private static final double EPSILON=1.0/256;
    private record Key(BlockState state,List<CompositeSpec.Part> parts){
        @Override public int hashCode(){return 31*System.identityHashCode(state)+System.identityHashCode(parts);}
        @Override public boolean equals(Object value){return value instanceof Key other&&state==other.state&&parts==other.parts;}
    }
    private record Entry(List<BakedModel> models,Optional<Box> bounds){}
    private static final LinkedHashMap<Key,Entry> CACHE=new LinkedHashMap<>(64,.75f,true);
    private static BakedModel bakeEpoch;
    private static World activeWorld;
    private static Frustum activeFrustum;
    private static Vec3d activeCamera;
    private static double activeDistance;
    private static boolean initialized;
    private static long hits,misses,evictions,epochClears,modelChanges,culled,distanceCulled,visible,failOpen,bypassed;
    private CompositeVisualBoundsV127(){}

    public static void initialize(){
        if(initialized)return;initialized=true;
        // START has no valid frustum. Never carry a prior camera/world into a new frame.
        WorldRenderEvents.START.register(context->clearFrame());
        WorldRenderEvents.BEFORE_ENTITIES.register(context->{refreshEpoch();activeWorld=context.world();activeFrustum=context.frustum();activeCamera=context.camera().getPos();activeDistance=(MinecraftClient.getInstance().options.getViewDistance().getValue()+1)*16.0;});
        WorldRenderEvents.END.register(context->clearFrame());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
    }
    private static void clearFrame(){activeWorld=null;activeFrustum=null;activeCamera=null;activeDistance=0;}
    private static synchronized void clear(){CACHE.clear();bakeEpoch=null;clearFrame();}
    private static synchronized void refreshEpoch(){var epoch=MinecraftClient.getInstance().getBakedModelManager().getMissingModel();if(epoch!=bakeEpoch){if(bakeEpoch!=null)epochClears++;CACHE.clear();bakeEpoch=epoch;}}
    public static boolean supports(CompositeRootBlock block){return block.spec.simplePhysicalCollision&&block.spec.id.getNamespace().equals("bloodborne_dw")&&block.spec.id.getPath().startsWith("source_");}

    public static boolean visible(CompositeBlockEntity entity,CompositeRootBlock block,BlockState state,NbtCompound payload){
        if(!supports(block)){bypassed++;return true;}
        if(activeFrustum==null||activeWorld!=entity.getWorld()){failOpen++;return true;}
        try{
            var bounds=localBounds(block,state,payload);if(bounds.isEmpty()){failOpen++;return true;}
            var shift=CompositeSourceShift.world(state,payload);var root=entity.getPos();
            Box world=bounds.get().offset(root.getX()+shift.x(),root.getY()+entity.mountY()+shift.y(),root.getZ()+shift.z());
            if(activeCamera!=null&&!withinDistance(world,activeCamera,activeDistance)){distanceCulled++;return false;}
            boolean result=activeFrustum.isVisible(world);if(result)visible++;else culled++;return result;
        }catch(RuntimeException unsupported){failOpen++;return true;}
    }

    /** Compare the closest visual edge, not the installation cell. Tall objects stay visible. */
    public static boolean withinDistance(Box bounds,Vec3d camera,double distance){double x=Math.max(bounds.minX-camera.x,Math.max(0,camera.x-bounds.maxX));double z=Math.max(bounds.minZ-camera.z,Math.max(0,camera.z-bounds.maxZ));return x*x+z*z<=distance*distance;}

    /** Effective renderer state and selected legacy parts; outer instance translations are excluded. */
    public static synchronized Optional<Box> localBounds(CompositeRootBlock block,BlockState state,NbtCompound payload){
        if(!supports(block))return Optional.empty();
        var manager=MinecraftClient.getInstance().getBakedModelManager();
        // The manager object survives reload; its newly baked missing model is the epoch token.
        refreshEpoch();var epoch=bakeEpoch;
        var parts=ImportedStairLegacyArt.selectedParts(block,state,payload);var key=new Key(state,parts);var known=CACHE.get(key);
        if(known!=null){boolean same=known.models.size()==parts.size();for(int i=0;same&&i<parts.size();i++)same=known.models.get(i)==manager.getModel(model(parts.get(i),state));if(same){hits++;return known.bounds;}modelChanges++;CACHE.remove(key);}
        misses++;var models=new ArrayList<BakedModel>(parts.size());for(var part:parts)models.add(manager.getModel(model(part,state)));
        Optional<Box> result;
        try{result=calculate(block,state,parts,models,epoch);}catch(RuntimeException unsupported){result=Optional.empty();}
        if(CACHE.size()>=LIMIT){var oldest=CACHE.keySet().iterator();oldest.next();oldest.remove();evictions++;}
        CACHE.put(key,new Entry(List.copyOf(models),result));return result;
    }
    private static net.minecraft.util.Identifier model(CompositeSpec.Part part,BlockState state){return state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT?part.altModel():part.model();}
    private static Optional<Box> calculate(CompositeRootBlock block,BlockState state,List<CompositeSpec.Part> parts,List<BakedModel> models,BakedModel missing){
        if(parts.isEmpty()||parts.size()>4096)return Optional.empty();
        // Only native immutable JSON models. Unknown/custom/dynamic adapters render normally.
        for(var model:models)if(model==missing||model.getClass()!=BasicBakedModel.class||model.isBuiltin())return Optional.empty();
        var matrices=new MatrixStack();rotate(matrices,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
        if(ThinWindowRootBlock.mount(state)!=ThinWindowRootBlock.Mount.VERTICAL){rotate(matrices,.5,.5,.5,ThinWindowRootBlock.mount(state)==ThinWindowRootBlock.Mount.FLOOR?90:-90,true);rotate(matrices,.5,.5,.5,block.spec.variant(state).mountAlignmentYaw(),false);}
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;int vertices=0;
        var random=Random.create(42);var point=new Vector3f();var faces=new ArrayList<Direction>(List.of(Direction.values()));faces.add(null);
        for(int p=0;p<parts.size();p++){
            var part=parts.get(p);matrices.push();matrices.translate(part.offset().x()/16,part.offset().y()/16,part.offset().z()/16);
            rotate(matrices,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
            rotate(matrices,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
            rotate(matrices,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
            for(var face:faces){random.setSeed(42);for(var quad:models.get(p).getQuads(state,face,random)){
                int[] data=quad.getVertexData();if(data.length%4!=0||data.length<12||data.length>256)return Optional.empty();int stride=data.length/4;
                for(int vertex=0;vertex<4;vertex++){
                    if(++vertices>1_000_000)return Optional.empty();int i=vertex*stride;
                    point.set(Float.intBitsToFloat(data[i]),Float.intBitsToFloat(data[i+1]),Float.intBitsToFloat(data[i+2])).mulPosition(matrices.peek().getPositionMatrix());
                    if(!Float.isFinite(point.x)||!Float.isFinite(point.y)||!Float.isFinite(point.z))return Optional.empty();
                    minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);
                }
            }}matrices.pop();
        }
        return vertices==0?Optional.empty():Optional.of(new Box(minX-EPSILON,minY-EPSILON,minZ-EPSILON,maxX+EPSILON,maxY+EPSILON,maxZ+EPSILON));
    }
    private static void rotate(MatrixStack matrices,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrices.translate(x,y,z);matrices.multiply((pitch?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Y).rotationDegrees((float)degrees));matrices.translate(-x,-y,-z);}
    public static synchronized Map<String,Object> snapshot(){var result=new LinkedHashMap<String,Object>();result.put("scope","NEW_SOURCE_RENDERED_BAKED_VERTEX_AABB_ONLY");result.put("cacheEntries",CACHE.size());result.put("cacheLimit",LIMIT);result.put("cacheHits",hits);result.put("cacheMisses",misses);result.put("evictions",evictions);result.put("bakeEpochClears",epochClears);result.put("individualModelChanges",modelChanges);result.put("culledNewDraws",culled);result.put("distanceCulledNewDraws",distanceCulled);result.put("visibleNewDraws",visible);result.put("failOpenNewDraws",failOpen);result.put("originalBypasses",bypassed);result.put("frustumFrameActive",activeFrustum!=null);return Collections.unmodifiableMap(result);}
}
