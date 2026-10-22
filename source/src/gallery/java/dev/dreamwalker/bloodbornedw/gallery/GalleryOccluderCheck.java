package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Addressed temporary occluder exercise on a working-copy integrated server.
 * Only the reviewed AIR cell is changed; the static root is always read-only. */
final class GalleryOccluderCheck {
    private static final Gson JSON=new Gson();
    private final Path gameDir;private final BlockPos cell,root;private final String prefix,expectedRootId;private final int expectedPart;
    private final JsonObject report=new JsonObject();private final JsonArray snapshots=new JsonArray();
    private MinecraftServer server;private ServerWorld serverWorld;private BlockState rootBefore,original;
    private volatile boolean serverAck,temporaryPlaced,restored;private volatile String serverFailure;
    private volatile String initialCellState,initialBlockEntity;
    private volatile boolean screenshotSaved;private boolean screenshotPending,complete,passed;
    private String phase="BOOT",stage="before";private int ticks,stable,beforeQuads,coveredQuads,restoredQuads;
    private JsonObject beforeProbe,restoredProbe;

    GalleryOccluderCheck(JsonObject input,Path gameDir)throws Exception{
        this.gameDir=gameDir;cell=position(input.getAsJsonArray("position"));root=position(input.getAsJsonArray("root"));prefix=input.get("filePrefix").getAsString();
        expectedRootId=input.get("expectedRootBlock").getAsString();expectedPart=input.get("expectedRootPart").getAsInt();
        require(prefix.matches("[A-Za-z0-9_-]{1,64}"),"Unsafe occluder screenshot prefix");
        require(cell.getSquaredDistance(root)<=16&&!cell.equals(root),"Occluder must be within four blocks of a distinct root");
        for(String label:List.of("before","covered","restored"))require(!Files.exists(imagePath(label)),"Occluder evidence already exists");
        report.addProperty("schema","dreamwalker-addressed-occluder-v1");report.add("cell",position(cell));report.add("root",position(root));report.addProperty("expectedRootBlock",expectedRootId);report.addProperty("expectedRootPart",expectedPart);report.add("snapshots",snapshots);
        // Runs on the native server thread before saving; this remains armed
        // even if a client-side capture exception starts ordinary shutdown.
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping->{if(stopping==server)restoreOnServer();});
        update();
    }
    boolean tick(MinecraftClient client)throws Exception{
        if(complete)return true;require(++ticks<=7200,"Temporary occluder check timed out");
        require(client.world!=null&&client.player!=null,"Occluder client world disappeared");
        if(phase.equals("BOOT")){
            server=client.getServer();require(server!=null,"Occluder check requires an integrated server");serverWorld=server.getWorld(client.world.getRegistryKey());require(serverWorld!=null,"Integrated dimension unavailable");
            serverAck=false;server.execute(()->{
                try{
                    original=serverWorld.getBlockState(cell);var blockEntity=serverWorld.getBlockEntity(cell);initialCellState=original.toString();initialBlockEntity=blockEntity==null?"NONE":blockEntity.getClass().getName();
                    require(original==Blocks.AIR.getDefaultState()&&blockEntity==null,"Occluder cell is not exact empty AIR: state="+initialCellState+", blockEntity="+initialBlockEntity);
                    rootBefore=serverWorld.getBlockState(root);require(rootBefore.getBlock() instanceof StaticArchitectureBlock,"Occluder root is not static architecture");
                    require(Registries.BLOCK.getId(rootBefore.getBlock()).toString().equals(expectedRootId)&&StaticArchitectureCatalog.recipe(rootBefore).index()==expectedPart,"Occluder root recipe differs");
                }catch(Throwable failure){serverFailure=failure.toString();}finally{serverAck=true;}
            });phase="WAIT_BEFORE";stable=0;update();return false;
        }
        if(!serverAck)return false;require(serverFailure==null,serverFailure);
        require(client.world.getBlockState(root)==rootBefore,"Static root changed during occluder check");
        if(screenshotPending){
            if(!screenshotSaved)return false;Path file=imagePath(stage);require(Files.isRegularFile(file)&&Files.size(file)>24,"Occluder screenshot missing");
            JsonObject snapshot=snapshots.get(snapshots.size()-1).getAsJsonObject();snapshot.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));snapshot.addProperty("bytes",Files.size(file));screenshotPending=false;
            if(stage.equals("before")){requestCover();stage="covered";phase="WAIT_COVERED";stable=0;}
            else if(stage.equals("covered")){requestRestore();stage="restored";phase="WAIT_RESTORED";stable=0;}
            else{
                require(restored&&client.world.getBlockState(cell)==original,"Occluder rollback did not reach client");
                boolean probeRestored=beforeProbe!=null&&beforeProbe.equals(restoredProbe);
                var images=compareImages();report.add("imageComparison",images);
                passed=beforeQuads>0&&restoredQuads==beforeQuads&&probeRestored&&images.get("coveredChangedPixelsInTargetArea").getAsInt()>0&&images.get("restoredChangedPixelsInTargetArea").getAsInt()==0;
                report.addProperty("beforeQuads",beforeQuads);report.addProperty("coveredQuads",coveredQuads);report.addProperty("restoredQuads",restoredQuads);
                report.addProperty("rootUnchanged",true);report.addProperty("airRestored",true);report.addProperty("hiddenFaceRemoved",beforeQuads>coveredQuads);report.addProperty("hiddenFaceReturned",restoredQuads==beforeQuads);
                report.addProperty("rootGeometryAndLightRestored",probeRestored);report.addProperty("status",passed?"PASS_NATIVE_OCCLUDER_AND_RESTORE":"FAIL_EXPECTED_IMAGE_OR_RESTORATION");complete=true;phase="COMPLETE";
            }
            update();return complete;
        }
        BlockState expected=stage.equals("covered")?Blocks.STONE.getDefaultState():original;
        if(client.world.getBlockState(cell)!=expected||!client.worldRenderer.isTerrainRenderComplete()){stable=0;return false;}
        if(++stable<60)return false;
        var probe=GalleryClientCapture.probe(client,position(root));int count=probe.has("quads")?probe.getAsJsonArray("quads").size():0;
        if(stage.equals("before")){beforeQuads=count;beforeProbe=probe.deepCopy();}else if(stage.equals("covered"))coveredQuads=count;else{restoredQuads=count;restoredProbe=probe.deepCopy();}
        var snapshot=new JsonObject();snapshot.addProperty("stage",stage);snapshot.addProperty("fileName",imagePath(stage).getFileName().toString());snapshot.addProperty("worldTick",client.world.getTime());snapshot.addProperty("terrainComplete",true);snapshot.addProperty("occluderState",client.world.getBlockState(cell).toString());snapshot.add("rootProbe",probe);snapshot.add("staticGeometry",JSON.toJsonTree(StaticArchitectureGeometry.metrics()));snapshot.add("camera",JSON.toJsonTree(List.of(client.player.getX(),client.player.getY(),client.player.getZ(),client.player.getYaw(),client.player.getPitch())));snapshots.add(snapshot);
        screenshotPending=true;screenshotSaved=false;phase="SAVE_"+stage.toUpperCase(Locale.ROOT);update();
        ScreenshotRecorder.saveScreenshot(gameDir.toFile(),imagePath(stage).getFileName().toString(),client.getFramebuffer(),message->client.execute(()->screenshotSaved=true));return false;
    }
    private void requestCover(){serverAck=false;server.execute(()->{
        try{
            require(serverWorld.getBlockState(root)==rootBefore&&serverWorld.getBlockState(cell)==original,"Occluder preconditions changed before cover");
            // Mark before mutation so every exceptional exit remains recoverable.
            temporaryPlaced=true;serverWorld.setBlockState(cell,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);require(serverWorld.getBlockState(cell)==Blocks.STONE.getDefaultState(),"Native occluder placement failed");
        }catch(Throwable failure){serverFailure=failure.toString();}finally{if(serverFailure!=null)restoreOnServer();serverAck=true;}
    });}
    private void requestRestore(){serverAck=false;server.execute(()->{
        try{require(serverWorld.getBlockState(root)==rootBefore,"Static root changed before restore");}
        catch(Throwable failure){serverFailure=failure.toString();}
        finally{restoreOnServer();serverAck=true;}
    });}
    /** Called by the existing capture's failure path and again at native save. */
    void restoreFinally(){if(server!=null&&temporaryPlaced&&!restored)server.execute(this::restoreOnServer);}
    private void restoreOnServer(){
        if(!temporaryPlaced||restored||serverWorld==null||original==null)return;
        try{serverWorld.setBlockState(cell,original,Block.NOTIFY_ALL);restored=serverWorld.getBlockState(cell)==original;if(!restored)serverFailure="Exact native AIR restore failed";}
        catch(Throwable failure){serverFailure="Exact native AIR restore failed: "+failure;}
    }
    private JsonObject compareImages()throws Exception{
        var before=javax.imageio.ImageIO.read(imagePath("before").toFile());var covered=javax.imageio.ImageIO.read(imagePath("covered").toFile());var after=javax.imageio.ImageIO.read(imagePath("restored").toFile());
        require(before!=null&&covered!=null&&after!=null,"Occluder image cannot be decoded");int width=before.getWidth(),height=before.getHeight();require(covered.getWidth()==width&&after.getWidth()==width&&covered.getHeight()==height&&after.getHeight()==height,"Occluder framebuffer changed size");
        // Fixed close-up camera centres the addressed facade. Exclude the sky
        // and other peripheral animations from exact restoration comparison.
        int x0=Math.max(0,width/2-128),x1=Math.min(width,width/2+128),y0=Math.max(0,height/2-128),y1=Math.min(height,height/2+128),coverDiff=0,restoreDiff=0,totalCoverDiff=0,totalRestoreDiff=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){boolean changed=before.getRGB(x,y)!=covered.getRGB(x,y),notRestored=before.getRGB(x,y)!=after.getRGB(x,y);if(changed)totalCoverDiff++;if(notRestored)totalRestoreDiff++;if(x>=x0&&x<x1&&y>=y0&&y<y1){if(changed)coverDiff++;if(notRestored)restoreDiff++;}}
        var result=new JsonObject();result.add("targetAreaPixels",JSON.toJsonTree(List.of(x0,y0,x1,y1)));result.addProperty("coveredChangedPixelsInTargetArea",coverDiff);result.addProperty("restoredChangedPixelsInTargetArea",restoreDiff);result.addProperty("coveredChangedPixelsInFullFrame",totalCoverDiff);result.addProperty("restoredChangedPixelsInFullFrame",totalRestoreDiff);return result;
    }
    boolean passed(){return passed;}JsonObject report(){update();return report.deepCopy();}
    private void update(){report.addProperty("phase",phase);report.addProperty("ticks",ticks);report.addProperty("complete",complete);report.addProperty("temporaryPlaced",temporaryPlaced);report.addProperty("restored",restored);if(initialCellState!=null)report.addProperty("actualInitialState",initialCellState);if(initialBlockEntity!=null)report.addProperty("actualInitialBlockEntity",initialBlockEntity);if(serverFailure!=null)report.addProperty("failure",serverFailure);}
    private Path imagePath(String label){return gameDir.resolve("screenshots").resolve(prefix+"-"+label+".png");}
    private static BlockPos position(JsonArray a){require(a!=null&&a.size()==3,"Expected bounded block position");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static JsonArray position(BlockPos p){return JSON.toJsonTree(List.of(p.getX(),p.getY(),p.getZ())).getAsJsonArray();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
