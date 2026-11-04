package dev.dreamwalker.editorqa;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedStairBlock;
import dev.dreamwalker.bloodbornedw.composite.ImportedStairShapes;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.composite.SourceFallbackCarrierV1271;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import dev.dreamwalker.bloodbornedw.editor.CityEditorServer;
import dev.dreamwalker.bloodbornedw.editor.EditorCells;
import dev.dreamwalker.bloodbornedw.editor.EditorData;
import dev.dreamwalker.bloodbornedw.editor.StonePaletteSupport;
import dev.dreamwalker.bloodbornedw.editor.catalogue.EditorCatalogue;
import dev.dreamwalker.bloodbornedw.visual.CityVisualModeState;
import dev.dreamwalker.bloodbornedw.visual.CityVisualClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/** Disposable integrated-world verification. This source set is never included in production jars. */
public final class EditorQa implements ClientModInitializer {
    private enum Phase {
        START, PREPARE, PLACE_FIXTURE, PENDING_REPLACEMENT, WAIT_PENDING_REPLACEMENT, QUICK_PREPARE, QUICK_CLICK, WAIT_QUICK_CLICK, QUICK_UNDO, WAIT_QUICK_UNDO, QUICK_UNDO_FIRST, WAIT_QUICK_UNDO_FIRST, AUTO_SAMPLE_CONFIG,AUTO_SAMPLE,SINGLE_CONFIG, SINGLE_PAINT, WAIT_SINGLE, SAMPLE_CONFIG, SAMPLE,
        RESET, WAIT_RESET, UNDO, WAIT_UNDO, REDO, WAIT_REDO, FIRST, SECOND, AREA_CONFIG, AREA_PAINT,
        WAIT_AREA, PLANE_CONFIG,PLANE_PAINT,WAIT_PLANE,PLANE_UNDO,WAIT_PLANE_UNDO,FILL_CONFIG, FILL, WAIT_FILL, NOISE_CONFIG, NOISE, WAIT_NOISE, REJECT_STAMP, STROKE_CONFIG, STROKE_PAINT_A, STROKE_WAIT_A,
        STROKE_PAINT_B, STROKE_WAIT_B, STROKE_END, STROKE_WAIT_END, STROKE_UNDO, STROKE_WAIT_UNDO, STROKE_REDO, STROKE_WAIT_REDO,
        INTERRUPT_PREPARE, WAIT_INTERRUPT_READY, INTERRUPT_CONFIG, INTERRUPT_FIRST, INTERRUPT_SECOND, INTERRUPT_PAINT,
        WAIT_INTERRUPT_PAINT, INTERRUPT_UNDO, WAIT_INTERRUPT, STALE_UNDO, WAIT_STALE_UNDO, CLEAR_GRID,
        QUICK_RECOVER_CANCEL, QUICK_RECOVER_WAIT,
        CATALOG_PLACE, CATALOG_CLEAR, CATALOG_RESTORE_VERIFY, CLEANUP, PERSIST_WRITE_PREP, PERSIST_WRITE_APPLY, PERSIST_WRITE_SAVE,
        PERSIST_VERIFY_CLIENT, PERSIST_VERIFY_BREAK, DONE
    }
    private record SectionFixture(BlockPos pos, BlockState state, int editorStyle, int nativeIndex) {}
    private record PlacementStats(int changed, int supportedAdded, boolean baseStyle) {}
    private record CataloguePlacementContext(JsonObject evidence, BlockHitResult hit) {}

    private final JsonObject report = new JsonObject();
    private final JsonArray checks = new JsonArray(), failures = new JsonArray(), unsupported = new JsonArray(), items = new JsonArray();
    private volatile boolean running, done, interruptArmed;
    private volatile Phase phase = Phase.START;
    private volatile int ticks;
    private int waitTicks, cursor, placed, cleared, fixtureCursor, catalogueAttempts, pendingReplacementTick;
    private int lastActionTick = -100, nativeIndexBeforePaint;
    private BlockState stateBeforePaint;
    private List<ItemStack> catalogue = List.of();
    private List<ItemStack> catalogueRun = List.of();
    private final Set<String> catalogueExcludedVerified = new LinkedHashSet<>();
    private final Set<String> catalogueRemovedVerified = new LinkedHashSet<>();
    private final boolean catalogueOnly=Boolean.getBoolean("dw.editorqa.catalogueOnly");
    private final boolean editor2Only=Boolean.getBoolean("dw.editorqa.editor2Only");
    private final boolean functionalAfterEditor2=Boolean.getBoolean("dw.editorqa.functionalAfterEditor2");
    private boolean functionalAfterEditor2Started;
    private final List<BlockPos> editor2Targets = new ArrayList<>();
    private final JsonArray editor2QuadEvidence = new JsonArray();
    private final JsonArray editor2ClientLatencies = new JsonArray();
    private int editor2Target, editor2Colour, editor2Stage, editor2Before, editor2Native, editor2ChunkSize, editor2LastAction=-100;
    private BlockState editor2State;
    private ItemStack editor2Held=ItemStack.EMPTY;
    private boolean editor2ActorOwned,editor2OriginalFlying,editor2OriginalNoGravity;
    private NbtCompound editor2BlockEntity;
    private int editor2Light;
    private long editor2Epoch;
    private long editor2WaitStart,editor2WaitDeadline;
    private int editor2WaitServerTick,editor2WaitClientTick;
    private boolean editor2ClientLatencyRecorded;
    private boolean editor2MatrixDone, editor2WallOwned, editor2FloorOwned;
    private static final BlockPos EDITOR2_WALL=new BlockPos(117,200,-1091);
    private boolean catalogueFiltered;
    private final List<BlockPos> gridFloors = new ArrayList<>(130), gridTargets = new ArrayList<>(130);
    private List<SectionFixture> fixtureBaseline = List.of();
    private List<SectionFixture> catalogBaseline = List.of();
    private BlockPos baseFloor, sampleTarget, autoSamplePos, catalogSupport, catalogFloor, catalogTarget, interruptedPos, pendingPos;
    private BlockPos catalogTemporarySide;
    private BlockPos catalogTemporaryStairNeighbor;
    private final List<BlockPos> catalogTemporaryCleared = new ArrayList<>();
    private int interruptDone, pendingExpected;
    private BlockPos staleOtherPos;
    private BlockPos strokeA, strokeB;
    private int strokeBeforeA, strokeBeforeB, strokeUndoCount;
    private static final String STROKE_ID = "00000000-0000-4000-8000-000000000001";
    private ServerPlayerEntity qaPlayer;
    private UUID playerId;
    private Object visualState;
    private Field visualAlt;
    private boolean originalAlt;
    private GameMode originalGameMode;
    private boolean fixtureOwned, cleanupRunning;
    private boolean finalCleanupVerified, functionalSuiteFailed, catalogueRecoveryStarted;
    private final String persistenceMode = System.getProperty("dw.editorqa.persistence", "write");
    private final boolean uiOnly=Boolean.getBoolean("dw.editorqa.uiOnly");
    private final boolean uiBeFixture=Boolean.getBoolean("dw.editorqa.uiBeFixture");
    private static final BlockPos UI_BE_TARGET=new BlockPos(117,200,-1091);
    private static final BlockPos UI_BE_ART_SAMPLE=new BlockPos(117,62,-1091);
    private BlockState uiBeOriginalState;
    private NbtCompound uiBeOriginalNbt;
    private int uiBeOriginalStyle;
    private boolean uiBeOwned;
    private boolean persistenceVerified, persistenceCommitted, persistenceSupportOwned, persistenceCreated;
    private BlockPos persistencePos, persistenceSupport;
    private int persistenceNativeIndex;
    private volatile int clientSnapshotStyle = -2;
    private record Editor2ClientReadiness(BlockPos target,boolean chunkLoaded,String state,int nativeIndex,int style,String dimension,double playerX,double playerY,double playerZ,String screen,long sampledNano,int sampledTick) {}
    private volatile BlockPos editor2ObservedTarget;
    private volatile Editor2ClientReadiness editor2ClientReadiness;
    private long editor2ReadyDeadline,editor2NextReadyReport;
    private boolean failureHoldStarted;
    private long failureHoldUntil;
    private volatile boolean failureHoldReady,failureHoldRestored;
    private boolean failureHoldRestoring,failureHoldActorCaptured;
    private GameMode failureHoldOriginalMode;
    private ItemStack failureHoldOriginalHeld=ItemStack.EMPTY;
    private boolean failureHoldOriginalFlying,failureHoldOriginalNoGravity;
    private int persistenceWait;
    private volatile boolean quickClickInvoked;
    private volatile boolean quickClickRaceExercised;
    private volatile long quickClientEventNano;
    private long quickAcquireDeadline,persistenceClientDeadline;
    private record QuickClientState(String position,String eye,String velocity,float yaw,float pitch,boolean flying,boolean noGravity,String held,String screen,String hitType,String hitPosition,boolean chunkLoaded,long sampledNano,int sampledTick) {}
    private volatile QuickClientState quickClientState;
    private boolean functionalActorOwned,functionalOriginalFlying,functionalOriginalNoGravity;
    private ItemStack functionalOriginalHeld=ItemStack.EMPTY;
    private int quickPaintTick;
    private ItemStack safeFixture = ItemStack.EMPTY;
    private final String output = System.getProperty("dw.editorqa.output", "");
    private long uiHoldUntil;
    private boolean uiHoldStarted,uiRestoreRequested;
    private boolean uiMotionFrozen,uiPreviousFlying,uiPreviousNoGravity;
    private volatile boolean uiRestoreDone;
    private ItemStack uiPreviousHeld=ItemStack.EMPTY;

    @Override public void onInitializeClient() {
        report.add("checks", checks); report.add("failures", failures); report.add("unsupported", unsupported); report.add("items", items);
        report.addProperty("continuousStrokeUi", "not exercised; grouped server stroke/history is covered by deterministic QA");
        report.addProperty("appearanceCheckScope", "Explicit appearance fields on matching physical roots only; optimized native visuals, redirected assemblies and source payload shifts are not verified.");
        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
        ServerTickEvents.START_SERVER_TICK.register(this::interruptAtTickStart);
    }

