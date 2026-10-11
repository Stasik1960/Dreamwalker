package dev.dreamwalker.bloodbornedw.remainingqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Baked-resource audit, optional framebuffer captures and explicit network QA
 * in an isolated test client; never physical mouse/keyboard acceptance. */
public final class RemainingTransferClientQa implements ClientModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private Path gameDir,output,captureOutput;
    private final JsonObject report=new JsonObject(),capture=new JsonObject();
    private final JsonArray captureRows=new JsonArray();
    private JsonArray frames;
    private JsonObject savingFrame;
    private int readyTicks,index,age,settled;
    private boolean auditDone,captureStopped,sent,waiting,renderReady;
    private volatile boolean saved;
    private RemainingTransferNetworkQa network;

    @Override public void onInitializeClient() {
        var client=MinecraftClient.getInstance();gameDir=client.runDirectory.toPath().toAbsolutePath().normalize();
        output=gameDir.resolve("remaining-transfer-client-qa.json");captureOutput=gameDir.resolve("remaining-transfer-capture.json");
        try {
            require(!Files.exists(output),"Preserving existing QA evidence; use a fresh client run");
            var paths=FabricLoader.getInstance().getModContainer("bloodborne_dw").orElseThrow().getOrigin().getPaths();
            require(paths.size()==1&&Files.isRegularFile(paths.get(0),LinkOption.NOFOLLOW_LINKS),"Production must load from one ordinary JAR");
            String bound=sha(paths.get(0));
            report.addProperty("schema","dreamwalker-remaining-transfer-client-qa-v1");report.addProperty("productionJarSha256",bound);
            report.addProperty("clientGameplayInput","NOT_RUN");report.addProperty("visualAppearanceAcceptance","NOT_RUN");
            report.addProperty("scope","Every source_ composite part model and offered variant icon; no production behavior is changed");
            write("WAITING_FOR_WORLD");
            network=new RemainingTransferNetworkQa(client,gameDir,bound);
            Path input=gameDir.resolve("remaining-transfer-qa-input.json");
            if(Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)) {
                require(Files.size(input)<=1048576,"Capture marker exceeds 1 MiB");
                var marker=JsonParser.parseString(Files.readString(input)).getAsJsonObject();
                require(bound.equals(marker.get("productionJarSha256").getAsString()),"Capture marker must bind the installed production JAR");
                frames=marker.getAsJsonArray("frames");require(frames!=null&&!frames.isEmpty()&&frames.size()<=1000,"Capture requires 1..1000 bounded frames");
                Set<String> names=new HashSet<>();
                for(var element:frames) {
                    var frame=element.getAsJsonObject();String file=frame.get("fileName").getAsString();
                    require(file.matches("[A-Za-z0-9_-]{1,96}\\.png")&&names.add(file)&&!Files.exists(gameDir.resolve("screenshots").resolve(file)),"Fresh safe PNG name required");
                    var camera=frame.getAsJsonArray("camera");require(camera!=null&&camera.size()==5,"Camera is x,y,z,yaw,pitch");
                    for(var value:camera)require(Double.isFinite(value.getAsDouble()),"Finite camera required");
                    require(Math.abs(camera.get(4).getAsDouble())<=90,"Invalid camera pitch");
                    String command=frame.get("command").getAsString();require(command.startsWith("tp @s ")&&command.length()<256&&!command.contains("\n")&&!command.contains("\r"),"Only camera tp is allowed");
                    int ticks=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():40;require(ticks>=1&&ticks<=200,"waitTicks outside 1..200");
                }
                require(!Files.exists(captureOutput),"Preserving existing capture evidence");
                capture.addProperty("schema","dreamwalker-remaining-transfer-capture-v1");capture.addProperty("productionJarSha256",bound);
                capture.addProperty("method","Minecraft ScreenshotRecorder framebuffer; camera navigation only, no gameplay input");
                capture.addProperty("expectedFrames",frames.size());capture.add("frames",captureRows);writeCapture("WAITING_FOR_WORLD");
                WorldRenderEvents.END.register(context->{if(!captureStopped&&sent&&!waiting&&client.player!=null&&client.world!=null&&atCamera(client,frames.get(index).getAsJsonObject().getAsJsonArray("camera")))renderReady=true;});
            } else captureStopped=true;
            ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        } catch(Throwable failure) {fail(failure);}
    }

    private void tick(MinecraftClient client) {
        try {
            if(!auditDone) {
                if(client.player==null||client.world==null||client.currentScreen!=null||client.isPaused())return;
                if(++readyTicks<40)return;
                audit(client);auditDone=true;
            }
            if(network!=null&&!network.complete()){network.tick(client);return;}
            if(!captureStopped)captureTick(client);
        } catch(Throwable failure) {fail(failure);}
    }

    private void audit(MinecraftClient client)throws Exception {
        var additions=CompositeArchitecture.blocks().stream().filter(block->block.spec.id.getPath().startsWith("source_")).toList();
        require(!additions.isEmpty(),"No new source_ composites registered");
        var manager=client.getBakedModelManager();var missing=manager.getMissingModel();
        Set<Identifier> models=new TreeSet<>(Comparator.comparing(Identifier::toString));
        for(var block:additions)for(var variant:block.spec.variants)for(var pose:List.of(variant.closed(),variant.open()))for(var part:pose.parts()) {models.add(part.model());models.add(part.altModel());}
        JsonArray bakedRows=new JsonArray(),failures=new JsonArray(),itemRows=new JsonArray(),poseRows=new JsonArray();
        Map<Identifier,Integer> quadsByModel=new HashMap<>();
        for(var id:models) {
            var row=new JsonObject();row.addProperty("model",id.toString());
            boolean resource=client.getResourceManager().getResource(new Identifier(id.getNamespace(),"models/"+id.getPath()+".json")).isPresent();
            var model=manager.getModel(id);boolean resolved=model!=missing;Set<String> sprites=new TreeSet<>();int quadCount=0;
            if(resolved)for(int side=-1;side<6;side++)for(var quad:model.getQuads(null,side<0?null:Direction.values()[side],net.minecraft.util.math.random.Random.create(0))) {quadCount++;sprites.add(quad.getSprite().getContents().getId().toString());}
            boolean noMissing=sprites.stream().noneMatch(sprite->sprite.equals("minecraft:missingno"));
            boolean emptyService=quadCount==0&&knownEmptyService(client,id);
            boolean passed=resource&&resolved&&(quadCount>0||emptyService)&&noMissing;
            quadsByModel.put(id,quadCount);
            row.addProperty("knownEmptySourceServicePart",emptyService);
            row.addProperty("resourceExists",resource);row.addProperty("bakedModelResolved",resolved);row.addProperty("quads",quadCount);row.add("sprites",JSON.toJsonTree(sprites));row.addProperty("status",passed?"PASS":"FAIL");
            bakedRows.add(row);if(!passed)failures.add("Missing/empty model or sprite: "+id);
        }
        for(var block:additions)for(int variant=0;variant<block.spec.variants.size();variant++)for(var poseEntry:Map.of("closed",block.spec.variants.get(variant).closed(),"open",block.spec.variants.get(variant).open()).entrySet())for(boolean alt:new boolean[]{false,true}) {
            int total=poseEntry.getValue().parts().stream().mapToInt(part->quadsByModel.getOrDefault(alt?part.altModel():part.model(),0)).sum();
            var row=new JsonObject();row.addProperty("id",block.spec.id.toString());row.addProperty("variant",variant);row.addProperty("pose",poseEntry.getKey());row.addProperty("profile",alt?"alt":"base");row.addProperty("quads",total);row.addProperty("status",total>0?"PASS":"FAIL");poseRows.add(row);
            if(total==0)failures.add("Whole offered pose is empty: "+block.spec.id+" variant="+variant+" "+poseEntry.getKey());
        }
        var offered=UnifiedCreativeCatalogue.canonicalItems();
        for(var block:additions)for(int variant=0;variant<block.spec.variants.size();variant++) {
            final int selected=variant;
            var found=offered.stream().filter(stack->stack.isOf(block.asItem())&&(stack.getSubNbt("BlockStateTag")==null?0:Integer.parseInt(stack.getSubNbt("BlockStateTag").getString("variant")))==selected).findFirst();
            var row=new JsonObject();row.addProperty("id",block.spec.id.toString());row.addProperty("variant",variant);row.addProperty("offered",found.isPresent());
            boolean passed=false;
            if(found.isPresent()) {
                var stack=found.get();BakedModel icon=client.getItemRenderer().getModel(stack,client.world,client.player,0);
                row.addProperty("russianName",stack.getName().getString());row.addProperty("builtinCompositeRenderer",icon.isBuiltin());row.addProperty("itemModelResolved",icon!=missing);
                passed=icon!=missing&&icon.isBuiltin()&&stack.getName().getString().matches(".*[А-Яа-яЁё].*");
            }
            row.addProperty("status",passed?"PASS":"FAIL");itemRows.add(row);if(!passed)failures.add("Missing offered variant/icon/name: "+block.spec.id+" variant="+variant);
        }
        report.addProperty("objects",additions.size());report.addProperty("partModels",models.size());report.addProperty("offeredVariants",itemRows.size());
        report.add("models",bakedRows);report.add("items",itemRows);report.add("failures",failures);
        report.add("wholePoses",poseRows);
        var tint=RemainingTransferTintQa.audit(client);report.add("grassTintRenderer",tint);if(!tint.get("status").getAsString().equals("PASS"))failures.add("Authored grass RGB renderer or white old/source control differs");
        write(failures.isEmpty()?"PASS":"FAIL");
    }
    private static boolean knownEmptyService(MinecraftClient client,Identifier id)throws Exception {
        if(!Set.of("bloodborne_dw:remaining/source/minecraft/block/cyan_stained_glass_pane_post","bloodborne_dw:remaining/source/minecraft/block/yellow_stained_glass_pane_post").contains(id.toString()))return false;
        var resource=client.getResourceManager().getResource(new Identifier(id.getNamespace(),"models/"+id.getPath()+".json"));if(resource.isEmpty())return false;
        try(var reader=resource.get().getReader()) {var raw=JsonParser.parseReader(reader).getAsJsonObject();return !raw.has("parent")&&(!raw.has("elements")||raw.getAsJsonArray("elements").isEmpty())&&raw.getAsJsonObject("textures").keySet().equals(Set.of("particle"));}
    }

    private void captureTick(MinecraftClient client)throws Exception {
        if(waiting) {
            require(++age<=200,"Native screenshot save timed out");if(!saved)return;
            Path png=gameDir.resolve("screenshots").resolve(savingFrame.get("fileName").getAsString());
            require(Files.isRegularFile(png,LinkOption.NOFOLLOW_LINKS)&&Files.size(png)>24,"Native PNG missing");
            var decoded=ImageIO.read(png.toFile());require(decoded!=null,"Native PNG not decodable");
            savingFrame.addProperty("sha256",sha(png));savingFrame.addProperty("bytes",Files.size(png));savingFrame.addProperty("width",decoded.getWidth());savingFrame.addProperty("height",decoded.getHeight());
            captureRows.add(savingFrame);index++;sent=false;waiting=false;settled=0;age=0;captureStopped=index==frames.size();writeCapture(captureStopped?"CAPTURE_COMPLETE":"RUNNING");return;
        }
        if(client.player==null||client.world==null||client.currentScreen!=null||client.isPaused())return;
        var frame=frames.get(index).getAsJsonObject();var camera=frame.getAsJsonArray("camera");
        if(!sent) {client.player.networkHandler.sendChatCommand(frame.get("command").getAsString());sent=true;renderReady=false;age=0;return;}
        require(++age<=600,"Camera did not settle");boolean at=atCamera(client,camera);if(!at)renderReady=false;settled=at?settled+1:0;
        int ticks=frame.has("waitTicks")?frame.get("waitTicks").getAsInt():40;if(settled<ticks||!renderReady)return;
        savingFrame=frame.deepCopy();savingFrame.addProperty("worldTick",client.world.getTime());savingFrame.add("actualCamera",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));
        saved=false;waiting=true;age=0;ScreenshotRecorder.saveScreenshot(gameDir.toFile(),frame.get("fileName").getAsString(),client.getFramebuffer(),message->client.execute(()->saved=true));
    }
    private static boolean atCamera(MinecraftClient client,JsonArray camera) {
        double dx=client.player.getX()-camera.get(0).getAsDouble(),dy=client.player.getY()-camera.get(1).getAsDouble(),dz=client.player.getZ()-camera.get(2).getAsDouble();
        return dx*dx+dy*dy+dz*dz<.0025&&Math.abs(MathHelper.wrapDegrees(client.player.getYaw()-camera.get(3).getAsDouble()))<.5&&Math.abs(client.player.getPitch()-camera.get(4).getAsDouble())<.5&&client.world.isChunkLoaded(client.player.getBlockPos());
    }
    private void write(String status)throws Exception {report.addProperty("status",status);report.addProperty("updatedUtc",Instant.now().toString());Files.writeString(output,JSON.toJson(report));}
    private void writeCapture(String status)throws Exception {capture.addProperty("status",status);capture.addProperty("capturedFrames",captureRows.size());Files.writeString(captureOutput,JSON.toJson(capture));}
    private void fail(Throwable failure) {auditDone=true;captureStopped=true;report.addProperty("failure",failure.toString());try {write("FAIL");if(frames!=null) {capture.addProperty("failure",failure.toString());writeCapture("FAIL");}} catch(Exception ignored) {}failure.printStackTrace();}
    private static String sha(Path path)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
