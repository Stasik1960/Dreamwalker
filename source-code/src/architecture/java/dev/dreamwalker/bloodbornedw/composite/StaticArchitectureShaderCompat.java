package dev.dreamwalker.bloodbornedw.composite;
import java.lang.invoke.*;
import net.fabricmc.loader.api.FabricLoader;
/** Optional public Iris API; no Iris dependency is imposed on the base pack. */
final class StaticArchitectureShaderCompat {
    private static final boolean IRIS=FabricLoader.getInstance().isModLoaded("iris");
    private static final MethodHandle ACTIVE=resolve();
    private StaticArchitectureShaderCompat(){}
    private static MethodHandle resolve(){
        if(!IRIS)return null;
        try{
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");Object instance=api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().unreflect(api.getMethod("isShaderPackInUse")).bindTo(instance).asType(MethodType.methodType(boolean.class));
        }catch(ReflectiveOperationException|LinkageError unavailable){return null;}
    }
    static boolean active(){
        if(!IRIS)return false;if(ACTIVE==null)return true;
        try{return (boolean)ACTIVE.invokeExact();}catch(Throwable unavailable){return true;}
    }
}
