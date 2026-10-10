package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
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
import net.minecraft.util.math.MathHelper;

/** Opt-in camera evidence only: ordinary server commands and Minecraft's native screenshots. */
public final class GalleryClientCapture implements ClientModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private Path gameDir,output;
    private JsonArray frames,rows = new JsonArray();
    private JsonObject report = new JsonObject(),captured;
    private String setupCommand;
    private int index,age,settled;
    private boolean stopped,sent,waiting,setupDone,renderReady;
    private volatile boolean saved;

    @Override public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        gameDir = client.runDirectory.toPath().toAbsolutePath().normalize();
        Path input = gameDir.resolve("client-capture-input.json");
        if (!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)) return;
        output = gameDir.resolve("client-capture-output.json");
        if (Files.exists(output)) {System.err.println("Gallery capture requires a fresh output; preserving existing evidence");return;}
        try {
            JsonObject marker = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            String bound = marker.get("productionJarSha256").getAsString();
            require(bound.matches("[a-f0-9]{64}"),"A production JAR SHA256 is required");
            var paths = FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
            require(paths.size()==1 && Files.isRegularFile(paths.get(0),LinkOption.NOFOLLOW_LINKS),"Production must load from one ordinary JAR");
            require(bound.equals(sha(paths.get(0))),"Installed production JAR does not match capture input");
            frames = marker.getAsJsonArray("frames");
            require(frames!=null && !frames.isEmpty() && frames.size()<=1000,"Expected 1..1000 explicit camera frames");
            Set<String> names = new HashSet<>();
            for (JsonElement entry:frames) {
                JsonObject frame = entry.getAsJsonObject();String name = frame.get("fileName").getAsString();
                require(name.matches("[A-Za-z0-9_-]{1,96}\\.png") && names.add(name),"Unsafe or duplicate PNG name");
                require(!Files.exists(gameDir.resolve("screenshots").resolve(name)),"PNG already exists: "+name);
                JsonArray camera = frame.getAsJsonArray("camera");require(camera!=null && camera.size()==5,"Camera must be [x,y,z,yaw,pitch]");
                for (JsonElement value:camera) require(Double.isFinite(value.getAsDouble()),"Nonfinite camera coordinate");
                require(Math.abs(camera.get(4).getAsDouble())<=90,"Pitch outside -90..90");
                int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():40;require(wait>=1 && wait<=200,"waitTicks outside 1..200");
                String command = frame.get("command").getAsString();
                require(command.startsWith("tp @s ") || command.matches("function dw_gallery:visit/[a-z0-9_]+"),"Only gallery navigation commands are allowed");
                require(!command.contains("\n") && !command.contains("\r") && command.length()<256,"Invalid navigation command");
            }
            setupCommand = marker.has("setupCommand")?marker.get("setupCommand").getAsString():null;
            require(setupCommand==null || setupCommand.equals("gamemode spectator @s"),"Only optional spectator camera setup is allowed");
            report.addProperty("schema","dreamwalker-native-client-capture-v1");report.addProperty("productionJarSha256",bound);
            report.addProperty("inputSha256",sha(input));report.addProperty("expectedFrames",frames.size());report.add("frames",rows);
            report.addProperty("captureMethod","Minecraft ScreenshotRecorder; last completed framebuffer at END_CLIENT_TICK");
            report.addProperty("defaultWaitTicks",40);write("RUNNING",false);
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
            WorldRenderEvents.END.register(context->{if (!stopped && sent && !waiting && client.player!=null && client.world!=null
                && atCamera(client,frames.get(index).getAsJsonObject().getAsJsonArray("camera"))) renderReady=true;});
        } catch (Throwable failure) { fail(failure); }
    }

    private void tick(MinecraftClient client) {
        if (stopped) return;
        try {
            if (waiting) {
                require(++age<=200,"Native screenshot save timed out");
                if (!saved) return;
                Path png = gameDir.resolve("screenshots").resolve(captured.get("fileName").getAsString());
                require(Files.isRegularFile(png,LinkOption.NOFOLLOW_LINKS) && Files.size(png)>24,"Native PNG missing or empty");
                byte[] signature = Files.readAllBytes(png);
                require(Arrays.equals(Arrays.copyOf(signature,8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"Native output is not PNG");
                var image = ImageIO.read(png.toFile());require(image!=null && image.getWidth()>0 && image.getHeight()>0,"PNG cannot be decoded");
                captured.addProperty("sha256",sha(png));captured.addProperty("bytes",Files.size(png));
                captured.addProperty("width",image.getWidth());captured.addProperty("height",image.getHeight());
                rows.add(captured);index++;waiting=false;sent=false;age=0;settled=0;
                stopped=index==frames.size();write(stopped?"CAPTURE_COMPLETE":"RUNNING",stopped);return;
            }
            if (client.player==null || client.world==null || client.currentScreen!=null || client.isPaused()) return;
            if (!setupDone) { setupDone=true;if (setupCommand!=null) {client.player.networkHandler.sendChatCommand(setupCommand);return;} }
            JsonObject frame = frames.get(index).getAsJsonObject();JsonArray camera = frame.getAsJsonArray("camera");
            if (!sent) {client.player.networkHandler.sendChatCommand(frame.get("command").getAsString());sent=true;renderReady=false;age=0;return;}
            require(++age<=600,"Camera did not settle for frame "+frame.get("fileName").getAsString());
            boolean atCamera=atCamera(client,camera);if (!atCamera) renderReady=false;
            settled=atCamera?settled+1:0;int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():40;if (settled<wait || !renderReady) return;
            captured=frame.deepCopy();captured.add("actualCamera",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));
            captured.addProperty("world",client.world.getRegistryKey().getValue().toString());captured.addProperty("worldTick",client.world.getTime());
            JsonArray states=new JsonArray();for (var entity:client.world.getEntities()) if (entity instanceof RpObjectEntity object) {
                JsonObject state=new JsonObject();state.addProperty("uuid",object.getUuidAsString());state.addProperty("assetId",object.assetId());
                state.addProperty("isOpen",object.isOpen());state.addProperty("animationBusy",object.animationBusy());
                state.add("position",JSON.toJsonTree(List.of(object.getX(),object.getY(),object.getZ())));states.add(state);
            }captured.add("rpStates",states);captured.addProperty("waitTicks",wait);captured.addProperty("settledTicks",settled);
            waiting=true;saved=false;age=0;
            ScreenshotRecorder.saveScreenshot(gameDir.toFile(),frame.get("fileName").getAsString(),client.getFramebuffer(),message->client.execute(()->saved=true));
        } catch (Throwable failure) { fail(failure); }
    }
    private static boolean atCamera(MinecraftClient client,JsonArray camera) {
        double dx=client.player.getX()-camera.get(0).getAsDouble(),dy=client.player.getY()-camera.get(1).getAsDouble(),dz=client.player.getZ()-camera.get(2).getAsDouble();
        return dx*dx+dy*dy+dz*dz<0.0025 && Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-camera.get(3).getAsDouble()))<0.5
            && Math.abs(client.player.getPitch()-camera.get(4).getAsDouble())<0.5 && client.world.isChunkLoaded(client.player.getBlockPos());
    }
    private void write(String status,boolean complete) throws Exception {
        report.addProperty("status",status);report.addProperty("captureComplete",complete);report.addProperty("capturedFrames",rows.size());
        Files.writeString(output,JSON.toJson(report));
    }
    private void fail(Throwable failure) {
        stopped=true;report.addProperty("failure",failure.toString());
        try {write("FAIL_CAPTURE",false);} catch (Exception ignored) {}
        failure.printStackTrace();
    }
    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private static void require(boolean condition,String message) {if (!condition) throw new IllegalStateException(message);}
}
