package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock;
import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/**
 * One opt-in native gameplay route for the already migrated city copy.
 *
 * <p>The first run uses ordinary client interaction packets and returns the
 * removed one-cell expectation that must be supplied after a normal integrated
 * server shutdown. The reopen run verifies persistence, restores the exact
 * native palette state with a guarded vanilla {@code setblock ... keep}, and
 * leaves final persistence to the caller's normal shutdown/reopen cycle. It
 * never scans the world or creates owners/ledger data.</p>
 */
public final class GalleryNativeRoute {
    private static final Gson JSON=new Gson();
    private static final BlockPos ROOT=new BlockPos(289,128,-656);
    private static final BlockPos STANDING=new BlockPos(290,128,-656);
    private static final BlockPos GLASS=new BlockPos(291,128,-656);
    private static final BlockPos STAIR=new BlockPos(290,137,-656);
    private static final BlockPos DOOR=new BlockPos(284,118,-649);
    private static final BlockPos TRAPDOOR=new BlockPos(287,135,-649);
    private static final String ROOT_ID="bloodborne_dw:static_architecture_0";
    private static final int ROOT_PART=38;
    private static final String GLASS_ID="bloodborne_dw:source_assembly_orange_stained_glass_pane";
    private static final String STAIR_ID="bloodborne_dw:source_cobbled_deepslate_stairs";
    private static final String DOOR_ID="bloodborne_dw:prototype_double_door";
    private static final String TRAPDOOR_ID="bloodborne_dw:source_acacia_trapdoor_bottom";
    private static final String KNOWN_DOOR_REASON="collision_with_owned_object:Cell[x=285, y=120, z=-649]";
    private static final String QA_PLAYER_UUID="5e8ff009-3f88-325a-99e8-f57614c984d1";
    private static final String BASELINE_WORLD_SHA="2386f97c0a4f43f857787be5687540bfd4e4d99d492b06959909818334abde58";
    private static final Map<String,String> QA_DROP_IDS=Map.of(
        "65d20da3-45f3-41f9-b0fb-531e52161106","minecraft:black_shulker_box",
        "1daa4267-c1af-4730-ac6b-dc09486ced05","minecraft:black_shulker_box",
        "a8e3ef71-7724-4b64-95f0-17eb0442bfb1","minecraft:carved_pumpkin");
    private static final int MAX_TOTAL_TICKS=2400;
    private final JsonObject report=new JsonObject();
    private final JsonArray operations=new JsonArray();
    private final JsonObject prior;
    private final boolean reopen,recoverDoor,knownDoorBlocked;
    private final JsonObject knownDoorResult;
    private final JsonObject qaDropInput,carriedQaDropRecovery;
    private final Map<BlockPos,String> neighbors=new LinkedHashMap<>();
    private String rootBefore,phase="BOOT";
    private Direction breakSide;
    private boolean complete,failed,serverDiagnosticRequested,serverDiagnosticReceived,respawnRequested,qaDropRequested,qaDropReceived;
    private String qaDropFailure;
    private int stageTicks,totalTicks,settled,respawnTicks,emptySlot=-1;

    /** Create the packet/action run. */
    public GalleryNativeRoute(){this(null);}

    /** Create the read-only reopen proof followed by guarded exact restoration. */
    public GalleryNativeRoute(JsonObject priorEvidence){
        prior=priorEvidence==null?null:priorEvidence.deepCopy();knownDoorResult=findKnownDoorBlocker(prior);knownDoorBlocked=knownDoorResult!=null;
        qaDropInput=prior!=null&&prior.has("qaDropRecoveryInput")&&prior.get("qaDropRecoveryInput").isJsonObject()?prior.getAsJsonObject("qaDropRecoveryInput").deepCopy():null;
        carriedQaDropRecovery=prior!=null&&prior.has("qaDropRecovery")&&prior.get("qaDropRecovery").isJsonObject()&&validCarriedQaDropRecovery(prior.getAsJsonObject("qaDropRecovery"))?prior.getAsJsonObject("qaDropRecovery").deepCopy():null;
        String priorStatus=prior==null?null:string(prior,"status");
        recoverDoor=prior!=null&&prior.has("recoveryExpectation")&&(("FAIL".equals(priorStatus)&&knownDoorBlocked)||"PASS_REOPEN_STATE_SAVED_WITH_KNOWN_DOOR_BLOCKER".equals(priorStatus)||"PASS_REOPEN_STATE_READY_FOR_NATIVE_SHUTDOWN_WITH_KNOWN_DOOR_BLOCKER".equals(priorStatus));reopen=prior!=null&&!recoverDoor;
        report.addProperty("schema","dreamwalker-static-city-native-route-v1");
        report.addProperty("mode",recoverDoor?"VERIFY_RESTORED_WITH_KNOWN_DOOR_BLOCKER":reopen?"VERIFY_REOPEN_AND_RESTORE":"INTERACT_SAVE");
        report.addProperty("physicalMouseKeyboardInput","NOT_RUN");
        report.addProperty("scope","Six fixed positions in the 3x3 pilot chunks; no discovery scan");
        report.addProperty("ladder","NOT_CHECKED_NO_LADDER_IN_BOUNDED_ROUTE");
        report.add("operations",operations);update("WAITING_FOR_WORLD");
    }

