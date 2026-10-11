package qa.attach;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Read-only snapshot + native scheduleStop for exactly one QA client. No transformer. */
public final class CameraProbeAgent {
    public static void agentmain(String path, Instrumentation instrumentation) throws Exception {
        if (ProcessHandle.current().pid() != 128096) throw new IllegalStateException("Wrong target PID");
        if (Arrays.stream(instrumentation.getAllLoadedClasses()).noneMatch(c -> c.getName().equals("dev.dreamwalker.bloodbornedw.gallery.FlatGalleryClientCapture"))) throw new IllegalStateException("Expected QA gallery capture class is not loaded");
        Class<?> minecraft = Arrays.stream(instrumentation.getAllLoadedClasses()).filter(c -> c.getName().equals("net.minecraft.class_310")).findFirst().orElseThrow();
        Object client = minecraft.getMethod("method_1551").invoke(null);
        CountDownLatch done = new CountDownLatch(1);
        minecraft.getMethod("execute", Runnable.class).invoke(client, (Runnable) () -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("schema", "dreamwalker-client2-readonly-camera-probe-v1");
            result.put("pid", ProcessHandle.current().pid()); result.put("timeUtc", Instant.now().toString());
            result.put("thread", Thread.currentThread().getName());
            result.put("expectedCamera", List.of(42.625,64.78,1158.0,14.0362,16.795));
            result.put("noHotswapOrTransformers", true);
            try {
                result.put("windowFocused", call(client,"method_1569"));
                Object screen = field(client,"field_1755"); result.put("currentScreen",screen == null ? null : screen.getClass().getName());
                Object player = field(client,"field_1724"), world = field(client,"field_1687");
                result.put("playerPresent",player != null); result.put("worldPresent",world != null);
                if (player != null) {
                    result.put("playerXYZ",List.of(call(player,"method_23317"),call(player,"method_23318"),call(player,"method_23321")));
                    result.put("playerYawPitch",List.of(call(player,"method_36454"),call(player,"method_36455")));
                    result.put("playerBlockPos",call(player,"method_24515").toString());
                    result.put("playerVelocity",vec(call(player,"method_18798")));
                    result.put("playerSpectator",call(player,"method_7325")); result.put("playerNoClip",field(player,"field_5960"));
                    Object abilities=call(player,"method_31549"); result.put("playerFlying",field(abilities,"field_7479"));
                    if (world != null) {
                        int x=((Number)call(player,"method_23317")).intValue() >> 4,z=((Number)call(player,"method_23321")).intValue() >> 4;
                        result.put("playerChunkLoaded",world.getClass().getMethod("method_8393",int.class,int.class).invoke(world,x,z));
                    }
                }
                Object renderer = field(client,"field_1773"), camera = call(renderer,"method_19418");
                result.put("renderCameraXYZ",vec(call(camera,"method_19326")));
                result.put("renderCameraYawPitch",List.of(call(camera,"method_19330"),call(camera,"method_19329")));
                Object window=call(client,"method_22683");
                result.put("framebuffer",List.of(call(window,"method_4489"),call(window,"method_4506")));
                long handle=((Number)call(window,"method_4490")).longValue();
                Class<?> glfw=Class.forName("org.lwjgl.glfw.GLFW",false,minecraft.getClassLoader());
                Method attribute=glfw.getMethod("glfwGetWindowAttrib",long.class,int.class);
                result.put("windowIconified",((Number)attribute.invoke(null,handle,0x00020002)).intValue()==1);
                result.put("windowVisible",((Number)attribute.invoke(null,handle,0x00020004)).intValue()==1);
                result.put("status","PASS_SNAPSHOT");
            } catch (Throwable error) { result.put("status","PARTIAL_SNAPSHOT");result.put("snapshotFailure",error.toString()); }
            try {
                result.put("normalCloseMethod","MinecraftClient.scheduleStop / method_1592");
                minecraft.getMethod("method_1592").invoke(client);
                result.put("scheduleStopInvoked",true);
            } catch (Throwable error) {result.put("scheduleStopInvoked",false);result.put("closeFailure",error.toString());}
            try {Files.writeString(Path.of(path),json(result)+"\n",StandardCharsets.UTF_8);}
            catch(Exception error) {error.printStackTrace();}
            finally {done.countDown();}
        });
        if (!done.await(30,TimeUnit.SECONDS)) throw new IllegalStateException("Render-thread task did not run within 30 seconds; no force stop requested");
    }
    private static Object call(Object target,String name) throws Exception {return target.getClass().getMethod(name).invoke(target);}
    private static Object field(Object target,String name) throws Exception {
        Class<?> c=target.getClass();while(c!=null){try {Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){c=c.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private static List<Object> vec(Object target) throws Exception {return List.of(field(target,"field_1352"),field(target,"field_1351"),field(target,"field_1350"));}
    private static String json(Object value) {
        if(value==null)return "null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map){List<String> rows=new ArrayList<>();map.forEach((k,v)->rows.add(json(k.toString())+":"+json(v)));return "{"+String.join(",",rows)+"}";}
        if(value instanceof Iterable<?> values){List<String> rows=new ArrayList<>();values.forEach(v->rows.add(json(v)));return "["+String.join(",",rows)+"]";}
        return "\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
    }
}
