package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.diagnostics.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Marker-only actual ordinary interaction plus bounded OFF/ON/OFF observation.
 * Runs only on the isolated copied review world; temporary placement is removed
 * through the ordinary creative break packet before matched frame windows/save.
 * Resource lookup/model selection is never called visual acceptance.
 */
final class DiagnosticsClientReviewProbe {
    private static final Identifier WINDOW=new Identifier("bloodborne_dw","prototype_glass_window_02");
    private final JsonObject options,result=new JsonObject();
    private final JsonArray windows=new JsonArray();
    private final Map<BlockPos,BlockState> before=new LinkedHashMap<>();
    private BlockPos root;private UUID session,instance;private int phase,ticks,phaseTicks;private long phaseStart;
    private ItemStack originalHand;private volatile boolean handRestored;
    private GameMode originalMode;private boolean modeRequested;private volatile boolean modePrepared,modeRestored;
    private volatile Throwable serverFailure;private volatile boolean prepared,cleaned,saved;private volatile JsonObject serverStatus;
    private volatile CompletableFuture<Path> serverExport;private boolean interactionSent,breakSent;
    DiagnosticsClientReviewProbe(JsonObject options){this.options=options;result.addProperty("schema","dw-actual-client-diagnostics-review-v1");result.addProperty("status","RUNNING");result.add("matchedPresentationWindows",windows);result.addProperty("manualVisualAcceptance","NOT_RUN");result.addProperty("performanceConclusion","Raw OFF/ON/OFF real presentation intervals; asynchronous rendering/GC/scheduler differences prevent exact causal overhead or object FPS percentage");}
    JsonObject report(){return result;}
    boolean tick(MinecraftClient client)throws Exception{
        ticks++;phaseTicks++;if(ticks>2400)throw new IllegalStateException("Bounded diagnostics QA timed out phase="+phase+" report="+result);
        if(serverFailure!=null)throw new IllegalStateException("Diagnostics QA server operation failed: "+serverFailure,serverFailure);
        int windowTicks=options.has("windowTicks")?options.get("windowTicks").getAsInt():100;
        int seconds=options.has("seconds")?options.get("seconds").getAsInt():20;
        if(windowTicks<60||windowTicks>200||seconds<15||seconds>60)throw new IllegalArgumentException("Diagnostics review requires windowTicks60..200 and seconds15..60");
        switch(phase){
            case 0->{
                if(DwClientDiagnostics.enabled())throw new IllegalStateException("Recording must initially be off");
                if(!modeRequested){originalMode=client.interactionManager.getCurrentGameMode();modeRequested=true;result.addProperty("originalJoinedGameMode",originalMode.getName());client.getServer().execute(()->{try{var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());if(player==null)throw new IllegalStateException("Actual joined player missing during isolated creative setup");player.changeGameMode(GameMode.CREATIVE);modePrepared=true;}catch(Throwable failure){serverFailure=failure;}});return false;}
                if(!modePrepared||client.interactionManager.getCurrentGameMode()!=GameMode.CREATIVE)return false;
                result.addProperty("explicitIsolatedCreativeSetupBeforeAllComparisonWindows",true);
                JsonArray p=options.getAsJsonArray("root");if(p==null||p.size()!=3)throw new IllegalArgumentException("Explicit isolated temporary diagnostics root required");root=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                if(root.getSquaredDistance(client.player.getBlockPos())>25)throw new IllegalArgumentException("Temporary root must be within ordinary interaction reach");
                Vec3d direction=new Vec3d(root.getX()+.5,root.getY()+.02,root.getZ()+.5).subtract(client.player.getEyePos());float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)),pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
                client.player.setYaw(yaw);client.player.setHeadYaw(yaw);client.player.setPitch(pitch);
                result.addProperty("cameraYaw",yaw);result.addProperty("cameraPitch",pitch);result.addProperty("cameraPosition",client.player.getPos().toString());result.add("temporaryRoot",p.deepCopy());
                originalHand=client.player.getMainHandStack().copy();result.addProperty("recordingInitiallyOff",true);phase=1;phaseTicks=0;
            }
            case 1->{if(phaseTicks<40)return false;DwClientDiagnostics.beginFrameComparisonWindow("OFF_BEFORE");phase=2;phaseTicks=0;phaseStart=System.nanoTime();}
            case 2->{
                if(phaseTicks<windowTicks)return false;windows.add(DwClientDiagnostics.endFrameComparisonWindow());phase=3;phaseTicks=0;
                client.getServer().execute(()->{try{
                    ServerPlayerEntity player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());if(player==null||!player.isCreative())throw new IllegalStateException("Actual joined creative player required");
                    var world=player.getServerWorld();BlockState floor=world.getBlockState(root.down());if(floor.isAir()||!floor.isFullCube(world,root.down())||world.getBlockEntity(root.down())!=null)throw new IllegalStateException("Existing native full top surface required; no QA support writes");before.put(root.down(),floor);
                    ItemStack item=new ItemStack(Registries.ITEM.get(WINDOW));ItemPlacementContext context=new ItemPlacementContext(world,player,Hand.MAIN_HAND,item,new BlockHitResult(new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.down(),false));
                    BlockState state=GlazingMount.placementState(Registries.BLOCK.get(WINDOW).getDefaultState(),context);var payload=GlazingMount.placementPayload(context,null);if(!GlazingMount.seat(world,root,state,payload))throw new IllegalStateException("Ordinary prospective seating failed");
                    var prospective=CompositeRuntime.instance(root,state,UUID.randomUUID(),payload);for(var cell:prospective.cells().keySet()){BlockPos pos=root.add(cell.x(),cell.y(),cell.z());if(!world.isChunkLoaded(pos))throw new IllegalStateException("No forcing unloaded temporary cells");BlockState nativeState=world.getBlockState(pos);if(!nativeState.isAir()||world.getBlockEntity(pos)!=null)throw new IllegalStateException("Actual prospective helper host not empty "+pos+" "+nativeState);before.put(pos,nativeState);}
                    result.addProperty("clickedNativeSurface",floor.toString());result.addProperty("clickedFace","UP");

                    session=DwDiagnostics.start(player.getServerWorld(),seconds,DwDiagnostics.Filter.all(player.getServerWorld()));DwDiagnostics.mark(player.getServerWorld(),"QA actual ordinary90010 placement and renderer acknowledgement; no source artwork edits");
                    player.getInventory().setStack(player.getInventory().selectedSlot,new ItemStack(Registries.ITEM.get(WINDOW)));player.currentScreenHandler.sendContentUpdates();prepared=true;
                }catch(Throwable failure){serverFailure=failure;}});
            }
            case 3->{
                if(!prepared||!DwClientDiagnostics.enabled()||!Registries.ITEM.getId(client.player.getMainHandStack().getItem()).equals(WINDOW))return false;
                if(!interactionSent){interactionSent=true;result.addProperty("clientOrdinaryInteractResult",client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.down(),false)).toString());}
                if(!(client.world.getBlockEntity(root) instanceof CompositeBlockEntity be)||be.resident()==null)return false;
                instance=be.resident().instanceId();JsonObject accepted=DwClientDiagnostics.acceptedPlacement(instance);if(accepted==null)return false;
                if(!accepted.get("typeId").getAsString().equals("90010")||!accepted.getAsJsonObject("before").get("heldItem").getAsString().equals(WINDOW.toString()))throw new IllegalStateException("Actual accepted item/type mismatch:"+accepted);
                result.add("actualServerPlacementAcknowledgement",accepted);result.addProperty("instanceId",instance.toString());result.addProperty("sessionId",session.toString());
                JsonObject render=DwClientDiagnostics.renderedPlacement(instance);if(render==null)return false;result.add("actualRendererAcknowledgement",render);
                if(!breakSent){var selected=CompositeRuntime.targetReadOnly(client.world,root,client.player);if(!be.resident().equals(selected)||!CompositeData.pos(selected.root()).equals(root)||!selected.registryId().equals(WINDOW.toString()))throw new IllegalStateException("Ordinary creative break ray did not select the exact placed owner/root: expected="+be.resident()+" actual="+selected+" root="+root+" eye="+client.player.getEyePos()+" direction="+client.player.getRotationVec(1));result.addProperty("ordinaryCreativeBreakSelectedOwner",selected.instanceId().toString());result.addProperty("ordinaryCreativeBreakSelectedRoot",CompositeData.pos(selected.root()).toShortString());breakSent=true;client.getServer().execute(()->{try{ServerPlayerEntity player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());DwDiagnostics.snapshot(player.getServerWorld(),player,root,Map.of("note","QA: ordinary server accepted and actual client renderer selected model"));}catch(Throwable failure){serverFailure=failure;}});result.addProperty("ordinaryCreativeBreakPacketSent",client.interactionManager.attackBlock(root,Direction.UP));}
                phase=4;phaseTicks=0;
            }
            case 4->{
                if(!client.world.getBlockState(root).isAir()||client.world.getBlockEntity(root)!=null)return false;
                if(!cleaned){client.getServer().execute(()->{try{var world=client.getServer().getOverworld();for(var entry:before.entrySet()){if(!world.getBlockState(entry.getKey()).equals(entry.getValue())||world.getBlockEntity(entry.getKey())!=null)throw new IllegalStateException("Ordinary cleanup did not restore exact temporary native state/BlockEntity "+entry.getKey());if(CompositeRuntime.contributions(world,entry.getKey()).stream().anyMatch(contribution->contribution.owner().instanceId().equals(instance)))throw new IllegalStateException("Ordinary cleanup left the temporary owner's contribution: cell="+entry.getKey()+" owner="+instance);}var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.getInventory().setStack(player.getInventory().selectedSlot,originalHand.copy());player.currentScreenHandler.sendContentUpdates();cleaned=true;handRestored=true;}catch(Throwable failure){serverFailure=failure;}});return false;}
                result.addProperty("temporaryNativeCellsRestored",before.size());result.addProperty("temporaryLedgerRemoved",true);phase=5;phaseTicks=0;
            }
            case 5->{if(phaseTicks<40||!handRestored||!ItemStack.areEqual(originalHand,client.player.getMainHandStack()))return false;result.addProperty("originalHandRestoredBeforeOnWindow",true);DwClientDiagnostics.beginFrameComparisonWindow("ON_IDENTICAL_CLEANED_SCENE");phase=6;phaseTicks=0;}
            case 6->{
                if(phaseTicks<windowTicks)return false;if(!DwClientDiagnostics.enabled())throw new IllegalStateException("Active session expired before matched ON window");windows.add(DwClientDiagnostics.endFrameComparisonWindow());
                result.add("clientBuffersAfterOnWindow",DwClientDiagnostics.snapshot());client.getServer().execute(()->{try{DwDiagnostics.mark(client.getServer().getOverworld(),"QA temporary object removed, native host restored, matched ON frame window ended");boolean nativeSave=client.getServer().save(false,true,false);result.addProperty("activeNativeSaveResult",nativeSave);saved=true;}catch(Throwable failure){serverFailure=failure;}});phase=7;phaseTicks=0;
            }
            case 7->{
                if(!saved||DwClientDiagnostics.enabled())return false;
                result.add("actualClientBatches",DwClientDiagnostics.recentBatches());client.getServer().execute(()->{try{serverStatus=new Gson().toJsonTree(DwDiagnostics.status(client.getServer())).getAsJsonObject();serverExport=DwDiagnostics.export(client.getServer());}catch(Throwable failure){serverFailure=failure;}});DwClientDiagnostics.beginFrameComparisonWindow("OFF_AFTER");phase=8;phaseTicks=0;
            }
            case 8->{
                if(phaseTicks<windowTicks)return false;windows.add(DwClientDiagnostics.endFrameComparisonWindow());phase=9;phaseTicks=0;
            }
            case 9->{
                if(serverStatus==null||serverExport==null||!serverExport.isDone())return false;
                if(!serverStatus.get("stopReason").getAsString().equals("AUTO_DURATION_EXPIRED"))throw new IllegalStateException("Actual automatic session expiry not observed:"+serverStatus);
                Path local=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("diagnostics/dreamwalker-client-"+session+".zip");if(!Files.isRegularFile(local))return false;
                Path server=serverExport.get();try(ZipFile zip=new ZipFile(local.toFile())){for(String name:List.of("summary.json","runtime.json","graphics.json","client-batches.json","errors.json","partial-window.json","tail-events.json"))if(zip.getEntry(name)==null)throw new IllegalStateException("Missing actual client export member:"+name);}
                JsonArray batches=result.getAsJsonArray("actualClientBatches");if(batches.size()<3)throw new IllegalStateException("Actual bounded telemetry batches not observed");long frames=0;for(JsonElement raw:batches)frames+=raw.getAsJsonObject().getAsJsonObject("frames").get("measurements").getAsLong();if(frames<10)throw new IllegalStateException("Actual flip-frame samples absent");
                result.addProperty("actualFrameIntervalsRecorded",frames);result.add("serverStatusAfterAutomaticExpiry",serverStatus);result.addProperty("clientExport",local.toString());result.addProperty("serverExport",server.toString());result.addProperty("clientExportBytes",Files.size(local));result.addProperty("serverExportBytes",Files.size(server));result.addProperty("recordingFinallyOff",!DwClientDiagnostics.enabled());result.addProperty("matchedCameraUnchanged",Math.abs(client.player.getYaw()-result.get("cameraYaw").getAsFloat())<.01&&Math.abs(client.player.getPitch()-result.get("cameraPitch").getAsFloat())<.01);if(!result.get("matchedCameraUnchanged").getAsBoolean())throw new IllegalStateException("Camera changed during matched diagnostics windows");client.getServer().execute(()->{try{var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());player.changeGameMode(originalMode);modeRestored=true;}catch(Throwable failure){serverFailure=failure;}});phase=10;phaseTicks=0;
            }
            case 10->{if(!modeRestored||client.interactionManager.getCurrentGameMode()!=originalMode)return false;result.addProperty("originalJoinedGameModeRestored",true);result.addProperty("status","PASS_ACTUAL_ORDINARY_ITEM_SERVER_ACK_RENDER_ACK_CLEANUP_TELEMETRY_EXPORT_OFF_ON_OFF");return true;}
            default->throw new IllegalStateException("Invalid QA phase");
        }return false;
    }
    private static JsonObject renderAcknowledgement(UUID instance){
        JsonArray rows=DwClientDiagnostics.recentEvents();for(JsonElement raw:DwClientDiagnostics.recentBatches()){JsonArray events=raw.getAsJsonObject().getAsJsonArray("events");if(events!=null)events.forEach(rows::add);}
        for(JsonElement raw:rows){JsonObject row=raw.getAsJsonObject();if(row.has("action")&&row.get("action").getAsString().equals("visual_model_chain")&&row.has("instanceId")&&row.get("instanceId").getAsString().equals(instance.toString())&&row.has("serverPlacementAcknowledgement")&&row.get("serverPlacementAcknowledgement").isJsonObject()&&row.get("purpose").getAsString().equals("world-root-render"))return row.deepCopy();}return null;
    }
}
