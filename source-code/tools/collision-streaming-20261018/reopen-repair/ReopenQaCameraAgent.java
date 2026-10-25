import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** One-use live QA expectation repair. Never transforms bytecode or writes world data. */
public final class ReopenQaCameraAgent {
    private static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Object method(Object object,String name,Class<?>[] types,Object...args)throws Exception {
        return object.getClass().getMethod(name,types).invoke(object,args);
    }
    private static Object json(Object object,String key)throws Exception {
        return method(object,"get",new Class<?>[]{String.class},key);
    }
    private static String string(Object object)throws Exception {
        return (String)method(object,"getAsString",new Class<?>[]{});
    }
    private static double number(Object object)throws Exception {
        return ((Number)method(object,"getAsDouble",new Class<?>[]{})).doubleValue();
    }
    private static Object item(Object array,int index)throws Exception {
        return method(array,"get",new Class<?>[]{int.class},index);
    }
    private static int size(Object array)throws Exception {
        return ((Number)method(array,"size",new Class<?>[]{})).intValue();
    }
    private static Class<?> loaded(Instrumentation instrumentation,String name) {
        for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getName().equals(name))return type;
        throw new IllegalStateException("Required loaded class absent: "+name);
    }
    private static void require(boolean value,String why) {
        if(!value)throw new IllegalStateException(why);
    }
    private static String quoted(String value) {
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
    }
    private static String sha(Path path)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(path)){byte[] block=new byte[65536];for(int n;(n=in.read(block))!=-1;)digest.update(block,0,n);}
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void agentmain(String arguments,Instrumentation instrumentation)throws Exception {
        String[] args=arguments.split("\\|",-1);
        require(args.length==3,"Expected runtime|productionSHA256|receipt");
        Path runtime=Path.of(args[0]).toAbsolutePath().normalize(),receipt=Path.of(args[2]).toAbsolutePath().normalize();
        require(!Files.exists(receipt),"Preserve previous repair receipt");
        require(runtime.getFileName().toString().equals("runtime-client-collision-shapes-reopen"),"Unexpected QA runtime");
        require(args[1].equals("a179fce5acf607bbcdf86202ddbb20e33928f0a5c6936b5e6ef7de7be14ecbaf"),"Unexpected final JAR SHA");
        Class<?> loaderApi=loaded(instrumentation,"net.fabricmc.loader.api.FabricLoader");
        Class<?> initializer=loaded(instrumentation,"net.fabricmc.api.ClientModInitializer");
        Object loader=loaderApi.getMethod("getInstance").invoke(null);
        List<?> entries=(List<?>)loaderApi.getMethod("getEntrypoints",String.class,Class.class).invoke(loader,"client",initializer);
        List<?> matches=entries.stream().filter(x->x.getClass().getName().equals("dev.dreamwalker.bloodbornedw.gallery.GalleryClientCapture")).toList();
        require(matches.size()==1,"Expected exactly one existing gallery entrypoint instance");
        Object capture=matches.get(0);
        Class<?> clientType=loaded(instrumentation,"net.minecraft.class_310");
        Object client=clientType.getMethod("method_1551").invoke(null);
        CountDownLatch done=new CountDownLatch(1);Throwable[] failure=new Throwable[1];
        Runnable correction=()->{
            try {
                require(field(capture,"gameDir").equals(runtime),"Capture belongs to another runtime");
                require(!(Boolean)field(capture,"stopped")&&(Boolean)field(capture,"sent")&&!(Boolean)field(capture,"waiting"),"Capture is not the active settled-wait state");
                require(((Number)field(capture,"index")).intValue()==0,"Expected first and only frame");
                require(field(capture,"nativeRoute")==null&&field(capture,"spectatorTravel")==null&&field(capture,"routeMode").equals("NONE"),"Unexpected gameplay/travel route");
                Object report=field(capture,"report");
                require(string(json(report,"productionJarSha256")).equals(args[1]),"Capture bound to another production artifact");
                require(string(json(report,"captureCase")).equals("shapes-reopen"),"Unexpected capture case");
                Object frames=field(capture,"frames");require(size(frames)==1,"Expected one reopen frame");
                Object frame=method(item(frames,0),"getAsJsonObject",new Class<?>[]{});
                require(string(json(frame,"key")).equals("brick"),"Expected brick frame");
                require(string(json(frame,"command")).equals("tp @s 980 100 95 44.36 35"),"Unexpected original integer teleport");
                require(number(json(frame,"requiredClientChunkRadius"))==8,"Delivery gate changed");
                Object camera=method(frame,"getAsJsonArray",new Class<?>[]{String.class},"camera");
                require(size(camera)==5&&number(item(camera,0))==980&&number(item(camera,1))==100&&number(item(camera,2))==95,"Expected exact uncorrected camera");
                Object player=clientType.getField("field_1724").get(client);require(player!=null,"Missing native player");
                double x=((Number)method(player,"method_23317",new Class<?>[]{})).doubleValue();
                double y=((Number)method(player,"method_23318",new Class<?>[]{})).doubleValue();
                double z=((Number)method(player,"method_23321",new Class<?>[]{})).doubleValue();
                require(Math.abs(x-980.5)<1e-7&&Math.abs(y-100)<1e-7&&Math.abs(z-95.5)<1e-7,"Actual player does not match vanilla integer-centred teleport");
                List<Path> jars;
                try(var files=Files.list(runtime.resolve("mods"))){jars=files.filter(p->p.getFileName().toString().startsWith("dreamwalker-bb-fabric-")&&p.getFileName().toString().endsWith(".jar")).toList();}
                require(jars.size()==1&&sha(jars.get(0)).equals(args[1]),"Installed production JAR changed");
                String old=camera.toString();
                ClassLoader gameLoader=camera.getClass().getClassLoader();
                Class<?> element=Class.forName("com.google.gson.JsonElement",false,gameLoader),primitive=Class.forName("com.google.gson.JsonPrimitive",false,gameLoader);
                Constructor<?> value=primitive.getConstructor(Number.class);
                // The only two mutations: expected QA camera x/z, on the client thread.
                method(camera,"set",new Class<?>[]{int.class,element},0,value.newInstance(Double.valueOf(980.5)));
                method(camera,"set",new Class<?>[]{int.class,element},2,value.newInstance(Double.valueOf(95.5)));
                String text="{\"status\":\"PASS_QA_CAMERA_EXPECTATION_ONLY\",\"utc\":"+quoted(Instant.now().toString())+",\"runtime\":"+quoted(runtime.toString())+",\"productionJarSha256\":"+quoted(args[1])+",\"oldExpectedCamera\":"+old+",\"newExpectedCamera\":"+camera+",\"actualPlayerPosition\":["+x+","+y+","+z+"],\"clientThread\":"+quoted(Thread.currentThread().getName())+",\"scope\":\"Only live QA expected camera x/z changed to native vanilla integer-centred position. No JAR/bytecode/world/player changes. Existing FULL delivery/block/render/stability gates remain. Performance pair unaffected.\"}\n";
                Files.writeString(receipt,text,StandardOpenOption.CREATE_NEW);
            }catch(Throwable problem){failure[0]=problem;try{Files.writeString(receipt,"{\"status\":\"FAIL_BEFORE_OR_DURING_QA_EXPECTATION_REPAIR\",\"error\":"+quoted(problem.toString())+"}\n",StandardOpenOption.CREATE_NEW);}catch(Exception ignored){}}
            finally{done.countDown();}
        };
        clientType.getMethod("execute",Runnable.class).invoke(client,correction);
        require(done.await(30,TimeUnit.SECONDS),"Client did not execute correction in 30 seconds");
        if(failure[0]!=null)throw new IllegalStateException("QA camera repair refused",failure[0]);
    }
}