    /** Returns true after PASS or FAIL. The caller owns scheduling and output. */
    public boolean tick(MinecraftClient client){
        if(complete)return true;
        if(client.player==null||client.world==null||client.interactionManager==null)return false;
        try{
            require(++totalTicks<=MAX_TOTAL_TICKS,"Native route total tick budget exceeded at "+phase);
            if(client.player.isDead()){
                require(recoverDoor||reopen,"Automatic respawn is restricted to an explicit recovery/reopen route");
                require(++respawnTicks<=240,"Native recovery respawn did not synchronize");
                if(!respawnRequested){
                    JsonObject detail=actualAim(client);detail.addProperty("health",client.player.getHealth());detail.addProperty("phaseBeforeRespawn",phase);
                    detail.addProperty("screen",client.currentScreen==null?"null":client.currentScreen.getClass().getName());
                    client.player.requestRespawn();respawnRequested=true;record(client,"native_requestRespawn_once_for_explicit_recovery",ROOT,detail);
                }
                return false;
            }
            if(respawnRequested&&!report.has("respawnAcknowledged")){report.addProperty("respawnAcknowledged",true);record(client,"native_respawn_acknowledged_before_recovery_setup",ROOT,actualAim(client));}
            // A server-acknowledged respawn can briefly leave the old death screen
            // installed. Do not manipulate that UI; continue the native recovery
            // state machine once the replacement player is alive.
            if(client.currentScreen!=null&&!(respawnRequested&&client.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen))return false;
            if(client.isPaused())return false;
            stageTicks++;
            switch(phase){
                case "BOOT" -> boot(client);
                case "WAIT_SURVIVAL" -> waitSurvival(client);
                case "WAIT_STANDING" -> waitStanding(client);
                case "READY_TO_BREAK" -> startBreak(client);
                case "BREAK_PROGRESS" -> breakProgress(client);
                case "WAIT_DOOR" -> waitDoor(client);
                case "WAIT_OPEN" -> waitOpen(client);
                case "WAIT_CLOSED" -> waitClosed(client);
                case "WAIT_FIRST_SAVE" -> waitFirstSave(client);
                case "WAIT_REOPEN_POSITION" -> waitReopenPosition(client);
                case "WAIT_FAILED_RECOVERY" -> waitFailedRecovery(client);
                case "WAIT_RECOVERY_DOOR" -> waitRecoveryDoor(client);
                case "WAIT_RESTORED" -> waitRestored(client);
                case "WAIT_RESTORE_SAVE" -> waitRestoreSave(client);
                case "WAIT_RECOVERY_SAVE" -> waitRecoverySave(client);
                case "WAIT_KNOWN_SPECTATOR" -> waitKnownSpectator(client);
                case "WAIT_KNOWN_STANDING" -> waitKnownStanding(client);
                case "WAIT_QA_DROP_RECOVERY" -> waitQaDropRecovery(client);
                case "WAIT_KNOWN_SURVIVAL" -> waitKnownSurvival(client);
                default -> throw new IllegalStateException("Unknown native route phase "+phase);
            }
        }catch(Throwable failure){
            failed=true;complete=true;report.addProperty("failure",failure.toString());update("FAIL");failure.printStackTrace();
        }
        return complete;
    }

    public JsonObject jsonReport(){return report.deepCopy();}
    public boolean passed(){return complete&&!failed;}

    private void boot(MinecraftClient client){
        require(client.isInSingleplayer(),"Native route requires the isolated integrated-server world");
        if(recoverDoor){
            JsonObject recovery=prior.getAsJsonObject("recoveryExpectation");require(recovery!=null,"Failed route recovery expectation missing");
            report.add("recoveryExpectation",recovery.deepCopy());preserveKnownDoorBlocker();
            rootBefore=recovery.get("rootBefore").getAsString();require(rootBefore.contains(ROOT_ID)&&rootBefore.contains("part="+ROOT_PART),"Failed-route exact recovery state changed");
            verifySavedNeighbors(client,recovery.getAsJsonArray("unchangedNeighbours"));requireClosedDoor(client);requireClosedTrapdoor(client);BlockState current=client.world.getBlockState(ROOT);
            if(current.toString().equals(rootBefore)){require(client.world.getBlockEntity(ROOT)==null,"Pre-capture recovery created block entity data");record(client,"guarded_pre_capture_recovery_validated_without_repeating_break",ROOT,state(current));beginKnownBlockerSetup(client);return;}
            require(current.isAir()&&client.world.getBlockEntity(ROOT)==null,"Failed-route recovery requires exact AIR or already-restored root");
            client.player.networkHandler.sendChatCommand("setblock 289 128 -656 "+ROOT_ID+"[part="+ROOT_PART+"] keep");record(client,"guarded_failed_route_setblock_keep_restore_command",ROOT,null);stage("WAIT_FAILED_RECOVERY");return;
        }
        if(reopen){
            String status=string(prior,"status");require("PASS_MUTATION_SAVED".equals(status)||"PASS_MUTATION_READY_FOR_NATIVE_SHUTDOWN".equals(status),"Reopen requires successful first-run evidence");
            require(prior.has("savedExpectation"),"First-run saved expectation missing");
            client.player.networkHandler.sendChatCommand("tp @s 290.500000 128.000000 -655.500000 90.000000 66.000000");
            record(client,"reopen_navigation_command",STANDING,null);stage("WAIT_REOPEN_POSITION");return;
        }
        client.player.networkHandler.sendChatCommand("gamemode survival @s");
        record(client,"survival_mode_command",STANDING,null);stage("WAIT_SURVIVAL");
    }

    private void waitSurvival(MinecraftClient client){
        require(stageTicks<=120,"Survival mode did not synchronize");
        if(client.player.isCreative()||client.player.isSpectator())return;
        emptySlot=emptyHotbarSlot(client);require(emptySlot>=0,"Native door use requires one existing empty hotbar slot");
        select(client,emptySlot);
        client.player.networkHandler.sendChatCommand("tp @s 290.500000 128.000000 -655.500000 90.000000 66.000000");
        record(client,"standing_navigation_command",STANDING,null);stage("WAIT_STANDING");
    }