    private void clientTick(MinecraftClient client) {
        if (done) return;
        if (phase == Phase.DONE) { finish(client); return; }
        if(!failures.isEmpty()&&Integer.getInteger("dw.editorqa.failureHoldSeconds",0)>0&&!running&&client.getServer()!=null) {
            if(phase==Phase.QUICK_CLICK||phase==Phase.WAIT_QUICK_CLICK){functionalSuiteFailed=true;report.addProperty("functionalSuiteStatus","FAIL");report.add("quickClickClientState",quickClientStateJson());}
            running=true;MinecraftServer server=client.getServer();
            server.execute(()->{try{ServerPlayerEntity player=playerId==null?null:server.getPlayerManager().getPlayer(playerId);stopForFailureHold(player);}finally{phase=Phase.DONE;running=false;client.execute(()->finish(client));}});
            return;
        }
        if(uiOnly||editor2Only)EditorRenderProbe.tick(client);
        BlockPos observed=editor2ObservedTarget;
        if(editor2Only && observed!=null && client.world!=null && client.player!=null) {
            var chunk=client.world.getChunkManager().getChunk(observed.getX()>>4,observed.getZ()>>4,ChunkStatus.FULL,false);
            int observedStyle=chunk==null?-2:cells(chunk).style(observed);
            int nativeIndex=chunk==null?-2:NativeVisuals.index(client.world,observed);
            String state=chunk==null?"UNLOADED":client.world.getBlockState(observed).toString();
            var player=client.player;
            editor2ClientReadiness=new Editor2ClientReadiness(observed,chunk!=null,state,nativeIndex,observedStyle,client.world.getRegistryKey().getValue().toString(),player.getX(),player.getY(),player.getZ(),client.currentScreen==null?"none":client.currentScreen.getClass().getName(),System.nanoTime(),ticks);
            clientSnapshotStyle=observedStyle;
        }
        if((phase==Phase.QUICK_CLICK||phase==Phase.WAIT_QUICK_CLICK)&&client.player!=null&&client.world!=null&&sampleTarget!=null) {
            var p=client.player;var hit=client.crosshairTarget;
            var chunk=client.world.getChunkManager().getChunk(sampleTarget.getX()>>4,sampleTarget.getZ()>>4,ChunkStatus.FULL,false);
            quickClientState=new QuickClientState(p.getPos().toString(),p.getEyePos().toString(),p.getVelocity().toString(),p.getYaw(),p.getPitch(),p.getAbilities().flying,p.hasNoGravity(),Registries.ITEM.getId(p.getMainHandStack().getItem()).toString(),client.currentScreen==null?"none":client.currentScreen.getClass().getName(),hit==null?"none":hit.getType().toString(),hit instanceof BlockHitResult block?block.getBlockPos().toShortString():"none",chunk!=null,System.nanoTime(),ticks);
        }
        if(phase==Phase.QUICK_CLICK&&client.player!=null&&client.world!=null&&client.currentScreen==null
                &&client.player.getMainHandStack().isOf(CityEditorServer.BRUSH)
                &&client.crosshairTarget instanceof BlockHitResult hit&&hit.getBlockPos().equals(sampleTarget)){
            if(client.options.attackKey.isPressed())failures.add("Short-click regression requires the attack key released");
            else{
                exerciseSecondQuickClickWhilePending(client,hit);
                ActionResult result=AttackBlockCallback.EVENT.invoker().interact(client.player,client.world,Hand.MAIN_HAND,hit.getBlockPos(),hit.getSide());
                if(result!=ActionResult.FAIL)failures.add("Brush did not consume released-key attack event");
                quickClientEventNano=System.nanoTime();quickClickInvoked=true;
            }
            phase=Phase.WAIT_QUICK_CLICK;
        }
        if (phase == Phase.PERSIST_VERIFY_CLIENT && client.world != null && persistencePos != null) {
            var candidate = client.world.getChunkManager().getChunk(persistencePos.getX() >> 4, persistencePos.getZ() >> 4, ChunkStatus.FULL, false);
            if (candidate != null) clientSnapshotStyle = cells(candidate).style(persistencePos);
        }
        if (++ticks > 48000) { failures.add("QA exceeded the 48000 tick limit including startup"); phase=Phase.DONE;finish(client); return; }
        if (!Boolean.getBoolean("dw.editorqa.disposable") || output.isBlank()) { failures.add("Requires disposable-copy opt-in and output path");phase=Phase.DONE;finish(client); return; }
        if (client.world == null || client.player == null || client.getServer() == null || running) return;
        playerId = client.player.getUuid(); running = true;
        MinecraftServer server = client.getServer();
        server.execute(() -> {
            try {
                qaPlayer = server.getPlayerManager().getPlayer(playerId);
                if (qaPlayer != null) step(qaPlayer);
            } catch (Throwable error) {
                failures.add(error.getClass().getName() + ": " + String.valueOf(error.getMessage()));
                java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
                report.addProperty("failureStack",trace.toString());report.addProperty("failurePhase",phase.name());
                if(phase==Phase.QUICK_CLICK||phase==Phase.WAIT_QUICK_CLICK){report.add("quickClickClientState",quickClientStateJson());if(qaPlayer!=null){report.addProperty("quickClickFailureDiagnostic",quickClickDiagnostic(qaPlayer));report.addProperty("quickClickServerPosition",qaPlayer.getPos().toString());report.addProperty("quickClickServerEye",qaPlayer.getEyePos().toString());report.addProperty("quickClickServerVelocity",qaPlayer.getVelocity().toString());report.addProperty("quickClickServerYaw",qaPlayer.getYaw());report.addProperty("quickClickServerPitch",qaPlayer.getPitch());report.addProperty("quickClickServerHeadYaw",qaPlayer.getHeadYaw());report.addProperty("quickClickServerFlying",qaPlayer.getAbilities().flying);report.addProperty("quickClickServerNoGravity",qaPlayer.hasNoGravity());}}
                if(!uiOnly&&!catalogueOnly&&(!editor2Only||editor2MatrixDone&&functionalAfterEditor2Started)&&phase!=Phase.CATALOG_PLACE&&phase!=Phase.CATALOG_CLEAR&&phase!=Phase.CATALOG_RESTORE_VERIFY&&phase!=Phase.CLEANUP&&phase!=Phase.PERSIST_WRITE_PREP&&phase!=Phase.PERSIST_WRITE_APPLY&&phase!=Phase.PERSIST_WRITE_SAVE&&phase!=Phase.PERSIST_VERIFY_CLIENT&&phase!=Phase.PERSIST_VERIFY_BREAK&&phase!=Phase.DONE){functionalSuiteFailed=true;report.addProperty("functionalSuiteStatus","FAIL");}
                if(editor2Only && !editor2MatrixDone && qaPlayer!=null) {
                    if(editor2State!=null && editor2Target<editor2Targets.size() && !editor2Targets.get(editor2Target).equals(EDITOR2_WALL)) {
                        try{CityEditorServer.queueAppearance(qaPlayer.getServerWorld(),editor2Targets.get(editor2Target),editor2Before);report.addProperty("editor2FailureRestoreQueued",true);}
                        catch(Throwable cleanupError){failures.add("editor2 appearance restore failed: "+cleanupError);}
                    }
                    if(editor2WallOwned||editor2FloorOwned)try{cleanupEditor2Wall(qaPlayer);}catch(Throwable cleanupError){failures.add("editor2 wall cleanup failed: "+cleanupError);}
                }
                if(Integer.getInteger("dw.editorqa.failureHoldSeconds",0)>0){stopForFailureHold(qaPlayer);phase=Phase.DONE;}
                else if(qaPlayer==null||!recoverScenarioFailure(qaPlayer,client,error)){
                    if (qaPlayer != null) try{cleanup(qaPlayer);}catch(Throwable cleanupError){failures.add("final cleanup failed: "+cleanupError);}
                    finally{if(qaPlayer!=null){try{restoreEditor2Actor(qaPlayer);}catch(Throwable actorError){failures.add("editor2 actor restore failed: "+actorError);}try{restoreFunctionalActor(qaPlayer);}catch(Throwable actorError){failures.add("functional actor restore failed: "+actorError);}}}
                    if(qaPlayer!=null&&fixtureBaseline.size()>0)finalCleanupVerified=matches(qaPlayer.getServerWorld(),fixtureBaseline);
                    phase = Phase.DONE;
                }
            } finally { running = false; }
            if (phase == Phase.DONE) client.execute(() -> finish(client));
        });
    }

    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks.add(label);
    }
    private void stopForFailureHold(ServerPlayerEntity player) {
        interruptArmed=false;
        if(player==null)return;
        try {
            if(editor2Only&&!editor2MatrixDone&&editor2State!=null&&editor2Target<editor2Targets.size()&&!editor2Targets.get(editor2Target).equals(EDITOR2_WALL))
                CityEditorServer.queueAppearance(player.getServerWorld(),editor2Targets.get(editor2Target),editor2Before);
        } catch(Throwable error){failures.add("Failure-hold appearance restore failed: "+error);}
        try {if(editor2WallOwned||editor2FloorOwned)cleanupEditor2Wall(player);}
        catch(Throwable error){failures.add("Failure-hold wall cleanup failed: "+error);}
        try {if(pendingCatalogue!=null&&(phase==Phase.CATALOG_CLEAR||phase==Phase.CATALOG_RESTORE_VERIFY))restoreTouchedFixture(player,pendingCatalogue.probe());}
        catch(Throwable error){failures.add("Failure-hold catalogue cleanup failed: "+error);}
        try {cleanup(player);}
        catch(Throwable error){failures.add("Failure-hold cleanup failed: "+error);}
        finally {try{restoreEditor2Actor(player);}catch(Throwable error){failures.add("Failure-hold actor restore failed: "+error);}try{restoreFunctionalActor(player);}catch(Throwable error){failures.add("Failure-hold functional actor restore failed: "+error);}}
    }
    private void exerciseSecondQuickClickWhilePending(MinecraftClient client,BlockHitResult hit){
        try{
            Class<?> type=Class.forName("dev.dreamwalker.bloodbornedw.editor.CityEditorClient");
            String[] names={"clickGeneration","pendingRequestId","pendingPosition","pendingOneShot","pendingGeneration",
                    "attackClickQueued","releaseAfterQueued","pressActive","strokePressGeneration","strokeId","lastStrokePos",
                    "retryPosition","retryGeneration","retryOneShot","waitingForServerIdle","retryAfterTick",
                    "cornerAction","cornerPosition","cornerDimension","cornerRequestId","cornerAttempts","cornerAfterTick","cornerDeadline","lastStrokeTick"};
            java.util.Map<String,Object> saved=new java.util.LinkedHashMap<>();
            for(String name:names){java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);saved.put(name,field.get(null));}
            long firstGeneration=1001L,firstRequest=0x5eedL;
            try{
                setClientField(type,"clickGeneration",firstGeneration);setClientField(type,"pendingRequestId",firstRequest);
                setClientField(type,"pendingPosition",sampleTarget.asLong());setClientField(type,"pendingOneShot",true);
                setClientField(type,"pendingGeneration",firstGeneration);setClientField(type,"attackClickQueued",false);
                setClientField(type,"releaseAfterQueued",true);setClientField(type,"pressActive",false);
                setClientField(type,"strokePressGeneration",Long.MIN_VALUE);setClientField(type,"strokeId","");
                setClientField(type,"lastStrokePos",Long.MIN_VALUE);setClientField(type,"retryPosition",Long.MIN_VALUE);
                setClientField(type,"retryGeneration",-1L);setClientField(type,"retryOneShot",false);
                setClientField(type,"waitingForServerIdle",false);setClientField(type,"retryAfterTick",0);
                ActionResult second=AttackBlockCallback.EVENT.invoker().interact(client.player,client.world,Hand.MAIN_HAND,hit.getBlockPos(),hit.getSide());
                if(second!=ActionResult.FAIL)throw new IllegalStateException("second short-click did not queue through AttackBlockCallback");
                long secondGeneration=((Number)getClientField(type,"clickGeneration")).longValue();
                setClientField(type,"releaseAfterQueued",true);setClientField(type,"pressActive",false);
                Method acknowledge=type.getDeclaredMethod("acknowledgePaint",long.class,boolean.class,boolean.class,boolean.class);acknowledge.setAccessible(true);
                if(!((Boolean)acknowledge.invoke(null,firstRequest,true,false,false)))throw new IllegalStateException("first pending request ACK was not matched");
                if(!((Boolean)getClientField(type,"attackClickQueued"))
                        ||((Number)getClientField(type,"pendingRequestId")).longValue()!=-1L
                        ||secondGeneration<=firstGeneration
                        ||((Number)getClientField(type,"strokePressGeneration")).longValue()==secondGeneration)
                    throw new IllegalStateException("first ACK consumed the newer queued quick-click");
                report.addProperty("quickClickFirstGeneration",firstGeneration);report.addProperty("quickClickSecondGeneration",secondGeneration);
                report.addProperty("quickClickSyntheticAckRequestId",firstRequest);report.addProperty("quickClickAckPreservedSecond",true);
                report.addProperty("quickClickOneShotAckGenerationIsolationSimulated",true);
                // Queue a Shift corner while a paint is in flight, then deliver its ACK.
                setClientField(type,"pendingRequestId",firstRequest);
                setClientField(type,"pendingPosition",sampleTarget.asLong());
                setClientField(type,"pendingGeneration",firstGeneration);
                setClientField(type,"pendingOneShot",true);
                setClientField(type,"cornerAction","");
                setClientField(type,"strokeId","");
                Method queueCorner=type.getDeclaredMethod("queueCorner",String.class,MinecraftClient.class,long.class);
                queueCorner.setAccessible(true);
                queueCorner.invoke(null,"first",client,hit.getBlockPos().asLong());
                if((Boolean)acknowledge.invoke(null,firstRequest+1,true,false,false))
                    throw new IllegalStateException("unrelated ACK matched pending paint");
                if(!((Boolean)acknowledge.invoke(null,firstRequest,true,false,false))
                        || !"first".equals(getClientField(type,"cornerAction"))
                        || ((Number)getClientField(type,"cornerPosition")).longValue()!=hit.getBlockPos().asLong()
                        || ((Number)getClientField(type,"pendingRequestId")).longValue()!=-1L
                        || ((Number)getClientField(type,"cornerAfterTick")).intValue()<=((Number)getClientField(type,"strokeTick")).intValue())
                    throw new IllegalStateException("paint ACK lost or failed to defer queued Shift corner");
                report.addProperty("shiftCornerPendingPaintAckPreservedSimulated",true);
            }finally{
                for(var entry:saved.entrySet()){java.lang.reflect.Field field=type.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(null,entry.getValue());}
            }
            quickClickRaceExercised=true;
        }catch(Throwable error){failures.add("two-press ACK race regression: "+error.getClass().getSimpleName()+": "+error.getMessage());}
    }
    private static void setClientField(Class<?> type,String name,Object value)throws ReflectiveOperationException{
        java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(null,value);
    }
    private static Object getClientField(Class<?> type,String name)throws ReflectiveOperationException{
        java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }
    private String quickClickDiagnostic(ServerPlayerEntity player){
        try{
            Class<?> type=Class.forName("dev.dreamwalker.bloodbornedw.editor.CityEditorClient");
            return "target="+sampleTarget+",style="+style(player.getServerWorld(),sampleTarget)
                    +",serverMaterial="+sessionInt(player,"material")
                    +",clientMaterial="+getClientField(type,"material")
                    +",busy="+getClientField(type,"serverBusy")
                    +",pendingId="+getClientField(type,"pendingRequestId")
                    +",pendingGeneration="+getClientField(type,"pendingGeneration")
                    +",clickGeneration="+getClientField(type,"clickGeneration")
                    +",queued="+getClientField(type,"attackClickQueued")
                    +",jobActive="+jobActive(player);
        }catch(Throwable error){return "diagnostic unavailable: "+error.getClass().getSimpleName()+": "+error.getMessage();}
    }
    private JsonObject quickClientStateJson(){
        QuickClientState state=quickClientState;JsonObject row=new JsonObject();
        row.addProperty("expected",sampleTarget==null?"none":sampleTarget.toShortString());row.addProperty("available",state!=null);
        if(state!=null){row.addProperty("position",state.position());row.addProperty("eye",state.eye());row.addProperty("velocity",state.velocity());row.addProperty("yaw",state.yaw());row.addProperty("pitch",state.pitch());row.addProperty("flying",state.flying());row.addProperty("noGravity",state.noGravity());row.addProperty("held",state.held());row.addProperty("screen",state.screen());row.addProperty("hitType",state.hitType());row.addProperty("hitPosition",state.hitPosition());row.addProperty("chunkLoaded",state.chunkLoaded());row.addProperty("sampledNano",state.sampledNano());row.addProperty("sampledClientTick",state.sampledTick());}
        return row;
    }
    private void fail(String id, String reason) {
        JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("status", "FAIL"); row.addProperty("reason", reason);
        items.add(row); failures.add(id + ": " + reason);
        if(phase!=Phase.CATALOG_PLACE&&phase!=Phase.CATALOG_CLEAR&&phase!=Phase.CATALOG_RESTORE_VERIFY){functionalSuiteFailed=true;report.addProperty("functionalSuiteStatus","FAIL");}
    }
    private void unsupported(String id, String reason) {
        JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("status", "UNSUPPORTED"); row.addProperty("reason", reason);
        items.add(row); unsupported.add(id + ": " + reason); failures.add(id + ": unsupported: " + reason);
        if(phase!=Phase.CATALOG_PLACE&&phase!=Phase.CATALOG_CLEAR&&phase!=Phase.CATALOG_RESTORE_VERIFY){functionalSuiteFailed=true;report.addProperty("functionalSuiteStatus","FAIL");}
    }
    private static String catalogueKey(ItemStack stack){
        String id=Registries.ITEM.getId(stack.getItem()).toString();
        return id+(stack.hasNbt()?"|"+stack.getNbt().toString():"");
    }
    /** Independently verify creative entries by item identity, not NBT-variant keys. */
    private void auditCanonicalItemIds() {
        Set<String> ids = new HashSet<>();
        JsonArray duplicates = new JsonArray();
        int brushes = 0;
        for (ItemStack stack : catalogue) {
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (!ids.add(id)) duplicates.add(id);
            if (stack.isOf(CityEditorServer.BRUSH)) brushes++;
        }
        report.add("catalogueDuplicateItemIds", duplicates);
        report.addProperty("catalogueBrushCount", brushes);
        report.addProperty("catalogueSize", catalogue.size());
        report.addProperty("registeredBrushId", Registries.ITEM.getId(CityEditorServer.BRUSH).toString());
        JsonArray catalogueIds = new JsonArray(); ids.stream().sorted().forEach(catalogueIds::add);
        report.add("catalogueItemIds", catalogueIds);
        check(duplicates.size() == 0, "creative catalogue contains one entry per item ID");
        check(brushes == 1, "creative catalogue contains exactly one editor brush");
    }
    private void configureCatalogueRun() throws Exception {
        catalogueRun=catalogue;String rawPath=System.getProperty("dw.editorqa.catalogueFilter","");
        report.addProperty("catalogueFullSize",catalogue.size());
        Set<String> available=new HashSet<>();for(ItemStack stack:catalogue)available.add(catalogueKey(stack));
        Set<String> wanted=null;
        if(!rawPath.isBlank()){
            Path filterPath=Path.of(rawPath).toAbsolutePath().normalize();byte[] bytes=Files.readAllBytes(filterPath);
            report.addProperty("catalogueFilterPath",filterPath.toString());report.addProperty("catalogueFilterSha256",sha256(bytes));
            wanted=parseCatalogueKeyList(bytes,"catalogueFilter",false,false);
        } else { report.addProperty("catalogueFilterPath","");report.addProperty("catalogueFilterSha256","none"); }
        String excludePath=System.getProperty("dw.editorqa.catalogueExcludeVerified","");
        boolean allowRemoved=Boolean.getBoolean("dw.editorqa.catalogueExcludeVerifiedAllowRemoved");
        report.addProperty("catalogueExcludeVerifiedAllowRemoved",allowRemoved);
        if(!excludePath.isBlank()){
            Path path=Path.of(excludePath).toAbsolutePath().normalize();byte[] bytes=Files.readAllBytes(path);
            report.addProperty("catalogueExcludeVerifiedPath",path.toString());report.addProperty("catalogueExcludeVerifiedSha256",sha256(bytes));
            catalogueExcludedVerified.addAll(parseCatalogueKeyList(bytes,"catalogueExcludeVerified",true,allowRemoved));
        }else{report.addProperty("catalogueExcludeVerifiedPath","");report.addProperty("catalogueExcludeVerifiedSha256","none");}
        Set<String> allSelected=wanted==null?new LinkedHashSet<>(available):new LinkedHashSet<>(wanted);
        Set<String> excludedUnknown=new LinkedHashSet<>(catalogueExcludedVerified);excludedUnknown.removeAll(available);
        if(!excludedUnknown.isEmpty()){
            if(!allowRemoved){
                report.addProperty("catalogueExcludeUnknownKeys",excludedUnknown.toString());report.addProperty("catalogueScope","EXCLUSION_INVALID");
                fail("catalogue-exclude-verified","unknown literal catalogue keys: "+excludedUnknown);
                throw new IllegalArgumentException("catalogueExcludeVerified contains keys absent from the canonical catalogue");
            }
            catalogueRemovedVerified.addAll(excludedUnknown);
        }
        allSelected.removeAll(catalogueExcludedVerified);
        if(allSelected.isEmpty())throw new IllegalArgumentException("catalogue filter/exclusions selected no remaining items");
        catalogueRun=catalogue.stream().filter(stack->allSelected.contains(catalogueKey(stack))).toList();
        catalogueFiltered=!rawPath.isBlank()||!catalogueExcludedVerified.isEmpty();
        report.addProperty("catalogueFilteredCount",catalogueRun.size());
        report.addProperty("catalogueExcludedVerifiedCount",catalogueExcludedVerified.size());
        report.addProperty("catalogueExcludedVerifiedMatchedCount",catalogueExcludedVerified.size()-catalogueRemovedVerified.size());
        JsonArray excludedKeys=new JsonArray();for(String key:catalogueExcludedVerified)excludedKeys.add(key);
        report.add("catalogueExcludedVerifiedKeys",excludedKeys);
        report.addProperty("catalogueRemovedVerifiedCount",catalogueRemovedVerified.size());
        JsonArray removedKeys=new JsonArray();for(String key:catalogueRemovedVerified)removedKeys.add(key);
        report.add("catalogueRemovedVerifiedKeys",removedKeys);
        report.addProperty("catalogueSkippedVerifiedPassCount",catalogue.size()-catalogueRun.size());
        report.addProperty("catalogueScope",catalogueFiltered?"FILTERED_OR_VERIFIED_EXCLUSIONS_NOT_WHOLE_CATALOGUE":"FULL_CATALOGUE");
        report.addProperty("wholeCatalogueFreshPass",false);
    }
    private static String sha256(byte[] bytes)throws Exception{
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    private Set<String> parseCatalogueKeyList(byte[] bytes,String label,boolean allowEmpty,boolean allowUnknown){
        var parsed=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8));
        if(!parsed.isJsonArray())throw new IllegalArgumentException(label+" must be a JSON array of literal catalogueKey strings");
        Set<String> keys=new LinkedHashSet<>();
        for(var element:parsed.getAsJsonArray()){
            if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isString())throw new IllegalArgumentException(label+" contains a non-string key");
            if(!keys.add(element.getAsString()))throw new IllegalArgumentException(label+" contains duplicate literal key: "+element.getAsString());
        }
        if(keys.isEmpty()&&!allowEmpty)throw new IllegalArgumentException(label+" must select at least one literal key");
        Set<String> available=new HashSet<>();for(ItemStack stack:catalogue)available.add(catalogueKey(stack));
        Set<String> unknown=new LinkedHashSet<>(keys);unknown.removeAll(available);
        if(!unknown.isEmpty()&&!allowUnknown){
            report.addProperty(label+"UnknownKeys",unknown.toString());report.addProperty("catalogueScope",label.equals("catalogueFilter")?"FILTER_INVALID":"EXCLUSION_INVALID");
            fail(label,"unknown literal catalogue keys: "+unknown);
            throw new IllegalArgumentException(label+" has unknown literal catalogue keys: "+unknown);
        }
        return keys;
    }
    private JsonObject passItem(String id, int footprint, boolean placementStyle, boolean clean) {
        JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("status", "PASS");
        row.addProperty("changedCells", footprint); row.addProperty("baseStyleOnPlacement", placementStyle); row.addProperty("restored", clean); items.add(row); return row;
    }
    private static NbtCompound request(String action) { NbtCompound n = new NbtCompound(); n.putString("action", action); return n; }
    private static NbtCompound strokeRequest(String action) { NbtCompound n = request(action); n.putString("stroke", STROKE_ID); return n; }
    private static NbtCompound config(int material, int radius, String mode) {
        NbtCompound n = request("config"); n.putInt("material", material); n.putInt("radius", radius); n.putString("mode", mode); return n;
    }
    private static void invoke(ServerPlayerEntity player, NbtCompound request) { CityEditorServer.request(player, request); }
    private static EditorCells cells(WorldChunk chunk) { return ((RetainedFrameData)(Object)NativeVisuals.data(chunk)).dw$editor(); }
    private static int style(ServerWorld world, BlockPos pos) { return EditorData.style(world, pos); }
    private static void setAim(ServerPlayerEntity player, BlockPos target) {
        setAim(player,target,3.0,1.5);
    }
    private static void setPlacementAim(ServerPlayerEntity player, BlockPos target) {
        setAim(player,target,1.5,4.5);
    }
    private static BlockHitResult sidePlacementHit(ServerPlayerEntity player, BlockPos support, Direction face) {
        Vec3d direction=new Vec3d(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());
        Vec3d hit=Vec3d.ofCenter(support).add(direction.multiply(.499));
        Vec3d eye=Vec3d.ofCenter(support).add(direction.multiply(3.5));
        player.teleport(player.getServerWorld(),eye.x,eye.y-player.getStandingEyeHeight(),eye.z,player.getYaw(),player.getPitch());
        double dx=hit.x-eye.x,dy=hit.y-eye.y,dz=hit.z-eye.z,horizontal=Math.sqrt(dx*dx+dz*dz);
        player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));
        player.setPitch((float)-Math.toDegrees(Math.atan2(dy,horizontal)));
        player.setHeadYaw(player.getYaw());
        Vec3d eyeAfter=player.getEyePos();
        var actual=player.getServerWorld().raycast(new net.minecraft.world.RaycastContext(eyeAfter,
                eyeAfter.add(player.getRotationVec(1.0F).multiply(6.0)),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        if(actual.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK)return null;
        BlockHitResult block=(BlockHitResult)actual;
        return block.getBlockPos().equals(support)&&block.getSide()==face&&eyeAfter.distanceTo(block.getPos())<=6.0?block:null;
    }
    private static void setAim(ServerPlayerEntity player, BlockPos target, double height, double distanceZ) {
        double px = target.getX() + 0.5, py = target.getY() + height, pz = target.getZ() + distanceZ;
        Vec3d eye = new Vec3d(px, py + player.getStandingEyeHeight(), pz);
        Vec3d center = Vec3d.ofCenter(target);
        double dx = center.x - eye.x, dy = center.y - eye.y, dz = center.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)-Math.toDegrees(Math.atan2(dy, horizontal));
        player.teleport(player.getServerWorld(), px, py, pz, yaw, pitch);
        player.setHeadYaw(yaw);
    }
    private static ActionResult interactOn(ServerPlayerEntity player, BlockPos floor, ItemStack stack) {
        ServerWorld world = player.getServerWorld();
        player.setStackInHand(Hand.MAIN_HAND, stack.copy());
        BlockHitResult hit = new BlockHitResult(new Vec3d(floor.getX() + .5, floor.getY() + 1, floor.getZ() + .5), Direction.UP, floor, false);
        return player.interactionManager.interactBlock(player, world, player.getMainHandStack(), Hand.MAIN_HAND, hit);
    }
    private boolean physicalBreak(ServerPlayerEntity player, BlockPos pos) {
        boolean broken=player.interactionManager.tryBreakBlock(pos);if(broken)cleared++;return broken;
    }
    private static int intField(Object owner, String name) throws ReflectiveOperationException {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(owner);
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private Object session(ServerPlayerEntity player) throws ReflectiveOperationException {
        Field f = CityEditorServer.class.getDeclaredField("SESSIONS"); f.setAccessible(true);
        return ((Map<?, ?>)f.get(null)).get(player.getUuid());
    }
    private int sessionInt(ServerPlayerEntity player, String name) throws ReflectiveOperationException { return intField(session(player), name); }
    private String sessionString(ServerPlayerEntity player, String name) throws ReflectiveOperationException {
        Field f = session(player).getClass().getDeclaredField(name); f.setAccessible(true); return (String)f.get(session(player));
    }
    private boolean jobActive(ServerPlayerEntity player) throws ReflectiveOperationException { return field(session(player), "job") != null; }

    private void step(ServerPlayerEntity player) throws Exception {
        ServerWorld world = player.getServerWorld();
        if (editor2Only && !editor2MatrixDone) { editor2Step(player); return; }
        if (usesEditorAction(phase)) {
            int now = player.getServer().getTicks();
            if (now - lastActionTick < 2) return;
            if(now-sessionInt(player,"lastRequest")<2)return;
            lastActionTick = now;
        }
        switch (phase) {
            case START -> {
                if(editor2Only&&functionalAfterEditor2)functionalAfterEditor2Started=true;
                if (player.getServerWorld().getRegistryKey() != net.minecraft.world.World.OVERWORLD) throw new IllegalStateException("QA fixture requires the Overworld city copy");
                if (!persistenceMode.equals("write") && !persistenceMode.equals("verify")) throw new IllegalStateException("dw.editorqa.persistence must be write or verify");
                originalGameMode = player.interactionManager.getGameMode();
                if(uiOnly){
                    report.addProperty("runScope","UI_ONLY");report.addProperty("uiOnly",true);
                    report.addProperty("functionalOperationSuite","SKIPPED by dw.editorqa.uiOnly");
                    report.addProperty("functionalSuiteStatus","SKIPPED");report.addProperty("functionalSuiteComplete",false);
                    report.addProperty("catalogueScope","UI_ONLY_NOT_RUN");report.addProperty("catalogueNotRun",true);
                    report.addProperty("uiOnlyWorldFixtureOwned",uiBeFixture);
                    if(originalGameMode!=GameMode.CREATIVE)player.changeGameMode(GameMode.CREATIVE);
                    if(!CityEditorServer.editable(player))throw new IllegalStateException("QA player lacks editor permission for UI-only mode");
                    uiPreviousFlying=player.getAbilities().flying;uiPreviousNoGravity=player.hasNoGravity();
                    player.getAbilities().flying=true;player.setNoGravity(true);player.setVelocity(Vec3d.ZERO);player.sendAbilitiesUpdate();uiMotionFrozen=true;
                    world.getGameRules().get(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE).set(false,player.getServer());
                    world.getGameRules().get(net.minecraft.world.GameRules.DO_WEATHER_CYCLE).set(false,player.getServer());
                    world.setTimeOfDay(6000L);world.setWeather(0,12000,false,false);
                    player.teleport(world,114.518,uiBeFixture?199.262:61.262,-1089.413,uiBeFixture?-110.0F:-116.6F,uiBeFixture?7.4F:10.0F);
                    if(uiBeFixture)installUiBeFixture(player);
                    report.addProperty("uiPlayerMotionFrozen",true);
                    report.addProperty("uiDaylightAndWeatherFrozen",true);
                    report.addProperty("uiServerAltAtStart",CityVisualModeState.isAlt(player.getServer()));
                    finalCleanupVerified=true;report.addProperty("finalCleanupVerified",true);
                    phase=Phase.DONE;return;
                }
                if (persistenceMode.equals("verify") && !persistenceVerified) {
                    persistencePos = persistencePosition(world);
                    var requestedChunk=world.getChunkManager().getChunk(persistencePos.getX()>>4,persistencePos.getZ()>>4,ChunkStatus.FULL,true);
                    WorldChunk sentinelChunk=requestedChunk instanceof WorldChunk ready?ready:null;
                    report.addProperty("persistenceVerifyChunkLoad","explicit FULL chunk request completed before reading the saved sentinel");
                    if (sentinelChunk == null || !CityEditorServer.supported(world.getBlockState(persistencePos)) || style(world, persistencePos) != 2)
                        throw new IllegalStateException("Persistence sentinel missing, unsupported, or not style 2 at " + persistencePos);
                    report.addProperty("persistenceSentinelPosition", persistencePos.toString());
                    report.addProperty("persistenceSentinelBlock", Registries.BLOCK.getId(world.getBlockState(persistencePos).getBlock()).toString());
                    report.addProperty("persistenceSentinelNativeIndex", NativeVisuals.index(world, persistencePos));
                    report.addProperty("persistenceSentinelStyle", style(world, persistencePos));
                    clientSnapshotStyle = -2; persistenceWait = 0; setAim(player, persistencePos);persistenceClientDeadline=System.nanoTime()+30_000_000_000L; phase = Phase.PERSIST_VERIFY_CLIENT;
                    return;
                }
                if(!catalogueOnly){functionalOriginalHeld=player.getMainHandStack().copy();functionalOriginalFlying=player.getAbilities().flying;functionalOriginalNoGravity=player.hasNoGravity();functionalActorOwned=true;}
                if (originalGameMode != GameMode.CREATIVE) player.changeGameMode(GameMode.CREATIVE);
                if (!CityEditorServer.editable(player)) throw new IllegalStateException("QA player lacks editor permission after Creative mode setup");
                if(functionalActorOwned){player.getAbilities().flying=true;player.setNoGravity(true);player.setVelocity(Vec3d.ZERO);player.sendAbilitiesUpdate();}
                catalogue = UnifiedCreativeCatalogue.canonicalItems();
                auditCanonicalItemIds();
                configureCatalogueRun();
                report.addProperty("runScope",catalogueOnly?"CATALOGUE_ONLY_WITH_STANDARD_START_CLEANUP_AND_PERSISTENCE":(editor2Only&&functionalAfterEditor2?"EDITOR2_MATRIX_PLUS_":"")+"FULL_FUNCTIONAL_QA_PLUS_"+(catalogueFiltered?"FILTERED_CATALOGUE_SUBSET":"FULL_CATALOGUE"));
                enableAlt(player.getServer());
                report.add("catalogueAudit", EditorCatalogue.audit());
                if (report.getAsJsonObject("catalogueAudit").get("pass").getAsBoolean()) checks.add("creative catalogue audit pass");
                else failures.add("creative catalogue audit failed; see catalogueAudit");
                report.addProperty("catalogueEntries", catalogue.size());
                report.addProperty("functionalOperationSuite",catalogueOnly?"SKIPPED by dw.editorqa.catalogueOnly":"RUN");
                report.addProperty("functionalSuiteStatus",catalogueOnly?"SKIPPED":"RUNNING");
                baseFloor = new BlockPos(166, world.getTopY() - 112, -812);
                int baseX = baseFloor.getX(), baseZ = baseFloor.getZ();
                for (int z = 0; z < 10; z++) for (int x = 0; x < 13; x++) {
                    BlockPos floor = new BlockPos(baseX + x, baseFloor.getY(), baseZ + z);
                    gridFloors.add(floor); gridTargets.add(floor.up());
                }
                sampleTarget = gridTargets.get(0);
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));
                autoSamplePos=findAutoSample(world,player);
                if(autoSamplePos==null)throw new IllegalStateException("no unpainted DreamWalker cell found in the reviewed rose-city region");
                catalogSupport = new BlockPos(baseX + 6, baseFloor.getY() + 11, baseZ + 4);
                catalogFloor = catalogSupport.up(); catalogTarget = catalogFloor.up();
                ensureFixtureAir(world);
                fixtureOwned = true;
                fixtureBaseline = captureFixture(world);
                runCellsDataChecks(world);
                for (BlockPos floor : gridFloors) { world.setBlockState(floor, Blocks.STONE.getDefaultState(), 3); placed++; }
                world.setBlockState(catalogSupport, Blocks.STONE.getDefaultState(), 3); placed++;
                world.setBlockState(catalogFloor, Blocks.STONE.getDefaultState(), 3); placed++;
                catalogBaseline=captureFixture(world);phase=Phase.PREPARE;
            }
            case PREPARE -> {
                safeFixture = findSafeTestStack();
                if (safeFixture.isEmpty()) throw new IllegalStateException("Catalogue contains no SourceFallbackCarrierV1271 BlockItem fixture");
                report.addProperty("operationFixtureItem", Registries.ITEM.getId(safeFixture.getItem()).toString());
                cursor = 0; phase = catalogueOnly ? Phase.CATALOG_PLACE : Phase.PLACE_FIXTURE;
            }
            case PLACE_FIXTURE -> {
                if (cursor < gridFloors.size()) {
                    BlockPos floor = gridFloors.get(cursor), root = gridTargets.get(cursor);
                    setAim(player, root);
                    ActionResult result = interactOn(player, floor, safeFixture);
                    if (!result.isAccepted() || !CityEditorServer.supported(world.getBlockState(root)))
                        throw new IllegalStateException("Fixture player interaction failed at " + root);
                    placed++;
                    check(style(world, root) == 0, "fixture placement writes manual base at " + root);
                    cursor++;
                } else { cursor = 0; phase = Phase.PENDING_REPLACEMENT; }
            }
            case PENDING_REPLACEMENT -> {
                int last = gridTargets.size() - 1;
                BlockPos pos = gridTargets.get(last);
                BlockState before = world.getBlockState(pos);
                check(style(world, pos) == 0, "pending replacement fixture starts at base style");
                CityEditorServer.queueAppearance(world, pos, 1);
                check(CityEditorServer.pendingStyle(world, pos) == 1, "external appearance is pending before replacement");
                check(physicalBreak(player, pos), "normal break succeeds with pending appearance");
                check(CityEditorServer.pendingStyle(world, pos) == -2 && style(world, pos) == -1,
                        "normal break discards pending appearance and clears style");
                setAim(player, pos);
                ActionResult result = interactOn(player, gridFloors.get(last), safeFixture);
                check(result.isAccepted() && world.getBlockState(pos).getBlock() == before.getBlock(),
                        "normal placement restores the fixture block");
                placed++;
                check(style(world, pos) == 0, "replacement starts with manual base appearance");
                pendingReplacementTick = player.getServer().getTicks();
                phase = Phase.WAIT_PENDING_REPLACEMENT;
            }
            case WAIT_PENDING_REPLACEMENT -> {
                if (player.getServer().getTicks() <= pendingReplacementTick) return;
                BlockPos pos = gridTargets.get(gridTargets.size() - 1);
                check(CityEditorServer.pendingStyle(world, pos) == -2 && style(world, pos) == 0,
                        "discarded appearance does not return after the server tick");
                phase = Phase.QUICK_PREPARE;
            }
            case QUICK_PREPARE -> {
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));
                invoke(player,config(4,0,"single"));aimEditor2(player,sampleTarget);quickClientState=null;quickClientEventNano=0;quickAcquireDeadline=System.nanoTime()+30_000_000_000L;waitTicks=0;phase=Phase.QUICK_CLICK;
            }
            case QUICK_CLICK -> {if(System.nanoTime()>=quickAcquireDeadline)throw new IllegalStateException("Client did not acquire the short-click fixture target within 30 seconds: "+quickClientStateJson());}
            case WAIT_QUICK_CLICK -> {
                if(quickClickInvoked&&quickClickRaceExercised&&style(world,sampleTarget)==4&&!jobActive(player)){
                    check(true,"released-key attack event paints through actual client packet path");
                    check(true,"synthetic pending ACK preserves the newer released-key press generation");
                    check(sessionInt(player,"material")==4,"server config selects the released-key paint material");
                    quickPaintTick=player.getServer().getTicks();waitTicks=0;phase=Phase.QUICK_UNDO;
                }else if(quickClientEventNano>0&&System.nanoTime()-quickClientEventNano>=30_000_000_000L)throw new IllegalStateException("Released-key attack event did not paint the fixture within 30 seconds; "+quickClickDiagnostic(player)+" client="+quickClientStateJson());
            }
            case QUICK_UNDO -> {
                if(player.getServer().getTicks()-quickPaintTick<2)return;
                invoke(player,request("undo"));waitTicks=0;phase=Phase.WAIT_QUICK_UNDO;
            }
            case WAIT_QUICK_UNDO -> {
                if(style(world,sampleTarget)==0&&!jobActive(player)){
                    check(true,"released-key short click has an undo entry");phase=Phase.AUTO_SAMPLE_CONFIG;
                }else timeout("Released-key short-click undo did not restore base style");
            }
            case QUICK_RECOVER_CANCEL -> {
                if(player.getServer().getTicks()-pendingReplacementTick<2)return;
                setAim(player,sampleTarget);invoke(player,request("reset"));waitTicks=0;phase=Phase.QUICK_RECOVER_WAIT;
            }
            case QUICK_RECOVER_WAIT -> {
                if(!jobActive(player)&&style(world,sampleTarget)==0){
                    report.addProperty("quickClickRecoveryVerified",true);phase=Phase.AUTO_SAMPLE_CONFIG;
                }else timeout("Quick-click recovery could not reset its owned sample cell safely");
            }
            case QUICK_UNDO_FIRST -> {
                if(player.getServer().getTicks()-quickPaintTick<2)return;
                invoke(player,request("undo"));waitTicks=0;phase=Phase.WAIT_QUICK_UNDO_FIRST;
            }
            case WAIT_QUICK_UNDO_FIRST -> {
                if(style(world,sampleTarget)==0&&!jobActive(player)){
                    check(true,"first quick-click remains independently undoable");phase=Phase.AUTO_SAMPLE_CONFIG;
                }else timeout("First short-click undo did not restore base style");
            }
            case AUTO_SAMPLE_CONFIG -> { invoke(player,config(4,0,"single"));phase=Phase.AUTO_SAMPLE; }
            case AUTO_SAMPLE -> {
                setAim(player,autoSamplePos);
                Method target=CityEditorServer.class.getDeclaredMethod("target",ServerPlayerEntity.class);target.setAccessible(true);
                BlockHitResult hit=(BlockHitResult)target.invoke(null,player);
                report.addProperty("autoSampleExpected",autoSamplePos.toString());
                report.addProperty("autoSampleActualHit",hit==null?"MISS":hit.getBlockPos().toString());
                invoke(player,request("sample"));
                report.addProperty("autoSampleMaterial",sessionInt(player,"material"));report.addProperty("autoSampleMessage",sessionString(player,"message"));
                check(sessionInt(player,"material")==1,"sample copies automatic rose-region palette");phase=Phase.SINGLE_CONFIG;
            }
            case SINGLE_CONFIG -> { invoke(player, config(2, 0, "single")); phase = Phase.SINGLE_PAINT; }
            case SINGLE_PAINT -> { stateBeforePaint=world.getBlockState(sampleTarget);nativeIndexBeforePaint=NativeVisuals.index(world,sampleTarget);setAim(player, sampleTarget); invoke(player, request("paint")); waitTicks = 0; phase = Phase.WAIT_SINGLE; }
            case WAIT_SINGLE -> {
                if (style(world, sampleTarget) == 2 && !jobActive(player)) {
                    check(world.getBlockState(sampleTarget)==stateBeforePaint,"single paint leaves the physical block unchanged");
                    check(NativeVisuals.index(world,sampleTarget)==nativeIndexBeforePaint,"single paint preserves native visual index");
                    check(true, "single paint commits palette style"); phase = Phase.SAMPLE_CONFIG;
                }
                else timeout("single paint did not finish");
            }
            case SAMPLE_CONFIG -> { invoke(player, config(4, 0, "single")); phase = Phase.SAMPLE; }
            case SAMPLE -> { setAim(player, sampleTarget); invoke(player, request("sample")); check(sessionInt(player, "material") == 2, "sample copies manually painted style"); phase = Phase.RESET; }
            case RESET -> { setAim(player, sampleTarget); invoke(player, request("reset")); waitTicks = 0; phase = Phase.WAIT_RESET; }
            case WAIT_RESET -> { if (style(world, sampleTarget) == 0 && !jobActive(player)) { check(true, "reset writes explicit base style"); phase = Phase.UNDO; } else timeout("reset did not finish"); }
            case UNDO -> { setAim(player, sampleTarget); invoke(player, request("undo")); waitTicks = 0; phase = Phase.WAIT_UNDO; }
            case WAIT_UNDO -> { if (style(world, sampleTarget) == 2 && !jobActive(player)) { check(true, "undo restores previous palette style"); phase = Phase.REDO; } else timeout("undo did not restore style"); }
            case REDO -> { setAim(player, sampleTarget); invoke(player, request("redo")); waitTicks = 0; phase = Phase.WAIT_REDO; }
            case WAIT_REDO -> { if (style(world, sampleTarget) == 0 && !jobActive(player)) { check(true, "redo reapplies explicit base"); nativeRoundTrip(world, sampleTarget); phase = Phase.FIRST; } else timeout("redo did not restore style"); }
            case FIRST -> { setAim(player, gridTargets.get(0)); invoke(player, request("first")); phase = Phase.SECOND; }
            case SECOND -> { setAim(player, gridTargets.get(1)); invoke(player, request("second")); invoke(player, config(3, 0, "area")); phase = Phase.AREA_PAINT; }
            case AREA_PAINT -> { setAim(player, sampleTarget); invoke(player, request("paint")); waitTicks = 0; phase = Phase.WAIT_AREA; }
            case WAIT_AREA -> { if (style(world, gridTargets.get(0)) == 3 && style(world, gridTargets.get(1)) == 3 && !jobActive(player)) { check(true, "area paint updates selected cells"); phase = Phase.PLANE_CONFIG; } else timeout("area paint incomplete"); }
            case PLANE_CONFIG -> { invoke(player,config(2,1,"plane"));phase=Phase.PLANE_PAINT; }
            case PLANE_PAINT -> { setAim(player,gridTargets.get(71));invoke(player,request("paint"));waitTicks=0;phase=Phase.WAIT_PLANE; }
            case WAIT_PLANE -> { if(allStyles(world,planeTargets(),2)&&!jobActive(player)){check(true,"radius plane paints its 3x3 face");phase=Phase.PLANE_UNDO;}else timeout("plane paint incomplete"); }
            case PLANE_UNDO -> { setAim(player,gridTargets.get(71));invoke(player,request("undo"));waitTicks=0;phase=Phase.WAIT_PLANE_UNDO; }
            case WAIT_PLANE_UNDO -> { if(allStyles(world,planeTargets(),0)&&!jobActive(player)){check(true,"plane undo restores base styles");phase=Phase.FILL_CONFIG;}else timeout("plane undo incomplete"); }
            case FILL_CONFIG -> { invoke(player, config(4, 0, "fill")); phase = Phase.FILL; }
            case FILL -> { setAim(player, sampleTarget); invoke(player, request("fill")); waitTicks = 0; phase = Phase.WAIT_FILL; }
            case WAIT_FILL -> { if (style(world, gridTargets.get(0)) == 4 && style(world, gridTargets.get(1)) == 4 && !jobActive(player)) { check(true, "connected fill updates matching component"); phase = Phase.NOISE_CONFIG; } else timeout("fill incomplete"); }
            case NOISE_CONFIG -> { invoke(player, config(1, 0, "noise")); phase = Phase.NOISE; }
            case NOISE -> { setAim(player, sampleTarget); invoke(player, request("paint")); waitTicks = 0; phase = Phase.WAIT_NOISE; }
            case WAIT_NOISE -> {
                int expected = noiseStyle(gridTargets.get(0), 1), expectedSecond=noiseStyle(gridTargets.get(1),1);
                if (style(world, gridTargets.get(0)) == expected && style(world,gridTargets.get(1))==expectedSecond && !jobActive(player)) { check(true, "noise palette is deterministic across the selected fixture cells"); phase = Phase.REJECT_STAMP; }
                else timeout("noise operation did not finish");
            }
            case REJECT_STAMP -> {
                invoke(player, config(2, 0, "single"));
                invoke(player, config(2, 0, "stamp"));
                check("single".equals(sessionString(player,"mode")), "legacy stamp config cannot switch brush mode");
                setAim(player, sampleTarget);
                invoke(player, request("stamp"));
                check("single".equals(sessionString(player,"mode")), "legacy stamp action cannot switch brush mode");
                for (BlockPos candidate : gridTargets) if (CityEditorServer.supported(world.getBlockState(candidate)) && style(world,candidate) != 4) {
                    if (strokeA == null) { strokeA=candidate.toImmutable(); strokeBeforeA=style(world,candidate); }
                    else if (!candidate.equals(strokeA)) { strokeB=candidate.toImmutable(); strokeBeforeB=style(world,candidate); break; }
                }
                if (strokeA == null || strokeB == null) throw new IllegalStateException("Could not find two supported stroke fixture cells whose prior styles differ from 4");
                strokeUndoCount = ((java.util.Deque<?>)field(session(player),"undo")).size(); phase = Phase.STROKE_CONFIG;
            }
            case STROKE_CONFIG -> { invoke(player,config(4,0,"single"));phase=Phase.STROKE_PAINT_A; }
            case STROKE_PAINT_A -> { setAim(player,strokeA);invoke(player,strokeRequest("paint"));waitTicks=0;phase=Phase.STROKE_WAIT_A; }
            case STROKE_WAIT_A -> { if(style(world,strokeA)==4&&!jobActive(player))phase=Phase.STROKE_PAINT_B;else timeout("first grouped stroke cell did not paint"); }
            case STROKE_PAINT_B -> { setAim(player,strokeB);invoke(player,strokeRequest("paint"));waitTicks=0;phase=Phase.STROKE_WAIT_B; }
            case STROKE_WAIT_B -> { if(style(world,strokeB)==4&&!jobActive(player))phase=Phase.STROKE_END;else timeout("second grouped stroke cell did not paint"); }
            case STROKE_END -> { invoke(player,strokeRequest("endStroke"));phase=Phase.STROKE_WAIT_END; }
            case STROKE_WAIT_END -> {
                if (!jobActive(player) && ((java.util.Deque<?>)field(session(player),"undo")).size()==strokeUndoCount+1) {
                    check(style(world,strokeA)==4&&style(world,strokeB)==4,"two distinct paints with one stroke id create one grouped undo entry");phase=Phase.STROKE_UNDO;
                } else timeout("grouped stroke did not flush as exactly one undo entry");
            }
            case STROKE_UNDO -> { setAim(player,strokeA);invoke(player,request("undo"));waitTicks=0;phase=Phase.STROKE_WAIT_UNDO; }
            case STROKE_WAIT_UNDO -> {
                if(!jobActive(player)&&style(world,strokeA)==strokeBeforeA&&style(world,strokeB)==strokeBeforeB){check(true,"one undo restores both grouped stroke cells to their prior styles");phase=Phase.STROKE_REDO;}
                else timeout("grouped stroke undo did not restore both prior styles");
            }
            case STROKE_REDO -> { setAim(player,strokeA);invoke(player,request("redo"));waitTicks=0;phase=Phase.STROKE_WAIT_REDO; }
            case STROKE_WAIT_REDO -> {
                if(!jobActive(player)&&style(world,strokeA)==4&&style(world,strokeB)==4){check(true,"one redo restores both grouped stroke cells");phase=Phase.INTERRUPT_PREPARE;}
                else timeout("grouped stroke redo did not restore both cells");
            }
            case INTERRUPT_PREPARE -> {
                for(int i=0;i<gridTargets.size();i++){
                    BlockPos pos=gridTargets.get(i);
                    if(world.getBlockState(pos).isAir()){
                        setAim(player,pos);ActionResult result=interactOn(player,gridFloors.get(i),safeFixture);
                        if(!result.isAccepted()||!CityEditorServer.supported(world.getBlockState(pos)))throw new IllegalStateException("Could not restore interruption fixture at "+pos);
                        placed++;
                    }
                    if(!CityEditorServer.supported(world.getBlockState(pos)))throw new IllegalStateException("Interruption fixture has unsupported cell at "+pos);
                    CityEditorServer.queueAppearance(world,pos,0);
                }
                waitTicks=0;phase=Phase.WAIT_INTERRUPT_READY;
            }
            case WAIT_INTERRUPT_READY -> {
                if(allStyles(world,gridTargets,0)&&!jobActive(player)){
                    check(true,"interruption fixture has 130 supported cells at base style before repaint");phase=Phase.INTERRUPT_CONFIG;
                }else timeout("interruption fixture reset did not finish");
            }
            case INTERRUPT_CONFIG -> { invoke(player, config(4, 0, "area")); phase = Phase.INTERRUPT_FIRST; }
            case INTERRUPT_FIRST -> { setAim(player, gridTargets.get(0)); invoke(player, request("first")); phase = Phase.INTERRUPT_SECOND; }
            case INTERRUPT_SECOND -> { setAim(player, gridTargets.get(gridTargets.size() - 1)); invoke(player, request("second")); phase = Phase.INTERRUPT_PAINT; }
            case INTERRUPT_PAINT -> {
                setAim(player,gridTargets.get(0));invoke(player,request("paint"));
                Object job=field(session(player),"job");
                report.addProperty("interruptionPaintChanges",job==null?0:((List<?>)field(field(job,"edit"),"changes")).size());
                report.addProperty("interruptionPaintMessage",sessionString(player,"message"));
                waitTicks=0;phase=Phase.WAIT_INTERRUPT_PAINT;
            }
            case WAIT_INTERRUPT_PAINT -> {
                if (allStyles(world, gridTargets, 4) && !jobActive(player)) { check(true, "large area paint completes before interrupted undo"); phase = Phase.INTERRUPT_UNDO; }
                else timeout("large area paint incomplete");
            }
            case INTERRUPT_UNDO -> { invoke(player, request("undo")); interruptArmed = true; waitTicks = 0; phase = Phase.WAIT_INTERRUPT; }
            case WAIT_INTERRUPT -> {
                if (!interruptArmed && !jobActive(player) && interruptedPos != null) {
                    check(world.getBlockState(interruptedPos).isOf(Blocks.DIRT), "normal replacement interrupts an active undo");
                    check(style(world, interruptedPos) == -1, "replacement has no stale editor style");
                    check(interruptDone > 0 && pendingPos != null, "undo applied a bounded prefix before interruption");
                    if (staleOtherPos != null) check(style(world, staleOtherPos) == pendingExpected, "unreached undo suffix remains unchanged");
                    phase = Phase.STALE_UNDO;
                } else timeout("undo interruption did not occur");
            }
            case STALE_UNDO -> { invoke(player, request("undo")); waitTicks = 0; phase = Phase.WAIT_STALE_UNDO; }
            case WAIT_STALE_UNDO -> {
                if (!jobActive(player)) {
                    check(world.getBlockState(interruptedPos).isOf(Blocks.DIRT) && style(world, interruptedPos) == -1,
                        "stale history cannot overwrite a normally replaced block");
                    String outcome=sessionString(player,"message");report.addProperty("staleUndoOutcome",outcome);
                    check(outcome.contains("Отмена отклонена")||outcome.equals("История пуста"),"stale undo is rejected or invalidated history is empty");
                    phase = Phase.CLEAR_GRID; fixtureCursor = 0;
                } else timeout("stale undo unexpectedly started");
            }
            case CLEAR_GRID -> {
                if (fixtureCursor < gridTargets.size()) {
                    BlockPos p = gridTargets.get(fixtureCursor++);
                    if (!world.getBlockState(p).isAir()) physicalBreak(player, p);
                    if (!world.getBlockState(p).isAir() || style(world,p) != -1) fail("fixture cell " + p,"grid clear left block or editor style");
                } else {
                    if(!matches(world,catalogBaseline)){fail("fixture cleanup","operation blocks remained after normal break");restoreBoundedByBreaking(player,catalogBaseline);if(!matches(world,catalogBaseline))throw new IllegalStateException("operation fixture was not restored before catalogue audit");}
                    cursor = 0; phase = Phase.CATALOG_PLACE;
                }
            }
            case CATALOG_PLACE -> runCataloguePlacement(player);
            case CATALOG_CLEAR -> clearCataloguePlacement(player);
            case CATALOG_RESTORE_VERIFY -> verifyCatalogueRestore(player);
            case CLEANUP -> { cleanup(player); phase = persistenceMode.equals("write") ? Phase.PERSIST_WRITE_PREP : Phase.DONE; }
            case PERSIST_WRITE_PREP -> {
                persistencePos = persistencePosition(world); persistenceSupport = persistencePos.down();
                if (!world.getBlockState(persistencePos).isAir() || !world.getBlockState(persistenceSupport).isAir())
                    throw new IllegalStateException("Persistence sentinel fixture is occupied at " + persistencePos);
                if (safeFixture.isEmpty()) safeFixture = findSafeTestStack();
                if (safeFixture.isEmpty()) throw new IllegalStateException("No safe single-cell carrier for persistence sentinel");
                if (player.interactionManager.getGameMode() != GameMode.CREATIVE) player.changeGameMode(GameMode.CREATIVE);
                if (!world.setBlockState(persistenceSupport, Blocks.STONE.getDefaultState(), 3)) throw new IllegalStateException("Could not install temporary persistence support");
                persistenceSupportOwned = true; setAim(player, persistencePos);
                ActionResult result = interactOn(player, persistenceSupport, safeFixture);
                if (!result.isAccepted() || !CityEditorServer.supported(world.getBlockState(persistencePos))) throw new IllegalStateException("Real player placement failed for persistence sentinel");
                persistenceCreated = true;
                if (!physicalBreak(player, persistenceSupport) || !world.getBlockState(persistenceSupport).isAir()) throw new IllegalStateException("Could not remove temporary persistence support");
                persistenceSupportOwned = false;
                persistenceNativeIndex = NativeVisuals.index(world, persistencePos);
                CityEditorServer.queueAppearance(world, persistencePos, 2);
                phase = Phase.PERSIST_WRITE_APPLY;
            }
            case PERSIST_WRITE_APPLY -> {
                if (style(world, persistencePos) != 2 || !CityEditorServer.supported(world.getBlockState(persistencePos))) throw new IllegalStateException("Queued persistence style did not flush");
                if (NativeVisuals.index(world, persistencePos) != persistenceNativeIndex) throw new IllegalStateException("Applying editor style changed native visual index");
                reportPersistence(world);
                if (originalGameMode != null && player.interactionManager.getGameMode() != originalGameMode) player.changeGameMode(originalGameMode);
                phase = Phase.PERSIST_WRITE_SAVE;
            }
            case PERSIST_WRITE_SAVE -> {
                saveNormally(player.getServer()); persistenceCommitted = true;
                check(true, "persistence sentinel saved through normal server save path"); phase = Phase.DONE;
            }
            case PERSIST_VERIFY_CLIENT -> {
                if (clientSnapshotStyle == 2) {
                    check(true, "client chunk snapshot loaded persisted editor style 2");
                    phase = Phase.PERSIST_VERIFY_BREAK;
                } else if (System.nanoTime()>=persistenceClientDeadline) throw new IllegalStateException("Client chunk snapshot did not load persisted style 2 within 30 seconds: target="+persistencePos+" style="+clientSnapshotStyle+" clientTicks="+ticks);
            }
            case PERSIST_VERIFY_BREAK -> {
                if (player.interactionManager.getGameMode() != GameMode.CREATIVE) player.changeGameMode(GameMode.CREATIVE);
                if (!physicalBreak(player, persistencePos)) throw new IllegalStateException("Could not normally break persisted sentinel");
                check(world.getBlockState(persistencePos).isAir() && style(world, persistencePos) == -1,
                    "persisted sentinel normal break clears its style");
                if (originalGameMode != null && originalGameMode != GameMode.CREATIVE) player.changeGameMode(originalGameMode);
                persistenceVerified = true; phase = Boolean.getBoolean("dw.editorqa.persistenceOnly")?Phase.DONE:Phase.START;
            }
            case DONE -> { }
        }
    }

    private void editor2Step(ServerPlayerEntity player) throws Exception {
        ServerWorld world=player.getServerWorld();
        if(editor2Stage==0) {
            if(functionalAfterEditor2&&catalogueOnly)throw new IllegalStateException("functionalAfterEditor2 cannot be combined with catalogueOnly");
            if(world.getRegistryKey()!=net.minecraft.world.World.OVERWORLD)throw new IllegalStateException("Editor2 QA requires the Overworld city copy");
            originalGameMode=player.interactionManager.getGameMode();
            editor2Held=player.getMainHandStack().copy();editor2OriginalFlying=player.getAbilities().flying;editor2OriginalNoGravity=player.hasNoGravity();editor2ActorOwned=true;
            if(originalGameMode!=GameMode.CREATIVE)player.changeGameMode(GameMode.CREATIVE);
            boolean editable=CityEditorServer.editable(player);report.addProperty("editor2ActorEditable",editable);
            check(editable,"editor2 creative actor has editor permission");
            player.getAbilities().flying=true;player.setNoGravity(true);player.setVelocity(Vec3d.ZERO);player.sendAbilitiesUpdate();
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));
            catalogue=UnifiedCreativeCatalogue.canonicalItems();auditCanonicalItemIds();
            report.add("catalogueAudit",EditorCatalogue.audit());
            check(report.getAsJsonObject("catalogueAudit").get("pass").getAsBoolean(),"canonical creative catalogue audit");
            editor2Targets.add(new BlockPos(117,62,-1091));editor2Targets.add(new BlockPos(257,55,-1010));
            editor2Targets.add(new BlockPos(117,141,-708));editor2Targets.add(new BlockPos(260,56,-921));
            editor2Targets.add(EDITOR2_WALL);
            report.addProperty("runScope","EDITOR2_REAL_CITY_SERVER_AND_CLIENT_SNAPSHOT");
            report.addProperty("appearanceCheckScope","Matrix checks physical block state, native index, block entity data, light, sparse styles, client snapshots and observed paired facade quads at selected city cells and a placed wall; full model topology and all nonstone faces are not verified.");
            report.add("editor2QuadEvidence",editor2QuadEvidence);
            report.add("editor2ClientLatencies",editor2ClientLatencies);
            report.addProperty("editor2VisualProof","paired facade material hooks check observed quad positions, light and appearance; full model topology and nonstone faces require separate inspection");
            editor2Stage=1;return;
        }
        if(editor2Target>=editor2Targets.size()) {
            cleanupEditor2Wall(player);
            restoreEditor2Actor(player);
            finalCleanupVerified=true;editor2MatrixDone=true;report.addProperty("editor2MatrixComplete",true);
            editor2ObservedTarget=null;
            phase=(catalogueOnly||functionalAfterEditor2)?Phase.START:Phase.DONE;return;
        }
        BlockPos pos=editor2Targets.get(editor2Target);
        if(editor2Stage==1) {
            // Existing city points are untouched; the final point is a disposable canonical-wall fixture.
            world.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,true);
            if(loaded(world,pos)==null)throw new IllegalStateException("Target chunk unavailable: "+pos);
            if(pos.equals(EDITOR2_WALL)&&!editor2WallOwned) {
                BlockPos floor=pos.down();
                if(!world.getBlockState(pos).isAir()||!world.getBlockState(floor).isAir()||style(world,pos)!=-1||style(world,floor)!=-1)
                    throw new IllegalStateException("Prototype wall fixture and floor must be empty: "+pos);
                ItemStack wall=catalogue.stream().filter(s->Registries.ITEM.getId(s.getItem()).toString().equals("bloodborne_dw:prototype_wall")).findFirst().orElseThrow(()->new IllegalStateException("Canonical prototype_wall item absent")).copy();
                if(!world.setBlockState(floor,Blocks.STONE.getDefaultState(),3))throw new IllegalStateException("Cannot create temporary wall floor");
                editor2FloorOwned=true;setPlacementAim(player,pos);
                ActionResult placedResult=interactOn(player,floor,wall);
                editor2WallOwned=!world.getBlockState(pos).isAir();
                if(!placedResult.isAccepted()||!editor2WallOwned||!Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals("bloodborne_dw:prototype_wall"))throw new IllegalStateException("Normal canonical prototype wall placement failed at "+pos);
                player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));
                report.addProperty("editor2PrototypeWallFixture",pos.toShortString());editor2StartWait(player);waitTicks=0;editor2Stage=11;return;
            }
            if(!StonePaletteSupport.paintable(world,pos))throw new IllegalStateException("Target is not a paintable city cell: "+pos+" "+world.getBlockState(pos));
            editor2Before=style(world,pos);
            if(editor2Before!=(pos.equals(EDITOR2_WALL)?0:-1))throw new IllegalStateException("Unexpected initial city or placed wall style at "+pos+": "+editor2Before);
            editor2State=world.getBlockState(pos);editor2Native=NativeVisuals.index(world,pos);
            editor2ChunkSize=cells(loaded(world,pos)).size();editor2Light=world.getLightLevel(pos);
            var be=world.getBlockEntity(pos);editor2BlockEntity=be==null?null:be.createNbtWithId();
            EditorRenderProbe.setTarget(pos);aimEditor2(player,pos);clientSnapshotStyle=-2;editor2ClientReadiness=null;editor2ObservedTarget=pos.toImmutable();
            editor2ReadyDeadline=System.nanoTime()+180_000_000_000L;editor2NextReadyReport=0;waitTicks=0;editor2Stage=2;return;
        }
        if(editor2Stage==11){if(System.nanoTime()>=editor2WaitDeadline||CityEditorServer.pendingStyle(world,pos)!=-2||cells(loaded(world,pos)).style(pos)!=0){editor2Wait(player,pos,0,"fixture server flush");return;}editor2Stage=1;return;}
        if(editor2Stage==2) {
            Editor2ClientReadiness ready=editor2ClientReadiness;
            boolean initialReady=ready!=null&&ready.target().equals(pos)&&ready.chunkLoaded()&&ready.style()==editor2Before
                    &&ready.nativeIndex()==editor2Native&&ready.state().equals(editor2State.toString())
                    &&ready.dimension().equals(world.getRegistryKey().getValue().toString())
                    &&new Vec3d(ready.playerX(),ready.playerY(),ready.playerZ()).squaredDistanceTo(Vec3d.ofCenter(pos))<=64.0;
            if(!initialReady) {
                long now=System.nanoTime();
                if(now>=editor2NextReadyReport) {
                    report.add("editor2ClientReadiness",editor2ReadinessJson(ready,pos,player));
                    report.addProperty("progressPhase","EDITOR2_WAIT_CLIENT");
                    Files.writeString(Path.of(output),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
                    editor2NextReadyReport=now+5_000_000_000L;
                }
                if(now>=editor2ReadyDeadline)throw new IllegalStateException("Client did not load initial city cell within 180 seconds: "+pos+" readiness="+editor2ReadinessJson(ready,pos,player));
                return;
            }
            report.add("editor2ClientReadiness",editor2ReadinessJson(ready,pos,player));report.addProperty("progressPhase","EDITOR2_MATRIX");
            if(!editor2ActionReady(player))return;
            invoke(player,config(editor2Colour==4?0:editor2Colour+1,0,"single"));editor2Stage=3;return;
        }
        if(editor2Stage==3) {
            if(!editor2ActionReady(player))return;
            aimEditor2(player,pos);
            Method target=CityEditorServer.class.getDeclaredMethod("target",ServerPlayerEntity.class);target.setAccessible(true);
            BlockHitResult hit=(BlockHitResult)target.invoke(null,player);
            if(hit==null||!hit.getBlockPos().equals(pos))throw new IllegalStateException("Cannot raycast city target "+pos+"; actual="+hit);
            if(EditorRenderProbe.enabled())editor2Epoch=EditorRenderProbe.arm(pos,editor2Colour==4?0:editor2Colour+1);
            editor2StartWait(player);invoke(player,request("paint"));waitTicks=0;editor2Stage=4;return;
        }
        int expected=editor2Colour==4?0:editor2Colour+1;
        if(editor2Stage==4||editor2Stage==6||editor2Stage==8||editor2Stage==10) {
            int wanted=(editor2Stage==6||editor2Stage==10)?editor2Before:expected;
            if(jobActive(player)){editor2Wait(player,pos,wanted,"server job");return;}
            if(style(world,pos)!=wanted){editor2Wait(player,pos,wanted,"server style");return;}
            Editor2ClientReadiness sample=editor2ClientReadiness;
            if(clientSnapshotStyle!=wanted||sample==null||!sample.target().equals(pos)||!sample.chunkLoaded()||sample.style()!=wanted||sample.sampledNano()<editor2WaitStart){editor2Wait(player,pos,wanted,"fresh client snapshot");return;}
            if(!editor2ClientLatencyRecorded){
                double seconds=(sample.sampledNano()-editor2WaitStart)/1_000_000_000.0;
                JsonObject latency=new JsonObject();latency.addProperty("target",pos.toShortString());latency.addProperty("stage",editor2Stage);latency.addProperty("style",wanted);latency.addProperty("seconds",seconds);latency.addProperty("overFiveSecondsInvestigate",seconds>5.0);latency.addProperty("startServerTick",editor2WaitServerTick);latency.addProperty("sampleObservedAtServerTick",player.getServer().getTicks());latency.addProperty("startClientTick",editor2WaitClientTick);latency.addProperty("sampleClientTick",sample.sampledTick());editor2ClientLatencies.add(latency);editor2ClientLatencyRecorded=true;
            }
            if(++waitTicks<12){editor2Wait(player,pos,wanted,"render settling");return;} // Give the terrain rebuild observer time to record this epoch.
            if(EditorRenderProbe.enabled() && (editor2Stage==4||editor2Stage==8) && EditorRenderProbe.liveStyle()==wanted) {
                long generation=editor2Epoch;long[] stats=EditorModelProbe.quadStats(generation);
                // The compact reference is the known FacadeModel route. Require actual changed material quads
                // for each manual colour, rather than inferring a visible transform from saved style.
                if((editor2Target==0||pos.equals(EDITOR2_WALL)||pos.equals(new BlockPos(260,56,-921))) && wanted>0 && (stats[0]==0||stats[1]==0)) {
                    editor2Wait(player,pos,wanted,"changed render quad generation="+generation+" observed="+stats[0]+" changed="+stats[1]);return;
                }
                check(stats[2]==0,"observed manual quad positions and light preserved at "+pos+" style "+wanted);
                JsonObject row=new JsonObject();row.addProperty("position",pos.toShortString());row.addProperty("style",wanted);row.addProperty("stage",editor2Stage);row.addProperty("generation",generation);row.addProperty("observed",stats[0]);row.addProperty("appearanceChanged",stats[1]);row.addProperty("geometryOrLightChanged",stats[2]);editor2QuadEvidence.add(row);
            } else if(EditorRenderProbe.enabled() && (editor2Stage==4||editor2Stage==8) && (editor2Target==0||pos.equals(EDITOR2_WALL)||pos.equals(new BlockPos(260,56,-921))) && wanted>0) {
                editor2Wait(player,pos,wanted,"render probe style");return;
            }
            if(System.nanoTime()>=editor2WaitDeadline){editor2Wait(player,pos,wanted,"matching client and render completed after deadline");return;}
            var be=world.getBlockEntity(pos);NbtCompound nbt=be==null?null:be.createNbtWithId();
            check(world.getBlockState(pos)==editor2State && NativeVisuals.index(world,pos)==editor2Native && java.util.Objects.equals(editor2BlockEntity,nbt) && world.getLightLevel(pos)==editor2Light,"city physical state, native index, block entity and light preserved at "+pos+" stage "+editor2Stage);
            check(cells(loaded(world,pos)).size()==editor2ChunkSize+(wanted==-1?0:1)-(editor2Before==-1?0:1),"city editor storage remains sparse at "+pos+" stage "+editor2Stage);
            waitTicks=0;
            if(editor2Stage==10){editor2Colour++;if(editor2Colour==(pos.equals(EDITOR2_WALL)?4:5)){editor2Colour=0;editor2Target++;editor2State=null;editor2Stage=1;}else editor2Stage=2;}
            else editor2Stage++;
            return;
        }
        if(editor2Stage==5||editor2Stage==7||editor2Stage==9) {
            if(!editor2ActionReady(player))return;
            if(EditorRenderProbe.enabled())editor2Epoch=EditorRenderProbe.arm(pos,editor2Stage==7?expected:editor2Before);
            editor2StartWait(player);invoke(player,request(editor2Stage==7?"redo":"undo"));waitTicks=0;editor2Stage++;return;
        }
        throw new IllegalStateException("Unknown editor2 stage "+editor2Stage);
    }
    private boolean editor2ActionReady(ServerPlayerEntity player) throws ReflectiveOperationException {
        int now=player.getServer().getTicks();
        if(now-editor2LastAction<2)return false;
        Object existing=session(player);
        if(existing!=null && now-intField(existing,"lastRequest")<2)return false;
        editor2LastAction=now;return true;
    }
    private void editor2StartWait(ServerPlayerEntity player) {
        editor2WaitStart=System.nanoTime();editor2WaitDeadline=editor2WaitStart+30_000_000_000L;
        editor2WaitServerTick=player.getServer().getTicks();editor2WaitClientTick=ticks;editor2ClientLatencyRecorded=false;
    }
    private void editor2Wait(ServerPlayerEntity player,BlockPos pos,int wanted,String reason) throws ReflectiveOperationException {
        long now=System.nanoTime();if(now<editor2WaitDeadline)return;
        Object current=session(player);boolean active=current!=null&&field(current,"job")!=null;
        JsonObject detail=new JsonObject();detail.addProperty("reason",reason);detail.addProperty("target",pos.toShortString());detail.addProperty("stage",editor2Stage);
        detail.addProperty("wanted",wanted);detail.addProperty("serverStyle",style(player.getServerWorld(),pos));detail.addProperty("clientStyle",clientSnapshotStyle);
        detail.addProperty("serverJobActive",active);detail.addProperty("serverMessage",current==null?"NO_SESSION":sessionString(player,"message"));
        detail.addProperty("nowNano",now);detail.addProperty("waitSeconds",(now-editor2WaitStart)/1_000_000_000.0);
        detail.addProperty("serverTick",player.getServer().getTicks());detail.addProperty("startServerTick",editor2WaitServerTick);
        detail.addProperty("clientTick",ticks);detail.addProperty("startClientTick",editor2WaitClientTick);detail.addProperty("renderEpoch",editor2Epoch);
        report.add("editor2WaitTimeout",detail);throw new IllegalStateException("Editor2 30-second wait expired: "+detail);
    }
    private void restoreEditor2Actor(ServerPlayerEntity player) {
        if(!editor2ActorOwned)return;
        player.setStackInHand(Hand.MAIN_HAND,editor2Held);
        if(originalGameMode!=null&&player.interactionManager.getGameMode()!=originalGameMode)player.changeGameMode(originalGameMode);
        player.getAbilities().flying=editor2OriginalFlying;player.setNoGravity(editor2OriginalNoGravity);player.sendAbilitiesUpdate();editor2ActorOwned=false;
    }
    private void restoreFunctionalActor(ServerPlayerEntity player) {
        if(!functionalActorOwned)return;
        player.setStackInHand(Hand.MAIN_HAND,functionalOriginalHeld);
        if(originalGameMode!=null&&player.interactionManager.getGameMode()!=originalGameMode)player.changeGameMode(originalGameMode);
        player.getAbilities().flying=functionalOriginalFlying;player.setNoGravity(functionalOriginalNoGravity);player.sendAbilitiesUpdate();functionalActorOwned=false;
    }
    private JsonObject editor2ReadinessJson(Editor2ClientReadiness ready,BlockPos expected,ServerPlayerEntity player) {
        JsonObject row=new JsonObject();row.addProperty("target",expected.toShortString());row.addProperty("available",ready!=null);
        row.addProperty("expectedStyle",editor2Before);row.addProperty("expectedState",editor2State.toString());row.addProperty("expectedNativeIndex",editor2Native);
        row.addProperty("serverDimension",player.getServerWorld().getRegistryKey().getValue().toString());
        row.addProperty("serverPlayerX",player.getX());row.addProperty("serverPlayerY",player.getY());row.addProperty("serverPlayerZ",player.getZ());
        if(ready!=null){row.addProperty("observedTarget",ready.target().toShortString());row.addProperty("chunkLoaded",ready.chunkLoaded());row.addProperty("state",ready.state());row.addProperty("nativeIndex",ready.nativeIndex());row.addProperty("style",ready.style());row.addProperty("dimension",ready.dimension());row.addProperty("playerX",ready.playerX());row.addProperty("playerY",ready.playerY());row.addProperty("playerZ",ready.playerZ());row.addProperty("screen",ready.screen());row.addProperty("playerWithinEightBlocks",new Vec3d(ready.playerX(),ready.playerY(),ready.playerZ()).squaredDistanceTo(Vec3d.ofCenter(expected))<=64.0);}
        return row;
    }
    private record Editor2View(Vec3d feet,float yaw,float pitch) {}
    private static final Map<BlockPos,Editor2View> EDITOR2_VIEWS=new HashMap<>();
    private boolean editor2View(ServerPlayerEntity player,BlockPos pos,Editor2View view,boolean requireClear,StringBuilder diagnostics) throws ReflectiveOperationException {
        ServerWorld world=player.getServerWorld();Vec3d feet=view.feet();
        if(requireClear&&!world.isSpaceEmpty(player,player.getBoundingBox().offset(feet.x-player.getX(),feet.y-player.getY(),feet.z-player.getZ())))return false;
        Vec3d eye=feet.add(0,player.getStandingEyeHeight(),0);
        var hit=world.raycast(new net.minecraft.world.RaycastContext(eye,eye.add(Vec3d.fromPolar(view.pitch(),view.yaw()).multiply(6.0)),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
        if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK||!hit.getBlockPos().equals(pos)) {
            if(diagnostics.length()<700)diagnostics.append(" [eye=").append(eye).append(" yaw=").append(view.yaw()).append(" pitch=").append(view.pitch()).append(" hit=").append(hit.getType()).append(':').append(hit.getBlockPos()).append(']');
            return false;
        }
        player.teleport(world,feet.x,feet.y,feet.z,view.yaw(),view.pitch());
        // LivingEntity.getRotationVec(1) reads headYaw; same-world teleport sets body yaw only.
        player.setHeadYaw(view.yaw());
        Vec3d actualEye=player.getEyePos(),actualDirection=player.getRotationVec(1);
        if(actualEye.squaredDistanceTo(eye)>1.0e-8 || actualDirection.squaredDistanceTo(Vec3d.fromPolar(view.pitch(),view.yaw()))>1.0e-8)
            throw new IllegalStateException("QA orientation differs after teleport: eye="+actualEye+" expected="+eye+" direction="+actualDirection+" view="+view);
        Method target=CityEditorServer.class.getDeclaredMethod("target",ServerPlayerEntity.class);target.setAccessible(true);
        BlockHitResult actual=(BlockHitResult)target.invoke(null,player);
        if(actual==null||!actual.getBlockPos().equals(pos)) {
            if(diagnostics.length()<700)diagnostics.append(" [server hit=").append(actual==null?null:actual.getBlockPos()+" side="+actual.getSide()+" point="+actual.getPos()).append(" view=").append(view).append(']');
            return false;
        }
        EDITOR2_VIEWS.put(pos.toImmutable(),view);
        JsonObject views=report.has("editor2Viewpoints")?report.getAsJsonObject("editor2Viewpoints"):new JsonObject();
        JsonObject row=new JsonObject();row.addProperty("feet",feet.toString());row.addProperty("yaw",view.yaw());row.addProperty("pitch",view.pitch());row.addProperty("collisionFree",requireClear);row.addProperty("exactServerRayHit",true);
        views.add(pos.toShortString(),row);report.add("editor2Viewpoints",views);return true;
    }
    private void aimEditor2(ServerPlayerEntity player, BlockPos pos) throws ReflectiveOperationException {
        ServerWorld world=player.getServerWorld();StringBuilder diagnostics=new StringBuilder();
        List<Vec3d> points=new ArrayList<>();points.add(Vec3d.ofCenter(pos));
        var boxes=world.getBlockState(pos).getOutlineShape(world,pos).getBoundingBoxes();
        for(int i=0;i<Math.min(32,boxes.size());i++) {
            var box=boxes.get(i);points.add(new Vec3d(pos.getX()+(box.minX+box.maxX)/2,pos.getY()+(box.minY+box.maxY)/2,pos.getZ()+(box.minZ+box.maxZ)/2));
        }
        Editor2View known=pos.equals(new BlockPos(117,62,-1091))?new Editor2View(new Vec3d(114.518,61.262,-1089.413),-116.6F,10.0F):null;
        Editor2View cached=EDITOR2_VIEWS.get(pos);int candidates=0;
        // Every accepted viewpoint must fit the normal player collision box.
        for(boolean requireClear:new boolean[]{true}) {
            if(known!=null){candidates++;if(editor2View(player,pos,known,requireClear,diagnostics))return;}
            if(cached!=null){candidates++;if(editor2View(player,pos,cached,requireClear,diagnostics))return;}
            for(Vec3d point:points)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
                if(dx==0&&dy==0&&dz==0)continue;
                Vec3d direction=new Vec3d(dx,dy,dz).normalize();
                for(double distance:new double[]{1.5,2.5,3.5,4.5}) {
                    Vec3d eye=point.add(direction.multiply(distance));Vec3d toward=point.subtract(eye);
                    float yaw=(float)Math.toDegrees(Math.atan2(-toward.x,toward.z));
                    float pitch=(float)-Math.toDegrees(Math.atan2(toward.y,Math.sqrt(toward.x*toward.x+toward.z*toward.z)));
                    Editor2View view=new Editor2View(eye.add(0,-player.getStandingEyeHeight(),0),yaw,pitch);candidates++;
                    if(editor2View(player,pos,view,requireClear,diagnostics))return;
                }
            }
        }
        throw new IllegalStateException("No exact OUTLINE ray to "+pos+"; boxes="+boxes.size()+" candidates="+candidates+diagnostics);
    }
    private void cleanupEditor2Wall(ServerPlayerEntity player) {
        ServerWorld world=player.getServerWorld();
        if(editor2WallOwned) {if(!physicalBreak(player,EDITOR2_WALL))throw new IllegalStateException("Could not normally break prototype wall fixture");editor2WallOwned=false;}
        if(editor2FloorOwned) {if(!physicalBreak(player,EDITOR2_WALL.down()))throw new IllegalStateException("Could not normally break prototype wall floor");editor2FloorOwned=false;}
        if(!world.getBlockState(EDITOR2_WALL).isAir()||!world.getBlockState(EDITOR2_WALL.down()).isAir()||style(world,EDITOR2_WALL)!=-1)
            throw new IllegalStateException("Prototype wall fixture did not clean up");
        report.addProperty("editor2PrototypeWallCleanupVerified",true);
    }

    private boolean recoverScenarioFailure(ServerPlayerEntity player,MinecraftClient client,Throwable error){
        Phase failedPhase=phase;
        boolean failedInCatalogue=failedPhase==Phase.CATALOG_PLACE||failedPhase==Phase.CATALOG_CLEAR||failedPhase==Phase.CATALOG_RESTORE_VERIFY;
        boolean functionalPhase=!catalogueOnly&&!failedInCatalogue&&failedPhase!=Phase.CLEANUP
                &&failedPhase!=Phase.PERSIST_WRITE_PREP&&failedPhase!=Phase.PERSIST_WRITE_APPLY
                &&failedPhase!=Phase.PERSIST_WRITE_SAVE&&failedPhase!=Phase.PERSIST_VERIFY_CLIENT
                &&failedPhase!=Phase.PERSIST_VERIFY_BREAK&&failedPhase!=Phase.DONE;
        if(functionalPhase){functionalSuiteFailed=true;report.addProperty("functionalSuiteStatus","FAIL");}
        if(failedPhase==Phase.WAIT_QUICK_CLICK||failedPhase==Phase.QUICK_CLICK){
            report.addProperty("quickClickFailureDiagnostic",quickClickDiagnostic(player));
            report.addProperty("quickClickRecoveryAttempted",true);
            client.execute(()->{
                try{
                    Class<?> type=Class.forName("dev.dreamwalker.bloodbornedw.editor.CityEditorClient");
                    Method reset=type.getDeclaredMethod("resetStroke");reset.setAccessible(true);reset.invoke(null);
                }catch(Throwable resetError){failures.add("client stroke reset failed during quick-click recovery: "+resetError.getMessage());}
            });
            invoke(player,request("cancel"));pendingReplacementTick=player.getServer().getTicks();phase=Phase.QUICK_RECOVER_CANCEL;waitTicks=0;
            return true;
        }
        if(fixtureBaseline.isEmpty()||baseFloor==null||catalogue.isEmpty())return false;
        try{
            cleanup(player);
            if(!matches(player.getServerWorld(),fixtureBaseline)){
                report.addProperty("scenarioRecoveryAbortedUnsafe",true);
                report.addProperty("scenarioRecoveryReason","bounded owned fixture did not match its captured baseline after cleanup");
                return false;
            }
            if(failedInCatalogue){
                String failedId=cursor<catalogueRun.size()?catalogueKey(catalogueRun.get(cursor)):"unknown catalogue entry";
                JsonObject row=null;
                for(int i=items.size()-1;i>=0;i--){JsonObject candidate=items.get(i).getAsJsonObject();if(candidate.has("id")&&candidate.get("id").getAsString().equals(failedId)){row=candidate;break;}}
                if(row==null){row=new JsonObject();row.addProperty("id",failedId);items.add(row);}
                row.addProperty("status","FAIL");row.addProperty("reason","scenario failure; bounded fixture restored before continuing remaining catalogue");row.addProperty("restored",true);
                failures.add(failedId+": scenario failure; bounded fixture restored before continuing remaining catalogue");cursor++;
            }
            else cursor=0;
            player.changeGameMode(GameMode.CREATIVE);enableAlt(player.getServer());
            ensureFixtureAir(player.getServerWorld());
            fixtureBaseline=captureFixture(player.getServerWorld());fixtureOwned=true;
            safeFixture=findSafeTestStack();
            if(safeFixture.isEmpty())throw new IllegalStateException("Cannot recover catalogue phase without a safe fixture carrier");
            catalogSupport=new BlockPos(baseFloor.getX()+6,baseFloor.getY()+11,baseFloor.getZ()+4);
            catalogFloor=catalogSupport.up();catalogTarget=catalogFloor.up();
            player.getServerWorld().setBlockState(catalogSupport,Blocks.STONE.getDefaultState(),3);
            player.getServerWorld().setBlockState(catalogFloor,Blocks.STONE.getDefaultState(),3);
            catalogBaseline=captureFixture(player.getServerWorld());
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));
            catalogueRecoveryStarted=true;report.addProperty("catalogueRecoveryStarted",true);
            report.addProperty("catalogueRecoveryAfterPhase",failedPhase.toString());
            report.addProperty("catalogueRecoveryCursor",cursor);
            report.addProperty("functionalSuiteStatus","FAIL_CONTINUED_WITH_CATALOGUE");
            finalCleanupVerified=false;phase=Phase.CATALOG_PLACE;waitTicks=0;
            return true;
        }catch(Throwable recoveryError){
            failures.add("bounded scenario recovery failed: "+recoveryError.getClass().getSimpleName()+": "+recoveryError.getMessage());
            report.addProperty("scenarioRecoveryAbortedUnsafe",true);return false;
        }
    }

    private static boolean usesEditorAction(Phase phase) {
        return switch (phase) {
            case AUTO_SAMPLE,SINGLE_PAINT,SAMPLE,RESET,UNDO,REDO,FIRST,SECOND,AREA_PAINT,FILL,NOISE,
                PLANE_PAINT,PLANE_UNDO,STROKE_PAINT_A,STROKE_PAINT_B,STROKE_END,
                STROKE_UNDO,STROKE_REDO,INTERRUPT_FIRST,INTERRUPT_SECOND,INTERRUPT_PAINT,INTERRUPT_UNDO,STALE_UNDO -> true;
            default -> false;
        };
    }

    private void timeout(String message) {
        if (++waitTicks > 100) throw new IllegalStateException(message);
    }

    private void ensureFixtureAir(ServerWorld world) {
        int minX = baseFloor.getX() - 1, maxX = baseFloor.getX() + 14;
        int minZ = baseFloor.getZ() - 1, maxZ = baseFloor.getZ() + 11;
        int minY = baseFloor.getY(), maxY = baseFloor.getY() + 18;
        for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            BlockPos pos = new BlockPos(x, y, z);
            WorldChunk chunk = loaded(world, pos);
            if (chunk == null || !world.getBlockState(pos).isAir()) throw new IllegalStateException("High city fixture is not empty: " + pos);
        }
        report.addProperty("fixture", "Overworld city bounds x=" + minX + ".." + maxX + " z=" + minZ + ".." + maxZ + " y=" + minY + ".." + maxY);
    }
    private static WorldChunk loaded(ServerWorld world, BlockPos pos) {
        var chunk = world.getChunkManager().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
        return chunk instanceof WorldChunk ready ? ready : null;
    }
    private static BlockPos persistencePosition(ServerWorld world) { return new BlockPos(164, world.getTopY() - 34, -812); }
    private void reportPersistence(ServerWorld world) {
        report.addProperty("persistenceSentinelPosition", persistencePos.toString());
        report.addProperty("persistenceSentinelBlock", Registries.BLOCK.getId(world.getBlockState(persistencePos).getBlock()).toString());
        report.addProperty("persistenceSentinelNativeIndex", NativeVisuals.index(world, persistencePos));
        report.addProperty("persistenceSentinelStyle", style(world, persistencePos));
        report.addProperty("persistenceSentinelRetained", true);
    }
    private static void saveNormally(MinecraftServer server) throws Exception {
        if (!server.saveAll(false, true, true)) throw new IllegalStateException("MinecraftServer.saveAll failed");
    }
    private ItemStack findSafeTestStack() {
        for (ItemStack stack : catalogue) if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof SourceFallbackCarrierV1271) return stack.copy();
        for (var item : Registries.ITEM) if (item instanceof BlockItem block && block.getBlock() instanceof SourceFallbackCarrierV1271) return new ItemStack(item);
        return ItemStack.EMPTY;
    }
    private BlockPos findAutoSample(ServerWorld world,ServerPlayerEntity player){
        for(int y=88;y<=100;y++)for(int z=-816;z<=-795;z++)for(int x=166;x<=184;x++){
            BlockPos pos=new BlockPos(x,y,z);BlockState state=world.getBlockState(pos);
            if(!CityEditorServer.supported(state)||style(world,pos)!=-1||CityEditorServer.effectiveStyle(world,pos)!=1)continue;
            Vec3d eye=new Vec3d(x+.5,y+3.0+player.getStandingEyeHeight(),z+1.5);
            BlockHitResult hit=world.raycast(new net.minecraft.world.RaycastContext(eye,Vec3d.ofCenter(pos),net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
            if(hit.getType()==net.minecraft.util.hit.HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos))return pos.toImmutable();
        }
        return null;
    }
    private List<SectionFixture> captureFixture(ServerWorld world) {
        List<SectionFixture> result=new ArrayList<>();
        for(int y=baseFloor.getY();y<=baseFloor.getY()+18;y++)
            for(int z=baseFloor.getZ()-1;z<=baseFloor.getZ()+11;z++)
                for(int x=baseFloor.getX()-1;x<=baseFloor.getX()+14;x++) {
                    BlockPos p=new BlockPos(x,y,z);result.add(new SectionFixture(p,world.getBlockState(p),style(world,p),NativeVisuals.index(world,p)));
                }
        return List.copyOf(result);
    }
    private void runCellsDataChecks(ServerWorld world) throws ReflectiveOperationException {
        Class<?> ingressType=Class.forName("dev.dreamwalker.bloodbornedw.editor.CityEditorServer$Ingress");
        var constructor=ingressType.getDeclaredConstructor();constructor.setAccessible(true);Object ingress=constructor.newInstance();
        Method acquire=ingressType.getDeclaredMethod("acquire"),release=ingressType.getDeclaredMethod("release");acquire.setAccessible(true);release.setAccessible(true);
        for(int i=0;i<64;i++)check((boolean)acquire.invoke(ingress),"ingress admitted bounded slot "+i);
        check(!(boolean)acquire.invoke(ingress),"ingress rejects pending request 65 before enqueue");
        release.invoke(ingress);Field updated=ingressType.getDeclaredField("updated");updated.setAccessible(true);updated.setLong(ingress,System.nanoTime()-1_000_000_000L);
        check((boolean)acquire.invoke(ingress),"ingress recovers one completed slot after refill");
        check(!(boolean)acquire.invoke(ingress),"ingress pending cap remains enforced after token refill");
        WorldChunk chunk = loaded(world, baseFloor);
        if (chunk == null) throw new IllegalStateException("Fixture chunk missing");
        ChunkPos cp = chunk.getPos(); int bottom = world.getBottomY(), top = world.getTopY();
        BlockPos a = new BlockPos(cp.getStartX(), bottom, cp.getStartZ()), b = new BlockPos(cp.getEndX(), top - 1, cp.getEndZ());
        EditorCells empty = EditorCells.EMPTY, cells = empty.withAll(Map.of(a.asLong(), 0, b.asLong(), 4));
        check(empty.size() == 0 && cells.size() == 2 && cells.style(a) == 0 && cells.style(b) == 4, "immutable batched palette add");
        EditorCells removed = cells.withAll(Map.of(a.asLong(), -1, b.asLong(), 1));
        check(removed.size() == 1 && removed.style(a) == -1 && removed.style(b) == 1 && cells.style(b) == 4, "immutable batched removal");
        EditorCells roundCells = EditorCells.readPacked(cells.packed(), cp, bottom, top);
        check(roundCells.size() == cells.size() && roundCells.positionAt(0) == cells.positionAt(0) && roundCells.styleAt(1) == cells.styleAt(1), "packed editor NBT round trip");
        NbtCompound exported = cells.packed(); long[] exportedPositions = exported.getLongArray("Positions"); byte[] exportedStyles = exported.getByteArray("Styles");
        exportedPositions[0] = b.asLong(); exportedStyles[0] = 3;
        check(cells.style(a) == 0 && cells.style(b) == 4 && cells.positionAt(0) == a.asLong(), "mutating packed exported arrays cannot change immutable editor cells");
        rejected(() -> { NbtCompound n = new NbtCompound(); n.putLongArray("Positions", new long[]{a.asLong(), a.asLong()}); n.putByteArray("Styles", new byte[]{0, 1}); EditorCells.readPacked(n, cp, bottom, top); }, "packed duplicate position rejected");
        rejected(() -> { NbtCompound n = new NbtCompound(); n.putLongArray("Positions", new long[]{a.asLong()}); n.putByteArray("Styles", new byte[0]); EditorCells.readPacked(n, cp, bottom, top); }, "packed array length mismatch rejected");
        NbtList duplicate = cells.nbt(); duplicate.add(duplicate.get(0).copy());
        rejected(() -> EditorCells.read(duplicate, cp, bottom, top), "legacy list duplicate rejected");
        NativeVisualData emptyData = NativeVisualData.EMPTY;
        NativeVisualData attached = ((RetainedFrameData)(Object)emptyData).dw$withEditor(cells);
        attached = ((RetainedFrameData)(Object)attached).dw$withFrames(Map.of(sampleTarget.asLong(), 1));
        NativeVisualData copy = attached.with(sampleTarget, 0);
        check(emptyData.empty() && ((RetainedFrameData)(Object)emptyData).dw$editor().size() == 0
            && ((RetainedFrameData)(Object)copy).dw$frames().size() == 1, "native snapshots preserve independent editor/frame metadata");
        NativeVisualData contextData=((RetainedFrameData)(Object)NativeVisuals.data(chunk)).dw$withEditor(cells);
        contextData=((RetainedFrameData)(Object)contextData).dw$withFrames(Map.of(sampleTarget.asLong(),1));
        NativeVisualData restored = NativeVisualData.read(contextData.nbt(), chunk);
        check(((RetainedFrameData)(Object)restored).dw$editor().size() == cells.size()
            && ((RetainedFrameData)(Object)restored).dw$frames().size() == 1, "native chunk save/reload NBT round trip");
    }
    private interface Check { void run() throws Exception; }
    private void rejected(Check operation, String label) {
        try { operation.run(); } catch (IllegalArgumentException expected) { checks.add(label); return; }
        catch (Exception unexpected) { throw new AssertionError(label, unexpected); }
        throw new AssertionError(label);
    }
    private static int noiseStyle(BlockPos pos, int material) {
        long random = net.minecraft.util.math.MathHelper.hashCode(pos.getX(), pos.getY(), pos.getZ());
        int value = Math.floorMod(random, 100); return value < 80 ? material : value < 95 ? 2 : 3;
    }
    private boolean allStyles(ServerWorld world, List<BlockPos> positions, int expected) {
        for (BlockPos p : positions) if (style(world, p) != expected) return false;
        return true;
    }
    private List<BlockPos> planeTargets(){
        BlockPos center=gridTargets.get(71);List<BlockPos> result=new ArrayList<>(9);
        for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++)result.add(center.add(x,0,z));return result;
    }
    private static void nativeRoundTrip(ServerWorld world, BlockPos pos) {
        WorldChunk chunk = loaded(world, pos); NativeVisualData before = NativeVisuals.data(chunk);
        NativeVisualData read = NativeVisualData.read(before.nbt(), chunk);
        if (((RetainedFrameData)(Object)read).dw$editor().style(pos) != ((RetainedFrameData)(Object)before).dw$editor().style(pos))
            throw new AssertionError("saved chunk metadata did not restore editor style");
    }
    private void enableAlt(MinecraftServer server) throws ReflectiveOperationException {
        Method get = CityVisualModeState.class.getDeclaredMethod("get", MinecraftServer.class); get.setAccessible(true);
        visualState = get.invoke(null, server); visualAlt = CityVisualModeState.class.getDeclaredField("alt"); visualAlt.setAccessible(true);
        originalAlt = visualAlt.getBoolean(visualState); visualAlt.setBoolean(visualState, true); ((net.minecraft.world.PersistentState)visualState).markDirty();
        report.addProperty("originalAlt", originalAlt);
    }
    private void restoreMode() {
        try { if (visualAlt != null && visualState != null) { visualAlt.setBoolean(visualState, originalAlt); ((net.minecraft.world.PersistentState)visualState).markDirty(); } }
        catch (ReflectiveOperationException e) { failures.add("could not restore city mode: " + e.getMessage()); }
    }

    private void interruptAtTickStart(MinecraftServer server) {
        if (phase==Phase.DONE || !interruptArmed || server.getPlayerManager().getPlayer(playerId) == null) return;
        try {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            Object session = session(player), job = field(session, "job");
            if (job == null || intField(job, "direction") != -1) return;
            @SuppressWarnings("unchecked") List<Object> changes = (List<Object>)field(field(job, "edit"), "changes");
            int index = intField(job, "index");
            if (index <= 0 || index >= changes.size()) return; // Let one bounded batch apply first.
            interruptedPos = (BlockPos)field(changes.get(index), "pos");
            pendingPos = interruptedPos; interruptDone = ((List<?>)field(job, "done")).size();
            staleOtherPos = index + 1 < changes.size() ? (BlockPos)field(changes.get(index + 1), "pos") : null;
            pendingExpected = staleOtherPos == null ? -1 : ((Number)field(changes.get(index + 1), "after")).intValue();
            setAim(player, interruptedPos);
            if (!physicalBreak(player, interruptedPos)) throw new IllegalStateException("could not normally break in-flight undo target");
            BlockPos support = interruptedPos.down();
            ActionResult result = interactOn(player, support, new ItemStack(Blocks.DIRT));
            if (!result.isAccepted() || !player.getServerWorld().getBlockState(interruptedPos).isOf(Blocks.DIRT))
                throw new IllegalStateException("could not replace in-flight undo target with dirt");
            interruptArmed = false;
        } catch (Throwable e) {
            interruptArmed = false; failures.add("interrupted undo fixture: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(BlockState state, net.minecraft.state.property.Property property) {
        return property.name(state.get(property));
    }
    /** Redirected assemblies and native storage require a separate visual oracle. */
    private static JsonObject appearanceCheck(ItemStack stack, BlockState state) {
        JsonObject result = new JsonObject(); JsonArray checked = new JsonArray(), mismatches = new JsonArray();
        result.add("checkedFields", checked); result.add("mismatches", mismatches);
        if (!(stack.getItem() instanceof BlockItem item) || state.getBlock() != item.getBlock()) {
            result.addProperty("scope", "not checked: placed root differs from item block; native/assembly visual identity requires a separate oracle");
            return result;
        }
        NbtCompound tag = stack.getSubNbt("BlockStateTag");
        if (tag != null) for (String name : List.of("variant", "art_bank", "material", "profile", "source", "source_bank", "trim")) {
            if (!tag.contains(name, 8)) continue;
            var property = state.getBlock().getStateManager().getProperty(name);
            String requested = tag.getString(name);
            if (property == null && name.equals("art_bank") && requested.equals("0")
                    && item.getBlock() instanceof ImportedArtworkBlock
                    && ImportedArtworkBlock.bankProperty(state) == null) {
                var variantProperty=state.getBlock().getStateManager().getProperty("variant");
                try {
                    int requestedVariant=Integer.parseInt(tag.getString("variant"));
                    int effectivePose=ImportedArtworkBlock.variant(state);
                    if(variantProperty!=null&&effectivePose==requestedVariant)
                        checked.add("art_bank=0 -> implicit default 0 (effective pose " + effectivePose + ")");
                    else mismatches.add("art_bank=0: absent bank did not resolve to requested effective pose " + requestedVariant);
                } catch(NumberFormatException invalid) { mismatches.add("art_bank=0: invalid requested variant"); }
            }
            else if (property == null) mismatches.add(name + "=" + requested + ": property absent on placed block");
            else {
                String actual = propertyName(state, property);
                checked.add(name + "=" + requested + " -> " + actual);
                if (!requested.equals(actual)) mismatches.add(name + "=" + requested + " -> " + actual);
            }
        }
        result.addProperty("scope", checked.size() == 0 && mismatches.size() == 0
                ? "not checked: no explicit comparable appearance fields" : "same physical root; explicit appearance fields only");
        return result;
    }
    private boolean isLegacyShiftedHoldStatue(ItemStack stack){
        if(!Registries.ITEM.getId(stack.getItem()).toString().equals("bloodborne_dw:source_hold_statue")||!stack.hasNbt())return false;
        NbtCompound root=stack.getNbt();
        NbtCompound art=stack.getSubNbt("BlockStateTag");
        if(art==null)return false;
        int variant;try{variant=Integer.parseInt(art.getString("variant"));}catch(NumberFormatException invalid){return false;}
        double expected=variant==0?-1.0:variant>=1&&variant<=3?-0.982370625:Double.NaN;
        if(root.contains("ImportedArtShift",11)){
            int[] shift=root.getIntArray("ImportedArtShift");return shift.length==3&&shift[0]==0&&shift[1]==expected&&shift[2]==0;
        }
        if(!root.contains("ImportedArtShift",9))return false;
        NbtList shift=root.getList("ImportedArtShift",6);
        return shift.size()==3&&shift.getDouble(0)==0.0&&shift.getDouble(1)==expected&&shift.getDouble(2)==0.0;
    }
    private CataloguePlacementContext setupCataloguePlacementContext(ServerPlayerEntity player,ItemStack stack,TouchedCells probe){
        ServerWorld world=player.getServerWorld();NbtCompound tag=stack.getSubNbt("BlockStateTag");
        if(tag==null||!tag.contains("variant",8))return null;
        int requested;try{requested=Integer.parseInt(tag.getString("variant"));}catch(NumberFormatException invalid){return null;}
        if(stack.getItem() instanceof BlockItem blockItem&&blockItem.getBlock() instanceof ImportedStairBlock
                &&(requested>=1&&requested<=4||requested>=6&&requested<=9)){
            NbtCompound art=stack.getSubNbt("BlockStateTag");
            int rotation;int rotationDelta=0;boolean legacy=art!=null&&art.contains("imported_rotation_delta",8);
            if(legacy){
                try{rotationDelta=Integer.parseInt(art.getString("imported_rotation_delta"));}
                catch(NumberFormatException invalid){throw new IllegalStateException("Invalid imported stair rotation delta in QA fixture",invalid);}
                rotation=Math.floorMod(Math.round(player.getYaw()/45.0f)+rotationDelta,8);
            }else rotation=ImportedStairShapes.rotation(player.getHorizontalFacing());
            Direction facing=switch(Math.floorMod((rotation+1)/2,4)){
                case 0->Direction.EAST;case 1->Direction.SOUTH;case 2->Direction.WEST;default->Direction.NORTH;
            };
            boolean outer=requested==3||requested==4||requested==8||requested==9;
            boolean left=requested==1||requested==3||requested==6||requested==8;
            Direction neighborFacing=left?facing.rotateYCounterclockwise():facing.rotateYClockwise();
            BlockPos neighbor=catalogTarget.offset(outer?facing:facing.getOpposite());
            boolean capturedAir=catalogBaseline.stream().anyMatch(cell->cell.pos().equals(neighbor)&&cell.state().isAir());
            if(!capturedAir||!world.getBlockState(neighbor).isAir())throw new IllegalStateException("Stair context neighbor is outside the captured empty fixture at "+neighbor);
            probe.captureBeforeFixtureChange(neighbor);catalogTemporaryStairNeighbor=neighbor.toImmutable();
            BlockState neighborState=blockItem.getBlock().getDefaultState().with(CompositeRootBlock.ROTATION,ImportedStairShapes.rotation(neighborFacing));
            net.minecraft.block.enums.BlockHalf half=requested<5?net.minecraft.block.enums.BlockHalf.BOTTOM:net.minecraft.block.enums.BlockHalf.TOP;
            neighborState=ImportedStairShapes.withHalf(neighborState,half);
            if(!world.setBlockState(neighbor,neighborState,3))throw new IllegalStateException("Could not stage same-half adjacent stair for requested corner pose "+requested);
            JsonObject evidence=new JsonObject();evidence.addProperty("kind",outer?"OUTER_CORNER":"INNER_CORNER");
            evidence.addProperty("requestedPose",requested);evidence.addProperty("expectedResultingPose",requested);evidence.addProperty("targetFacing",facing.toString());
            evidence.addProperty("legacyRotationBranch",legacy);evidence.addProperty("placementRotation",rotation);evidence.addProperty("importedRotationDelta",rotationDelta);
            evidence.addProperty("neighborPosition",neighbor.toString());evidence.addProperty("neighborFacing",neighborFacing.toString());
            evidence.addProperty("neighborHalf",half.toString());evidence.addProperty("connectedRule",outer?"same-half compatible stair ahead":"same-half compatible stair behind");
            return new CataloguePlacementContext(evidence,null);
        }
        String itemId=Registries.ITEM.getId(stack.getItem()).toString();
        if(!itemId.equals("bloodborne_dw:source_standard_lantern")||requested!=0)return null;
        for(BlockPos pos:List.of(catalogFloor,catalogSupport)){
            if(!world.getBlockState(pos).isOf(Blocks.STONE))throw new IllegalStateException("Expected captured lantern floor at "+pos);
            probe.captureBeforeFixtureChange(pos);catalogTemporaryCleared.add(pos.toImmutable());
            if(!world.setBlockState(pos,Blocks.AIR.getDefaultState(),3))throw new IllegalStateException("Could not clear lantern floor at "+pos);
        }
        BlockPos support=catalogTarget.up();
        if(!catalogBaseline.stream().anyMatch(cell->cell.pos().equals(support)&&cell.state().isAir())||!world.getBlockState(support).isAir())
            throw new IllegalStateException("Lantern ceiling is outside captured empty fixture at "+support);
        probe.captureBeforeFixtureChange(support);world.setBlockState(support,Blocks.STONE.getDefaultState(),3);catalogTemporarySide=support.toImmutable();
        BlockHitResult hit=sidePlacementHit(player,support,Direction.DOWN);
        if(hit==null||!new net.minecraft.item.ItemPlacementContext(player,Hand.MAIN_HAND,stack,hit).getBlockPos().equals(catalogTarget))
            throw new IllegalStateException("Could not raycast ceiling underside to lantern target within six blocks");
        JsonObject evidence=new JsonObject();evidence.addProperty("kind","HANGING_LANTERN_CEILING_MOUNT");
        evidence.addProperty("requestedVariant",requested);evidence.addProperty("support",support.toString());evidence.addProperty("face","down");
        evidence.addProperty("validRaycastBlockHit",true);evidence.addProperty("withinSixBlocks",true);
        return new CataloguePlacementContext(evidence,hit);
    }
    private ActionResult tryLegacyStatuePlacementContexts(ServerPlayerEntity player,ItemStack stack,TouchedCells probe,JsonArray attempts){
        if(!isLegacyShiftedHoldStatue(stack))return ActionResult.PASS;
        ServerWorld world=player.getServerWorld();
        Direction[] faces={Direction.EAST,Direction.WEST,Direction.SOUTH,Direction.NORTH};
        BlockPos[] supports={catalogTarget.west(),catalogTarget.east(),catalogTarget.north(),catalogTarget.south()};
        ActionResult result=ActionResult.PASS;
        for(int i=0;i<supports.length;i++){
            for(BlockPos pos:List.of(catalogFloor,catalogSupport)){
                BlockState old=world.getBlockState(pos);
                if(!old.isOf(Blocks.STONE))throw new IllegalStateException("Legacy statue fallback expected restored fixture STONE at "+pos+", found "+old);
                probe.captureBeforeFixtureChange(pos);catalogTemporaryCleared.add(pos.toImmutable());
                if(!world.setBlockState(pos,Blocks.AIR.getDefaultState(),3))throw new IllegalStateException("Could not clear bounded statue fixture support at "+pos);
            }
            BlockPos support=supports[i];Direction face=faces[i];
            boolean capturedAir=catalogBaseline.stream().anyMatch(cell->cell.pos().equals(support)&&cell.state().isAir());
            if(!capturedAir||!world.getBlockState(support).isAir())throw new IllegalStateException("Legacy statue fallback support is outside captured empty fixture at "+support);
            probe.captureBeforeFixtureChange(support);
            world.setBlockState(support,Blocks.STONE.getDefaultState(),3);
            catalogTemporarySide=support.toImmutable();
            BlockHitResult hit=sidePlacementHit(player,support,face);
            if(hit!=null){player.setStackInHand(Hand.MAIN_HAND,stack.copy());probe.begin(false);
                try{result=player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,hit);}finally{probe.end();}}
            JsonObject attempt=new JsonObject();attempt.addProperty("support",support.toString());attempt.addProperty("face",face.toString());
            attempt.addProperty("validBlockHit",hit!=null);
            attempt.addProperty("hitWithinSixBlocks",hit!=null&&player.getEyePos().distanceTo(hit.getPos())<=6.0);
            attempt.addProperty("accepted",result.isAccepted());attempts.add(attempt);
            if(result.isAccepted())return result;
            world.setBlockState(support,Blocks.AIR.getDefaultState(),3);
            catalogTemporarySide=null;
            restoreLegacyStatueFixture(world);
            if(probe.unsupported()!=null||touchedMismatches(world,probe).size()!=0)return result;
            for(BlockPos pos:List.of(catalogFloor,catalogSupport,support)){
                SectionFixture expected=catalogBaseline.stream().filter(cell->cell.pos().equals(pos)).findFirst().orElseThrow();
                if(world.getBlockState(pos)!=expected.state()||NativeVisuals.index(world,pos)!=expected.nativeIndex()||style(world,pos)!=expected.editorStyle())
                    throw new IllegalStateException("Rejected placement context did not fully restore bounded fixture at "+pos);
            }
            attempt.addProperty("temporaryFixtureCleanupVerified",true);
        }
        return result;
    }
    private void restoreLegacyStatueFixture(ServerWorld world){
        if(catalogTemporarySide!=null){restoreCapturedCataloguePos(world,catalogTemporarySide);catalogTemporarySide=null;}
        if(catalogTemporaryStairNeighbor!=null){restoreCapturedCataloguePos(world,catalogTemporaryStairNeighbor);catalogTemporaryStairNeighbor=null;}
        for(BlockPos pos:catalogTemporaryCleared){
            restoreCapturedCataloguePos(world,pos);
        }
        catalogTemporaryCleared.clear();
    }
    private void restoreCapturedCataloguePos(ServerWorld world,BlockPos pos){
        SectionFixture expected=catalogBaseline.stream().filter(cell->cell.pos().equals(pos)).findFirst()
                .orElseThrow(()->new IllegalStateException("Temporary catalogue fixture position was not captured: "+pos));
        if(!expected.state().isAir()&&!expected.state().isOf(Blocks.STONE))throw new IllegalStateException("Unsafe catalogue fixture restore at "+pos+": "+expected.state());
        if(!world.setBlockState(pos,expected.state(),3)&&world.getBlockState(pos)!=expected.state())throw new IllegalStateException("Failed catalogue fixture restore at "+pos);
        NativeVisuals.stage(world,pos,expected.nativeIndex());CityEditorServer.queueAppearance(world,pos,expected.editorStyle());
        if(expected.state().isAir()&&(world.getBlockEntity(pos)!=null||TouchedCells.owner(world,pos)!=null))throw new IllegalStateException("Restored empty catalogue fixture retained block entity/owner at "+pos);
    }
    private boolean temporaryCatalogueContextCell(BlockPos pos){
        return pos.equals(catalogTemporarySide)||pos.equals(catalogTemporaryStairNeighbor)||catalogTemporaryCleared.contains(pos);
    }
    private void runCataloguePlacement(ServerPlayerEntity player) {
        if (cursor >= catalogueRun.size()) {
            report.addProperty("cataloguePlacementAttempts", catalogueAttempts); report.addProperty("catalogueResults", items.size());
            fixtureCursor = 0; phase = Phase.CLEANUP; return;
        }
        ItemStack stack = catalogueRun.get(cursor).copy(); String id = catalogueKey(stack);
        if (stack.getItem() == CityEditorServer.BRUSH) {
            JsonObject row = new JsonObject(); row.addProperty("id", id); row.addProperty("status", "PASS"); row.addProperty("kind", "editor tool"); items.add(row); cursor++; return;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            JsonObject row = new JsonObject(); row.addProperty("id",id); row.addProperty("status","SKIP"); row.addProperty("reason","non-block creative item; outside block placement audit"); items.add(row); cursor++; return;
        }
        if (!Registries.BLOCK.getId(blockItem.getBlock()).getNamespace().equals("bloodborne_dw")) { unsupported(id, "editor supports DreamWalker blocks only"); cursor++; return; }
        ServerWorld world = player.getServerWorld();
        if (!worldSafeAir(world, catalogTarget)) { fail(id, "catalogue target occupied before placement"); cursor++; return; }
        setPlacementAim(player, catalogTarget);
        TouchedCells probe = new TouchedCells(world, catalogTarget);
        CataloguePlacementContext placementContext=setupCataloguePlacementContext(player,stack,probe);
        catalogueAttempts++;
        ActionResult result;
        probe.begin(false);
        try {
            if(placementContext!=null&&placementContext.hit()!=null){
                player.setStackInHand(Hand.MAIN_HAND,stack.copy());
                result=player.interactionManager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,placementContext.hit());
            }else result=interactOn(player, catalogFloor, stack);
        } finally { probe.end(); }
        JsonArray contextAttempts=new JsonArray();
        if(!result.isAccepted()&&probe.unsupported()==null&&touchedMismatches(world,probe).size()==0)
            result=tryLegacyStatuePlacementContexts(player,stack,probe,contextAttempts);
        if (result.isAccepted()) placed++;
        int changed = 0, supported = 0; boolean base = true;
        for (TouchedCells.Cell old : probe.cells().values()) {
            if(temporaryCatalogueContextCell(old.pos()))continue;
            BlockState state = world.getBlockState(old.pos());
            if (state != old.state() || NativeVisuals.index(world, old.pos()) != old.nativeIndex() || style(world, old.pos()) != old.style()) changed++;
            if (state != old.state() && CityEditorServer.supported(state)) { supported++; if (style(world, old.pos()) != 0) base = false; }
        }
        boolean rejectedNoMutation=!result.isAccepted()&&probe.unsupported()==null&&touchedMismatches(world,probe).size()==0;
        JsonObject appearance = appearanceCheck(stack, world.getBlockState(catalogTarget));
        boolean contextShapeMismatch=false;
        if(placementContext!=null){
            JsonObject evidence=placementContext.evidence();
            if(blockItem.getBlock() instanceof ImportedStairBlock){
                int actualPose=world.getBlockState(catalogTarget).getBlock() instanceof ImportedStairBlock?ImportedStairShapes.variant(world.getBlockState(catalogTarget)):-1;
                evidence.addProperty("actualResultingPose",actualPose);
                contextShapeMismatch=actualPose!=evidence.get("expectedResultingPose").getAsInt();
            }
            if(placementContext.hit()!=null){
                evidence.addProperty("actualTarget",placementContext.hit().getBlockPos().offset(placementContext.hit().getSide()).toString());
                var variantProperty=world.getBlockState(catalogTarget).getBlock().getStateManager().getProperty("variant");
                if(variantProperty!=null){String actual=propertyName(world.getBlockState(catalogTarget),variantProperty);evidence.addProperty("actualLanternVariant",actual);contextShapeMismatch=!actual.equals("0");}
                else contextShapeMismatch=true;
                evidence.addProperty("expectedLanternVariant",0);
            }
        }
        JsonObject row = null;
        if (probe.unsupported() != null) unsupported(id, probe.unsupported());
        else if (!result.isAccepted()) fail(id, "real block placement was rejected");
        else if (supported == 0) unsupported(id, "no supported changed physical cells observed");
        else if (!base) fail(id, "normal placement did not initialize base style");
        else if(contextShapeMismatch)fail(id,"placement context did not produce the exact requested stair/lantern variant");
        else if (appearance.getAsJsonArray("mismatches").size() != 0) { fail(id, "appearance fields differ: " + appearance.getAsJsonArray("mismatches")); items.get(items.size() - 1).getAsJsonObject().add("appearanceCheck", appearance); }
        else row = passItem(id, changed, true, false);
        if (row != null) row.add("appearanceCheck", appearance);
        if(placementContext!=null){JsonObject contextRow=row==null?items.get(items.size()-1).getAsJsonObject():row;contextRow.add("placementContext",placementContext.evidence());}
        if(contextAttempts.size()!=0){
            JsonObject contextRow=row==null?items.get(items.size()-1).getAsJsonObject():row;
            contextRow.add("placementContextAttempts",contextAttempts);
            contextRow.addProperty("placementContextCleanupPending",catalogTemporarySide!=null||catalogTemporaryStairNeighbor!=null||!catalogTemporaryCleared.isEmpty());
        }
        if(!rejectedNoMutation){
            probe.begin(true);
            try { physicalBreak(player, catalogTarget); } finally { probe.end(); }
        }
        pendingCatalogue = new CataloguePending(id, probe, row, player.getServer().getTicks());
        phase = Phase.CATALOG_CLEAR;
    }
    private record CataloguePending(String id, TouchedCells probe, JsonObject resultRow, int tick) {}
    private CataloguePending pendingCatalogue;
    private String fatalAfterRecovery;
    private JsonArray touchedMismatches(ServerWorld world,TouchedCells probe){
        JsonArray result=new JsonArray();
        for(TouchedCells.Cell before:probe.cells().values()){
            BlockState after=world.getBlockState(before.pos());int nativeAfter=NativeVisuals.index(world,before.pos());
            int styleAfter=style(world,before.pos());UUID ownerAfter=TouchedCells.owner(world,before.pos());
            String entityAfter=blockEntityType(world,before.pos());
            if(after==before.state()&&nativeAfter==before.nativeIndex()&&styleAfter==before.style()
                    &&java.util.Objects.equals(ownerAfter,before.owner())&&java.util.Objects.equals(entityAfter,before.blockEntityType()))continue;
            JsonObject row=new JsonObject();row.addProperty("x",before.pos().getX());row.addProperty("y",before.pos().getY());row.addProperty("z",before.pos().getZ());
            row.addProperty("beforeState",before.state().toString());row.addProperty("afterState",after.toString());
            row.addProperty("beforeNativeIndex",before.nativeIndex());row.addProperty("afterNativeIndex",nativeAfter);
            row.addProperty("beforeEditorStyle",before.style());row.addProperty("afterEditorStyle",styleAfter);
            row.addProperty("beforeOwner",String.valueOf(before.owner()));row.addProperty("afterOwner",String.valueOf(ownerAfter));
            row.addProperty("beforeBlockEntity",String.valueOf(before.blockEntityType()));row.addProperty("afterBlockEntity",String.valueOf(entityAfter));result.add(row);
        }
        return result;
    }
    private static String blockEntityType(ServerWorld world,BlockPos pos){var entity=world.getBlockEntity(pos);return entity==null?null:entity.getClass().getName();}
    private JsonObject recordCatalogueRecoveryFailure(CataloguePending pending,String reason){
        JsonObject row=pending.resultRow();
        if(row==null){fail(pending.id(),reason);row=items.get(items.size()-1).getAsJsonObject();}
        else{row.addProperty("status","FAIL");row.addProperty("reason",reason);failures.add(pending.id()+": "+reason);}
        return row;
    }
    private void attachCatalogueDiagnostics(String id,String key,JsonArray diagnostics){
        for(int i=items.size()-1;i>=0;i--){JsonObject row=items.get(i).getAsJsonObject();if(row.has("id")&&row.get("id").getAsString().equals(id)){row.add(key,diagnostics);return;}}
    }
    private void restoreTouchedFixture(ServerPlayerEntity player,TouchedCells probe){
        ServerWorld world=player.getServerWorld();
        if(!Boolean.getBoolean("dw.editorqa.disposable"))throw new IllegalStateException("Refusing direct per-case fixture restoration outside an opted-in disposable copy");
        for(TouchedCells.Cell old:probe.cells().values()){
            if(old.blockEntityType()!=null||old.owner()!=null)throw new IllegalStateException("Unsafe fixture recovery: pre-existing block entity/owner at "+old.pos()+" type="+old.blockEntityType()+" owner="+old.owner());
            if(!old.state().isAir()&&!old.state().isOf(Blocks.STONE))throw new IllegalStateException("Unsafe fixture recovery: pre-state is outside AIR/STONE fixture at "+old.pos()+" state="+old.state());
            var chunk=world.getChunkManager().getChunk(old.pos().getX()>>4,old.pos().getZ()>>4,ChunkStatus.FULL,false);
            if(!(chunk instanceof WorldChunk))throw new IllegalStateException("Unsafe fixture recovery: touched chunk unloaded at "+old.pos());
        }
        for(TouchedCells.Cell old:probe.cells().values()){
            if(world.getBlockState(old.pos())!=old.state()&&!world.setBlockState(old.pos(),old.state(),3))
                throw new IllegalStateException("Could not restore disposable fixture block state at "+old.pos());
            NativeVisuals.stage(world,old.pos(),old.nativeIndex());
            CityEditorServer.queueAppearance(world,old.pos(),old.style());
        }
    }
    private void clearCataloguePlacement(ServerPlayerEntity player) {
        if (player.getServer().getTicks() <= pendingCatalogue.tick()) return;
        ServerWorld world = player.getServerWorld(); TouchedCells probe = pendingCatalogue.probe();
        if(catalogTemporarySide!=null||catalogTemporaryStairNeighbor!=null||!catalogTemporaryCleared.isEmpty()){
            restoreLegacyStatueFixture(world);
            JsonArray tempMismatch=new JsonArray();
            for(BlockPos pos:List.of(catalogFloor,catalogSupport,catalogTarget.west(),catalogTarget.east(),catalogTarget.north(),catalogTarget.south())){
                SectionFixture expected=catalogBaseline.stream().filter(cell->cell.pos().equals(pos)).findFirst().orElseThrow();
                if(world.getBlockState(pos)!=expected.state()||NativeVisuals.index(world,pos)!=expected.nativeIndex()||style(world,pos)!=expected.editorStyle()){
                    JsonObject mismatch=new JsonObject();mismatch.addProperty("position",pos.toString());mismatch.addProperty("expected",expected.state().toString());
                    mismatch.addProperty("actual",world.getBlockState(pos).toString());mismatch.addProperty("expectedNative",expected.nativeIndex());
                    mismatch.addProperty("actualNative",NativeVisuals.index(world,pos));mismatch.addProperty("expectedStyle",expected.editorStyle());mismatch.addProperty("actualStyle",style(world,pos));tempMismatch.add(mismatch);
                }
                if(TouchedCells.owner(world,pos)!=null||blockEntityType(world,pos)!=null){JsonObject mismatch=new JsonObject();mismatch.addProperty("position",pos.toString());
                    mismatch.addProperty("unexpectedOwner",String.valueOf(TouchedCells.owner(world,pos)));mismatch.addProperty("unexpectedBlockEntity",String.valueOf(blockEntityType(world,pos)));tempMismatch.add(mismatch);}
            }
            attachCatalogueDiagnostics(pendingCatalogue.id(),"placementContextCleanupMismatches",tempMismatch);
            if(tempMismatch.size()!=0)throw new IllegalStateException("Temporary placement supports were not restored: "+tempMismatch);
            for(int i=items.size()-1;i>=0;i--){JsonObject row=items.get(i).getAsJsonObject();if(row.has("id")&&row.get("id").getAsString().equals(pendingCatalogue.id())){row.addProperty("placementContextCleanupVerified",true);break;}}
        }
        if (probe.unsupported() != null) {
            if (pendingCatalogue.resultRow() != null) { pendingCatalogue.resultRow().addProperty("status", "UNSUPPORTED"); pendingCatalogue.resultRow().addProperty("reason", probe.unsupported()); }
            unsupported(pendingCatalogue.id(), probe.unsupported());
            JsonArray diagnostics=touchedMismatches(world,probe); attachCatalogueDiagnostics(pendingCatalogue.id(),"untrackedFootprint",diagnostics);
            if(diagnostics.size()!=0){
                JsonObject failureRow=recordCatalogueRecoveryFailure(pendingCatalogue,"unsupported placement footprint left captured cells changed");
                failureRow.add("beforeAfterMismatches",diagnostics);restoreTouchedFixture(player,probe);
                fatalAfterRecovery="Cannot continue: catalogue probe did not track its complete footprint for "+pendingCatalogue.id()+": "+probe.unsupported();
                pendingCatalogue=new CataloguePending(pendingCatalogue.id(),probe,failureRow,player.getServer().getTicks());phase=Phase.CATALOG_RESTORE_VERIFY;return;
            }
            throw new IllegalStateException("Cannot safely continue after untracked catalogue fixture footprint for " + pendingCatalogue.id() + ": " + probe.unsupported());
        }
        JsonArray diagnostics=touchedMismatches(world,probe);
        if(diagnostics.size()!=0){
            String reason="normal break left "+diagnostics.size()+" touched fixture cell(s) changed";
            JsonObject failureRow=recordCatalogueRecoveryFailure(pendingCatalogue,reason);
            failureRow.add("beforeAfterMismatches",diagnostics);
            restoreTouchedFixture(player,probe);
            pendingCatalogue=new CataloguePending(pendingCatalogue.id(),probe,failureRow,player.getServer().getTicks());
            phase=Phase.CATALOG_RESTORE_VERIFY;return;
        } else if (pendingCatalogue.resultRow() != null) {
            pendingCatalogue.resultRow().addProperty("restored", true);
            check(true, "catalogue touched cells restored: " + pendingCatalogue.id());
        }
        advanceCatalogueCase(player);
    }
    private void verifyCatalogueRestore(ServerPlayerEntity player) {
        if(player.getServer().getTicks()<=pendingCatalogue.tick())return;
        JsonArray diagnostics=touchedMismatches(player.getServerWorld(),pendingCatalogue.probe());
        if(diagnostics.size()!=0){
            pendingCatalogue.resultRow().add("restoreVerificationMismatches",diagnostics);
            throw new IllegalStateException("Catalogue fixture recovery did not restore captured pre-state for "+pendingCatalogue.id()+": "+diagnostics);
        }
        pendingCatalogue.resultRow().addProperty("fixtureRecoveryVerified",true);
        checks.add("failed catalogue fixture isolated and restored: "+pendingCatalogue.id());
        if(fatalAfterRecovery!=null){String fatal=fatalAfterRecovery;fatalAfterRecovery=null;throw new IllegalStateException(fatal);}
        advanceCatalogueCase(player);
    }
    private void advanceCatalogueCase(ServerPlayerEntity player){
        cursor++;
        if(cursor%100==0){
            report.addProperty("progressCatalogueCursor",cursor);report.addProperty("progressPhase",phase.toString());report.addProperty("progressTicks",ticks);
            try{Files.writeString(Path.of(output),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);}
            catch(java.io.IOException error){throw new IllegalStateException("Could not checkpoint QA evidence",error);}
        }
        phase = Phase.CATALOG_PLACE;
    }
    private static boolean worldSafeAir(ServerWorld world, BlockPos pos) { return world.getBlockState(pos).isAir(); }
    private int countDifferences(ServerWorld world, List<SectionFixture> baseline) {
        int count = 0;
        for (SectionFixture state : baseline) if (world.getBlockState(state.pos()) != state.state() || style(world, state.pos()) != state.editorStyle() || NativeVisuals.index(world,state.pos())!=state.nativeIndex()) count++;
        return count;
    }
    private PlacementStats placementStats(ServerWorld world,List<SectionFixture> baseline){
        int changed=0,added=0;boolean base=true;
        for(SectionFixture old:baseline){
            BlockState now=world.getBlockState(old.pos());int style=style(world,old.pos());
            if(now!=old.state()||style!=old.editorStyle()||NativeVisuals.index(world,old.pos())!=old.nativeIndex())changed++;
            if(now!=old.state()&&CityEditorServer.supported(now)){added++;if(style!=0)base=false;}
        }
        return new PlacementStats(changed,added,base);
    }
    private boolean matches(ServerWorld world, List<SectionFixture> baseline) { return countDifferences(world, baseline) == 0; }
    private void restoreBoundedByBreaking(ServerPlayerEntity player, List<SectionFixture> baseline) {
        ServerWorld world = player.getServerWorld();
        for (SectionFixture expected : baseline) {
            BlockState current = world.getBlockState(expected.pos());
            if (current != expected.state() && !current.isAir()) physicalBreak(player, expected.pos());
            if (expected.state() != Blocks.AIR.getDefaultState() && world.getBlockState(expected.pos()) != expected.state()) {
                // Only disposable fixture supports are reconstructed directly after normal break removed an altered support.
                if (!world.setBlockState(expected.pos(), expected.state(), 3)) failures.add("could not restore test support at " + expected.pos());
            }
        }
    }

    private void cleanup(ServerPlayerEntity player) {
        if (player == null) return;
        ServerWorld world = player.getServerWorld();
        if(catalogTemporarySide!=null||catalogTemporaryStairNeighbor!=null||!catalogTemporaryCleared.isEmpty())restoreLegacyStatueFixture(world);
        if (persistenceSupportOwned && persistenceSupport != null && !world.getBlockState(persistenceSupport).isAir()) physicalBreak(player, persistenceSupport);
        persistenceSupportOwned = false;
        if (persistenceCreated && !persistenceCommitted && persistencePos != null && CityEditorServer.supported(world.getBlockState(persistencePos))) {
            physicalBreak(player, persistencePos); persistenceCreated = false;
        }
        if (fixtureOwned) {
            restoreBoundedByBreaking(player, fixtureBaseline);
            if (!matches(world, fixtureBaseline)) { failures.add("bounded fixture cleanup left modified blocks, native ids or editor cells");finalCleanupVerified=false; }
            else { checks.add("bounded high city fixture restored to baseline");finalCleanupVerified=true; }
            fixtureOwned=false;
        }
        if(uiBeOwned)restoreUiBeFixture(player);
        restoreEditor2Actor(player);restoreFunctionalActor(player);restoreMode();
        if (originalGameMode != null && player.interactionManager.getGameMode() != originalGameMode) player.changeGameMode(originalGameMode);
    }
    private void installUiBeFixture(ServerPlayerEntity player) {
        ServerWorld world=player.getServerWorld();
        verifyUiBeAreaEmpty(world);
        BlockState before=world.getBlockState(UI_BE_ART_SAMPLE);
        report.addProperty("uiBePosition",UI_BE_TARGET.toShortString());
        report.addProperty("uiBeArtSampleState",before.toString());
        if(!(before.getBlock() instanceof ImportedCompactBlock) || !before.contains(ImportedCompactBlock.COMPACT)
                || !before.contains(CompositeRootBlock.ROTATION))
            throw new IllegalStateException("Unexpected UI BE fixture target: "+before);
        var oldEntity=world.getBlockEntity(UI_BE_TARGET);
        uiBeOriginalState=world.getBlockState(UI_BE_TARGET);
        uiBeOriginalNbt=oldEntity==null?null:oldEntity.createNbtWithId();
        uiBeOriginalStyle=style(world,UI_BE_TARGET);
        report.addProperty("uiBeBeforeState",uiBeOriginalState.toString());
        report.addProperty("uiBeBeforeType",oldEntity==null?"none":oldEntity.getClass().getName());
        report.addProperty("uiBeBeforePayload",uiBeOriginalNbt==null?"none":uiBeOriginalNbt.toString());
        report.addProperty("uiBeBeforeStyle",uiBeOriginalStyle);
        ItemStack pick=before.getBlock().getPickStack(world,UI_BE_ART_SAMPLE,before);
        if(pick.isEmpty() || !(pick.getItem() instanceof BlockItem item) || item.getBlock()!=before.getBlock())
            pick=new ItemStack(before.getBlock().asItem());
        if(pick.isEmpty())throw new IllegalStateException("UI BE target has no placeable item");
        NbtCompound tag=pick.getOrCreateSubNbt("BlockStateTag");
        for(String name:List.of("variant","art_bank")){
            var property=before.getBlock().getStateManager().getProperty(name);
            if(property!=null)tag.putString(name,propertyName(before,property));
        }
        tag.putString("compact","false");tag.remove("rotation");
        BlockPos support=UI_BE_TARGET.down();
        ItemStack previous=player.getMainHandStack().copy();float yaw=player.getYaw();boolean sneaking=player.isSneaking();
        uiBeOwned=true;
        try {
            if(!world.setBlockState(support,Blocks.STONE.getDefaultState(),3))
                throw new IllegalStateException("Could not create UI BE fixture support");
            player.setStackInHand(Hand.MAIN_HAND,pick);player.setSneaking(true);player.setYaw(45.0F);
            var hit=new BlockHitResult(Vec3d.ofCenter(support).add(0,0.5,0),Direction.UP,support,false);
            var context=new net.minecraft.item.ItemUsageContext(player,Hand.MAIN_HAND,hit);
            var placement=new net.minecraft.item.ItemPlacementContext(context);
            if(!placement.getBlockPos().equals(UI_BE_TARGET))
                throw new IllegalStateException("UI BE item context targets unexpected cell: "+placement.getBlockPos());
            ActionResult result=player.interactionManager.interactBlock(player,world,pick,Hand.MAIN_HAND,hit);
            report.addProperty("uiBePlacementResult",result.toString());
            if(!result.isAccepted())throw new IllegalStateException("Normal UI BE fixture placement failed: "+result);
        } finally {
            player.setStackInHand(Hand.MAIN_HAND,previous);player.setSneaking(sneaking);player.setYaw(yaw);
        }
        BlockState fixture=world.getBlockState(UI_BE_TARGET);
        var entity=world.getBlockEntity(UI_BE_TARGET);
        report.addProperty("uiBeFixtureState",world.getBlockState(UI_BE_TARGET).toString());
        report.addProperty("uiBeFixtureType",entity==null?"none":entity.getClass().getName());
        report.addProperty("uiBeFixturePayload",entity==null?"none":entity.createNbtWithId().toString());
        boolean valid=fixture.getBlock()==before.getBlock() && fixture.contains(ImportedCompactBlock.COMPACT)
                && !fixture.get(ImportedCompactBlock.COMPACT) && fixture.contains(CompositeRootBlock.ROTATION)
                && (fixture.get(CompositeRootBlock.ROTATION)&1)==1
                && entity instanceof CompositeBlockEntity composite && composite.resident()!=null;
        report.addProperty("uiBeFixtureResident",entity instanceof CompositeBlockEntity composite && composite.resident()!=null);
        if(!valid)throw new IllegalStateException("Normal UI BE fixture lacks requested state or resident");
        report.addProperty("uiBeFixtureStyle",style(world,UI_BE_TARGET));
        if(style(world,UI_BE_TARGET)!=0)throw new IllegalStateException("Normal UI BE placement did not write base style");
    }
    private void verifyUiBeAreaEmpty(ServerWorld world) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=3;dy++){
            BlockPos pos=UI_BE_TARGET.add(dx,dy,dz);
            WorldChunk chunk=world.getWorldChunk(pos);
            var data=(RetainedFrameData)(Object)NativeVisuals.data(chunk);
            if(!world.getBlockState(pos).isAir() || world.getBlockEntity(pos)!=null
                    || NativeVisuals.index(world,pos)!=-1 || style(world,pos)!=-1
                    || data.dw$frames().containsKey(pos.asLong()) || TouchedCells.owner(world,pos)!=null)
                throw new IllegalStateException("UI BE fixture area is not empty at "+pos);
        }
    }
    private void restoreUiBeFixture(ServerPlayerEntity player) {
        ServerWorld world=player.getServerWorld();
        if(!world.getBlockState(UI_BE_TARGET).isAir() && !player.interactionManager.tryBreakBlock(UI_BE_TARGET))
            throw new IllegalStateException("Could not normally break UI BE fixture");
        BlockPos support=UI_BE_TARGET.down();
        if(!world.getBlockState(support).isAir() && !player.interactionManager.tryBreakBlock(support))
            throw new IllegalStateException("Could not normally break UI BE support");
        verifyUiBeAreaEmpty(world);
        report.addProperty("uiBeRestored",true);
        uiBeOwned=false;
    }
    private void finish(MinecraftClient client) {
        if (done) return;
        if (fixtureOwned && !cleanupRunning && client.getServer()!=null) {
            cleanupRunning=true;
            client.getServer().execute(() -> {
                try { ServerPlayerEntity player=client.getServer().getPlayerManager().getPlayer(playerId); if(player!=null)cleanup(player); }
                catch(Throwable cleanupError){failures.add("final cleanup failed: "+cleanupError.getMessage());fixtureOwned=false;}
                cleanupRunning=false;client.execute(() -> finish(client));
            });
            return;
        }
        int failureSeconds=Math.max(0,Math.min(300,Integer.getInteger("dw.editorqa.failureHoldSeconds",0)));
        if(!failures.isEmpty()&&failureSeconds>0&&client.world!=null&&client.player!=null&&client.getServer()!=null&&!output.isBlank()) {
            phase=Phase.DONE;
            if(!failureHoldStarted) {
                failureHoldStarted=true;failureHoldUntil=System.nanoTime()+failureSeconds*1_000_000_000L;
                report.addProperty("status","FAIL");report.addProperty("pass",false);report.addProperty("progressPhase","FAILURE_HOLD");report.addProperty("failureHoldActive",true);
                try{Files.writeString(Path.of(output),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);}
                catch(java.io.IOException error){failures.add("Failure-hold report write failed: "+error.getMessage());}
                client.getServer().execute(()->{
                    try {
                        ServerPlayerEntity player=playerId==null?null:client.getServer().getPlayerManager().getPlayer(playerId);
                        if(player!=null){
                            failureHoldOriginalMode=player.interactionManager.getGameMode();failureHoldOriginalHeld=player.getMainHandStack().copy();
                            failureHoldOriginalFlying=player.getAbilities().flying;failureHoldOriginalNoGravity=player.hasNoGravity();failureHoldActorCaptured=true;
                            player.changeGameMode(GameMode.CREATIVE);player.getAbilities().flying=true;player.setNoGravity(true);player.setVelocity(Vec3d.ZERO);player.sendAbilitiesUpdate();
                            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));
                        }
                    }catch(Throwable error){failures.add("Failure-hold actor setup failed: "+error);}
                    finally{failureHoldReady=true;}
                });
            }
            if(!failureHoldReady)return;
            if(System.nanoTime()<failureHoldUntil&&!Files.isRegularFile(client.runDirectory.toPath().resolve("evidence/failure-reviewed.ok")))return;
            if(!failureHoldRestoring){
                failureHoldRestoring=true;
                client.getServer().execute(()->{
                    try{
                        ServerPlayerEntity player=playerId==null?null:client.getServer().getPlayerManager().getPlayer(playerId);
                        if(player!=null&&failureHoldActorCaptured){player.setStackInHand(Hand.MAIN_HAND,failureHoldOriginalHeld);player.changeGameMode(failureHoldOriginalMode);player.getAbilities().flying=failureHoldOriginalFlying;player.setNoGravity(failureHoldOriginalNoGravity);player.sendAbilitiesUpdate();}
                    }catch(Throwable error){failures.add("Failure-hold actor restore failed: "+error);}
                    finally{failureHoldRestored=true;}
                });
                return;
            }
            if(!failureHoldRestored)return;
            report.addProperty("failureHoldActive",false);
        }
        int holdSeconds=Math.max(0,Math.min(uiOnly?900:300,Integer.getInteger("dw.editorqa.uiHoldSeconds",0)));
        if(!failureHoldStarted&&holdSeconds>0&&finalCleanupVerified&&client.getServer()!=null&&playerId!=null){
            if(!uiHoldStarted){
                uiHoldStarted=true;uiHoldUntil=System.nanoTime()+holdSeconds*1_000_000_000L;
                report.addProperty("progressPhase","UI_HOLD");report.addProperty("uiHoldActive",true);report.addProperty("uiHoldAfterScenarioFailures",!failures.isEmpty());
                if(uiOnly)report.addProperty("uiClientAltAtHold",CityVisualClient.isAlt());
                if(uiBeFixture&&client.world!=null){
                    var entity=client.world.getBlockEntity(UI_BE_TARGET);
                    report.addProperty("uiBeClientStateAtHold",client.world.getBlockState(UI_BE_TARGET).toString());
                    report.addProperty("uiBeClientTypeAtHold",entity==null?"none":entity.getClass().getName());
                }
                client.getServer().execute(()->{
                    ServerPlayerEntity player=client.getServer().getPlayerManager().getPlayer(playerId);
                    if(player!=null){uiPreviousHeld=player.getMainHandStack().copy();player.changeGameMode(GameMode.CREATIVE);player.setStackInHand(Hand.MAIN_HAND,new ItemStack(CityEditorServer.BRUSH));invoke(player,config(0,0,"single"));}
                });
                try{Files.writeString(Path.of(output),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);}
                catch(java.io.IOException error){failures.add("UI checkpoint write failed: "+error.getMessage());}
                return;
            }
            if(System.nanoTime()<uiHoldUntil&&!Files.isRegularFile(client.runDirectory.toPath().resolve("evidence/ui-reviewed.ok")))return;
            if(!uiRestoreRequested){
                uiRestoreRequested=true;
                client.getServer().execute(()->{
                    try{
                        ServerPlayerEntity player=client.getServer().getPlayerManager().getPlayer(playerId);
                        if(player!=null){
                            int material=sessionInt(player,"material");String mode=sessionString(player,"mode");
                            report.addProperty("uiFinalMaterial",material);report.addProperty("uiFinalMode",mode);
                            if(uiOnly)report.addProperty("uiServerAltAtReview",CityVisualModeState.isAlt(player.getServer()));
                            check(material==4&&mode.equals("plane"),"real UI palette and mode requests reached server through packet validation");
                        }else failures.add("UI QA player disconnected");
                    }catch(Throwable error){failures.add("UI server-state check failed: "+error.getMessage());}
                    finally{
                        ServerPlayerEntity player=client.getServer().getPlayerManager().getPlayer(playerId);
                        if(player!=null&&uiBeOwned)try{restoreUiBeFixture(player);}catch(Throwable error){finalCleanupVerified=false;failures.add("UI BE fixture restore failed: "+error);}
                        if(player!=null){player.setStackInHand(Hand.MAIN_HAND,uiPreviousHeld);if(originalGameMode!=null)player.changeGameMode(originalGameMode);}
                        if(player!=null&&uiMotionFrozen){player.getAbilities().flying=uiPreviousFlying;player.setNoGravity(uiPreviousNoGravity);player.sendAbilitiesUpdate();}
                        uiRestoreDone=true;
                    }
                });
                return;
            }
            if(!uiRestoreDone)return;
            if(uiOnly&&report.has("uiServerAltAtReview")){
                boolean clientAlt=CityVisualClient.isAlt();report.addProperty("uiClientAltAtReview",clientAlt);
                check(clientAlt==report.get("uiServerAltAtReview").getAsBoolean(),"UI client active appearance matches server mode");
                if(client.player!=null){report.addProperty("uiReviewX",client.player.getX());report.addProperty("uiReviewY",client.player.getY());report.addProperty("uiReviewZ",client.player.getZ());}
            }
            report.addProperty("uiHoldActive",false);
        }
        done = true;
        report.addProperty("functionalSuiteFailed",functionalSuiteFailed);
        boolean functionalRequested=!uiOnly&&!catalogueOnly&&(!editor2Only||functionalAfterEditor2);
        if(!functionalRequested)report.addProperty("functionalSuiteStatus","SKIPPED");
        else if(editor2Only&&!functionalAfterEditor2Started)report.addProperty("functionalSuiteStatus","INCOMPLETE");
        else if(functionalSuiteFailed)report.addProperty("functionalSuiteStatus","FAIL");
        else if(editor2Only&&!failures.isEmpty())report.addProperty("functionalSuiteStatus","FAIL");
        else report.addProperty("functionalSuiteStatus","PASS");
        report.addProperty("functionalSuiteComplete",functionalRequested&&(!editor2Only||editor2MatrixDone&&functionalAfterEditor2Started&&failures.isEmpty())&&!catalogueRecoveryStarted&&!functionalSuiteFailed);
        report.addProperty("finalCleanupVerified",finalCleanupVerified);
        report.addProperty("catalogueRecoveryUsed",catalogueRecoveryStarted);
        boolean fullCatalogueCompleted=!uiOnly&&(!editor2Only||editor2MatrixDone&&catalogueOnly)&&!catalogueFiltered&&!catalogue.isEmpty()&&cursor>=catalogue.size();
        report.addProperty("catalogueCompletedCount",cursor);
        if(!uiOnly)report.addProperty("catalogueScope",editor2Only&&!catalogueOnly&&!functionalAfterEditor2?"CANONICAL_ID_AUDIT_ONLY":(catalogueFiltered?"FILTERED_OR_VERIFIED_EXCLUSIONS_NOT_WHOLE_CATALOGUE":(fullCatalogueCompleted?(catalogueOnly?"CATALOGUE_ONLY_FULL_RUN":"FULL_CATALOGUE"):"INCOMPLETE")));
        report.addProperty("wholeCatalogueFreshPass",fullCatalogueCompleted&&failures.isEmpty()&&unsupported.isEmpty());
        if(uiOnly)report.addProperty("uiOnlyPass",failures.isEmpty()&&unsupported.isEmpty());
        else report.addProperty("pass", failures.isEmpty() && unsupported.isEmpty());
        report.addProperty("ticks", ticks);
        report.addProperty("fixtureBlocksPlaced", placed); report.addProperty("fixtureBlocksCleared", cleared);
        report.addProperty("cataloguePlacementCalls",catalogueAttempts);
        try { Files.writeString(Path.of(output), new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8); }
        catch (Exception e) { System.err.println("Editor QA report write failed: " + e); }
        client.scheduleStop();
    }
}


