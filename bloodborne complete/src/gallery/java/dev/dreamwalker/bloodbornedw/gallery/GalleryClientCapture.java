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
    private final ArrayDeque<String> setupCommands=new ArrayDeque<>();
    private int index,age,settled;
    private boolean stopped,sent,waiting,setupDone,renderReady;
    private boolean stopAfterCapture;
    private String routeMode;
    private JsonObject previousRoute;
    private GalleryNativeRoute nativeRoute;
    private boolean recoverySent,recoveryVerified;
    private int recoveryTicks,recoveryTravelPhase,bootstrapRespawnTicks,recoveryStableTicks,recoveryLoadedChunks=-1;
    private boolean bootstrapRespawnRequested,bootstrapRespawnConfirmed;
    private int lastLoadedChunks=-1,chunkStableTicks;
    private String waitScreen="";
    private long frameWindowStarted;
    private JsonObject meshWindowStart;
    private static final ArrayList<Long> PRESENTATION_INTERVALS=new ArrayList<>();
    private static long previousPresentation;
    private static boolean measuring;
    /** Same flipFrame clock as the existing DwFrameDiagnosticsMixin. */
    public static void presentation(){if(!measuring)return;long now=System.nanoTime();if(previousPresentation!=0&&PRESENTATION_INTERVALS.size()<65536)PRESENTATION_INTERVALS.add(now-previousPresentation);previousPresentation=now;}
    private static void beginFrames(){PRESENTATION_INTERVALS.clear();previousPresentation=0;measuring=true;}
    private static JsonObject endFrames(){
        measuring=false;var result=new JsonObject();long total=0;for(long n:PRESENTATION_INTERVALS)total+=n;
        result.addProperty("measurements",PRESENTATION_INTERVALS.size());result.addProperty("averageMs",PRESENTATION_INTERVALS.isEmpty()?0:total/1e6/PRESENTATION_INTERVALS.size());
        result.addProperty("fpsAverage",total==0?0:1e9*PRESENTATION_INTERVALS.size()/total);
        var sorted=new ArrayList<>(PRESENTATION_INTERVALS);Collections.sort(sorted);if(!sorted.isEmpty()){result.addProperty("medianMs",sorted.get((sorted.size()-1)/2)/1e6);result.addProperty("p95Ms",sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1e6);}
        result.add("rawPresentationIntervalsNs",JSON.toJsonTree(PRESENTATION_INTERVALS));return result;
    }
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
            stopAfterCapture=marker.has("stopAfterCapture")&&marker.get("stopAfterCapture").getAsBoolean();
            routeMode=marker.has("nativeRoute")?marker.get("nativeRoute").getAsString():"NONE";
            require(Set.of("NONE","INTERACT_SAVE","REOPEN_AND_RESTORE","RECOVER_AND_DOOR").contains(routeMode),"Invalid explicit native route mode");
            if(routeMode.equals("REOPEN_AND_RESTORE")||routeMode.equals("RECOVER_AND_DOOR"))previousRoute=marker.getAsJsonObject("previousNativeRoute");
            String bound = marker.get("productionJarSha256").getAsString();
            require(bound.matches("[a-f0-9]{64}"),"A production JAR SHA256 is required");
            var paths = FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
            require(paths.size()==1 && Files.isRegularFile(paths.get(0),LinkOption.NOFOLLOW_LINKS),"Production must load from one ordinary JAR");
            require(bound.equals(sha(paths.get(0))),"Installed production JAR does not match capture input");
            frames = marker.getAsJsonArray("frames");
            require(frames!=null && frames.size()<=1000 && (!frames.isEmpty()||routeMode.equals("REOPEN_AND_RESTORE")),"Expected camera frames, or an explicit reopen-only route");
            Set<String> names = new HashSet<>();
            for (JsonElement entry:frames) {
                JsonObject frame = entry.getAsJsonObject();String name = frame.get("fileName").getAsString();
                require(name.matches("[A-Za-z0-9_-]{1,96}\\.png") && names.add(name),"Unsafe or duplicate PNG name");
                require(!Files.exists(gameDir.resolve("screenshots").resolve(name)),"PNG already exists: "+name);
                JsonArray camera = frame.getAsJsonArray("camera");require(camera!=null && camera.size()==5,"Camera must be [x,y,z,yaw,pitch]");
                for (JsonElement value:camera) require(Double.isFinite(value.getAsDouble()),"Nonfinite camera coordinate");
                require(Math.abs(camera.get(4).getAsDouble())<=90,"Pitch outside -90..90");
                int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():40;require(wait>=1 && wait<=200,"waitTicks outside 1..200");
                if(frame.has("measureFrameTimes"))require(frame.get("measureFrameTimes").isJsonPrimitive()&&frame.get("measureFrameTimes").getAsJsonPrimitive().isBoolean(),"measureFrameTimes must be boolean");
                if(frame.has("probePixel")){JsonArray pixel=frame.getAsJsonArray("probePixel");require(pixel!=null&&pixel.size()==2,"probePixel must be [x,y]");for(JsonElement value:pixel)require(value.getAsInt()==value.getAsDouble()&&value.getAsInt()>=0&&value.getAsInt()<16384,"probePixel coordinate outside bounded integer range");}
                String command = frame.get("command").getAsString();
                require(command.startsWith("tp @s ") || command.matches("function dw_gallery:visit/[a-z0-9_]+"),"Only gallery navigation commands are allowed");
                require(!command.contains("\n") && !command.contains("\r") && command.length()<256,"Invalid navigation command");
            }
            setupCommand = marker.has("setupCommand")?marker.get("setupCommand").getAsString():null;
            require(setupCommand==null || setupCommand.equals("gamemode spectator @s"),"Only optional spectator camera setup is allowed");
            if(marker.has("setupCommands"))for(var value:marker.getAsJsonArray("setupCommands")){
                String command=value.getAsString();require(Set.of("gamemode spectator @s","gamerule doDaylightCycle false","time set 6000","weather clear").contains(command),"Unsupported capture setup");setupCommands.add(command);
            }
            report.addProperty("schema","dreamwalker-native-client-capture-v1");report.addProperty("productionJarSha256",bound);
            if(marker.has("captureCase"))report.addProperty("captureCase",marker.get("captureCase").getAsString());
            report.addProperty("inputSha256",sha(input));report.addProperty("expectedFrames",frames.size());report.add("frames",rows);
            report.addProperty("captureMethod","Minecraft ScreenshotRecorder; last completed framebuffer at END_CLIENT_TICK");
            report.addProperty("defaultWaitTicks",40);write("RUNNING",false);
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
            WorldRenderEvents.END.register(context->{if (!stopped && sent && !waiting && index<frames.size() && client.player!=null && client.world!=null
                && atCamera(client,frames.get(index).getAsJsonObject().getAsJsonArray("camera"))) renderReady=true;});
        } catch (Throwable failure) { fail(failure); }
    }

    private void tick(MinecraftClient client) {
        if (stopped) return;
        try {
            if(nativeRoute!=null){
                boolean complete=nativeRoute.tick(client);JsonObject route=nativeRoute.jsonReport();
                if(complete||!route.equals(report.get("nativeRoute"))){report.add("nativeRoute",route);write(complete?(nativeRoute.passed()?"CAPTURE_AND_ROUTE_COMPLETE":"FAIL_NATIVE_ROUTE"):"NATIVE_ROUTE_RUNNING",complete);}
                if(complete){stopped=true;if(stopAfterCapture)client.scheduleStop();}return;
            }
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
                boolean capturedAll=index==frames.size();
                if(capturedAll&&!routeMode.equals("NONE")){nativeRoute=routeMode.equals("INTERACT_SAVE")?new GalleryNativeRoute():new GalleryNativeRoute(previousRoute);report.add("nativeRoute",nativeRoute.jsonReport());write("NATIVE_ROUTE_RUNNING",false);return;}
                stopped=capturedAll;write(stopped?"CAPTURE_COMPLETE":"RUNNING",stopped);if(stopped&&stopAfterCapture)client.scheduleStop();return;
            }
            // An explicit recovery capture can begin with the saved QA death,
            // before the post-capture route object exists. Recover natively.
            if(routeMode.equals("RECOVER_AND_DOOR")&&client.player!=null&&client.world!=null){
                if(client.player.isDead()){
                    require(++bootstrapRespawnTicks<=240,"Pre-capture native QA respawn timed out");
                    if(!bootstrapRespawnRequested){client.player.requestRespawn();bootstrapRespawnRequested=true;report.addProperty("preCaptureRespawn","NATIVE_REQUEST_ONCE");write("PRE_CAPTURE_RESPAWN_REQUESTED",false);}return;
                }
                if(bootstrapRespawnRequested&&!bootstrapRespawnConfirmed){
                    bootstrapRespawnConfirmed=true;report.addProperty("preCaptureRespawn","NATIVE_ALIVE_ACK");
                    if(client.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen)client.setScreen(null);
                    write("PRE_CAPTURE_RESPAWN_CONFIRMED",false);
                }
            }
            if (client.player==null || client.world==null || client.currentScreen!=null || client.isPaused()) {
                String screen=client.currentScreen==null?"NO_WORLD_OR_PAUSED":client.currentScreen.getClass().getSimpleName();
                if(!screen.equals(waitScreen)){waitScreen=screen;report.addProperty("waitingScreen",screen);write("WAITING_FOR_WORLD",false);}return;
            }
            if(routeMode.equals("RECOVER_AND_DOOR")&&!recoveryVerified){recoverBeforeCapture(client);return;}
            if(!setupCommands.isEmpty()){client.player.networkHandler.sendChatCommand(setupCommands.remove());return;}
            if (!setupDone) { setupDone=true;if (setupCommand!=null) {client.player.networkHandler.sendChatCommand(setupCommand);return;} }
            if(index==frames.size()&&routeMode.equals("REOPEN_AND_RESTORE")){
                nativeRoute=new GalleryNativeRoute(previousRoute);report.add("nativeRoute",nativeRoute.jsonReport());write("NATIVE_ROUTE_RUNNING",false);return;
            }
            JsonObject frame = frames.get(index).getAsJsonObject();JsonArray camera = frame.getAsJsonArray("camera");
            if (!sent) {client.player.networkHandler.sendChatCommand(frame.get("command").getAsString());sent=true;renderReady=false;age=0;lastLoadedChunks=-1;chunkStableTicks=0;report.addProperty("currentFrame",frame.get("key").getAsString());write("NAVIGATING",false);return;}
            require(++age<=36000,"Camera did not settle for frame "+frame.get("fileName").getAsString());
            boolean atCamera=atCamera(client,camera);if (!atCamera) renderReady=false;
            settled=atCamera?settled+1:0;int wait=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():40;
            int loaded=client.world.getChunkManager().getLoadedChunkCount();chunkStableTicks=loaded==lastLoadedChunks?chunkStableTicks+1:0;lastLoadedChunks=loaded;
            if(settled<wait||!renderReady||!client.worldRenderer.isTerrainRenderComplete()||chunkStableTicks<100){
                if(frameWindowStarted!=0){endFrames();frameWindowStarted=0;}
                if(age%200==0){report.addProperty("actualCameraWhileWaiting",List.of(client.player.getX(),client.player.getY(),client.player.getZ()).toString());report.addProperty("loadedChunksWhileWaiting",loaded);report.addProperty("terrainCompleteWhileWaiting",client.worldRenderer.isTerrainRenderComplete());write("WAITING_FOR_SETTLED_TERRAIN",false);}return;
            }
            boolean measureFrameTimes=!frame.has("measureFrameTimes")||frame.get("measureFrameTimes").getAsBoolean();
            if(measureFrameTimes){
                if(frameWindowStarted==0){meshWindowStart=existingRenderCounters();beginFrames();frameWindowStarted=System.nanoTime();write("MEASURING_FRAME_TIMES",false);return;}
                if(System.nanoTime()-frameWindowStarted<20_000_000_000L)return;
            }
            captured=frame.deepCopy();captured.add("actualCamera",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));
            if(measureFrameTimes)captured.add("frameTimes",endFrames());
            else {var notMeasured=new JsonObject();notMeasured.addProperty("status","NOT_MEASURED");captured.add("frameTimes",notMeasured);}
            if(measureFrameTimes)captured.add("existingRenderCountersAtWindowStart",meshWindowStart);
            captured.add("existingRenderCountersAtCapture",existingRenderCounters());
            captured.addProperty("glRenderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
            captured.addProperty("glVendor",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR));
            frameWindowStarted=0;
            captured.addProperty("renderDistance",client.options.getViewDistance().getValue());
            captured.addProperty("simulationDistance",client.options.getSimulationDistance().getValue());
            captured.addProperty("loadedChunks",client.world.getChunkManager().getLoadedChunkCount());
            captured.addProperty("terrainRenderComplete",client.worldRenderer.isTerrainRenderComplete());
            captured.addProperty("dayTime",client.world.getTimeOfDay());
            if(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit){captured.addProperty("lookedAt",hit.getBlockPos().toShortString());captured.addProperty("lookedAtState",client.world.getBlockState(hit.getBlockPos()).toString());}
            if(frame.has("probeRoot"))captured.add("modelLightProbe",probe(client,frame.getAsJsonArray("probeRoot")));
            if(frame.has("probeRoots")){var probes=new JsonArray();for(var coordinates:frame.getAsJsonArray("probeRoots")){if(probes.size()>=4)break;probes.add(probe(client,coordinates.getAsJsonArray()));}captured.add("addressedLightProbes",probes);}
            if(frame.has("probePixel"))captured.add("pixelRayProbe",probePixel(client,frame.getAsJsonArray("probePixel")));
            try{var type=Class.forName("dev.dreamwalker.bloodbornedw.composite.StaticArchitectureGeometry");captured.add("staticGeometry",JSON.toJsonTree(type.getMethod("metrics").invoke(null)));}catch(ClassNotFoundException absent){captured.addProperty("staticGeometry","BASELINE_WITHOUT_HIDDEN_FACE_FILTER");}
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
    /** Exact rollback of the already proven one-cell native break, before the
     * next matched FPS capture. The immutable .6 checkpoint is never touched. */
    private void recoverBeforeCapture(MinecraftClient client) throws Exception {
        require(++recoveryTicks<=1200,"Prior native break recovery did not settle");
        require(previousRoute!=null&&previousRoute.has("recoveryExpectation"),"Prior recovery expectation required");
        var expected=previousRoute.getAsJsonObject("recoveryExpectation");var root=new net.minecraft.util.math.BlockPos(289,128,-656);
        require(expected.get("rootBefore").getAsString().equals("Block{bloodborne_dw:static_architecture_0}[part=38]"),"Unexpected prior recovery palette state");
        if(recoveryTravelPhase==0){client.player.networkHandler.sendChatCommand("gamemode spectator @s");recoveryTravelPhase=1;return;}
        if(recoveryTravelPhase==1){
            if(!client.player.isSpectator())return;
            client.player.networkHandler.sendChatCommand("tp @s 290.500000 128.000000 -655.500000 90.000000 66.000000");recoveryTravelPhase=2;return;
        }
        if(client.player.squaredDistanceTo(290.5,128,-655.5)>.0025)return;
        int loaded=client.world.getChunkManager().getLoadedChunkCount();
        recoveryStableTicks=loaded==recoveryLoadedChunks?recoveryStableTicks+1:0;recoveryLoadedChunks=loaded;
        if(recoveryStableTicks<100)return;
        var neighbors=expected.getAsJsonArray("unchangedNeighbours");require(neighbors.size()==26,"Prior one-cell recovery needs all 26 neighbours");
        if(!client.world.isChunkLoaded(root))return;
        for(var value:neighbors){var row=value.getAsJsonObject();var a=row.getAsJsonArray("position");var p=new net.minecraft.util.math.BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());if(!client.world.isChunkLoaded(p))return;require(client.world.getBlockState(p).toString().equals(row.get("state").getAsString()),"Recovery neighbour changed at "+p.toShortString());}
        var state=client.world.getBlockState(root);
        if(state.toString().equals(expected.get("rootBefore").getAsString())){
            require(client.world.getBlockEntity(root)==null,"Restored static root unexpectedly has BE data");recoveryVerified=true;report.addProperty("preCaptureRecovery","EXACT_RECIPE38_SERVER_ACK_26_NEIGHBOURS_UNCHANGED");write("PRE_CAPTURE_RECOVERY_COMPLETE",false);return;
        }
        require(state.isAir()&&client.world.getBlockEntity(root)==null,"Guarded recovery requires the exact prior air after-state");
        if(!recoverySent){
            String command="setblock 289 128 -656 bloodborne_dw:static_architecture_0[part=38] keep";
            require(command.equals(expected.get("guardedRestoreCommand").getAsString()),"Prior guarded recovery command changed");
            client.player.networkHandler.sendChatCommand(command);recoverySent=true;report.addProperty("preCaptureRecovery","GUARDED_KEEP_REQUESTED_AFTER_26_NEIGHBOUR_CHECK");write("PRE_CAPTURE_RECOVERY_REQUESTED",false);
        }
    }
    private static boolean atCamera(MinecraftClient client,JsonArray camera) {
        double dx=client.player.getX()-camera.get(0).getAsDouble(),dy=client.player.getY()-camera.get(1).getAsDouble(),dz=client.player.getZ()-camera.get(2).getAsDouble();
        return dx*dx+dy*dy+dz*dz<0.0025 && Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-camera.get(3).getAsDouble()))<0.5
            && Math.abs(client.player.getPitch()-camera.get(4).getAsDouble())<0.5 && client.world.isChunkLoaded(client.player.getBlockPos());
    }
    private static JsonObject existingRenderCounters(){
        var out=new JsonObject();
        out.add("compositeMesh",JSON.toJsonTree(dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh.metrics()));
        out.add("compositeVisibility",JSON.toJsonTree(dev.dreamwalker.bloodbornedw.composite.CompositeVisualBoundsV127.snapshot()));
        return out;
    }
    /** One explicitly selected object, using its actual renderer and native light.
     * No scans, relighting, profiler, or changes to the world/production renderer.
     */
    private static JsonObject probe(MinecraftClient client,JsonArray coordinates){
        var pos=new net.minecraft.util.math.BlockPos(coordinates.get(0).getAsInt(),coordinates.get(1).getAsInt(),coordinates.get(2).getAsInt());
        var state=client.world.getBlockState(pos);var out=new JsonObject();out.addProperty("position",pos.toShortString());out.addProperty("state",state.toString());
        if(state.getRenderType()!=net.minecraft.block.BlockRenderType.MODEL)return out;
        JsonArray rows=new JsonArray();out.add("quads",rows);
        net.minecraft.client.render.VertexConsumer recorder=new net.minecraft.client.render.VertexConsumer(){
            public void quad(net.minecraft.client.util.math.MatrixStack.Entry matrix,net.minecraft.client.render.model.BakedQuad quad,float[] brightness,float r,float g,float b,int[] light,int overlay,boolean color){
                if(rows.size()>=512)return;var row=new JsonObject();row.addProperty("face",quad.getFace().getName());row.add("brightness",JSON.toJsonTree(brightness));row.add("packedLight",JSON.toJsonTree(light));
                row.addProperty("shade",quad.hasShade());int[] data=quad.getVertexData();row.add("sourceVertices",JSON.toJsonTree(data));
                var sprite=quad.getSprite();row.addProperty("sprite",sprite.getContents().getId().toString());
                row.add("spriteUv",JSON.toJsonTree(List.of(sprite.getMinU(),sprite.getMaxU(),sprite.getMinV(),sprite.getMaxV())));
                row.add("spriteSize",JSON.toJsonTree(List.of(sprite.getContents().getWidth(),sprite.getContents().getHeight())));
                JsonArray transformed=new JsonArray();org.joml.Vector3f center=new org.joml.Vector3f();var points=new org.joml.Vector3f[4];
                for(int i=0;i<4;i++){int n=i*(data.length/4);var point=new org.joml.Vector3f(Float.intBitsToFloat(data[n]),Float.intBitsToFloat(data[n+1]),Float.intBitsToFloat(data[n+2]));matrix.getPositionMatrix().transformPosition(point);points[i]=point;center.add(point);transformed.add(JSON.toJsonTree(List.of(point.x,point.y,point.z)));}
                row.add("rootRelativeVertices",transformed);center.mul(.25f);
                var direction=quad.getFace();var normal=new org.joml.Vector3f(direction.getOffsetX(),direction.getOffsetY(),direction.getOffsetZ());matrix.getNormalMatrix().transform(normal).normalize();center.fma(.001f,normal);
                var faceCell=net.minecraft.util.math.BlockPos.ofFloored(pos.getX()+center.x,pos.getY()+center.y,pos.getZ()+center.z);
                row.addProperty("physicalOutwardCenterCell",faceCell.toShortString());row.addProperty("physicalOutwardSky",client.world.getLightLevel(net.minecraft.world.LightType.SKY,faceCell));row.addProperty("physicalOutwardBlock",client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,faceCell));
                JsonArray vertices=new JsonArray();var surfaceCenter=new org.joml.Vector3f(center).fma(-.001f,normal);
                for(int i=0;i<4;i++){
                    // Move off the geometric edge toward this face, then just
                    // outside it. This records the cell that lights the actual
                    // exterior vertex instead of an arbitrary edge neighbour.
                    var sample=new org.joml.Vector3f(points[i]).lerp(surfaceCenter,.01f).fma(.001f,normal);
                    var cell=net.minecraft.util.math.BlockPos.ofFloored(pos.getX()+sample.x,pos.getY()+sample.y,pos.getZ()+sample.z);
                    var vertex=new JsonObject();vertex.add("rootRelative",JSON.toJsonTree(List.of(points[i].x,points[i].y,points[i].z)));
                    vertex.add("sampleRootRelative",JSON.toJsonTree(List.of(sample.x,sample.y,sample.z)));vertex.addProperty("sampleCell",cell.toShortString());
                    vertex.addProperty("brightness",brightness[i]);vertex.add("rgb",JSON.toJsonTree(List.of(r*brightness[i],g*brightness[i],b*brightness[i])));vertex.addProperty("packedLight",light[i]);
                    vertex.addProperty("physicalSky",client.world.getLightLevel(net.minecraft.world.LightType.SKY,cell));vertex.addProperty("physicalBlock",client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,cell));vertex.addProperty("skyVisible",client.world.isSkyVisible(cell));vertices.add(vertex);
                }
                row.add("exteriorVertices",vertices);
                rows.add(row);
            }
            public net.minecraft.client.render.VertexConsumer vertex(double x,double y,double z){return this;}public net.minecraft.client.render.VertexConsumer color(int r,int g,int b,int a){return this;}public net.minecraft.client.render.VertexConsumer texture(float u,float v){return this;}public net.minecraft.client.render.VertexConsumer overlay(int u,int v){return this;}public net.minecraft.client.render.VertexConsumer light(int u,int v){return this;}public net.minecraft.client.render.VertexConsumer normal(float x,float y,float z){return this;}public void next(){}public void fixedColor(int r,int g,int b,int a){}public void unfixColor(){}
        };
        if(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock){
            out.addProperty("renderer","StaticArchitectureClient");
            dev.dreamwalker.bloodbornedw.composite.StaticArchitectureClient.render(client.getBlockRenderManager().getModelRenderer(),client.world,state,pos,new net.minecraft.client.util.math.MatrixStack(),recorder,net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
        }else{
            out.addProperty("renderer","nativeBlockModelRenderer");long seed=state.getRenderingSeed(pos);
            client.getBlockRenderManager().getModelRenderer().render(client.world,client.getBlockRenderManager().getModel(state),state,pos,new net.minecraft.client.util.math.MatrixStack(),recorder,true,net.minecraft.util.math.random.Random.create(seed),seed,net.minecraft.client.render.OverlayTexture.DEFAULT_UV);
        }
        return out;
    }
    private static JsonArray pos(net.minecraft.util.math.BlockPos position){return JSON.toJsonTree(List.of(position.getX(),position.getY(),position.getZ())).getAsJsonArray();}
    /** One configured screenshot pixel projected through the actual camera. */
    private static JsonObject probePixel(MinecraftClient client,JsonArray pixel){
        int px=pixel.get(0).getAsInt(),py=pixel.get(1).getAsInt();int width=client.getWindow().getFramebufferWidth(),height=client.getWindow().getFramebufferHeight();
        require(width>0&&height>0&&px<width&&py<height,"probePixel is outside the actual framebuffer");
        double fov=client.options.getFov().getValue(),scale=Math.tan(Math.toRadians(fov*.5));double ndcX=2*(px+.5)/width-1,ndcY=1-2*(py+.5)/height;
        var camera=client.gameRenderer.getCamera();var forward=new org.joml.Vector3f(camera.getHorizontalPlane());var right=new org.joml.Vector3f(camera.getDiagonalPlane());var up=new org.joml.Vector3f(camera.getVerticalPlane());
        var direction=forward.fma((float)(ndcX*width/height*scale),right).fma((float)(ndcY*scale),up).normalize();
        var start=camera.getPos();var end=start.add(direction.x*128,direction.y*128,direction.z*128);
        var hit=client.world.raycast(new net.minecraft.world.RaycastContext(start,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
        JsonObject out=new JsonObject();out.add("pixel",JSON.toJsonTree(List.of(px,py)));out.add("framebuffer",JSON.toJsonTree(List.of(width,height)));out.addProperty("verticalFov",fov);out.add("start",JSON.toJsonTree(List.of(start.x,start.y,start.z)));out.add("direction",JSON.toJsonTree(List.of(direction.x,direction.y,direction.z)));out.add("end",JSON.toJsonTree(List.of(end.x,end.y,end.z)));out.addProperty("maxDistance",128);out.addProperty("shape","COLLIDER");out.addProperty("fluid","NONE");out.addProperty("hitType",hit.getType().name());out.add("hitPosition",JSON.toJsonTree(List.of(hit.getPos().x,hit.getPos().y,hit.getPos().z)));
        if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK)return out;var blockHit=hit;
        var hitPos=blockHit.getBlockPos();var state=client.world.getBlockState(hitPos);out.addProperty("block",hitPos.toShortString());out.addProperty("side",blockHit.getSide().getName());out.addProperty("state",state.toString());
        var root=hitPos;
        if(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock){
            var recipe=dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog.recipe(state);JsonArray candidates=new JsonArray();
            for(var fragment:recipe.fragments()){
                var origin=fragment.origin();var candidate=hitPos.add(origin.x(),origin.y(),origin.z());var candidateState=client.world.getBlockState(candidate);
                if(dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog.matchesRoot(candidateState,fragment.pose())){candidates.add(pos(candidate));root=candidate;}
            }
            out.addProperty("resolution","STATIC_FRAGMENT_ORIGIN_TO_ACTUAL_ROOT");out.add("rootCandidates",candidates);out.addProperty("actualRoot",root.toShortString());out.add("actualRootProbe",probe(client,pos(root)));
            return out;
        }
        if(client.world.getBlockEntity(hitPos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity entity){
            if(entity.resident()!=null)out.add("compositeBlockEntityProbe",probeComposite(client,hitPos,state,entity));
            else{
                var roots=new JsonArray();var seen=new java.util.HashSet<net.minecraft.util.math.BlockPos>();
                for(var entry:dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.contributions(client.world,hitPos)){
                    if(roots.size()>=4)break;var owner=entry.owner();var ownerRoot=dev.dreamwalker.bloodbornedw.composite.CompositeData.pos(owner.root());
                    if(seen.add(ownerRoot)&&client.world.getBlockEntity(ownerRoot) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity rootEntity&&owner.equals(rootEntity.resident()))roots.add(probeComposite(client,ownerRoot,client.world.getBlockState(ownerRoot),rootEntity));
                }
                out.addProperty("resolution","COMPOSITE_HELPER_TO_VALID_RESIDENT_ROOT");out.add("actualRootProbes",roots);
            }
        }
        else if(state.getRenderType()==net.minecraft.block.BlockRenderType.MODEL){
            out.addProperty("resolution","NATIVE_MODEL_COLLIDER");out.add("nativeModelProbe",probe(client,pos(hitPos)));
        }else out.addProperty("resolution","NATIVE_COLLIDER_WITHOUT_STATIC_OR_COMPOSITE_RENDERER");
        return out;
    }
    private static JsonObject probeComposite(MinecraftClient client,net.minecraft.util.math.BlockPos pos,net.minecraft.block.BlockState state,dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity entity){
        JsonObject out=new JsonObject();out.addProperty("position",pos.toShortString());out.addProperty("state",state.toString());out.addProperty("blockEntityClass",entity.getClass().getName());
        var resident=entity.resident();if(resident!=null){out.addProperty("sourceRegistryId",resident.registryId());out.addProperty("sourceInstance",resident.instanceId().toString());out.add("sourceRoot",JSON.toJsonTree(List.of(resident.root().x(),resident.root().y(),resident.root().z())));}
        if(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock block){
            out.addProperty("spec",block.spec.id.toString());var selected=dev.dreamwalker.bloodbornedw.composite.ImportedStairLegacyArt.selectedParts(block,state,entity.payload());out.addProperty("selectedPartCount",selected.size());JsonArray parts=new JsonArray();
            for(int i=0;i<selected.size()&&i<128;i++){var part=selected.get(i);JsonObject row=new JsonObject();row.addProperty("index",i);row.addProperty("model",part.model().toString());row.addProperty("altModel",part.altModel().toString());row.add("offset",JSON.toJsonTree(List.of(part.offset().x(),part.offset().y(),part.offset().z())));row.addProperty("yaw",part.yaw());row.addProperty("pitch",part.pitch());parts.add(row);}out.add("parts",parts);
        }
        CompositeRecorder recorder=new CompositeRecorder();net.minecraft.client.render.VertexConsumerProvider provider=recorder::layer;
        client.getBlockEntityRenderDispatcher().render(entity,client.getTickDelta(),new net.minecraft.client.util.math.MatrixStack(),provider);var rendered=recorder.result();var samples=rendered.getAsJsonArray("samples");
        for(int first=0;first+3<samples.size();first+=4){
            var center=new org.joml.Vector3f();for(int i=0;i<4;i++){var p=samples.get(first+i).getAsJsonObject().getAsJsonArray("position");center.add(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());}center.mul(.25f);
            for(int i=0;i<4;i++){var row=samples.get(first+i).getAsJsonObject();var p=row.getAsJsonArray("position");var n=row.getAsJsonArray("normal");var sample=new org.joml.Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()).lerp(center,.01f).add(n.get(0).getAsFloat()*.001f,n.get(1).getAsFloat()*.001f,n.get(2).getAsFloat()*.001f);var cell=net.minecraft.util.math.BlockPos.ofFloored(pos.getX()+sample.x,pos.getY()+sample.y,pos.getZ()+sample.z);row.addProperty("sampleCell",cell.toShortString());row.addProperty("physicalSky",client.world.getLightLevel(net.minecraft.world.LightType.SKY,cell));row.addProperty("physicalBlock",client.world.getLightLevel(net.minecraft.world.LightType.BLOCK,cell));row.addProperty("skyVisible",client.world.isSkyVisible(cell));}
        }
        out.add("actualDispatcherVertices",rendered);return out;
    }
    /** Separate provider forces CompositeWorldMesh's safe old path; it cannot enqueue a GPU batch. */
    private static final class CompositeRecorder implements net.minecraft.client.render.VertexConsumer {
        private final JsonArray samples=new JsonArray();private String layer="";private int count;private float minR=Float.POSITIVE_INFINITY,minG=minR,minB=minR,minA=minR,maxR=Float.NEGATIVE_INFINITY,maxG=maxR,maxB=maxR,maxA=maxR;private int minLight=Integer.MAX_VALUE,maxLight=Integer.MIN_VALUE;
        private double x,y,z;private int r=255,g=255,b=255,a=255,overlay,light;private float u,v,nx,ny,nz;
        net.minecraft.client.render.VertexConsumer layer(net.minecraft.client.render.RenderLayer value){layer=value.toString();return this;}
        public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int overlay,int light,float nx,float ny,float nz){
            count++;minR=Math.min(minR,r);minG=Math.min(minG,g);minB=Math.min(minB,b);minA=Math.min(minA,a);maxR=Math.max(maxR,r);maxG=Math.max(maxG,g);maxB=Math.max(maxB,b);maxA=Math.max(maxA,a);minLight=Math.min(minLight,light);maxLight=Math.max(maxLight,light);
            if(samples.size()<64){JsonObject row=new JsonObject();row.addProperty("layer",layer);row.add("position",JSON.toJsonTree(List.of(x,y,z)));row.add("rgba",JSON.toJsonTree(List.of(r,g,b,a)));row.add("uv",JSON.toJsonTree(List.of(u,v)));row.addProperty("overlay",overlay);row.addProperty("light",light);row.add("normal",JSON.toJsonTree(List.of(nx,ny,nz)));samples.add(row);}
        }
        JsonObject result(){JsonObject out=new JsonObject();out.addProperty("vertexCount",count);out.add("samples",samples);if(count>0){out.add("rgbaMin",JSON.toJsonTree(List.of(minR,minG,minB,minA)));out.add("rgbaMax",JSON.toJsonTree(List.of(maxR,maxG,maxB,maxA)));out.addProperty("packedLightMin",minLight);out.addProperty("packedLightMax",maxLight);}return out;}
        public net.minecraft.client.render.VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}public net.minecraft.client.render.VertexConsumer color(int r,int g,int b,int a){this.r=r;this.g=g;this.b=b;this.a=a;return this;}public net.minecraft.client.render.VertexConsumer texture(float u,float v){this.u=u;this.v=v;return this;}public net.minecraft.client.render.VertexConsumer overlay(int u,int v){overlay=u|(v<<16);return this;}public net.minecraft.client.render.VertexConsumer light(int u,int v){light=u|(v<<16);return this;}public net.minecraft.client.render.VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}public void next(){vertex((float)x,(float)y,(float)z,r/255f,g/255f,b/255f,a/255f,u,v,overlay,light,nx,ny,nz);}public void fixedColor(int r,int g,int b,int a){color(r,g,b,a);}public void unfixColor(){}
    }
    private void write(String status,boolean complete) throws Exception {
        report.addProperty("status",status);report.addProperty("captureComplete",complete);report.addProperty("capturedFrames",rows.size());
        Files.writeString(output,JSON.toJson(report));
    }
    private void fail(Throwable failure) {
        stopped=true;report.addProperty("failure",failure.toString());
        try {write("FAIL_CAPTURE",false);} catch (Exception ignored) {}
        failure.printStackTrace();
        if(stopAfterCapture)MinecraftClient.getInstance().scheduleStop();
    }
    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private static void require(boolean condition,String message) {if (!condition) throw new IllegalStateException(message);}
}