    private void waitStanding(MinecraftClient client){
        require(stageTicks<=200,"Standing point did not settle");
        if(!at(client,new Vec3d(290.5,128,-655.5))) {settled=0;return;}
        if(!client.player.isOnGround()){settled=0;return;}
        if(++settled<12)return;
        require(client.player.getBlockPos().equals(STANDING),"Actual player feet block differs from bounded standing point");
        require(client.world.getBlockState(STANDING).isAir()&&client.world.getBlockState(STANDING.up()).isAir(),"Standing feet/head cells are not empty");
        require(client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Actual player body intersects world collision");
        BlockPos floor=STANDING.down();var floorShape=client.world.getBlockState(floor).getCollisionShape(client.world,floor,ShapeContext.of(client.player));
        require(!floorShape.isEmpty(),"Standing point has no floor collision");
        JsonObject standing=new JsonObject();standing.addProperty("isOnGround",client.player.isOnGround());standing.addProperty("bodyEmpty",true);
        standing.addProperty("floorState",client.world.getBlockState(floor).toString());standing.add("floorCollision",boxes(floorShape.getBoundingBoxes()));record(client,"actual_player_standing_collision",STANDING,standing);
        probeFixedStates(client);
        BlockState state=client.world.getBlockState(ROOT);require(state.getBlock() instanceof StaticArchitectureBlock,"Selected break root is not static architecture");
        StaticArchitectureBlock block=(StaticArchitectureBlock)state.getBlock();
        require(Registries.BLOCK.getId(state.getBlock()).toString().equals(ROOT_ID)&&state.get(StaticArchitectureBlock.PART)==ROOT_PART,"Selected static recipe changed");
        var recipe=block.recipe(state);require(recipe.index()==ROOT_PART&&recipe.resident()==0&&recipe.fragments().size()==1,"Recipe38 must remain a one-cell resident component");
        require(recipe.without(0).isAir()&&!recipe.collisionShape().isEmpty(),"Recipe38 removal/collision contract changed");
        require(client.world.getBlockEntity(ROOT)==null,"Static root unexpectedly has a block entity");
        rootBefore=state.toString();snapshotNeighbors(client);
        JsonObject before=new JsonObject();before.addProperty("state",rootBefore);before.addProperty("recipe",recipe.index());before.addProperty("resident",recipe.resident());before.addProperty("fragments",recipe.fragments().size());before.add("collision",boxes(recipe.collisionShape().getBoundingBoxes()));before.addProperty("expectedAfter","minecraft:air");record(client,"static_component_before",ROOT,before);
        preserveRecoveryExpectation();
        record(client,"recovery_expectation_prepared_before_mutation",ROOT,null);stage("READY_TO_BREAK");
    }

    private void startBreak(MinecraftClient client){
        if(!(client.crosshairTarget instanceof BlockHitResult hit)||!hit.getBlockPos().equals(ROOT)){
            if(stageTicks>40){record(client,"ready_to_break_crosshair_rejected",ROOT,actualAim(client));require(false,"Native crosshair did not remain on the selected static root");}
            return;
        }
        breakSide=hit.getSide();JsonObject aim=new JsonObject();aim.addProperty("side",breakSide.getName());aim.add("hit",vector(hit.getPos()));
        require(client.interactionManager.attackBlock(hit.getBlockPos(),breakSide),"Native survival attackBlock was not accepted locally");
        client.player.swingHand(Hand.MAIN_HAND);record(client,"native_crosshair_survival_attackBlock_packet",ROOT,aim);stage("BREAK_PROGRESS");
    }

    private void breakProgress(MinecraftClient client){
        require(stageTicks<=240,"Native survival block-breaking progress timed out");
        if(!client.world.getBlockState(ROOT).isAir()){
            require(breakSide!=null,"Native break side was not captured from crosshair");
            client.interactionManager.updateBlockBreakingProgress(ROOT,breakSide);
            if((stageTicks&3)==0)client.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        require(stageTicks>=2,"Static component disappeared before ordinary progress acknowledgement");
        require(client.world.getBlockEntity(ROOT)==null,"Removed static cell acquired block entity data");
        require(client.world.getBlockState(ROOT).getCollisionShape(client.world,ROOT,ShapeContext.absent()).isEmpty(),"Removed root retains collision");
        verifyNeighbors(client);
        JsonObject after=new JsonObject();after.addProperty("state",client.world.getBlockState(ROOT).toString());after.addProperty("collisionEmpty",true);after.addProperty("blockEntityAbsent",true);after.addProperty("unchangedNeighbourCells",neighbors.size());record(client,"static_component_removed_server_ack",ROOT,after);
        client.player.networkHandler.sendChatCommand("tp @s 284.500000 117.000000 -647.500000 180.000000 0.000000");
        record(client,"door_navigation_command",DOOR,null);stage("WAIT_DOOR");
    }

    private void waitFailedRecovery(MinecraftClient client){
        require(stageTicks<=160,"Failed-route exact recipe restoration did not synchronize");BlockState restored=client.world.getBlockState(ROOT);if(!restored.toString().equals(rootBefore))return;
        require(restored.getBlock() instanceof StaticArchitectureBlock block&&block.recipe(restored).index()==ROOT_PART,"Failed-route restore is not exact recipe38");require(client.world.getBlockEntity(ROOT)==null,"Failed-route restore created block entity data");
        JsonObject recovery=prior.getAsJsonObject("recoveryExpectation");verifySavedNeighbors(client,recovery.getAsJsonArray("unchangedNeighbours"));requireClosedDoor(client);requireClosedTrapdoor(client);record(client,"failed_route_exact_recipe_restored_without_repeating_break",ROOT,state(restored));
        beginKnownBlockerSetup(client);
    }
    private void beginRecoveryDoor(MinecraftClient client){client.player.networkHandler.sendChatCommand("gamemode survival @s");client.player.networkHandler.sendChatCommand("time set 1000");client.player.networkHandler.sendChatCommand("tp @s 284.500000 117.000000 -647.500000 180.000000 0.000000");record(client,"recovery_survival_daytime_and_door_navigation_commands",DOOR,null);stage("WAIT_RECOVERY_DOOR");}

    private void beginKnownBlockerSetup(MinecraftClient client){
        require(knownDoorBlocked,"Known door blocker evidence is required before skipping interaction");
        verifyRestoredFixedStates(client);client.player.networkHandler.sendChatCommand("gamemode spectator @s");
        record(client,"known_server_door_blocker_preserved_without_repeating_interaction",DOOR,knownDoorResult.deepCopy());
        record(client,"spectator_mode_requested_before_safe_recovery_teleport",STANDING,null);stage("WAIT_KNOWN_SPECTATOR");
    }

    private void waitKnownSpectator(MinecraftClient client){
        require(stageTicks<=120,"Spectator mode did not synchronize before safe recovery teleport");if(!client.player.isSpectator())return;
        client.player.networkHandler.sendChatCommand("tp @s 290.500000 128.000000 -655.500000 90.000000 66.000000");
        record(client,"safe_recovery_navigation_while_spectator",STANDING,null);stage("WAIT_KNOWN_STANDING");
    }

    private void waitKnownStanding(MinecraftClient client){
        require(stageTicks<=200,"Safe spectator recovery position did not settle");if(!at(client,new Vec3d(290.5,128,-655.5))){settled=0;return;}
        require(client.world.getBlockState(STANDING).isAir()&&client.world.getBlockState(STANDING.up()).isAir(),"Safe recovery feet/head cells changed");
        BlockPos floor=STANDING.down();require(!client.world.getBlockState(floor).getCollisionShape(client.world,floor,ShapeContext.absent()).isEmpty(),"Safe recovery floor lost collision");
        if(++settled<12)return;
        if(carriedQaDropRecovery!=null){report.add("qaDropRecovery",carriedQaDropRecovery.deepCopy());record(client,"prior_successful_qa_drop_recovery_carried_without_second_deletion",STANDING,null);beginKnownSurvival(client);return;}
        require(qaDropInput!=null,"Exact QA drop recovery input is required before final known-blocker recovery");
        requestQaDropRecovery(client);stage("WAIT_QA_DROP_RECOVERY");
    }

    private void beginKnownSurvival(MinecraftClient client){
        client.player.networkHandler.sendChatCommand("gamemode survival @s");client.player.networkHandler.sendChatCommand("time set 1000");
        record(client,"survival_and_daytime_requested_after_safe_spectator_teleport",STANDING,null);stage("WAIT_KNOWN_SURVIVAL");
    }

    private void waitQaDropRecovery(MinecraftClient client){
        require(stageTicks<=240,"Guarded QA drop recovery did not return from the integrated server");if(!qaDropReceived)return;
        require(qaDropFailure==null,"Guarded QA drop recovery rejected without deleting ambiguous entities: "+qaDropFailure);
        beginKnownSurvival(client);
    }

    private void waitKnownSurvival(MinecraftClient client){
        require(stageTicks<=240,"Safe recovery survival standing did not settle");
        if(client.player.isCreative()||client.player.isSpectator()||!at(client,new Vec3d(290.5,128,-655.5))||!client.player.isOnGround()){settled=0;return;}
        if(++settled<12)return;
        require(client.world.isSpaceEmpty(client.player,client.player.getBoundingBox()),"Recovered player body intersects world collision");verifyRestoredFixedStates(client);
        JsonObject detail=actualAim(client);detail.addProperty("isOnGround",true);detail.addProperty("persistence","PENDING_NORMAL_INTEGRATED_SERVER_SHUTDOWN_AND_SUBSEQUENT_REOPEN_PROOF");
        record(client,"fixed_state_and_safe_survival_ready_for_normal_native_shutdown",STANDING,detail);
        report.addProperty("doorOpenCloseVerified",false);report.addProperty("allFunctionalChecksPassed",false);
        report.addProperty("persistenceProof","PENDING_NORMAL_INTEGRATED_SERVER_SHUTDOWN_AND_SUBSEQUENT_REOPEN");
        complete=true;update("PASS_REOPEN_STATE_READY_FOR_NATIVE_SHUTDOWN_WITH_KNOWN_DOOR_BLOCKER");
    }

    private void waitRecoveryDoor(MinecraftClient client){
        require(stageTicks<=200,"Recovery door position/mode did not settle");if(client.player.isCreative()||client.player.isSpectator()||!at(client,new Vec3d(284.5,117,-647.5)))return;
        emptySlot=emptyHotbarSlot(client);require(emptySlot>=0,"Recovery door use requires one existing empty hotbar slot");select(client,emptySlot);record(client,"recovery_survival_empty_hand_ack",DOOR,actualAim(client));stage("WAIT_DOOR");
    }

    private void waitDoor(MinecraftClient client){
        require(stageTicks<=200,"Door route did not settle");
        if(!at(client,new Vec3d(284.5,117,-647.5)))return;
        BlockState state=client.world.getBlockState(DOOR);require(id(state).equals(DOOR_ID)&&state.contains(Properties.OPEN)&&!state.get(Properties.OPEN),"Bounded door is not in its original closed state");
        require(client.player.getMainHandStack().isEmpty(),"Door packet hand is not empty");
        var hit=new BlockHitResult(Vec3d.ofCenter(DOOR),Direction.SOUTH,DOOR,false);
        require(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit).isAccepted(),"Native door interact packet was not accepted locally");
        client.player.swingHand(Hand.MAIN_HAND);record(client,"native_interactBlock_open_packet",DOOR,null);stage("WAIT_OPEN");
    }

    private void waitOpen(MinecraftClient client){
        BlockState state=client.world.getBlockState(DOOR);if(!id(state).equals(DOOR_ID)||!state.contains(Properties.OPEN)||!state.get(Properties.OPEN)){if(stageTicks>=8&&!serverDiagnosticRequested)requestDoorServerDiagnostic(client);require(stageTicks<=240&&!(stageTicks>160&&serverDiagnosticReceived),"Door open did not synchronize");return;}
        if(stageTicks<8)return;
        record(client,"door_open_server_ack",DOOR,state(state));
        var hit=new BlockHitResult(Vec3d.ofCenter(DOOR),Direction.SOUTH,DOOR,false);
        require(client.interactionManager.interactBlock(client.player,Hand.MAIN_HAND,hit).isAccepted(),"Native door close packet was not accepted locally");
        client.player.swingHand(Hand.MAIN_HAND);record(client,"native_interactBlock_close_packet",DOOR,null);stage("WAIT_CLOSED");
    }

    private void waitClosed(MinecraftClient client){
        require(stageTicks<=160,"Door close did not synchronize");
        BlockState door=client.world.getBlockState(DOOR);if(!id(door).equals(DOOR_ID)||!door.contains(Properties.OPEN)||door.get(Properties.OPEN))return;
        requireClosedTrapdoor(client);record(client,"door_restored_closed_server_ack",DOOR,state(door));
        if(recoverDoor){require(client.world.getBlockState(ROOT).toString().equals(rootBefore),"Recovered recipe changed during door check");client.player.networkHandler.sendChatCommand("gamemode survival @s");client.player.networkHandler.sendChatCommand("time set 1000");record(client,"restore_survival_daytime_before_normal_native_shutdown",ROOT,null);stage("WAIT_RECOVERY_SAVE");return;}
        JsonObject expected=new JsonObject();expected.addProperty("rootAir",true);expected.addProperty("rootBefore",rootBefore);expected.add("root",pos(ROOT));expected.add("door",pos(DOOR));expected.add("trapdoor",pos(TRAPDOOR));
        JsonArray savedNeighbors=new JsonArray();neighbors.forEach((p,s)->{JsonObject row=new JsonObject();row.add("position",pos(p));row.addProperty("state",s);savedNeighbors.add(row);});expected.add("unchangedNeighbours",savedNeighbors);report.add("savedExpectation",expected);
        record(client,"removed_state_ready_for_normal_native_shutdown",ROOT,null);stage("WAIT_FIRST_SAVE");
    }

    private void waitFirstSave(MinecraftClient client){
        require(stageTicks<=200,"Post-break save settle timed out");if(stageTicks<80)return;
        require(client.world.getBlockState(ROOT).isAir(),"Removed root changed before save/reopen handoff");verifyNeighbors(client);requireClosedDoor(client);requireClosedTrapdoor(client);
        report.addProperty("persistenceProof","PENDING_NORMAL_INTEGRATED_SERVER_SHUTDOWN_AND_SUBSEQUENT_REOPEN");complete=true;update("PASS_MUTATION_READY_FOR_NATIVE_SHUTDOWN");
    }

    private void waitReopenPosition(MinecraftClient client){
        require(stageTicks<=240,"Reopen verification position did not settle");
        if(!client.world.isChunkLoaded(ROOT)||!at(client,new Vec3d(290.5,128,-655.5)))return;
        JsonObject expected=prior.getAsJsonObject("savedExpectation");
        require(expected.get("rootAir").getAsBoolean()&&client.world.getBlockState(ROOT).isAir(),"Native break did not persist across save/reopen");
        require(client.world.getBlockEntity(ROOT)==null,"Reopened removed root has block entity data");
        for(JsonElement value:expected.getAsJsonArray("unchangedNeighbours")){
            JsonObject row=value.getAsJsonObject();BlockPos p=readPos(row.getAsJsonArray("position"));require(client.world.getBlockState(p).toString().equals(row.get("state").getAsString()),"Neighbour changed across saved break at "+p.toShortString());
        }
        requireClosedDoor(client);requireClosedTrapdoor(client);record(client,"saved_native_break_reopen_ack",ROOT,state(client.world.getBlockState(ROOT)));
        String before=expected.get("rootBefore").getAsString();require(before.contains(ROOT_ID)&&before.contains("part="+ROOT_PART),"Prior exact static recipe evidence changed");
        require(client.world.getBlockState(ROOT).isAir(),"Guarded restoration requires exact expected air after-state");
        client.player.networkHandler.sendChatCommand("setblock 289 128 -656 "+ROOT_ID+"[part="+ROOT_PART+"] keep");
        record(client,"guarded_vanilla_setblock_keep_restore_command",ROOT,null);stage("WAIT_RESTORED");
    }

    private void waitRestored(MinecraftClient client){
        require(stageTicks<=160,"Exact static state restoration did not synchronize");
        JsonObject expected=prior.getAsJsonObject("savedExpectation");BlockState state=client.world.getBlockState(ROOT);
        if(!state.toString().equals(expected.get("rootBefore").getAsString()))return;
        require(state.getBlock() instanceof StaticArchitectureBlock block&&block.recipe(state).index()==ROOT_PART,"Restored state is not exact recipe38");
        require(client.world.getBlockEntity(ROOT)==null,"Native static restoration created forbidden block entity data");
        require(!state.getCollisionShape(client.world,ROOT,ShapeContext.absent()).isEmpty(),"Restored recipe lost collision");
        requireClosedDoor(client);requireClosedTrapdoor(client);record(client,"exact_static_recipe_restored_without_owner_data",ROOT,state(state));
        // The frozen .6 level.dat has playerGameType=0 and DayTime=1000.
        client.player.networkHandler.sendChatCommand("gamemode survival @s");
        client.player.networkHandler.sendChatCommand("time set 1000");
        record(client,"restore_original_survival_mode_and_daytime",STANDING,null);
        record(client,"restored_state_ready_for_normal_native_shutdown",ROOT,null);stage("WAIT_RESTORE_SAVE");
    }

    private void waitRestoreSave(MinecraftClient client){
        require(stageTicks<=200,"Post-restore save settle timed out");if(stageTicks<80)return;
        require(client.world.getBlockState(ROOT).toString().equals(prior.getAsJsonObject("savedExpectation").get("rootBefore").getAsString()),"Restored recipe changed before final save");
        require(!client.player.isCreative()&&!client.player.isSpectator()&&client.player.isOnGround(),"Final player mode/standing collision did not settle");
        requireClosedDoor(client);requireClosedTrapdoor(client);report.addProperty("persistenceProof","PENDING_NORMAL_INTEGRATED_SERVER_SHUTDOWN_AND_SUBSEQUENT_REOPEN");complete=true;update("PASS_REOPEN_RESTORED_READY_FOR_NATIVE_SHUTDOWN");
    }

    private void waitRecoverySave(MinecraftClient client){
        require(stageTicks<=200,"Recovered route shutdown handoff settle timed out");if(stageTicks<80)return;require(client.world.getBlockState(ROOT).toString().equals(rootBefore),"Recovered recipe changed before shutdown handoff");verifySavedNeighbors(client,prior.getAsJsonObject("recoveryExpectation").getAsJsonArray("unchangedNeighbours"));require(!client.player.isCreative()&&!client.player.isSpectator(),"Recovered route did not retain survival mode");requireClosedDoor(client);requireClosedTrapdoor(client);report.addProperty("persistenceProof","PENDING_NORMAL_INTEGRATED_SERVER_SHUTDOWN_AND_SUBSEQUENT_REOPEN");complete=true;update("PASS_RECOVERY_DOOR_READY_FOR_NATIVE_SHUTDOWN");
    }

    private void verifyRestoredFixedStates(MinecraftClient client){
        require(client.world.getBlockState(ROOT).toString().equals(rootBefore),"Restored recipe38 differs from recovery evidence");require(client.world.getBlockEntity(ROOT)==null,"Restored recipe38 has block entity data");
        verifySavedNeighbors(client,prior.getAsJsonObject("recoveryExpectation").getAsJsonArray("unchangedNeighbours"));
        require(client.world.getBlockState(STANDING).isAir()&&client.world.getBlockState(STANDING.up()).isAir(),"Bounded standing cells changed");BlockPos floor=STANDING.down();require(!client.world.getBlockState(floor).getCollisionShape(client.world,floor,ShapeContext.absent()).isEmpty(),"Bounded standing floor lost collision");
        BlockState glass=client.world.getBlockState(GLASS);require(id(glass).equals(GLASS_ID),"Bounded transparent detail changed");
        BlockState stair=client.world.getBlockState(STAIR);require(id(stair).equals(STAIR_ID)&&!stair.getCollisionShape(client.world,STAIR,ShapeContext.absent()).isEmpty(),"Bounded stair or its collision changed");
        requireClosedDoor(client);requireClosedTrapdoor(client);
    }

    private void probeFixedStates(MinecraftClient client){
        BlockState glass=client.world.getBlockState(GLASS);require(id(glass).equals(GLASS_ID),"Bounded transparent detail changed");
        JsonObject transparent=state(glass);transparent.addProperty("opaqueFullCube",glass.isOpaqueFullCube(client.world,GLASS));record(client,"transparent_detail_present",GLASS,transparent);
        BlockState stair=client.world.getBlockState(STAIR);require(id(stair).equals(STAIR_ID),"Bounded stair changed");var stairShape=stair.getCollisionShape(client.world,STAIR,ShapeContext.absent());require(!stairShape.isEmpty(),"Bounded stair lost collision");
        JsonObject stairEvidence=state(stair);stairEvidence.add("exactCollisionBoxes",boxes(stairShape.getBoundingBoxes()));record(client,"stair_exact_native_collision",STAIR,stairEvidence);
        requireClosedTrapdoor(client);record(client,"trapdoor_initial_closed",TRAPDOOR,state(client.world.getBlockState(TRAPDOOR)));
    }
    private void requestDoorServerDiagnostic(MinecraftClient client){
        serverDiagnosticRequested=true;var server=client.getServer();UUID playerId=client.player.getUuid();if(server==null){serverDiagnosticReceived=true;record(client,"door_server_diagnostic_unavailable",DOOR,null);return;}
        server.execute(()->{
            JsonObject detail=new JsonObject();try{
                var player=server.getPlayerManager().getPlayer(playerId);require(player!=null,"Integrated server player missing for door diagnostic");var world=player.getServerWorld();
                detail.addProperty("clientPlayerUuid",playerId.toString());detail.addProperty("serverPlayerUuid",player.getUuidAsString());detail.add("serverPosition",vector(player.getPos()));detail.add("serverEye",vector(player.getEyePos()));detail.addProperty("serverYaw",player.getYaw());detail.addProperty("serverPitch",player.getPitch());
                BlockState door=world.getBlockState(DOOR);detail.add("serverDoor",state(door));var target=dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.target(world,DOOR,player);
                if(target==null)detail.addProperty("actualTarget","null");else{JsonObject selected=new JsonObject();selected.addProperty("registryId",target.registryId());selected.addProperty("instanceId",target.instanceId().toString());selected.add("root",JSON.toJsonTree(List.of(target.root().x(),target.root().y(),target.root().z())));detail.add("actualTarget",selected);}
                if(world.getBlockEntity(DOOR) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity entity&&entity.resident()!=null){JsonObject resident=new JsonObject();resident.addProperty("registryId",entity.resident().registryId());resident.addProperty("instanceId",entity.resident().instanceId().toString());resident.add("root",JSON.toJsonTree(List.of(entity.resident().root().x(),entity.resident().root().y(),entity.resident().root().z())));detail.add("resident",resident);}
                for(var contribution:dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.contributions(world,DOOR))if(target!=null&&target.equals(contribution.owner())){JsonObject rootData=new JsonObject();rootData.addProperty("registryId",contribution.rootData().blockId());rootData.add("properties",JSON.toJsonTree(contribution.rootData().properties()));detail.add("targetRootData",rootData);break;}
                var result=dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.lastResult();if(result==null)detail.addProperty("lastResult","null");else{JsonObject row=new JsonObject();row.addProperty("outcome",result.outcome().name());row.addProperty("changedCells",result.changedCells());row.addProperty("reason",result.reason());detail.add("lastResult",row);}
            }catch(Throwable failure){detail.addProperty("diagnosticFailure",failure.toString());}
            String immutable=detail.toString();client.execute(()->{serverDiagnosticReceived=true;if(client.world!=null&&!complete)record(client,"door_server_transition_result_after_closed_ack_timeout",DOOR,JsonParser.parseString(immutable).getAsJsonObject());});
        });
    }

    private void requestQaDropRecovery(MinecraftClient client){
        require(!qaDropRequested,"Guarded QA drop recovery may be requested only once");qaDropRequested=true;
        validateQaDropInput(qaDropInput);UUID expectedPlayer=UUID.fromString(QA_PLAYER_UUID);require(client.player.getUuid().equals(expectedPlayer),"QA recovery client player UUID differs from the frozen host player");
        var server=client.getServer();require(server!=null,"QA drop recovery requires the integrated server");JsonObject immutableInput=qaDropInput.deepCopy();
        record(client,"guarded_qa_drop_recovery_requested_on_integrated_server",new BlockPos(318,119,-622),null);
        server.execute(()->{
            JsonObject result=new JsonObject();String failure=null;
            try{
                var player=server.getPlayerManager().getPlayer(expectedPlayer);require(player!=null&&player.getUuid().equals(expectedPlayer),"Exact QA host player is not present on the integrated server");
                var world=player.getServerWorld();var rows=new JsonArray();var removals=new ArrayList<net.minecraft.entity.ItemEntity>();var removalRows=new ArrayList<JsonObject>();
                for(JsonElement value:immutableInput.getAsJsonArray("drops")){
                    JsonObject drop=value.getAsJsonObject();UUID uuid=UUID.fromString(drop.get("uuid").getAsString());String expectedId=drop.get("id").getAsString();int expectedCount=drop.get("count").getAsInt();
                    byte[] compressed=Base64.getDecoder().decode(drop.get("itemNbtGzipBase64").getAsString());require(compressed.length>0&&compressed.length<=1_048_576,"QA expected item NBT is outside the bounded size");
                    NbtCompound expected;try(var input=new java.io.ByteArrayInputStream(compressed)){expected=NbtIo.readCompressed(input);}
                    var entity=world.getEntity(uuid);JsonObject row=new JsonObject();row.addProperty("uuid",uuid.toString());row.addProperty("expectedId",expectedId);row.addProperty("expectedCount",expectedCount);
                    if(entity==null){row.addProperty("outcome","ALREADY_ABSENT");rows.add(row);continue;}
                    require(entity instanceof net.minecraft.entity.ItemEntity,"Allowed QA UUID resolves to a non-item entity: "+uuid);
                    var item=(net.minecraft.entity.ItemEntity)entity;require(item.getPos().squaredDistanceTo(new Vec3d(318,119,-622))<=64.0,"Allowed QA item is outside the exact death radius: "+uuid);
                    var stack=item.getStack();require(Registries.ITEM.getId(stack.getItem()).toString().equals(expectedId)&&stack.getCount()==expectedCount,"Allowed QA item id/count differs: "+uuid);
                    require(stack.writeNbt(new NbtCompound()).equals(expected),"Allowed QA item NBT differs from frozen inventory evidence: "+uuid);
                    row.addProperty("outcome","VALIDATED_PENDING_DISCARD");rows.add(row);removals.add(item);removalRows.add(row);
                }
                // All three UUIDs and their complete stacks are proven before the
                // first mutation. No selector, inventory, chunk or nearby scan is used.
                for(int i=0;i<removals.size();i++){removals.get(i).discard();require(removals.get(i).isRemoved(),"Validated QA item did not acknowledge native discard");removalRows.get(i).addProperty("outcome","REMOVED");}
                result.addProperty("schemaVersion",1);result.addProperty("status","ACK_REMOVED_OR_ALREADY_ABSENT");result.addProperty("sourceBaselineWorldSha256",BASELINE_WORLD_SHA);result.addProperty("qaPlayerUuid",QA_PLAYER_UUID);result.add("deathPosition",JSON.toJsonTree(List.of(318,119,-622)));result.addProperty("radius",8);result.add("drops",rows);
            }catch(Throwable rejected){failure=rejected.toString();}
            String immutableResult=result.toString(),immutableFailure=failure;client.execute(()->{
                qaDropFailure=immutableFailure;qaDropReceived=true;
                if(client.world==null||complete)return;
                if(immutableFailure==null){JsonObject acknowledged=JsonParser.parseString(immutableResult).getAsJsonObject();report.add("qaDropRecovery",acknowledged.deepCopy());record(client,"guarded_qa_created_drops_native_ack",new BlockPos(318,119,-622),acknowledged);}
                else{JsonObject detail=new JsonObject();detail.addProperty("rejection",immutableFailure);detail.addProperty("mutation","NOT_STARTED_UNLESS_ALL_THREE_ENTITIES_WERE_VALIDATED");record(client,"guarded_qa_drop_recovery_rejected",new BlockPos(318,119,-622),detail);}
            });
        });
    }

    private static void validateQaDropInput(JsonObject input){
        require(input!=null&&input.has("schemaVersion")&&input.get("schemaVersion").getAsInt()==1,"QA drop recovery schema must be exactly 1");
        require(BASELINE_WORLD_SHA.equals(string(input,"sourceBaselineWorldSha256")),"QA drop recovery baseline world SHA differs");require(QA_PLAYER_UUID.equals(string(input,"qaPlayerUuid")),"QA drop recovery player UUID differs");
        JsonArray death=input.getAsJsonArray("deathPosition");require(death!=null&&death.size()==3&&death.get(0).getAsInt()==318&&death.get(1).getAsInt()==119&&death.get(2).getAsInt()==-622,"QA death position differs");require(input.has("radius")&&input.get("radius").getAsInt()==8,"QA drop recovery radius differs");
        JsonArray drops=input.getAsJsonArray("drops");require(drops!=null&&drops.size()==3,"QA drop recovery must contain exactly three drops");Set<String> seen=new HashSet<>();
        for(JsonElement value:drops){require(value.isJsonObject(),"QA drop row is not an object");JsonObject drop=value.getAsJsonObject();String uuid=string(drop,"uuid"),id=string(drop,"id");require(QA_DROP_IDS.containsKey(uuid)&&QA_DROP_IDS.get(uuid).equals(id)&&seen.add(uuid),"QA drop UUID/id is not in the exact allowlist");require(drop.has("count")&&drop.get("count").getAsInt()==1,"QA drop count differs");require(drop.has("itemNbtGzipBase64")&&drop.get("itemNbtGzipBase64").isJsonPrimitive()&&!drop.get("itemNbtGzipBase64").getAsString().isEmpty()&&drop.get("itemNbtGzipBase64").getAsString().length()<=1_500_000,"QA drop expected NBT is missing or unbounded");}
    }
    private static boolean validCarriedQaDropRecovery(JsonObject result){
        try{
            if(result.get("schemaVersion").getAsInt()!=1||!"ACK_REMOVED_OR_ALREADY_ABSENT".equals(string(result,"status"))||!BASELINE_WORLD_SHA.equals(string(result,"sourceBaselineWorldSha256"))||!QA_PLAYER_UUID.equals(string(result,"qaPlayerUuid"))||result.get("radius").getAsInt()!=8)return false;
            JsonArray death=result.getAsJsonArray("deathPosition");if(death==null||death.size()!=3||death.get(0).getAsInt()!=318||death.get(1).getAsInt()!=119||death.get(2).getAsInt()!=-622)return false;
            JsonArray rows=result.getAsJsonArray("drops");if(rows==null||rows.size()!=3)return false;Set<String> seen=new HashSet<>();
            for(JsonElement value:rows){JsonObject row=value.getAsJsonObject();String uuid=string(row,"uuid"),outcome=string(row,"outcome");if(!QA_DROP_IDS.containsKey(uuid)||!QA_DROP_IDS.get(uuid).equals(string(row,"expectedId"))||row.get("expectedCount").getAsInt()!=1||!("REMOVED".equals(outcome)||"ALREADY_ABSENT".equals(outcome))||!seen.add(uuid))return false;}
            return seen.size()==3;
        }catch(RuntimeException invalid){return false;}
    }

    private void snapshotNeighbors(MinecraftClient client){
        neighbors.clear();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(x!=0||y!=0||z!=0){BlockPos p=ROOT.add(x,y,z);neighbors.put(p,client.world.getBlockState(p).toString());}
    }
    private void preserveRecoveryExpectation(){
        JsonObject recovery=new JsonObject();recovery.add("root",pos(ROOT));recovery.addProperty("rootBefore",rootBefore);recovery.addProperty("expectedAfter","minecraft:air");
        recovery.addProperty("guardedRestoreCommand","setblock 289 128 -656 "+ROOT_ID+"[part="+ROOT_PART+"] keep");
        recovery.addProperty("affectedCellCount",1);JsonArray affected=new JsonArray();JsonObject cell=new JsonObject();cell.add("position",pos(ROOT));cell.addProperty("before",rootBefore);cell.addProperty("expectedAfter","minecraft:air");affected.add(cell);recovery.add("affectedCells",affected);
        JsonArray savedNeighbors=new JsonArray();neighbors.forEach((p,s)->{JsonObject row=new JsonObject();row.add("position",pos(p));row.addProperty("state",s);savedNeighbors.add(row);});recovery.add("unchangedNeighbours",savedNeighbors);
        recovery.addProperty("doorInitialOpen",false);recovery.addProperty("trapdoorInitialOpen",false);report.add("recoveryExpectation",recovery);
    }
    private void preserveKnownDoorBlocker(){
        require(knownDoorResult!=null,"Exact known door blocker result missing");report.add("knownDoorBlocker",knownDoorResult.deepCopy());report.addProperty("doorOpenCloseVerified",false);report.addProperty("allFunctionalChecksPassed",false);
    }
    private static JsonObject findKnownDoorBlocker(JsonObject evidence){
        if(evidence==null)return null;
        if(evidence.has("knownDoorBlocker")&&evidence.get("knownDoorBlocker").isJsonObject()){
            JsonObject result=evidence.getAsJsonObject("knownDoorBlocker");return exactKnownDoorBlocker(result)?result.deepCopy():null;
        }
        if(!evidence.has("operations")||!evidence.get("operations").isJsonArray())return null;
        for(JsonElement value:evidence.getAsJsonArray("operations")){
            if(!value.isJsonObject())continue;JsonObject operation=value.getAsJsonObject();
            if(!"door_server_transition_result_after_closed_ack_timeout".equals(string(operation,"operation"))||!operation.has("detail")||!operation.get("detail").isJsonObject())continue;
            JsonObject detail=operation.getAsJsonObject("detail");if(!detail.has("lastResult")||!detail.get("lastResult").isJsonObject())continue;
            JsonObject result=detail.getAsJsonObject("lastResult");if(exactKnownDoorBlocker(result)){JsonObject copy=result.deepCopy();copy.addProperty("sourceOperation","door_server_transition_result_after_closed_ack_timeout");return copy;}
        }
        return null;
    }
    private static boolean exactKnownDoorBlocker(JsonObject result){
        return "REJECTED".equals(string(result,"outcome"))&&result.has("changedCells")&&result.get("changedCells").getAsInt()==0&&KNOWN_DOOR_REASON.equals(string(result,"reason"));
    }
    private void verifyNeighbors(MinecraftClient client){neighbors.forEach((p,s)->require(client.world.getBlockState(p).toString().equals(s),"Native break changed unrelated neighbour "+p.toShortString()));}
    private static void verifySavedNeighbors(MinecraftClient client,JsonArray saved){require(saved!=null&&saved.size()==26,"Failed recovery requires all 26 neighbour states");Set<BlockPos> seen=new HashSet<>();for(JsonElement value:saved){JsonObject row=value.getAsJsonObject();BlockPos position=readPos(row.getAsJsonArray("position"));require(!position.equals(ROOT)&&Math.abs(position.getX()-ROOT.getX())<=1&&Math.abs(position.getY()-ROOT.getY())<=1&&Math.abs(position.getZ()-ROOT.getZ())<=1&&seen.add(position),"Invalid failed-recovery neighbour evidence");require(client.world.getBlockState(position).toString().equals(row.get("state").getAsString()),"Failed-recovery neighbour changed at "+position.toShortString());}}
    private static void requireClosedDoor(MinecraftClient client){BlockState state=client.world.getBlockState(DOOR);require(id(state).equals(DOOR_ID)&&state.contains(Properties.OPEN)&&!state.get(Properties.OPEN),"Door was not restored to original closed state");}
    private static void requireClosedTrapdoor(MinecraftClient client){BlockState state=client.world.getBlockState(TRAPDOOR);require(id(state).equals(TRAPDOOR_ID)&&state.contains(Properties.OPEN)&&!state.get(Properties.OPEN),"Trapdoor differs from original closed state");}
    private static int emptyHotbarSlot(MinecraftClient client){for(int i=0;i<9;i++)if(client.player.getInventory().getStack(i).isEmpty())return i;return -1;}
    private static void select(MinecraftClient client,int slot){client.player.getInventory().selectedSlot=slot;client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));}
    private void stage(String next){phase=next;stageTicks=0;settled=0;update("RUNNING");}
    private void update(String status){report.addProperty("status",status);report.addProperty("phase",phase);report.addProperty("totalTicks",totalTicks);report.addProperty("stageTicks",stageTicks);report.addProperty("complete",complete);}
    private void record(MinecraftClient client,String operation,BlockPos at,JsonObject detail){JsonObject row=new JsonObject();row.addProperty("operation",operation);row.add("at",pos(at));row.addProperty("clientWorldTick",client.world.getTime());if(detail!=null)row.add("detail",detail);operations.add(row);update("RUNNING");}
    private static JsonObject state(BlockState value){JsonObject out=new JsonObject();out.addProperty("registryId",id(value));out.addProperty("state",value.toString());return out;}
    private static String id(BlockState value){return Registries.BLOCK.getId(value.getBlock()).toString();}
    private static JsonArray pos(BlockPos p){JsonArray out=new JsonArray();out.add(p.getX());out.add(p.getY());out.add(p.getZ());return out;}
    private static JsonArray vector(Vec3d p){JsonArray out=new JsonArray();out.add(p.x);out.add(p.y);out.add(p.z);return out;}
    private static JsonObject actualAim(MinecraftClient client){
        JsonObject out=new JsonObject();out.add("eye",vector(client.player.getEyePos()));out.addProperty("yaw",client.player.getYaw());out.addProperty("pitch",client.player.getPitch());
        if(client.crosshairTarget==null)out.addProperty("crosshair","null");
        else{out.addProperty("crosshair",client.crosshairTarget.getType().name());out.add("hit",vector(client.crosshairTarget.getPos()));if(client.crosshairTarget instanceof BlockHitResult hit){out.add("block",pos(hit.getBlockPos()));out.addProperty("side",hit.getSide().getName());}}
        return out;
    }
    private static BlockPos readPos(JsonArray p){require(p!=null&&p.size()==3,"Saved route position must have three coordinates");return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
    private static JsonArray boxes(List<Box> values){JsonArray out=new JsonArray();for(Box box:values){JsonArray row=new JsonArray();row.add(box.minX);row.add(box.minY);row.add(box.minZ);row.add(box.maxX);row.add(box.maxY);row.add(box.maxZ);out.add(row);}return out;}
    private static boolean at(MinecraftClient client,Vec3d expected){return client.player.getPos().squaredDistanceTo(expected)<.01&&client.world.isChunkLoaded(client.player.getBlockPos());}
    private static String string(JsonObject object,String key){return object.has(key)?object.get(key).getAsString():null;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}


