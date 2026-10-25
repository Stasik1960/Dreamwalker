package dev.dreamwalker.probe;
import net.fabricmc.api.ClientModInitializer;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.io.*;
/** QA only, used in the cold-reopen/sun run, never in FPS comparisons. */
public final class ShaderToggleProbe implements ClientModInitializer {
    @FunctionalInterface interface Action {void run() throws Exception;}
    static Object client,api,config;static Class<?> apiType,configType;static long cachedBytes;
    static void mainThread(Action action)throws Exception{
        var done=new CompletableFuture<Void>();((java.util.concurrent.Executor)client).execute(()->{try{action.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});done.get(90,TimeUnit.SECONDS);
    }
    static boolean active()throws Exception{return (boolean)apiType.getMethod("isShaderPackInUse").invoke(api);}
    public void onInitializeClient(){
        Thread worker=new Thread(()->{
            Path output=Path.of(System.getProperty("dw.shader.toggle.output"));
            try{
                Path trigger=Path.of(System.getProperty("dw.shader.toggle.trigger"));long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(30);
                while(!Files.isRegularFile(trigger)){if(System.nanoTime()>deadline)throw new IllegalStateException("Morning screenshot trigger deadline");Thread.sleep(100);}
                client=Class.forName("net.minecraft.class_310").getMethod("method_1551").invoke(null);
                apiType=Class.forName("net.irisshaders.iris.api.v0.IrisApi");api=apiType.getMethod("getInstance").invoke(null);config=apiType.getMethod("getConfig").invoke(api);configType=Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
                mainThread(()->{if(!active())throw new IllegalStateException("Shader was not active before toggle");configType.getMethod("setShadersEnabledAndApply",boolean.class).invoke(config,false);if(active())throw new IllegalStateException("Shader did not disable");});
                Thread.sleep(2500);
                mainThread(()->{
                    Class<?> batch=Class.forName("dev.dreamwalker.bloodbornedw.composite.CompositeGpuBatch");Field bytes=batch.getDeclaredField("gpuBytes");bytes.setAccessible(true);cachedBytes=bytes.getLong(null);
                    configType.getMethod("setShadersEnabledAndApply",boolean.class).invoke(config,true);if(!active())throw new IllegalStateException("Shader did not re-enable");
                });
                Files.writeString(output,"{\"status\":\"PASS_SHADER_ON_OFF_ON\",\"retainedGpuBytesWhileOff\":"+cachedBytes+",\"method\":\"Public IrisApiConfig on Minecraft executor after morning frame; no UI automation\",\"retainedCacheActuallyPresent\":"+(cachedBytes>0)+"}\n");
            }catch(Throwable error){
                var text=new StringWriter();error.printStackTrace(new PrintWriter(text));try{Files.writeString(output.resolveSibling("shader-toggle-error.txt"),text.toString());Files.writeString(output,"{\"status\":\"FAIL\"}\n");}catch(IOException ignored){}
            }
        },"Dreamwalker-shader-toggle-QA");worker.setDaemon(true);worker.start();
    }
}
