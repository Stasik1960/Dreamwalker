package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.*;

/** Explicit-marker native framebuffer and persisted root identity verification. */
public final class FlatGalleryClientCapture implements ClientModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private Path gameDir,output;
    private JsonArray frames,rows=new JsonArray();
    private JsonObject report=new JsonObject(),captured;
    private int index,age,settled,quitAge;
    private boolean stopped,sent,waiting,setupDone,renderReady,quitAfterCapture;
    private volatile boolean saved;
    @Override public void onInitializeClient() {
        MinecraftClient client=MinecraftClient.getInstance();
        gameDir=client.runDirectory.toPath().toAbsolutePath().normalize();
        Path input=gameDir.resolve("client-capture-input.json");
        if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS))return;
        output=gameDir.resolve("client-capture-output.json");
        if(Files.exists(output)){System.err.println("Flat-gallery capture requires fresh evidence");return;}
        try {
            JsonObject marker=JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            String bound=marker.get("productionJarSha256").getAsString();
            require(bound.matches("[a-f0-9]{64}"),"Exact production SHA required");
            var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
            require(paths.size()==1&&Files.isRegularFile(paths.get(0),LinkOption.NOFOLLOW_LINKS)&&bound.equals(sha(paths.get(0))),"Installed production JAR differs");
            frames=marker.getAsJsonArray("frames");require(frames!=null&&!frames.isEmpty()&&frames.size()<=1000,"Expected 1..1000 frames");
            Set<String> names=new HashSet<>();
            for(JsonElement entry:frames) {
                JsonObject frame=entry.getAsJsonObject();String name=frame.get("fileName").getAsString();
                require(name.matches("[A-Za-z0-9_-]{1,96}\\.png")&&names.add(name)&&!Files.exists(gameDir.resolve("screenshots").resolve(name)),"Fresh safe PNG name required");
                JsonArray camera=frame.getAsJsonArray("camera");require(camera!=null&&camera.size()==5,"Camera requires x,y,z,yaw,pitch");
                for(JsonElement value:camera)require(Double.isFinite(value.getAsDouble()),"Nonfinite camera");
                require(Math.abs(camera.get(4).getAsDouble())<=90,"Invalid pitch");
                int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():60;require(wait>=1&&wait<=200,"Invalid render wait");
                String command=frame.get("command").getAsString();
                require((command.startsWith("tp @s ")||command.matches("function dw_gallery:visit/[a-z0-9_]+"))&&!command.contains("\n")&&!command.contains("\r")&&command.length()<256,"Only gallery camera navigation allowed");
            }
            quitAfterCapture=marker.has("quitAfterCapture")&&marker.get("quitAfterCapture").getAsBoolean();
            report.addProperty("schema","dreamwalker-flat-gallery-client-v1");report.addProperty("productionJarSha256",bound);
            report.addProperty("inputSha256",sha(input));report.addProperty("expectedFrames",frames.size());report.add("frames",rows);
            report.addProperty("captureMethod","Minecraft ScreenshotRecorder, native completed framebuffer; live client blockstate and UUID reads");
            report.addProperty("physicalMouseKeyboard","NOT_RUN");write("RUNNING",false);
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
            WorldRenderEvents.END.register(context->{if(!stopped&&sent&&!waiting&&client.player!=null&&client.world!=null&&atCamera(client,frames.get(index).getAsJsonObject().getAsJsonArray("camera")))renderReady=true;});
        }catch(Throwable failure){fail(failure);}
    }
    private void tick(MinecraftClient client) {
        if(stopped) {
            if(quitAfterCapture&&report.has("captureComplete")&&report.get("captureComplete").getAsBoolean()&&++quitAge==20)client.scheduleStop();
            return;
        }
        try {
            if(waiting) {
                require(++age<=200,"Native PNG save timed out");if(!saved)return;
                Path png=gameDir.resolve("screenshots").resolve(captured.get("fileName").getAsString());
                require(Files.isRegularFile(png,LinkOption.NOFOLLOW_LINKS)&&Files.size(png)>24,"PNG absent");
                byte[] bytes=Files.readAllBytes(png);require(Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"Not PNG");
                var image=ImageIO.read(png.toFile());require(image!=null&&image.getWidth()>0&&image.getHeight()>0,"PNG cannot decode");
                captured.addProperty("sha256",sha(png));captured.addProperty("bytes",bytes.length);captured.addProperty("width",image.getWidth());captured.addProperty("height",image.getHeight());
                rows.add(captured);index++;waiting=false;sent=false;age=0;settled=0;stopped=index==frames.size();write(stopped?"CAPTURE_COMPLETE":"RUNNING",stopped);return;
            }
            if(client.player==null||client.world==null||client.currentScreen!=null||client.isPaused())return;
            if(!setupDone){setupDone=true;client.player.networkHandler.sendChatCommand("gamemode spectator @s");return;}
            JsonObject frame=frames.get(index).getAsJsonObject();JsonArray camera=frame.getAsJsonArray("camera");
            if(!sent){client.player.networkHandler.sendChatCommand(frame.get("command").getAsString());sent=true;renderReady=false;age=0;return;}
            require(++age<=900,"Camera did not settle: "+frame.get("fileName").getAsString());
            boolean at=atCamera(client,camera);if(!at)renderReady=false;settled=at?settled+1:0;
            int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():60;if(settled<wait||!renderReady)return;
            captured=frame.deepCopy();captured.add("actualCamera",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));
            captured.addProperty("world",client.world.getRegistryKey().getValue().toString());captured.addProperty("worldTick",client.world.getTime());
            JsonArray checks=new JsonArray();if(frame.has("rootChecks"))for(JsonElement entry:frame.getAsJsonArray("rootChecks"))checks.add(checkRoot(client,entry.getAsJsonObject()));
            captured.add("actualRootChecks",checks);
            JsonArray entityChecks=new JsonArray();if(frame.has("entityChecks"))for(JsonElement entry:frame.getAsJsonArray("entityChecks"))entityChecks.add(checkEntity(client,entry.getAsJsonObject()));
            captured.add("actualEntityChecks",entityChecks);captured.addProperty("status","PASS");captured.addProperty("settledTicks",settled);
            waiting=true;saved=false;age=0;ScreenshotRecorder.saveScreenshot(gameDir.toFile(),frame.get("fileName").getAsString(),client.getFramebuffer(),message->client.execute(()->saved=true));
        }catch(Throwable failure){fail(failure);}
    }
    private static JsonObject checkRoot(MinecraftClient client,JsonObject expected) {
        JsonArray p=expected.getAsJsonArray("root");BlockPos root=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
        require(client.world.isChunkLoaded(root),"Checked root chunk is not loaded: "+root);
        var state=client.world.getBlockState(root);String registry=Registries.BLOCK.getId(state.getBlock()).toString();
        require(expected.get("registry").getAsString().equals(registry),"Client root registry differs: "+root+" "+registry);
        JsonObject properties=new JsonObject();state.getEntries().forEach((property,value)->properties.addProperty(property.getName(),value.toString().toLowerCase(java.util.Locale.ROOT)));
        for(String key:List.of("variant","profile","open"))if(expected.has(key)&&!expected.get(key).isJsonNull()) {
            String value=expected.get(key).getAsString();require(properties.has(key)&&value.equals(properties.get(key).getAsString()),"Client root "+key+" differs at "+root);
        }
        var owner=VerticalMount.owner(client.world,root);require(owner!=null&&expected.get("uuid").getAsString().equals(owner.instanceId().toString()),"Persisted client UUID differs at "+root);
        require(owner.root().x()==root.getX()&&owner.root().y()==root.getY()&&owner.root().z()==root.getZ(),"Client owner root differs");
        JsonObject result=expected.deepCopy();result.addProperty("actualRegistry",registry);result.add("actualProperties",properties);result.addProperty("actualUuid",owner.instanceId().toString());result.addProperty("status","PASS");return result;
    }
    private static JsonObject checkEntity(MinecraftClient client,JsonObject expected) {
        String uuid=expected.get("uuid").getAsString();
        for(var entity:client.world.getEntities())if(entity.getUuidAsString().equals(uuid)) {
            String registry=Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            require(expected.get("registry").getAsString().equals(registry),"Persisted client entity type differs: "+uuid);
            JsonObject result=expected.deepCopy();result.addProperty("actualUuid",entity.getUuidAsString());result.addProperty("actualRegistry",registry);result.addProperty("status","PASS");return result;
        }
        throw new IllegalStateException("Persisted client entity UUID is not loaded: "+uuid);
    }
    private static boolean atCamera(MinecraftClient client,JsonArray camera) {
        double dx=client.player.getX()-camera.get(0).getAsDouble(),dy=client.player.getY()-camera.get(1).getAsDouble(),dz=client.player.getZ()-camera.get(2).getAsDouble();
        return dx*dx+dy*dy+dz*dz<0.0025&&Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-camera.get(3).getAsDouble()))<0.5&&Math.abs(client.player.getPitch()-camera.get(4).getAsDouble())<0.5&&client.world.isChunkLoaded(client.player.getBlockPos());
    }
    private void write(String status,boolean complete)throws Exception{report.addProperty("status",status);report.addProperty("captureComplete",complete);report.addProperty("capturedFrames",rows.size());Files.writeString(output,JSON.toJson(report));}
    private void fail(Throwable failure){stopped=true;report.addProperty("failure",failure.toString());try{write("FAIL_CAPTURE",false);}catch(Exception ignored){}failure.printStackTrace();}
    private static String sha(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
