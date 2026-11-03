package dev.dreamwalker.renderdiag;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.world.World;

/** Test-only reads of public core metrics shared byte-for-byte by baseline and
 * candidate. Never flushes, bakes, publishes, mutates fields or walks all meshes.
 */
public final class NativeQueues {
    private static final Gson JSON=new Gson();
    private static volatile Hooks hooks;
    private record Hooks(Method meshes,Method terrain,Method shapes){}
    private NativeQueues(){}
    private static Hooks hooks(){
        Hooks value=hooks;if(value!=null)return value;
        synchronized(NativeQueues.class){
            if(hooks==null)try{
                String prefix="dev.dreamwalker.bloodbornedw.composite.";
                hooks=new Hooks(Class.forName(prefix+"CompositeWorldMesh").getMethod("metrics"),
                    Class.forName(prefix+"CompositeTerrainUpdates").getMethod("metrics"),
                    Class.forName(prefix+"CompositeShapeSnapshots").getMethod("metrics",World.class));
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("Required common native readiness metrics unavailable",failure);}
            return hooks;
        }
    }
    private static Map<?,?> read(Method method,Object... arguments){
        try{
            Object result=method.invoke(null,arguments);
            if(!(result instanceof Map<?,?> map))throw new IllegalStateException("Native metrics did not return a map: "+method);
            return map;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native readiness metric read failed: "+method,failure);}
    }
    private static long count(Map<?,?> values,String key){
        Object value=values.get(key);
        if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()!=n.longValue())
            throw new IllegalStateException("Missing/invalid required native readiness counter: "+key+"="+value);
        return n.longValue();
    }
    public static JsonObject sample(World world){
        if(world==null)throw new IllegalArgumentException("A live client world is required for native readiness");
        Hooks h=hooks();Map<?,?> meshes=read(h.meshes()),terrain=read(h.terrain()),shapes=read(h.shapes(),world);
        JsonObject result=new JsonObject();
        result.addProperty("pendingMeshes",count(meshes,"pendingMeshes"));
        result.addProperty("pendingSectionUpdates",count(terrain,"pendingSectionUpdates"));
        Object whole=terrain.get("pendingWholeView");
        if(!(whole instanceof Boolean))throw new IllegalStateException("Missing/invalid native pendingWholeView");
        result.addProperty("pendingWholeView",(Boolean)whole);
        result.addProperty("shapeSnapshotPendingCells",count(shapes,"shapeSnapshotPendingCells"));
        result.addProperty("shapeSnapshotBatchDepth",count(shapes,"shapeSnapshotBatchDepth"));
        result.add("worldMesh",JSON.toJsonTree(meshes));result.add("terrainUpdates",JSON.toJsonTree(terrain));result.add("shapeSnapshots",JSON.toJsonTree(shapes));
        result.addProperty("idle",idle(result));return result;
    }
    public static boolean idle(JsonObject snapshot){
        return snapshot.get("pendingMeshes").getAsLong()==0&&snapshot.get("pendingSectionUpdates").getAsLong()==0
            &&!snapshot.get("pendingWholeView").getAsBoolean()&&snapshot.get("shapeSnapshotPendingCells").getAsLong()==0
            &&snapshot.get("shapeSnapshotBatchDepth").getAsLong()==0;
    }
}
