package dev.dreamwalker.bloodbornedw.nativegridqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** One finite, opt-in functional pass on a bound disposable pilot. No tickets,
 * renderer hooks, benchmark, migration, raw state restore, or production code.
 * Old APIs match the existing partial/door/tree checks. New API use is reflected
 * only after a native_grid registry state has actually been found. */
public final class NativeGridFunctionChecks implements ModInitializer {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private JsonObject plan, report, inheritedAirDoor, inheritedBridgeDoor, runtimePatch;
    private final JsonArray checks = new JsonArray(), windows = new JsonArray(), actions = new JsonArray(), placements = new JsonArray();
    private final Set<Long> allowedChunks = new HashSet<>();
    private final Map<BlockPos, JsonObject> watched = new LinkedHashMap<>();
    private ServerWorld world;
    private ServerPlayerEntity actor;
    private BlockPos standMin, standMax, standRoot;
    private boolean standInitiallyAir, standRestored;
    private Path output;
    private int ticks, settled;
    private boolean done, candidate, continuation;
    private Runnable activeCleanup;
    private List<String> placementMessages;
    private ForeignGuestFixture foreignGuestFixture;
    private record Source(BlockPos root, BlockState state, NbtCompound payload,
                          ObjectInstance.Owner owner, List<BlockPos> offsets, boolean nativeGrid) {}
    private record BridgeFixture(BlockPos pos,int index,BlockState background,VoxelShape collision,VoxelShape outline) {}

    @Override public void onInitialize() {
        String path = System.getProperty("dw.nativegridqa.plan");
        if (path == null) return;
        try {
            Path input = ordinary(Path.of(path));
            require(Files.size(input) <= 2 * 1024 * 1024, "Finite QA plan exceeds 2 MiB");
            plan = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            require(plan.get("schemaVersion").getAsInt() == 1 && text(plan,"mode","").equals("BOUND_NATIVE_GRID_FUNCTION_QA"), "Unknown QA plan");
            candidate = text(plan,"variant","").equals("candidate");
            require(candidate || text(plan,"variant","").equals("baseline"), "Explicit baseline/candidate variant required");
            require(sha(ordinary(Path.of(plan.get("productionJar").getAsString()))).equals(plan.get("productionSha256").getAsString()), "Bound production JAR changed");
            output = ordinary(Path.of(plan.get("output").getAsString()));
            report = new JsonObject(); report.addProperty("status", "WAITING"); report.addProperty("variant", candidate ? "candidate" : "baseline");
            report.addProperty("productionSha256", plan.get("productionSha256").getAsString());
            report.addProperty("planSha256", sha(input)); report.add("checks",checks); report.add("windows",windows); report.add("actions",actions); report.add("placements",placements);
            if(plan.has("runtimePatch"))validateRuntimePatch();
            if(plan.has("continuation"))bindContinuation();
            save();
        } catch (Exception failure) { throw new IllegalStateException("Bound function QA refused",failure); }
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { prepare(server); } catch (Throwable failure) { finish(server,failure); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (done || world == null) return;
            try {
                require(++ticks <= 600, "Function QA could not settle within 600 native ticks");
                if (world.getChunkManager().getLightingProvider().hasUpdates()) { settled=0; return; }
                if (++settled < 40) return;
                if(foreignGuestFixture!=null){if(foreignGuestFixture.step())finish(server,null);return;}
                run(); finish(server,null);
            } catch (Throwable failure) { finish(server,failure); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (!done) return;
            // A target-profile dependency leaves non-daemon config workers
            // parked after normal server shutdown. Wait for all server-thread
            // callbacks to return, then end only this explicitly opted-in QA VM.
            Thread exit = new Thread(() -> {
                try {
                    server.getThread().join(30000);
                    if (server.getThread().isAlive()) return;
                    report.addProperty("normalServerThreadFinished",true); save();
                    System.exit(text(report,"status","FAIL").equals("PASS")?0:1);
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            },"NativeGridFunctionQaExit");
            exit.setDaemon(true); exit.start();
        });
    }

    private void bindContinuation() throws Exception {
        require(candidate,"Only the affected candidate run may continue");JsonObject bound=plan.getAsJsonObject("continuation");
        JsonObject parentPlan=readBoundJson(bound.getAsJsonObject("previousPlan")),previous=readBoundJson(bound.getAsJsonObject("previousReport"));
        require(previous.get("status").getAsString().equals("FAIL")&&previous.get("standRestored").getAsBoolean(),"Continuation needs the unchanged failed run with a restored stand");
        require(previous.get("planSha256").getAsString().equals(bound.getAsJsonObject("previousPlan").get("sha256").getAsString())&&compatibleProduction(previous),"Continuation evidence belongs to another plan/runtime");
        JsonObject current=comparablePlan(plan),prior=comparablePlan(parentPlan);require(current.equals(prior),"Continuation may change only output, its evidence binding and a proven adapter-only runtime patch");
        JsonArray oldActions=previous.getAsJsonArray("actions");require(oldActions.size()==1&&text(oldActions.get(0).getAsJsonObject(),"kind","").equals("staticCell")&&text(oldActions.get(0).getAsJsonObject(),"status","").equals("PASS"),"Only the failed door plus not-yet-run tree/statue may continue");
        JsonArray inherited=new JsonArray();int failures=0;
        for(JsonElement raw:previous.getAsJsonArray("checks")){JsonObject row=raw.getAsJsonObject();if(row.get("pass").getAsBoolean())inherited.add(row.deepCopy());else{failures++;require(row.get("name").getAsString().startsWith("registered picked BlockItem places at "),"Continuation contains another failed check");}}
        require(failures==1&&inherited.size()==bound.get("inheritedSuccessfulChecks").getAsInt(),"Continuation successful-check count changed");
        if(bound.has("diagnosticPreviousAttempt")){JsonObject diagnostic=readBoundJson(bound.getAsJsonObject("diagnosticPreviousAttempt"));require(text(diagnostic,"status","").equals("FAIL")&&diagnostic.get("standRestored").getAsBoolean()&&diagnostic.get("normalServerThreadFinished").getAsBoolean()&&compatibleProduction(diagnostic),"The intervening failed diagnostic run must remain exact, restored and normally stopped");}
        if(bound.has("priorFailedAttempts"))for(JsonElement raw:bound.getAsJsonArray("priorFailedAttempts")){JsonObject failure=raw.getAsJsonObject(),oldReport=readBoundJson(failure.getAsJsonObject("report"));readBoundJson(failure.getAsJsonObject("plan"));require(text(oldReport,"status","").equals("FAIL")&&compatibleProduction(oldReport)&&oldReport.get("planSha256").equals(failure.getAsJsonObject("plan").get("sha256")),"Prior failed-attempt history changed");}
        if(bound.has("inheritedAirDoor")){
            JsonObject air=bound.getAsJsonObject("inheritedAirDoor"),airReport=readBoundJson(air.getAsJsonObject("report")),airPlan=readBoundJson(air.getAsJsonObject("plan"));
            require(text(airReport,"status","").equals("FAIL")&&airReport.get("standRestored").getAsBoolean()&&airReport.get("normalServerThreadFinished").getAsBoolean()&&compatibleProduction(airReport),"Inherited AIR motion must come from the unchanged restored failed run on this runtime");
            require(airReport.get("planSha256").equals(air.getAsJsonObject("plan").get("sha256")),"Inherited AIR report belongs to another plan");
            require(comparablePlan(airPlan).equals(current),"Inherited AIR motion uses another source/stand/catalog plan");
            JsonArray inheritedMotion=new JsonArray();for(JsonElement raw:airReport.getAsJsonArray("checks")){JsonObject row=raw.getAsJsonObject();if(airDoorCheck(row.get("name").getAsString())){require(row.get("pass").getAsBoolean(),"An inherited AIR motion check failed");inheritedMotion.add(row.deepCopy());}}
            require(inheritedMotion.size()==5&&inheritedMotion.equals(air.getAsJsonArray("checks")),"Inherited AIR motion needs both real right clicks and the exact open/closed collision checks");
            List<JsonObject> installedDoor=new ArrayList<>();for(JsonElement raw:airReport.getAsJsonArray("installedWholeObjects")){JsonObject row=raw.getAsJsonObject();if(text(row,"kind","").equals("door"))installedDoor.add(row);}
            require(installedDoor.size()==1&&installedDoor.get(0).get("state").equals(air.get("manualState"))&&installedDoor.get(0).get("payloadNbt").equals(air.get("manualPayloadNbt")),"Inherited manual-door state/payload evidence differs");
            JsonObject proof=readBoundJson(air.getAsJsonObject("helperProof"));require(text(proof,"status","").equals("PASS")&&proof.get("helperInBothStates").getAsBoolean()&&proof.get("manualState").equals(air.get("manualState"))&&proof.get("manualPayloadNbt").equals(air.get("manualPayloadNbt")),"Inherited manual helper needs its exact source-bound two-pose proof");
            JsonObject representative=air.getAsJsonObject("representativeAssembly"),manifest=readBoundJson(representative.getAsJsonObject("manifest"));boolean matched=false;
            for(JsonElement raw:manifest.getAsJsonArray("entries")){JsonObject row=raw.getAsJsonObject();if(text(row,"key","").equals(representative.get("entryKey").getAsString())){require(!matched&&row.get("registryId").equals(air.getAsJsonObject("manualState").get("registryId"))&&row.getAsJsonObject("sourceSpec").get("sha256").equals(representative.get("sourceSpecSha256")),"Representative assembly identity/source decision differs");matched=true;}}
            require(matched,"The exact chosen whole-door assembly is absent from the confirmed manifest");
            try(InputStream stream=ImportedSourcePartsV127.class.getResourceAsStream("/bloodborne_dw/composite/source_dark_arch_door.json")){require(stream!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(representative.get("sourceSpecSha256").getAsString()),"Representative whole-door spec differs from the actually loaded resource");}
            inheritedAirDoor=air;report.add("inheritedAirDoorChecks",inheritedMotion);report.add("inheritedAirDoorEvidence",air.deepCopy());
        }
        if(bound.has("inheritedBridgeDoor")){
            require(runtimePatch!=null&&bound.has("fixtureRecovery"),"A failed terminal-bridge run needs the exact adapter patch and owned-fixture recovery");
            JsonObject bridge=bound.getAsJsonObject("inheritedBridgeDoor"),bridgeReport=readBoundJson(bridge.getAsJsonObject("report")),bridgePlan=readBoundJson(bridge.getAsJsonObject("plan"));
            require(text(bridgeReport,"status","").equals("FAIL")&&!bridgeReport.get("standRestored").getAsBoolean()&&bridgeReport.get("normalServerThreadFinished").getAsBoolean()&&compatibleProduction(bridgeReport)&&bridgeReport.get("planSha256").equals(bridge.getAsJsonObject("plan").get("sha256"))&&comparablePlan(bridgePlan).equals(current),"Inherited bridge motion belongs to another failure/runtime/fixture");
            JsonArray motion=new JsonArray();for(JsonElement raw:bridgeReport.getAsJsonArray("checks")){JsonObject check=raw.getAsJsonObject();if(!check.get("pass").getAsBoolean())break;motion.add(check.deepCopy());}
            require(motion.size()==17&&motion.equals(bridge.getAsJsonArray("checks")),"The exact seventeen successful bridge setup/motion checks are required");
            inheritedBridgeDoor=bridge;report.add("inheritedBridgeDoorChecks",motion);report.add("inheritedBridgeDoorEvidence",bridge.deepCopy());
        }
        report.add("continuation",bound.deepCopy());report.addProperty("priorRunStatus","FAIL");report.add("inheritedSuccessfulChecks",inherited);report.add("inheritedSuccessfulActions",oldActions.deepCopy());continuation=true;
    }
    private static boolean airDoorCheck(String name){return name.equals("actual selected door part handles right-click")||name.equals("door right click changes one whole door open state")||name.equals("door open collision moves with the leaf")||name.equals("door second right click restores closed-state exact collision");}
    private boolean compatibleProduction(JsonObject evidence){return evidence.get("productionSha256").equals(plan.get("productionSha256"))||runtimePatch!=null&&evidence.get("productionSha256").equals(runtimePatch.getAsJsonObject("before").get("sha256"));}
    private JsonObject comparablePlan(JsonObject input){JsonObject copy=input.deepCopy();copy.remove("continuation");copy.remove("output");if(runtimePatch!=null){copy.remove("runtimePatch");copy.remove("productionJar");copy.remove("productionSha256");}return copy;}
    private void validateRuntimePatch() throws Exception {
        JsonObject patch=readBoundJson(plan.getAsJsonObject("runtimePatch"));require(text(patch,"status","").equals("PASS_EXACT_BRIDGE_CLASS_DIFF_NOT_GAMEPLAY")&&!patch.get("gameplayAccepted").getAsBoolean()&&patch.get("allResourcesExact").getAsBoolean()&&patch.get("allNonAdapterClassesExact").getAsBoolean(),"Unknown adapter patch receipt");
        Path before=ordinary(Path.of(patch.getAsJsonObject("before").get("path").getAsString())),after=ordinary(Path.of(patch.getAsJsonObject("after").get("path").getAsString()));
        require(sha(before).equals(patch.getAsJsonObject("before").get("sha256").getAsString())&&sha(after).equals(patch.getAsJsonObject("after").get("sha256").getAsString())&&after.equals(ordinary(Path.of(plan.get("productionJar").getAsString())))&&patch.getAsJsonObject("after").get("sha256").equals(plan.get("productionSha256")),"Adapter patch JAR binding changed");
        Map<String,String> old=jarInventory(before),next=jarInventory(after);require(old.keySet().equals(next.keySet()),"Adapter patch added or removed JAR entries");JsonArray changes=new JsonArray();String adapter="dev/dreamwalker/bloodbornedw/composite/FabricCompositeWorld";
        for(String entry:old.keySet())if(!old.get(entry).equals(next.get(entry))){require(entry.equals(adapter+".class")||entry.startsWith(adapter+"$")&&entry.endsWith(".class"),"Production changed outside the reviewed bridge adapter: "+entry);JsonObject change=new JsonObject();change.addProperty("entry",entry);change.addProperty("beforeSha256",old.get(entry));change.addProperty("afterSha256",next.get(entry));changes.add(change);}
        require(changes.size()>0&&changes.equals(patch.getAsJsonArray("changedEntries"))&&old.get("assets/bloodborne_dw/native_grid/catalog.json").equals(patch.get("catalogSha256").getAsString()),"Adapter patch entry/catalog proof differs");runtimePatch=patch;report.add("runtimePatch",plan.getAsJsonObject("runtimePatch").deepCopy());report.addProperty("runtimePatchScope","exact adapter-only class diff; prior evidence keeps its original production SHA");
    }
    private static Map<String,String> jarInventory(Path path)throws Exception{Map<String,String> values=new TreeMap<>();try(ZipFile zip=new ZipFile(path.toFile())){var entries=zip.entries();while(entries.hasMoreElements()){var entry=entries.nextElement();if(entry.isDirectory())continue;MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=zip.getInputStream(entry)){byte[] buffer=new byte[1<<16];for(int n;(n=stream.read(buffer))>=0;)digest.update(buffer,0,n);}require(values.put(entry.getName(),HexFormat.of().formatHex(digest.digest()))==null,"Duplicate JAR entry");}}return values;}
    private JsonObject readBoundJson(JsonObject binding) throws Exception {Path path=ordinary(Path.of(binding.get("path").getAsString()));require(sha(path).equals(binding.get("sha256").getAsString()),"Continuation evidence bytes changed");return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}

    private void prepare(MinecraftServer server) throws Exception {
        require(ordinary(server.getSavePath(WorldSavePath.ROOT)).equals(ordinary(Path.of(plan.get("targetWorldPath").getAsString()))), "Wrong disposable pilot world");
        Identifier dimension = new Identifier(text(plan,"dimension","minecraft:overworld"));
        world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,dimension)); require(world != null,"Unknown QA dimension");
        JsonArray chunks = plan.getAsJsonArray("sourceChunks"); require(chunks != null && chunks.size()>0 && chunks.size()<=64,"Need 1..64 proved existing chunks");
        for (JsonElement raw:chunks) { JsonArray a=raw.getAsJsonArray(); require(a.size()==2,"Chunk pair required"); allowedChunks.add(ChunkPos.toLong(a.get(0).getAsInt(),a.get(1).getAsInt())); }
        // Standard one-time native requests, as in PartialChecks; never force/ticket chunks.
        for (long chunk:allowedChunks) world.getChunk(ChunkPos.getPackedX(chunk),ChunkPos.getPackedZ(chunk));
        JsonObject stand=plan.getAsJsonObject("stand"); standMin=pos(stand.getAsJsonArray("min")); standMax=pos(stand.getAsJsonArray("max")); standRoot=pos(stand.getAsJsonArray("root"));
        require(standMin.getX()<=standMax.getX()&&standMin.getY()<=standMax.getY()&&standMin.getZ()<=standMax.getZ(),"Invalid empty stand bounds");
        long volume=(long)(standMax.getX()-standMin.getX()+1)*(standMax.getY()-standMin.getY()+1)*(standMax.getZ()-standMin.getZ()+1);
        require(volume>0&&volume<=512000,"Finite empty stand exceeds 512000 cells");
        require(inside(standRoot),"Stand root outside guard");
        actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"NativeGridProbe")) {
            @Override public boolean hasPermissionLevel(int level) { return level<=4; }
            @Override public void sendMessage(Text message,boolean overlay) { if(placementMessages!=null)placementMessages.add(message.getString()); }
        };
        new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),actor);
        actor.changeGameMode(GameMode.CREATIVE); actor.setPosition(standRoot.getX()+6,standRoot.getY(),standRoot.getZ()+6);
        if(plan.has("foreignGuestFixture")){
            require(candidate&&!plan.has("continuation"),"The addressed foreign-guest case is a separate finite candidate action, not a replay of accepted groups");
            foreignGuestFixture=new ForeignGuestFixture(plan.getAsJsonObject("foreignGuestFixture"));
            if(foreignGuestFixture.reopening()){foreignGuestFixture.prepareReopen();report.addProperty("status","RUNNING");save();return;}
        }
        if(continuation&&plan.getAsJsonObject("continuation").has("fixtureRecovery"))recoverOwnedFixture(plan.getAsJsonObject("continuation").getAsJsonObject("fixtureRecovery"));
        for(BlockPos point:BlockPos.iterate(standMin,standMax)) { allowed(point); require(empty(point),"Stand is not genuinely empty: "+point); }
        standInitiallyAir=true;report.addProperty("standAirCellsVerified",volume);report.add("stand",stand.deepCopy());
        report.addProperty("status","RUNNING"); save();
    }

    private void recoverOwnedFixture(JsonObject input) throws Exception {
        require(candidate&&runtimePatch!=null&&inheritedBridgeDoor!=null,"Recovery is limited to the proven terminal-bridge failure with its adapter-only fix");
        JsonObject failed=readBoundJson(input.getAsJsonObject("report"));require(input.getAsJsonObject("report").equals(inheritedBridgeDoor.getAsJsonObject("report")),"Recovery must bind the exact inherited bridge failure");
        JsonObject observation=readBoundJson(input.getAsJsonObject("observation"));require(text(observation,"status","").equals("OBSERVED_EXACT_QA_STAND")&&observation.getAsJsonArray("issues").size()==0&&observation.get("ownerUuid").equals(input.get("ownerUuid"))&&observation.get("report").equals(input.get("report"))&&ordinary(Path.of(observation.get("world").getAsString())).equals(ordinary(Path.of(plan.get("targetWorldPath").getAsString()))),"Recovery lacks its exact read-only twelve-cell observation");
        JsonObject installed=failed.getAsJsonArray("installedWholeObjects").get(0).getAsJsonObject();UUID uuid=UUID.fromString(installed.get("ownerUuid").getAsString());
        require(uuid.equals(UUID.fromString(input.get("ownerUuid").getAsString()))&&!failed.get("standRestored").getAsBoolean(),"Recovery owner/failure role differs");
        List<BlockPos> points=positions(installed.getAsJsonArray("closedOpenCells"));require(points.size()==12,"Only this exact twelve-cell fresh fixture may recover");
        BlockPos background=pos(input.getAsJsonArray("background"));int index=input.get("cell").getAsInt();require(index==1713&&background.equals(standRoot.add(0,0,1))&&points.contains(background),"Recovery background differs from the fresh fixture");
        for(BlockPos point:BlockPos.iterate(standMin,standMax)){allowed(point);require(points.contains(point)||empty(point),"Recovery found data outside the twelve known owned cells: "+point);}
        require(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity,"Recovery root is absent");CompositeBlockEntity root=(CompositeBlockEntity)world.getBlockEntity(standRoot);ObjectInstance.Owner owner=root.resident();
        require(owner!=null&&owner.instanceId().equals(uuid)&&CompositeData.pos(owner.root()).equals(standRoot),"Recovery root belongs to another owner");
        BlockState state=world.getBlockState(standRoot);NbtCompound payload=root.payload();require(canonicalState(Registries.BLOCK.getId(state.getBlock()).toString(),state).equals(installed.get("state"))&&Base64.getEncoder().encodeToString(CompositeData.bytes(payload)).equals(installed.get("payloadNbt").getAsString()),"Recovery root state/private payload changed");
        ObjectInstance expected=CompositeRuntime.instance(world,standRoot,state,uuid,payload);Set<BlockPos> expectedPoints=new HashSet<>();for(var offset:expected.cells().keySet())expectedPoints.add(standRoot.add(offset.x(),offset.y(),offset.z()));require(expectedPoints.equals(new HashSet<>(points)),"Recovery original full footprint differs");
        Map<BlockPos,JsonObject> before=new LinkedHashMap<>();
        for(BlockPos point:points){require(inside(point)&&world.isChunkLoaded(point)&&world.getBlockEntity(point) instanceof CompositeBlockEntity,"Recovery cell is outside its loaded owned fixture");CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(point);
            require(point.equals(standRoot)?owner.equals(entity.resident()):entity.resident()==null&&world.getBlockState(point).isOf(CompositeArchitecture.CELL),"Recovery resident/physical host differs");
            require((Integer)call(entity,"nativeGridBase")== (point.equals(background)?index:-1),"Recovery has another background reference");
            require(entity.createNbt().getKeys().stream().allMatch(key->Set.of("payload","resident",CompositeOwnerNbt.KEY,"owners","NativeGridBase").contains(key)),"Recovery found unknown typed BE data");
            var entries=CompositeRuntime.contributions(world,point);require(entries.size()==1&&entries.get(0).owner().equals(owner)&&entries.get(0).rootData().equals(expected.rootData())&&entries.get(0).shape().equals(expected.at(CompositeData.cell(point))),"Recovery found foreign or changed contribution data");before.put(point.toImmutable(),cell(point));
        }
        JsonObject recovery=new JsonObject();recovery.add("binding",input.deepCopy());recovery.add("before",snapshots(before));recovery.addProperty("preconditionsExact",true);report.add("ownedFixtureRecovery",recovery);save();
        TransactionCore.Result result=CompositeRuntime.remove(world,owner,null,false);recovery.add("removalResult",transaction(result));save();require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Owned fixture recovery removal did not commit: "+result.reason());
        Class<?> catalog=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog");BlockState base=(BlockState)catalog.getMethod("state",int.class).invoke(null,index);
        for(BlockPos point:points)require(point.equals(background)?world.getBlockState(point).equals(base)&&world.getBlockEntity(point)==null&&CompositeRuntime.contributions(world,point).isEmpty():empty(point),"Owned recovery did not restore exactly the twelve expected cells");
        require(new FabricCompositeWorld(world,Map.of()).read(CompositeData.cell(background)).kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.FOREIGN,"Cold recovery adapter must expose the real plain native base as FOREIGN");
        actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);recovery.addProperty("ordinaryBackgroundBreakApiReturn",actor.interactionManager.tryBreakBlock(background));require(standEmpty(),"Recovery did not restore the complete originally AIR stand");
        recovery.addProperty("standRestored",true);recovery.addProperty("scope","only exact fresh test-owned UUID and its one attached background; original city data untouched");save();
    }

    private void run() throws Exception {
        if(continuation){
            // Re-establish within-run read-only invariants, without repeating
            // the successful functional groups from the pinned failed run.
            for(JsonElement raw:plan.getAsJsonArray("protected"))watch(pos(raw.getAsJsonObject().getAsJsonArray("pos")));
            for(JsonElement raw:plan.getAsJsonArray("windows"))for(BlockPos point:positions(raw.getAsJsonObject().getAsJsonArray("positions")))watch(point);
            for(JsonElement raw:plan.getAsJsonArray("luminance"))watch(pos(raw.getAsJsonObject().getAsJsonArray("pos")));
            watch(BlockPos.ofFloored(vec(plan.getAsJsonObject("ladder").getAsJsonArray("player"))));watch(pos(plan.getAsJsonObject("staticCell").getAsJsonArray("source")));
        }else{
        JsonArray protectedCells=plan.getAsJsonArray("protected"); require(protectedCells!=null&&protectedCells.size()>0,"Protected V12 witnesses required");
        for(JsonElement raw:protectedCells) {
            JsonObject row=raw.getAsJsonObject();BlockPos point=pos(row.getAsJsonArray("pos")); watch(point);
            if(candidate) require(row.has("reference"),"Candidate protected witness needs baseline state/NBT/owners reference");
            if(row.has("reference"))check(protectedMatches(cell(point),row.getAsJsonObject("reference"),row),"protected source checkpoint matches at "+point);
        }
        JsonArray inputWindows=plan.getAsJsonArray("windows");require(inputWindows!=null&&inputWindows.size()>0&&inputWindows.size()<=8,"Need 1..8 actual exact-shape windows");
        for(JsonElement raw:inputWindows) readWindow(raw.getAsJsonObject());
        JsonArray light=plan.getAsJsonArray("luminance");require(light!=null&&light.size()>0&&light.size()<=16,"Luminance witnesses required");
        JsonArray lights=new JsonArray();for(JsonElement raw:light){JsonObject row=raw.getAsJsonObject();BlockPos point=pos(row.getAsJsonArray("pos"));watch(point);int value=world.getBlockState(point).getLuminance();check(value==row.get("expected").getAsInt(),"source luminance at "+point);JsonObject result=cell(point);result.add("pos",coordinates(point));result.addProperty("luminance",value);result.addProperty("blockLight",world.getLightLevel(LightType.BLOCK,point));result.addProperty("skyLight",world.getLightLevel(LightType.SKY,point));lights.add(result);}report.add("luminance",lights);
        JsonObject ladder=plan.getAsJsonObject("ladder");require(ladder!=null,"One real ladder witness required");Vec3d player=vec(ladder.getAsJsonArray("player"));allowed(BlockPos.ofFloored(player));actor.setPosition(player);if(candidate)checkMigratedLadder(ladder);check(actor.isClimbing()==ladder.get("expected").getAsBoolean(),"real player ladder climb detection");
        if(candidate) testStatic(plan.getAsJsonObject("staticCell"));
        else {JsonObject skip=new JsonObject();skip.addProperty("kind","staticCell");skip.addProperty("status","BASELINE_HAS_ORIGINAL_OBJECT_DESTRUCTION");actions.add(skip);}
        }
        JsonArray objects=plan.getAsJsonArray("wholeObjects");require(objects!=null&&objects.size()==3,"Exactly one door, tree and statue witness required");Set<String> kinds=new HashSet<>();
        for(JsonElement raw:objects){JsonObject row=raw.getAsJsonObject();String kind=row.get("kind").getAsString();require(Set.of("door","tree","statue").contains(kind)&&kinds.add(kind),"Duplicate/unknown functional witness");testWhole(row,kind);}
        check(standEmpty(),"empty stand restored exactly by normal removal APIs");standRestored=true;
        for(var entry:watched.entrySet())check(cell(entry.getKey()).equals(entry.getValue()),"read-only original state/NBT/owners unchanged at "+entry.getKey());
        report.add("originalWitnesses",snapshots(watched));report.addProperty("newNativeApiHardLinked",false);report.addProperty("rawSetBlockCalls",0);
    }

    private void checkMigratedLadder(JsonObject input) throws Exception {
        require(input.has("migratedCell"),"Candidate ladder needs its final composed placement and source-owner binding");
        JsonObject bound=input.getAsJsonObject("migratedCell");BlockPos point=pos(bound.getAsJsonArray("pos"));watch(point);
        require(point.equals(actor.getBlockPos())&&bound.get("sourceOwnerSelectedForRemoval").getAsBoolean(),"Ladder assertion must use the declared actor cell and a selected original owner");
        UUID selected=UUID.fromString(bound.get("originalOwnerUuid").getAsString());
        require(bound.has("source")&&bound.has("pose")&&bound.has("registryId")&&!bound.get("pose").getAsString().isEmpty(),"Ladder source UUID, pose and root must be bound by the input builder");
        JsonObject result=new JsonObject();result.add("binding",bound.deepCopy());result.add("pos",coordinates(point));result.add("actual",cell(point));report.add("migratedLadder",result);
        Class<?> catalog=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog");
        try(InputStream stream=catalog.getResourceAsStream("/assets/bloodborne_dw/native_grid/catalog.json")){
            require(stream!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(bound.get("catalogSha256").getAsString()),"Ladder assertion binds the actual loaded catalog");
        }
        int index=bound.get("cell").getAsInt();Object compiled=catalog.getMethod("cell",int.class).invoke(null,index);
        require(call(compiled,"key").equals(bound.get("cellKey").getAsString()),"Ladder catalog key differs from its composed placement");
        VoxelShape climbing=(VoxelShape)call(compiled,"climbing");
        check(!climbing.isEmpty()&&same(climbing,shape(bound.getAsJsonArray("climbing"))),"migrated ladder has the exact nonempty prepared shared climbing shape");
        BlockState state=world.getBlockState(point);String mode=bound.get("mode").getAsString();
        var contributions=CompositeRuntime.contributions(world,point);
        if(mode.equals("plain")){
            BlockState expected=(BlockState)catalog.getMethod("state",int.class).invoke(null,index);
            check(nativeGrid(state)&&state.equals(expected)&&world.getBlockEntity(point)==null&&contributions.isEmpty(),"ladder witness is its exact ordinary NativeGrid state without BE or owners");
        }else{
            require(mode.equals("bridge"),"Unknown migrated ladder placement mode");
            require(canonicalState(Registries.BLOCK.getId(state.getBlock()).toString(),state).equals(bound.getAsJsonObject("hostState")),"Ladder bridge physical host differs from the exact composed host state");
            require(world.getBlockEntity(point) instanceof CompositeBlockEntity,"Ladder bridge is not its required existing composite BE");
            CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(point);
            Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridBridge").getMethod("validate",int.class,BlockState.class).invoke(null,index,state);
            check((Integer)call(entity,"nativeGridBase")==index&&entity.createNbt().contains("NativeGridBase",3)&&entity.createNbt().getInt("NativeGridBase")==index,"ladder bridge uses only the exact bound NativeGridBase type");
            Set<UUID> retained=new HashSet<>();for(JsonElement raw:bound.getAsJsonArray("retainedOwnerUuids"))retained.add(UUID.fromString(raw.getAsString()));
            Set<UUID> actual=new HashSet<>();for(var contribution:contributions)actual.add(contribution.owner().instanceId());
            if(entity.resident()!=null)actual.add(entity.resident().instanceId());
            check(!retained.isEmpty()&&!retained.contains(selected)&&actual.equals(retained),"ladder bridge contains precisely its bound original retained owners");
            check((entity.resident()!=null)==bound.get("retainedResident").getAsBoolean(),"ladder bridge retained-resident role agrees with the final plan");
        }
        check(contributions.stream().noneMatch(entry->entry.owner().instanceId().equals(selected)),"selected original ladder owner is absent from the migrated witness");
        Box body=actor.getBoundingBox();boolean intersects=climbing.getBoundingBoxes().stream().anyMatch(box->box.offset(point).intersects(body));
        check(intersects,"prepared migrated ladder climbing shape intersects the actual player body");
        result.addProperty("mode",mode);result.addProperty("cell",index);result.add("preparedClimbing",boxes(climbing));result.add("actorBody",JSON.toJsonTree(List.of(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ)));result.addProperty("preparedClimbIntersectsActor",intersects);result.addProperty("selectedOwnerAbsent",true);
    }

    private void readWindow(JsonObject input) {
        String key=input.get("key").getAsString();BlockPos origin=pos(input.getAsJsonArray("origin"));List<BlockPos> positions=positions(input.getAsJsonArray("positions"));
        for(BlockPos point:positions)watch(point);
        VoxelShape collision=union(positions,origin,true),outline=union(positions,origin,false);
        JsonObject result=new JsonObject();result.addProperty("key",key);result.add("origin",coordinates(origin));result.add("positions",JSON.toJsonTree(positions.stream().map(p->List.of(p.getX(),p.getY(),p.getZ())).toList()));result.add("collision",boxes(collision));result.add("outline",boxes(outline));
        if(candidate)require(input.has("referenceCollision")&&input.has("referenceOutline"),"Candidate exact window needs baseline collision and outline: "+key);
        if(input.has("referenceCollision"))check(same(collision,shape(input.getAsJsonArray("referenceCollision"))),"exact collision union "+key);
        if(input.has("referenceOutline"))check(same(outline,shape(input.getAsJsonArray("referenceOutline"))),"exact outline union "+key);
        for(BlockPos point:positions){BlockState state=world.getBlockState(point);check(same(state.getCollisionShape(world,point),state.getCollisionShape(world,point,ShapeContext.absent())),"cached/contextual collision agrees at "+point);}
        windows.add(result);
    }

    private void testStatic(JsonObject input) throws Exception {
        require(input!=null,"Candidate needs one static-cell destruction witness");BlockPos source=pos(input.getAsJsonArray("source"));watch(source);BlockState sourceState=world.getBlockState(source);
        require(nativeGrid(sourceState)&&world.getBlockEntity(source)==null,"Selected cell is not native grid/no BE");require(nativeMembers(sourceState)==null,"Static-cell witness is a whole-object member");
        require(sourceState.getFluidState().isEmpty(),"Choose a dry static-cell fixture for a reversible empty stand");
        ItemStack stack=sourceState.getBlock().getPickStack(world,source,sourceState);require(stack.getItem() instanceof BlockItem,"Static cell pick is not a placement item");
        BlockPos neighbor=standRoot.east();guard(List.of(standRoot,neighbor));activeCleanup=()->{for(BlockPos point:List.of(standRoot,neighbor))if(nativeGrid(world.getBlockState(point)))world.breakBlock(point,false);};
        try {
            placeItem(stack.copy(),standRoot);placeItem(stack.copy(),neighbor);
            check(world.getBlockState(standRoot).equals(sourceState)&&world.getBlockState(neighbor).equals(sourceState),"pick/place retains exact compiled static state");
            JsonObject retained=cell(neighbor);breakByRay(standRoot);
            check(world.getBlockState(standRoot).isAir()&&world.getBlockEntity(standRoot)==null,"one static-cell break removes its cell");
            check(cell(neighbor).equals(retained),"one static-cell break preserves adjacent independent cell");
            JsonObject result=new JsonObject();result.addProperty("kind","staticCell");result.addProperty("status","PASS");result.add("source",coordinates(source));result.addProperty("picked",stack.toString());actions.add(result);
        } finally { cleanupActive(); check(empty(standRoot)&&empty(neighbor),"static fixture returns touched cells to original empty state"); }
    }

    private void testWhole(JsonObject input,String kind) throws Exception {
        refreshStandChunks("whole "+kind);
        BlockPos requested=pos(input.getAsJsonArray("source"));Source source=source(input,requested);watch(source.root());
        for(BlockPos offset:source.offsets())watch(source.root().add(offset));
        // The whole finite stand is AIR/no-BE/no-owner before the real item is
        // used. Ordinary picked items intentionally discard source installation
        // privileges; their actual installed payload is the footprint authority.
        check(standEmpty(),"whole "+kind+" begins in the complete proved AIR/no-BE/no-owner stand");
        List<BlockPos> footprint=new ArrayList<>();if(source.nativeGrid())for(BlockPos offset:source.offsets())footprint.add(standRoot.add(offset));
        guard(footprint);final List<BlockPos> guardedFootprint=footprint;
        ItemStack picked=source.owner()==null?source.state().getBlock().getPickStack(world,source.root(),source.state()):CompositeRuntime.pick(world,source.owner());
        check(!picked.isEmpty()&&picked.getItem() instanceof BlockItem,"whole "+kind+" has a usable pick/place item");
        final ObjectInstance.Owner[] installed={null};
        final BridgeFixture[] bridge={null};
        activeCleanup=()->{if(!source.nativeGrid()){ObjectInstance.Owner owner=installed[0];if(owner==null&&world.getBlockEntity(standRoot) instanceof CompositeBlockEntity entity)owner=entity.resident();if(owner!=null){TransactionCore.Result result=CompositeRuntime.remove(world,owner,null,false);JsonArray cleanups=report.has("cleanupAttempts")?report.getAsJsonArray("cleanupAttempts"):new JsonArray();JsonObject cleanup=new JsonObject();cleanup.addProperty("ownerUuid",owner.instanceId().toString());cleanup.add("result",transaction(result));cleanups.add(cleanup);report.add("cleanupAttempts",cleanups);save();UUID cleanupOwner=owner.instanceId();require(guardedFootprint.stream().allMatch(p->CompositeRuntime.contributions(world,p).stream().noneMatch(c->c.owner().instanceId().equals(cleanupOwner))),"Owned cleanup transaction left its fixture contributions: "+result.reason());}}else for(BlockPos point:guardedFootprint)if(nativeGrid(world.getBlockState(point)))world.breakBlock(point,false);if(bridge[0]!=null&&world.getBlockState(bridge[0].pos()).equals(bridge[0].background())&&CompositeRuntime.contributions(world,bridge[0].pos()).isEmpty())world.breakBlock(bridge[0].pos(),false);};
        try{
            FabricCompositeWorld releasedRollbackProbe=null;
            if(source.nativeGrid())placeItem(picked.copy(),standRoot);
            else{
                actor.setYaw(source.state().get(CompositeRootBlock.ROTATION)*45F);actor.setHeadYaw(actor.getYaw());
                placeItem(picked.copy(),standRoot);
                require(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity,"Existing picked item did not install its local root");
                installed[0]=((CompositeBlockEntity)world.getBlockEntity(standRoot)).resident();require(installed[0]!=null,"Placed retained object lacks its local owner");
                require(!installed[0].instanceId().equals(source.owner().instanceId()),"Copied item reused original object's identity");
            }
            if(source.nativeGrid())for(BlockPos offset:source.offsets())check(world.getBlockState(standRoot.add(offset)).equals(world.getBlockState(source.root().add(offset))),"whole statue pick/place retains exact compiled member at "+offset);
            if(installed[0]!=null){
                var entry=CompositeRuntime.contributions(world,standRoot).stream().filter(c->c.owner().equals(installed[0])).findFirst().orElseThrow();
                BlockState installedState=CompositeData.state(entry.rootData());NbtCompound installedPayload=CompositeData.nbt(entry.rootData().blockEntityNbt());
                List<BlockPos> actual=new ArrayList<>();addFootprint(actual,installedState,installedPayload);if(installedState.contains(CompositeRootBlock.OPEN))addFootprint(actual,installedState.cycle(CompositeRootBlock.OPEN),installedPayload);
                JsonObject installedRecord=new JsonObject();installedRecord.addProperty("kind",kind);installedRecord.addProperty("ownerUuid",installed[0].instanceId().toString());installedRecord.add("state",canonicalState(Registries.BLOCK.getId(installedState.getBlock()).toString(),installedState));
                installedRecord.addProperty("payloadNbt",Base64.getEncoder().encodeToString(CompositeData.bytes(installedPayload)));installedRecord.addProperty("payloadSnbt",installedPayload.toString());installedRecord.add("closedOpenCells",JSON.toJsonTree(actual.stream().distinct().map(p->List.of(p.getX(),p.getY(),p.getZ())).toList()));
                installedRecord.addProperty("initialAirProof","complete finite stand checked immediately before ordinary item placement");
                JsonArray installedRecords=report.has("installedWholeObjects")?report.getAsJsonArray("installedWholeObjects"):new JsonArray();installedRecords.add(installedRecord);report.add("installedWholeObjects",installedRecords);save();
                footprint.clear();footprint.addAll(new LinkedHashSet<>(actual));
                for(BlockPos point:footprint){require(inside(point),"Actual manual closed/open footprint escaped the proved finite AIR stand at "+point);allowed(point);require(world.isChunkLoaded(point),"Actual manual footprint cell is not loaded: "+point);}
            }
            if(input.has("requireCrossChunk")&&input.get("requireCrossChunk").getAsBoolean())check(footprint.stream().map(p->new ChunkPos(p).toLong()).distinct().count()>1,"whole "+kind+" actual installed fixture spans a chunk boundary");
            VoxelShape closed=union(footprint,standRoot,true),closedOutline=union(footprint,standRoot,false);
            VoxelShape openCollision=null,openOutline=null;
            check(!closedOutline.isEmpty(),"whole "+kind+" retains selectable authored parts");
            if(kind.equals("door")){
                require(!source.nativeGrid(),"Openable doors must remain their existing complete object");
                if(inheritedAirDoor!=null){
                    CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(standRoot);BlockState state=world.getBlockState(standRoot);NbtCompound payload=entity.payload();
                    require(canonicalState(Registries.BLOCK.getId(state.getBlock()).toString(),state).equals(inheritedAirDoor.get("manualState"))&&Base64.getEncoder().encodeToString(CompositeData.bytes(payload)).equals(inheritedAirDoor.get("manualPayloadNbt").getAsString()),"Ordinary setup differs from the exact previously exercised AIR door");
                    var current=CompositeRuntime.instance(world,standRoot,state,installed[0].instanceId(),payload);var opened=CompositeRuntime.instance(world,standRoot,state.cycle(CompositeRootBlock.OPEN),installed[0].instanceId(),payload);
                    check(same(closed,instanceUnion(current,true))&&same(closedOutline,instanceUnion(current,false)),"fresh closed-door setup matches its actual prepared instance collision and outline");
                    openCollision=instanceUnion(opened,true);openOutline=instanceUnion(opened,false);require(!same(openCollision,closed),"Actual prepared manual open/closed collision unexpectedly stopped moving");
                    report.addProperty("airDoorMotionReused",true);report.addProperty("openReferenceOrigin","actual installed state/payload -> existing CompositeRuntime.instance; real AIR right-click checks inherited by exact report hash");
                }else{
                boolean before=world.getBlockState(standRoot).get(CompositeRootBlock.OPEN);useByRay(footprint);
                check(world.getBlockState(standRoot).get(CompositeRootBlock.OPEN)!=before,"door right click changes one whole door open state");
                VoxelShape open=union(footprint,standRoot,true);openCollision=open;openOutline=union(footprint,standRoot,false);check(!same(open,closed),"door open collision moves with the leaf");
                useByRay(footprint);check(world.getBlockState(standRoot).get(CompositeRootBlock.OPEN)==before&&same(union(footprint,standRoot,true),closed),"door second right click restores closed-state exact collision");
                }
            }
            if(candidate&&kind.equals("door")){
                // Exercise the actual persistent lower-helper background once.
                // The bound background has exact EMPTY collision and outline.
                require(input.has("bridgeFixture"),"Candidate door needs the one bound NativeGridBase fixture");
                require(openCollision!=null&&openOutline!=null,"The preceding AIR door cycle must supply exact open-shape references");
                bridge[0]=attachBridge(input.getAsJsonObject("bridgeFixture"),source,installed[0],guardedFootprint,bridge);
                BlockPos offset=bridge[0].pos().subtract(standRoot);
                VoxelShape expected=VoxelShapes.union(closed,bridge[0].collision().offset(offset.getX(),offset.getY(),offset.getZ()));
                VoxelShape expectedOutline=VoxelShapes.union(closedOutline,bridge[0].outline().offset(offset.getX(),offset.getY(),offset.getZ()));
                closed=union(footprint,standRoot,true);closedOutline=union(footprint,standRoot,false);
                check(same(closed,expected)&&same(closedOutline,expectedOutline),"candidate bridge is the exact shared background plus retained closed-door shape");
                if(inheritedBridgeDoor!=null){report.addProperty("bridgeDoorMotionReused",true);report.addProperty("bridgeMotionScope","seventeen exact prior setup/open/close checks inherited through the verified adapter-only patch; terminal detach and rollback are tested now");}
                else{
                List<BlockPos> aboveFloor=footprint.stream().filter(p->p.getY()>bridge[0].pos().getY()).toList();
                boolean before=world.getBlockState(standRoot).get(CompositeRootBlock.OPEN);useByRay(aboveFloor);
                check(world.getBlockState(standRoot).get(CompositeRootBlock.OPEN)!=before,"bridged door actual right click opens above its persistent floor background");
                check(same(union(footprint,standRoot,true),VoxelShapes.union(openCollision,bridge[0].collision().offset(offset.getX(),offset.getY(),offset.getZ())))&&same(union(footprint,standRoot,false),VoxelShapes.union(openOutline,bridge[0].outline().offset(offset.getX(),offset.getY(),offset.getZ()))),"bridged open door has exact AIR-open collision and outline plus the unchanged shared background");
                check(!same(union(footprint,standRoot,true),closed)&&!same(union(footprint,standRoot,false),closedOutline),"bridged open/closed collision and outline unions actually change with the leaf");
                checkBridge(bridge[0],installed[0],"after actual door opening");
                useByRay(aboveFloor);
                check(world.getBlockState(standRoot).get(CompositeRootBlock.OPEN)==before&&same(union(footprint,standRoot,true),closed)&&same(union(footprint,standRoot,false),closedOutline),"bridged second right click restores exact closed collision and outline union");
                checkBridge(bridge[0],installed[0],"after actual door closing");
                }
                if(runtimePatch!=null)releasedRollbackProbe=probeTerminalBridgeRollback(installed[0],bridge[0],footprint);
            }
            List<BlockPos> doorTargets=bridge[0]==null?footprint:footprint.stream().filter(p->p.getY()!=bridge[0].pos().getY()).toList();
            BlockPos hit=selectable(kind.equals("door")?doorTargets:footprint,input.has("requireCrossChunk")&&input.get("requireCrossChunk").getAsBoolean());breakByRay(hit);
            for(BlockPos point:footprint)if(bridge[0]!=null&&point.equals(bridge[0].pos()))check(world.getBlockState(point).equals(bridge[0].background())&&world.getBlockEntity(point)==null,"whole door break retains only its exact ordinary shared background");else check(empty(point),"whole "+kind+" break clears every authored/helper cell at "+point);
            if(bridge[0]!=null&&releasedRollbackProbe!=null){var at=CompositeData.cell(bridge[0].pos());check(releasedRollbackProbe.read(at).kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.FOREIGN&&new FabricCompositeWorld(world,Map.of()).read(at).kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.FOREIGN,"after terminal commit both released and cold adapters expose the actual plain native base as FOREIGN, never cached AIR");}
            if(installed[0]!=null)for(BlockPos point:footprint)check(CompositeRuntime.contributions(world,point).stream().noneMatch(c->c.owner().instanceId().equals(installed[0].instanceId())),"whole "+kind+" break retires its local contributions");
            if(bridge[0]!=null){if(bridge[0].outline().isEmpty()){actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);check(actor.interactionManager.tryBreakBlock(bridge[0].pos()),"server interaction API accepts independent background break; exact original outline is EMPTY so no ray assertion applies");}else breakByRay(bridge[0].pos());check(empty(bridge[0].pos()),"ordinary independent background break returns the bridge fixture cell to AIR");}
            if(kind.equals("door")&&inheritedAirDoor!=null)testAssemblyParts(source,picked,footprint);
            JsonObject result=new JsonObject();result.addProperty("kind",kind);result.addProperty("status","PASS");result.add("source",coordinates(source.root()));result.addProperty("picked",picked.toString());result.addProperty("cells",footprint.size());result.addProperty("originalSourceCells",source.offsets().size());result.add("collision",boxes(closed));result.add("outline",boxes(closedOutline));result.add("breakCell",coordinates(hit));result.addProperty("placementPath","registered picked item.useOnBlock -> BlockItem.place");actions.add(result);
            if(kind.equals("door")&&inheritedAirDoor!=null)result.add("representativePreservedAssembly",inheritedAirDoor.getAsJsonObject("representativeAssembly").deepCopy());
            if(bridge[0]!=null){JsonObject bound=new JsonObject();bound.addProperty("cell",bridge[0].index());bound.add("pos",coordinates(bridge[0].pos()));bound.add("helperOffset",coordinates(bridge[0].pos().subtract(standRoot)));bound.addProperty("persistentInBothStates",true);bound.addProperty("installedOnFreshOwnedHelperOnly",true);bound.addProperty("openCloseScope",inheritedAirDoor!=null?"prior real AIR motion inherited by exact report hash; one actual open/close cycle with the exact EMPTY-shape lower-helper background":"normal AIR cycle followed by actual open/close with the bound persistent lower-helper background");bound.addProperty("backgroundRestorePath","existing transaction adapter across open/close and whole break; final independent ordinary break");bound.addProperty("independentBreakMode",bridge[0].outline().isEmpty()?"server interaction API; ray not applicable: exact original outline EMPTY":"native selection ray + server interaction API");result.add("bridgeFixture",bound);}
        }finally{cleanupActive();check(guardedFootprint.stream().allMatch(this::empty)&&standEmpty(),kind+" fixture returns its actual cells and complete finite stand exactly");}
    }

    private void testAssemblyParts(Source source,ItemStack mainItem,List<BlockPos> provedFootprint) throws Exception {
        require(provedFootprint.size()>0&&provedFootprint.size()<=64,"Representative assembly part loop must remain finite");
        JsonArray parts=new JsonArray();report.add("representativeAssemblyParts",parts);int accessible=0;
        for(BlockPos target:provedFootprint){
            refreshStandChunks("representative assembly part setup");require(standEmpty(),"Each representative part starts from the complete proved AIR stand");
            ObjectInstance.Owner owner=null;JsonObject row=new JsonObject();row.add("requestedPart",coordinates(target));parts.add(row);
            try{
                actor.setYaw(source.state().get(CompositeRootBlock.ROTATION)*45F);actor.setHeadYaw(actor.getYaw());placeItem(mainItem.copy(),standRoot);
                require(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity,"Representative part setup lacks its ordinary root");
                CompositeBlockEntity root=(CompositeBlockEntity)world.getBlockEntity(standRoot);owner=root.resident();require(owner!=null&&!owner.instanceId().equals(source.owner().instanceId()),"Representative part setup reused the source identity");
                BlockState state=world.getBlockState(standRoot);NbtCompound payload=root.payload();
                require(canonicalState(Registries.BLOCK.getId(state.getBlock()).toString(),state).equals(inheritedAirDoor.get("manualState"))&&Base64.getEncoder().encodeToString(CompositeData.bytes(payload)).equals(inheritedAirDoor.get("manualPayloadNbt").getAsString()),"Per-part ordinary placement changed the exact confirmed whole pose");
                List<BlockPos> actual=new ArrayList<>();addFootprint(actual,state,payload);addFootprint(actual,state.cycle(CompositeRootBlock.OPEN),payload);actual=new ArrayList<>(new LinkedHashSet<>(actual));
                for(BlockPos point:actual){require(inside(point)&&world.isChunkLoaded(point),"Per-part whole footprint escaped its proved loaded stand");allowed(point);}
                require(new HashSet<>(actual).equals(new HashSet<>(provedFootprint))&&actual.contains(target),"Per-part ordinary placement changed the actual whole footprint");
                BlockState partState=world.getBlockState(target);ItemStack picked=partState.getBlock().getPickStack(world,target,partState);
                check(ItemStack.areEqual(picked,mainItem),"representative assembly part picks the same single main item at "+target);row.addProperty("pickedSameMainItem",true);row.addProperty("ownerUuid",owner.instanceId().toString());
                if(partState.getOutlineShape(world,target).isEmpty()){row.addProperty("accessible",false);row.addProperty("reason","exact actual outline EMPTY; no ray or destruction PASS claimed");continue;}
                BlockHitResult hit=exactPartRay(target,row);
                if(hit==null){row.addProperty("accessible",false);row.addProperty("reason","occluded under the declared authored-box-centres by six-side ray search; no destruction PASS claimed");continue;}
                row.addProperty("accessible",true);row.add("actualRayHit",coordinates(hit.getBlockPos()));row.addProperty("raySearch","each authored outline-box centre from six cardinal sides at three blocks");
                performBreak(target,hit);
                UUID removed=owner.instanceId();check(actual.stream().allMatch(this::empty)&&actual.stream().allMatch(p->CompositeRuntime.contributions(world,p).stream().noneMatch(c->c.owner().instanceId().equals(removed))),"representative assembly break from this part clears the complete whole and its owner entries at "+target);
                row.addProperty("wholeRemoved",true);accessible++;
            }finally{
                if(owner!=null)CompositeRuntime.remove(world,owner,null,false);
                check(standEmpty(),"representative assembly part fixture restores the complete AIR stand after "+target);save();
            }
        }
        require(accessible>0,"Representative assembly has no actually selectable part");report.addProperty("representativeAccessiblePartsBroken",accessible);
    }

    private FabricCompositeWorld probeTerminalBridgeRollback(ObjectInstance.Owner owner,BridgeFixture bridge,List<BlockPos> footprint){
        Map<BlockPos,JsonObject> before=new LinkedHashMap<>();for(BlockPos point:footprint)before.put(point,cell(point));
        var target=CompositeData.cell(bridge.pos());boolean[] reached={false};JsonObject result=new JsonObject();result.add("before",snapshots(before));report.add("terminalBridgeRollbackProbe",result);save();
        FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of()){
            @Override public boolean writeSilently(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell cell,dev.dreamwalker.bloodbornedw.runtime.CellSnapshot next){
                boolean written=super.writeSilently(cell,next);
                if(cell.equals(target)&&next.kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.AIR){
                    require(world.getBlockState(bridge.pos()).equals(bridge.background())&&world.getBlockEntity(bridge.pos())==null&&CompositeRuntime.contributions(world,bridge.pos()).isEmpty(),"Rollback probe terminal write did not preserve the exact physical background");
                    require(super.read(cell).equals(next),"Terminal write is not projected as logical AIR for exact transaction verification");reached[0]=true;result.addProperty("physicalNativeBaseObserved",true);result.addProperty("logicalTerminalAirObserved",true);save();return false;
                }
                return written;
            }
        };
        var preparation=CompositeRuntime.core().removal(access,owner);require(preparation.accepted(),"Owned rollback probe removal preflight refused: "+preparation.reason());
        TransactionCore.Result transaction=CompositeRuntime.execute(access,preparation);result.add("result",transaction(transaction));save();
        check(reached[0]&&transaction.outcome()==TransactionCore.Outcome.ROLLED_BACK&&transaction.reason().startsWith("write_rejected:"),"controlled failure after the real terminal base write rolls the transaction back");
        check(before.entrySet().stream().allMatch(entry->cell(entry.getKey()).equals(entry.getValue())),"terminal rollback restores all twelve exact states, typed BE payloads, background references and owner entries");
        check(access.read(target).kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.HELPER,"released rollback adapter reads its restored real helper, with no terminal AIR projection");result.addProperty("restoredExactly",true);save();return access;
    }

    private BlockHitResult exactPartRay(BlockPos target,JsonObject result){
        JsonArray attempts=new JsonArray();result.add("rayAttempts",attempts);
        for(Box box:world.getBlockState(target).getOutlineShape(world,target).getBoundingBoxes())for(Direction side:Direction.values()){
            Vec3d center=box.getCenter().add(target.getX(),target.getY(),target.getZ()),delta=new Vec3d(side.getOffsetX(),side.getOffsetY(),side.getOffsetZ()),eye=center.add(delta.multiply(3));
            BlockHitResult hit=world.raycast(new RaycastContext(eye,center.subtract(delta.multiply(.25)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));
            JsonObject attempt=new JsonObject();attempt.addProperty("side",side.asString());attempt.addProperty("hitType",hit.getType().name());if(hit.getType()==HitResult.Type.BLOCK)attempt.add("hitBlockPos",coordinates(hit.getBlockPos()));attempts.add(attempt);result.addProperty("testedRays",attempts.size());
            if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(target))continue;
            actor.setPosition(eye.x,eye.y-actor.getStandingEyeHeight(),eye.z);Vec3d direction=center.subtract(eye);actor.setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));actor.setHeadYaw(actor.getYaw());actor.setPitch((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));return hit;
        }
        return null;
    }

    private Source source(JsonObject input,BlockPos requested) throws Exception {
        allowed(requested);BlockState state=world.getBlockState(requested);
        if(nativeGrid(state)){
            require(text(input,"kind","").equals("statue"),"Only whole statue may use native template here");
            Object membership=nativeMembers(state);require(membership!=null,"Statue lacks whole template membership");
            BlockPos memberOffset=(BlockPos)call(membership,"offset");Object object=call(membership,"object");BlockPos root=requested.subtract(memberOffset);
            Class<?> catalog=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog");
            List<BlockPos> offsets=new ArrayList<>();for(Object member:(List<?>)call(object,"cells")){BlockPos offset=(BlockPos)call(member,"offset");offsets.add(offset);BlockPos at=root.add(offset);allowed(at);BlockState expected=(BlockState)catalog.getMethod("state",int.class).invoke(null,(Integer)call(member,"cell"));require(world.getBlockState(at).equals(expected),"Whole source statue has a missing/mismatched template member at "+at);}
            for(BlockPos offset:offsets){BlockPos at=root.add(offset);allowed(at);require(world.getBlockState(at).getFluidState().isEmpty(),"Choose a dry reversible whole-statue fixture");require(world.getBlockEntity(at)==null,"Native statue unexpectedly has a BE");}
            return new Source(root,world.getBlockState(root),new NbtCompound(),null,List.copyOf(offsets),true);
        }
        require(input.has("ownerUuid"),"Retained object needs its exact bound source owner UUID");UUID uuid=UUID.fromString(input.get("ownerUuid").getAsString());
        var contribution=CompositeRuntime.contributions(world,requested).stream().filter(c->c.owner().instanceId().equals(uuid)).findFirst().orElseThrow(()->new IllegalStateException("Bound source owner is absent"));
        BlockPos root=CompositeData.pos(contribution.owner().root());allowed(root);BlockState sourceState=CompositeData.state(contribution.rootData());require(sourceState.getBlock() instanceof CompositeRootBlock,"Retained witness is not an existing composite root");
        NbtCompound payload=CompositeData.nbt(contribution.rootData().blockEntityNbt());ObjectInstance instance=CompositeRuntime.instance(world,root,sourceState,uuid,payload);
        List<BlockPos> offsets=instance.cells().keySet().stream().map(c->new BlockPos(c.x(),c.y(),c.z())).toList();
        return new Source(root,sourceState,payload,contribution.owner(),offsets,false);
    }
    private static Object nativeMembers(BlockState state) throws Exception {
        if(!nativeGrid(state))return null;
        Class<?> catalog=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog");
        Object cell=catalog.getMethod("cell",BlockState.class).invoke(null,state);int index=(Integer)call(cell,"index");
        return catalog.getMethod("membership",int.class).invoke(null,index);
    }
    private static Object call(Object target,String name) throws Exception {Method method=target.getClass().getMethod(name);return method.invoke(target);}
    private BridgeFixture attachBridge(JsonObject input,Source source,ObjectInstance.Owner owner,List<BlockPos> guarded,BridgeFixture[] cleanup) throws Exception {
        require(candidate&&owner!=null,"NativeGridBase fixture is candidate-only and needs a fresh door owner");
        int index=input.get("cell").getAsInt();Class<?> catalog=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog");
        try(InputStream stream=catalog.getResourceAsStream("/assets/bloodborne_dw/native_grid/catalog.json")){
            require(stream!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(input.get("catalogSha256").getAsString()),"Bridge fixture binds the actual loaded catalog");
        }
        BlockState state=world.getBlockState(standRoot);require(!state.get(CompositeRootBlock.OPEN),"Bridge attaches to the fresh closed door only");
        CompositeBlockEntity root=(CompositeBlockEntity)world.getBlockEntity(standRoot);
        NbtCompound payload=root.payload();var closed=CompositeRuntime.instance(world,standRoot,state,owner.instanceId(),payload);
        var opened=CompositeRuntime.instance(world,standRoot,state.cycle(CompositeRootBlock.OPEN),owner.instanceId(),payload);
        BlockPos sourceOffset=pos(input.getAsJsonArray("helperOffset"));require(sourceOffset.equals(new BlockPos(0,-1,1)),"Bridge fixture must bind the exact original source floor contact");
        require(state.isOf(source.state().getBlock())&&state.get(CompositeRootBlock.ROTATION).equals(source.state().get(CompositeRootBlock.ROTATION)),"Manual door must retain the bound source registry and yaw");
        BlockState normal=ImportedSourcePartsV127.installation(source.state());
        require(CompositeSpec.poseVariant(state)==CompositeSpec.poseVariant(normal),"Manual door must follow its exact existing source-parts defaultInstallationPose policy");
        var oldShift=CompositeSourceShift.world(source.state(),source.payload());var newShift=CompositeSourceShift.world(state,payload);
        double x=sourceOffset.getX()+newShift.x()-oldShift.x(),y=sourceOffset.getY()+newShift.y()-oldShift.y()+payload.getDouble("MountY")-source.payload().getDouble("MountY"),z=sourceOffset.getZ()+newShift.z()-oldShift.z();
        require(Math.abs(x-Math.rint(x))<1e-7&&Math.abs(y-Math.rint(y))<1e-7&&Math.abs(z-Math.rint(z))<1e-7,"Manual persistent helper translation must be integral");
        BlockPos offset=new BlockPos((int)Math.rint(x),(int)Math.rint(y),(int)Math.rint(z));
        JsonObject helperPlacement=new JsonObject();helperPlacement.add("originalSourceHelperOffset",coordinates(sourceOffset));helperPlacement.add("actualManualHelperOffset",coordinates(offset));helperPlacement.addProperty("manualSourceShiftCopied",false);helperPlacement.addProperty("sourceCompatibilityPart",ImportedSourcePartsV127.part(source.state()));helperPlacement.addProperty("sourcePose",CompositeSpec.poseVariant(source.state()));helperPlacement.addProperty("manualInstallationPose",CompositeSpec.poseVariant(state));helperPlacement.addProperty("installationPolicy","existing ImportedSourcePartsV127.installation; same registry and yaw, no source installation privileges");report.add("bridgePlacement",helperPlacement);
        BlockPos point=standRoot.add(offset);
        require(!point.equals(standRoot)&&guarded.contains(point)&&inside(point),"Bound floor helper is outside the fresh owned guarded door fixture");
        require(closed.at(CompositeData.cell(point))!=null&&opened.at(CompositeData.cell(point))!=null,"Bound floor helper must persist in both actual closed/open door footprints");
        require(world.getBlockState(point).isOf(CompositeArchitecture.CELL)&&world.getBlockEntity(point) instanceof CompositeBlockEntity existing&&existing.resident()==null,"Bound floor contact must be a fresh nonresident helper");
        var entries=CompositeRuntime.contributions(world,point);require(!entries.isEmpty()&&entries.stream().allMatch(entry->entry.owner().equals(owner)),"Bound floor helper must contain only the fresh test-owned door");
        CompositeBlockEntity helper=(CompositeBlockEntity)world.getBlockEntity(point);
        require(!helper.createNbt().contains("NativeGridBase"),"The fresh fixture helper already has an unexpected background");
        var originalEntries=List.copyOf(helper.contributions());NbtCompound originalPayload=helper.payload();
        Object cell=catalog.getMethod("cell",int.class).invoke(null,index);
        BlockState background=(BlockState)catalog.getMethod("state",int.class).invoke(null,index);
        require(nativeGrid(background)&&nativeMembers(background)==null&&background.getFluidState().isEmpty()&&background.getLuminance()==0,"Finite fixture background must be an ordinary dry nonemitting cell");
        VoxelShape collision=(VoxelShape)call(cell,"collision"),outline=(VoxelShape)call(cell,"outline");
        Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridBridge").getMethod("validate",int.class,BlockState.class).invoke(null,index,world.getBlockState(point));
        BridgeFixture fixture=new BridgeFixture(point,index,background,collision,outline);
        // Register the exact test-owned background before the first mutation so
        // the existing owner removal plus ordinary break can clean up on error.
        cleanup[0]=fixture;
        helper.getClass().getMethod("nativeGridBase",int.class).invoke(helper,index);helper.markDirty();helper.publishShapeSnapshot();
        world.updateListeners(point,world.getBlockState(point),world.getBlockState(point),Block.NOTIFY_LISTENERS);world.getChunkManager().markForUpdate(point);
        check(helper.createNbt().contains("NativeGridBase",3)&&helper.createNbt().getInt("NativeGridBase")==index&&helper.resident()==null&&helper.contributions().equals(originalEntries)&&helper.payload().equals(originalPayload),"one shared base reference is attached only to the fresh owned door helper, with exact original owner entries and payload");
        return fixture;
    }
    private void checkBridge(BridgeFixture fixture,ObjectInstance.Owner owner,String when) throws Exception {
        BlockPos point=fixture.pos();require(world.getBlockState(point).isOf(CompositeArchitecture.CELL)&&world.getBlockEntity(point) instanceof CompositeBlockEntity,"Persistent bridge helper disappeared "+when);
        CompositeBlockEntity helper=(CompositeBlockEntity)world.getBlockEntity(point);
        check(helper.createNbt().contains("NativeGridBase",3)&&helper.createNbt().getInt("NativeGridBase")==fixture.index()&&helper.resident()==null,"exact shared background reference remains on its nonresident helper "+when);
        var entries=CompositeRuntime.contributions(world,point);check(!entries.isEmpty()&&entries.stream().allMatch(entry->entry.owner().equals(owner)),"only the same fresh door owner remains at its persistent bridge helper "+when);
    }
    private void addFootprint(List<BlockPos> points,BlockState state,NbtCompound payload){var instance=CompositeRuntime.instance(world,standRoot,state,UUID.randomUUID(),payload);for(var offset:instance.cells().keySet())points.add(standRoot.add(offset.x(),offset.y(),offset.z()));}
    private void placeItem(ItemStack item,BlockPos target){
        refreshStandChunks("item placement");
        actor.setPosition(standRoot.getX()+8,standRoot.getY(),standRoot.getZ()+8);actor.setStackInHand(Hand.MAIN_HAND,item);
        var hit=new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false);
        var usage=new ItemUsageContext(actor,Hand.MAIN_HAND,hit);var context=new ItemPlacementContext(usage);
        JsonObject diagnostic=new JsonObject();diagnostic.add("target",coordinates(target));diagnostic.add("placementPos",coordinates(context.getBlockPos()));
        diagnostic.addProperty("item",Registries.ITEM.getId(item.getItem()).toString());diagnostic.addProperty("itemClass",item.getItem().getClass().getName());diagnostic.addProperty("itemNbt",String.valueOf(item.getNbt()));
        diagnostic.addProperty("yaw",actor.getYaw());diagnostic.addProperty("sneaking",actor.isSneaking());diagnostic.addProperty("allowModifyWorld",actor.getAbilities().allowModifyWorld);
        diagnostic.addProperty("targetChunkLoaded",world.isChunkLoaded(context.getBlockPos()));diagnostic.addProperty("canPlayerModifyAt",world.canPlayerModifyAt(actor,context.getBlockPos()));diagnostic.addProperty("contextCanPlace",context.canPlace());
        TransactionCore.Result before=CompositeRuntime.lastResult();placementMessages=new ArrayList<>();ActionResult result;
        try{result=item.getItem().useOnBlock(usage);}finally{diagnostic.add("messages",JSON.toJsonTree(placementMessages));placementMessages=null;placements.add(diagnostic);}
        TransactionCore.Result after=CompositeRuntime.lastResult();diagnostic.addProperty("actionResult",result.name());diagnostic.addProperty("transactionResultUpdated",after!=before);
        if(after!=null){diagnostic.addProperty("transactionOutcome",after.outcome().name());diagnostic.addProperty("transactionReason",after.reason());diagnostic.addProperty("transactionChangedCells",after.changedCells());}
        check(result.isAccepted(),"registered picked BlockItem places at "+target+"; result="+result+"; transaction="+(after==null?"none":after.reason()));
    }
    private void refreshStandChunks(String action){
        int count=0;
        for(int cx=standMin.getX()>>4;cx<=standMax.getX()>>4;cx++)for(int cz=standMin.getZ()>>4;cz<=standMax.getZ()>>4;cz++){
            require(allowedChunks.contains(ChunkPos.toLong(cx,cz)),"Stand refresh escaped the finite declared source chunks");
            world.getChunk(cx,cz);count++;
        }
        for(int cx=standMin.getX()>>4;cx<=standMax.getX()>>4;cx++)for(int cz=standMin.getZ()>>4;cz<=standMax.getZ()>>4;cz++)
            require(world.isChunkLoaded(new BlockPos(Math.max(cx*16,standMin.getX()),standRoot.getY(),Math.max(cz*16,standMin.getZ()))),"Native stand chunk request did not make its physical probes available");
        for(BlockPos probe:BlockPos.iterate(standRoot.add(-1,-1,-1),standRoot.add(1,1,1)))require(inside(probe)&&world.isChunkLoaded(probe),"Placement target physical-probe halo is outside the loaded stand: "+probe);
        JsonArray refreshes=report.has("standChunkRefreshes")?report.getAsJsonArray("standChunkRefreshes"):new JsonArray();
        JsonObject row=new JsonObject();row.addProperty("beforeAction",action);row.addProperty("ordinaryChunkRequests",count);row.addProperty("forcedTickets",0);row.addProperty("targetProbeHaloLoaded",true);refreshes.add(row);report.add("standChunkRefreshes",refreshes);
    }
    private void assertDoorPhysicalProbes(){
        if(!(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity root)||root.resident()==null)return;
        BlockState state=world.getBlockState(standRoot);NbtCompound payload=root.payload();
        for(BlockState pose:List.of(state,state.cycle(CompositeRootBlock.OPEN))){
            var object=CompositeRuntime.instance(world,standRoot,pose,root.resident().instanceId(),payload);
            for(Box box:dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.physicalBoxes(object)){
                double eps=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.EPS;
                for(int x=(int)Math.floor(box.minX+eps)-1;x<(int)Math.ceil(box.maxX-eps)+1;x++)
                    for(int y=(int)Math.floor(box.minY+eps)-1;y<(int)Math.ceil(box.maxY-eps)+1;y++)
                        for(int z=(int)Math.floor(box.minZ+eps)-1;z<(int)Math.ceil(box.maxZ-eps)+1;z++){
                            BlockPos probe=new BlockPos(x,y,z);require(inside(probe)&&world.isChunkLoaded(probe),"Actual closed/open door physical probe escaped the finite loaded stand: "+probe);
                        }
            }
        }
    }
    private void useByRay(List<BlockPos> footprint){refreshStandChunks("door right click");assertDoorPhysicalProbes();BlockPos target=selectable(footprint,false);BlockHitResult hit=ray(target);actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);BlockState state=world.getBlockState(hit.getBlockPos());check(state.getBlock().onUse(state,world,hit.getBlockPos(),actor,Hand.MAIN_HAND,hit).isAccepted(),"actual selected door part handles right-click");}
    private void breakByRay(BlockPos target){refreshStandChunks("selected break");BlockHitResult hit=ray(target);performBreak(target,hit);}
    private void performBreak(BlockPos requested,BlockHitResult hit){
        actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);boolean owned=!CompositeRuntime.contributions(world,hit.getBlockPos()).isEmpty();
        TransactionCore.Result before=CompositeRuntime.lastResult();JsonObject row=new JsonObject();row.add("requested",coordinates(requested));row.add("actualRayHit",coordinates(hit.getBlockPos()));row.add("before",cell(hit.getBlockPos()));row.add("previousTransaction",transaction(before));
        boolean api=actor.interactionManager.tryBreakBlock(hit.getBlockPos());TransactionCore.Result after=CompositeRuntime.lastResult();row.addProperty("apiReturn",api);row.addProperty("ownedBefore",owned);row.addProperty("transactionUpdated",after!=before);row.add("transaction",transaction(after));row.add("after",cell(hit.getBlockPos()));
        JsonArray breaks=report.has("breakAttempts")?report.getAsJsonArray("breakAttempts"):new JsonArray();breaks.add(row);report.add("breakAttempts",breaks);save();
        // BEFORE deliberately returns false after a handled composite removal.
        // The actual transaction and complete postconditions determine success.
        if(owned)check(after!=before&&after!=null&&after.outcome()==TransactionCore.Outcome.COMMITTED,"selected whole-object removal transaction commits at "+hit.getBlockPos());
    }
    private static JsonObject transaction(TransactionCore.Result result){JsonObject row=new JsonObject();row.addProperty("available",result!=null);if(result!=null){row.addProperty("outcome",result.outcome().name());row.addProperty("changedCells",result.changedCells());row.addProperty("reason",result.reason());}return row;}
    private BlockHitResult ray(BlockPos target){List<Box> selection=world.getBlockState(target).getOutlineShape(world,target).getBoundingBoxes();require(!selection.isEmpty(),"No native selection at "+target);Vec3d center=selection.get(0).getCenter().add(target.getX(),target.getY(),target.getZ());Vec3d eye=center.add(0,0,-3);actor.setPosition(eye.x,eye.y-actor.getStandingEyeHeight(),eye.z);Vec3d delta=center.subtract(eye);actor.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));actor.setHeadYaw(actor.getYaw());actor.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));var hit=world.raycast(new RaycastContext(eye,center.add(0,0,1),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));require(hit.getType()==HitResult.Type.BLOCK&&inside(hit.getBlockPos()),"Native selection ray missed guarded object");return hit;}
    private BlockPos selectable(List<BlockPos> footprint,boolean across){return footprint.stream().filter(p->!world.getBlockState(p).getOutlineShape(world,p).isEmpty()).sorted(Comparator.comparingInt(p->across&&new ChunkPos(p).toLong()!=new ChunkPos(standRoot).toLong()?0:1)).findFirst().orElseThrow(()->new IllegalStateException("No selectable whole-object cell"));}
    private void cleanupActive(){Runnable cleanup=activeCleanup;if(cleanup!=null)cleanup.run();activeCleanup=null;}
    private void guard(Collection<BlockPos> positions){for(BlockPos point:positions){allowed(point);require(inside(point)&&empty(point),"Proposed fixture outside empty stand: "+point);}}
    private boolean inside(BlockPos p){return p.getX()>=standMin.getX()&&p.getX()<=standMax.getX()&&p.getY()>=standMin.getY()&&p.getY()<=standMax.getY()&&p.getZ()>=standMin.getZ()&&p.getZ()<=standMax.getZ();}
    private boolean empty(BlockPos point){return world.getBlockState(point).isOf(Blocks.AIR)&&world.getFluidState(point).isEmpty()&&world.getBlockEntity(point)==null&&CompositeRuntime.contributions(world,point).isEmpty();}
    private void allowed(BlockPos point){require(allowedChunks.contains(new ChunkPos(point).toLong()),"Address outside proved existing QA chunks: "+point);}
    private void watch(BlockPos point){allowed(point);require(!inside(point),"Source/protected witness overlaps disposable stand");watched.putIfAbsent(point.toImmutable(),cell(point));}
    private static boolean protectedMatches(JsonObject actual,JsonObject reference,JsonObject row){
        if(!row.has("protectedOwnerUuid"))return actual.equals(reference);
        UUID owner=UUID.fromString(row.get("protectedOwnerUuid").getAsString());
        if(!actual.get("state").equals(reference.get("state"))||!actual.get("fluid").equals(reference.get("fluid")))return false;
        NbtCompound before=protectedNbt(reference.get("nbt").getAsString(),owner),after=protectedNbt(actual.get("nbt").getAsString(),owner);
        NbtCompound beforeOwners=protectedNbt(reference.get("owners").getAsString(),owner),afterOwners=protectedNbt(actual.get("owners").getAsString(),owner);
        require(!CompositeData.savedContributions(beforeOwners).isEmpty(),"Protected baseline owner is missing at a declared helper");
        return before.equals(after)&&beforeOwners.equals(afterOwners);
    }
    private static NbtCompound protectedNbt(String encoded,UUID owner){
        if(encoded.isEmpty())return new NbtCompound();
        NbtCompound nbt=CompositeData.nbt(Base64.getDecoder().decode(encoded));
        boolean owners=nbt.contains("owners")||nbt.contains(CompositeOwnerNbt.KEY);
        if(owners){
            var retained=CompositeData.savedContributions(nbt).stream().filter(value->value.owner().instanceId().equals(owner)).toList();
            nbt.remove("owners");nbt.remove(CompositeOwnerNbt.KEY);nbt.put("owners",CompositeData.contributions(retained));
        }
        // Only the shared static background reference is new. The protected
        // object's resident, payload, contribution and root data remain exact.
        nbt.remove("NativeGridBase");
        return nbt;
    }
    private JsonObject cell(BlockPos point){JsonObject row=new JsonObject();var state=world.getBlockState(point);row.add("state",canonicalState(Registries.BLOCK.getId(state.getBlock()).toString(),state));var entity=world.getBlockEntity(point);row.addProperty("nbt",entity==null?"":Base64.getEncoder().encodeToString(CompositeData.bytes(entity.createNbtWithIdentifyingData())));NbtCompound owners=new NbtCompound();owners.put("owners",CompositeData.contributions(CompositeRuntime.contributions(world,point)));row.addProperty("owners",Base64.getEncoder().encodeToString(CompositeData.bytes(owners)));var fluid=world.getFluidState(point);row.add("fluid",canonicalState(Registries.FLUID.getId(fluid.getFluid()).toString(),fluid));return row;}
    private static JsonObject canonicalState(String registryId,net.minecraft.state.State<?,?> state){JsonObject row=new JsonObject(),properties=new JsonObject();row.addProperty("registryId",registryId);TreeMap<String,String> sorted=new TreeMap<>();for(var entry:state.getEntries().entrySet())sorted.put(entry.getKey().getName(),propertyName(entry.getKey(),entry.getValue()));for(var entry:sorted.entrySet())properties.addProperty(entry.getKey(),entry.getValue());row.add("properties",properties);return row;}
    @SuppressWarnings({"rawtypes","unchecked"}) private static String propertyName(Property property,Comparable value){return property.name(value);}
    private boolean standEmpty(){for(BlockPos point:BlockPos.iterate(standMin,standMax))if(!empty(point))return false;return true;}
    private JsonArray snapshots(Map<BlockPos,JsonObject> input){JsonArray rows=new JsonArray();for(var e:input.entrySet()){JsonObject row=new JsonObject();row.add("pos",coordinates(e.getKey()));row.add("reference",e.getValue());rows.add(row);}return rows;}
    private VoxelShape union(List<BlockPos> positions,BlockPos origin,boolean collision){VoxelShape result=VoxelShapes.empty();for(BlockPos point:positions){BlockState state=world.getBlockState(point);VoxelShape shape=collision?state.getCollisionShape(world,point):state.getOutlineShape(world,point);result=VoxelShapes.union(result,shape.offset(point.getX()-origin.getX(),point.getY()-origin.getY(),point.getZ()-origin.getZ()));}return result;}
    private static VoxelShape instanceUnion(ObjectInstance instance,boolean collision){VoxelShape result=VoxelShapes.empty();for(var entry:instance.cells().entrySet()){var offset=entry.getKey();for(var b:collision?entry.getValue().collision():entry.getValue().selection())result=VoxelShapes.union(result,VoxelShapes.cuboid(b.minX()+offset.x(),b.minY()+offset.y(),b.minZ()+offset.z(),b.maxX()+offset.x(),b.maxY()+offset.y(),b.maxZ()+offset.z()));}return result;}
    private static boolean nativeGrid(BlockState state){var id=Registries.BLOCK.getId(state.getBlock());return id.getNamespace().equals("bloodborne_dw")&&id.getPath().startsWith("native_grid_");}
    private static boolean same(VoxelShape a,VoxelShape b){return !VoxelShapes.matchesAnywhere(a,b,BooleanBiFunction.NOT_SAME);}
    private static JsonArray boxes(VoxelShape shape){return JSON.toJsonTree(shape.getBoundingBoxes().stream().map(b->List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList()).getAsJsonArray();}
    private static VoxelShape shape(JsonArray boxes){VoxelShape shape=VoxelShapes.empty();for(JsonElement raw:boxes){var b=raw.getAsJsonArray();require(b.size()==6,"Expected six-coordinate reference box");shape=VoxelShapes.union(shape,VoxelShapes.cuboid(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));}return shape;}
    private static List<BlockPos> positions(JsonArray raw){require(raw!=null&&raw.size()>0&&raw.size()<=8192,"Finite position witness list required");return java.util.stream.StreamSupport.stream(raw.spliterator(),false).map(e->pos(e.getAsJsonArray())).distinct().toList();}
    private static JsonArray coordinates(BlockPos point){return JSON.toJsonTree(new int[]{point.getX(),point.getY(),point.getZ()}).getAsJsonArray();}
    private static BlockPos pos(JsonArray a){require(a!=null&&a.size()==3,"Integer position triple required");for(JsonElement n:a)require(Double.isFinite(n.getAsDouble())&&n.getAsDouble()==n.getAsInt(),"Noninteger QA coordinate");return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static Vec3d vec(JsonArray a){require(a!=null&&a.size()==3,"Finite player vector required");for(JsonElement n:a)require(Double.isFinite(n.getAsDouble()),"Nonfinite QA vector");return new Vec3d(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private void check(boolean condition,String name){JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",condition);checks.add(row);require(condition,name);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static String text(JsonObject row,String key,String fallback){return row.has(key)?row.get(key).getAsString():fallback;}
    /** One exact retained ImportedRoot case. Its one plain substitution and one
     * existing-helper background are authorized setup, not placement claims. */
    private final class ForeignGuestFixture {
        private static final String SOURCE_SHA="6261a58cd23120f0fc5ae6b9210919f18e385482d03464da0b8366e720eee268";
        private static final String SOURCE_OWNER="[-1182530740,-1421322098,-1822126450,163509195]";
        private static final String SOURCE_POSE="sp_e70009f84997fbb5b6ede9de";
        private static final String POLICY_SHA="79392979f165ef447666f947b49d539c6cb262ce126e6b217cdcaf8bf6556afe";
        private final JsonObject input,proof,fact,detail=new JsonObject();
        private final List<BlockPos> members=new ArrayList<>();
        private final Map<BlockPos,BlockState> backgrounds=new LinkedHashMap<>();
        private final Map<BlockPos,JsonObject> backgroundRows=new LinkedHashMap<>();
        private ObjectInstance.Owner owner;
        private ObjectInstance instance;
        private BlockPos plainPoint,bridgePoint;
        private int bridgeIndex;
        private Map<BlockPos,JsonObject> installed;
        private boolean setupComplete,savedForReopen,foreignFinished;
        private final LegacyWholeItemFixture legacyWhole;
        ForeignGuestFixture(JsonObject input)throws Exception{
            this.input=input;
            require(Set.of("install_save","reopen_finish").contains(text(input,"phase","")),"Unknown finite foreign-guest phase");
            require(input.getAsJsonObject("sourceProof").get("sha256").getAsString().equals(SOURCE_SHA),"Foreign fixture must use the exact addressed source facts");
            JsonObject source=readBoundJson(input.getAsJsonObject("sourceProof"));
            fact=java.util.stream.StreamSupport.stream(source.getAsJsonArray("allAddressedOwnerSourceFacts").spliterator(),false).map(JsonElement::getAsJsonObject).filter(row->row.get("uuid").getAsString().equals(SOURCE_OWNER)).findFirst().orElseThrow();
            require(fact.get("pose").getAsString().equals(SOURCE_POSE)&&fact.getAsJsonArray("sourceFailures").size()==0&&fact.get("className").getAsString().equals("dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock"),"Addressed source D facts did not pass");
            require(input.getAsJsonObject("pointProof").has("sha256"),"Exact FULL composition/type proof required");
            proof=readBoundJson(input.getAsJsonObject("pointProof"));
            require(text(proof,"status","").equals("PASS_SOURCE_COMPOSITION_AND_TYPE_BINDING_NOT_GAMEPLAY")&&text(proof,"kind","").equals("EXACT_D_E_NONROOT_NATIVE_GRID_OVERLAY")&&text(proof,"scope","").equals("FULL_CITY")&&proof.get("sourceOwnerUuid").getAsString().equals(SOURCE_OWNER)&&proof.get("sourcePose").getAsString().equals(SOURCE_POSE)&&proof.get("sourceBinding").equals(source.get("sourceBinding")),"Unknown or unbound foreign-guest point certificate");
            require(proof.getAsJsonObject("sourceProof").equals(input.getAsJsonObject("sourceProof")),"Point/type evidence used different source facts");
            require(input.getAsJsonObject("memberPolicyProof").get("sha256").getAsString().equals(POLICY_SHA)&&proof.getAsJsonObject("memberPolicyProof").equals(input.getAsJsonObject("memberPolicyProof")),"Exact mixed nonroot member policies must remain bound");
            JsonObject policies=readBoundJson(input.getAsJsonObject("memberPolicyProof"));require(text(policies,"status","").equals("PASS")&&policies.get("sourceBinding").equals(source.get("sourceBinding")),"Member-policy source proof changed");
            Class<?> catalog=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog");
            try(InputStream stream=catalog.getResourceAsStream("/assets/bloodborne_dw/native_grid/catalog.json")){
                require(stream!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())).equals(proof.get("catalogSha256").getAsString()),"Foreign fixture needs its actual FULL catalog");
            }
            JsonObject audit=readBoundJson(input.getAsJsonObject("targetBridgeAudit"));
            require(text(audit,"status","").equals("PASS")&&audit.get("nativeRegistryQueries").getAsBoolean()&&audit.get("runtimeSha256").equals(plan.get("productionSha256"))&&audit.get("catalogSha256").equals(proof.get("catalogSha256"))&&audit.get("bridgePlanSha256").equals(proof.get("targetBridgePlanSha256")),"Actual all-target bridge audit is absent or bound to another FULL build");
            require(proof.getAsJsonArray("overlays").size()==2,"Exactly two addressed nonroot overlay cells required");
            BlockPos sourceRoot=pos(fact.getAsJsonArray("root"));
            for(BlockPos point:positions(fact.getAsJsonArray("runtimeMemberCells")))members.add(standRoot.add(point.subtract(sourceRoot)));
            require(members.size()==4&&members.contains(standRoot),"Exact D must keep all four runtime members");
            Set<BlockPos> expected=Set.of(standRoot.add(0,-1,-1),standRoot.add(0,-1,0));
            for(JsonElement raw:proof.getAsJsonArray("overlays")){
                JsonObject row=raw.getAsJsonObject();BlockPos offset=pos(row.getAsJsonArray("offset")),point=standRoot.add(offset);
                require(expected.contains(point)&&!backgrounds.containsKey(point)&&row.get("baseExcludesD").getAsBoolean()&&row.has("compositionKey")&&pos(row.getAsJsonArray("sourcePosition")).equals(sourceRoot.add(offset)),"Foreign overlay is not the exact source D/E point");
                BlockState state=(BlockState)catalog.getMethod("state",int.class).invoke(null,row.get("cell").getAsInt());
                require(nativeGrid(state)&&nativeMembers(state)==null&&!state.hasBlockEntity()&&state.getFluidState().isEmpty()&&state.getLuminance()==0,"Foreign background must be a plain dry nonemitting catalog cell");
                require(same(state.getBlock().getCollisionShape(state,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent()),shape(row.getAsJsonArray("collision")))&&same(state.getBlock().getOutlineShape(state,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent()),shape(row.getAsJsonArray("outline"))),"FULL target background shapes differ from exact composition/type evidence");
                backgrounds.put(point,state);backgroundRows.put(point,row);
                if(offset.equals(new BlockPos(0,-1,-1))){require(text(row,"carrierMode","").equals("plain_foreign"),"First actual member must remain the proved plain FOREIGN route");plainPoint=point;}
                else{require(text(row,"carrierMode","").equals("existing_helper_bridge"),"Second actual member must retain its existing nonresident CompositeBE");bridgePoint=point;bridgeIndex=row.get("cell").getAsInt();}
            }
            if(!reopening())guard(members);for(BlockPos point:members){require(inside(point),"Foreign fixture escapes the existing empty stand");allowed(point);}
            legacyWhole=input.has("legacyNativeWholeItem")?new LegacyWholeItemFixture(input.getAsJsonObject("legacyNativeWholeItem")):null;
            detail.add("binding",input.deepCopy());detail.addProperty("sourceOwnerUuid",SOURCE_OWNER);detail.addProperty("sourcePose",SOURCE_POSE);detail.addProperty("authorizedSetupCarrierSubstitutions",1);detail.addProperty("existingHelperBackgroundReferencesAdded",1);detail.addProperty("ordinaryPlacementClaim",false);detail.addProperty("newBlockEntitiesCreatedBySubstitution",0);report.add("foreignGuestFixture",detail);
        }
        boolean reopening(){return text(input,"phase","").equals("reopen_finish");}
        void prepareReopen()throws Exception{
            JsonObject previous=readBoundJson(input.getAsJsonObject("previousReport"));
            require(text(previous,"status","").equals("PASS")&&previous.get("normalServerThreadFinished").getAsBoolean()&&previous.get("productionSha256").equals(plan.get("productionSha256")),"Reopen requires the exact normally saved first phase");
            JsonObject prior=previous.getAsJsonObject("foreignGuestFixture"),priorInput=prior.getAsJsonObject("binding").deepCopy(),now=input.deepCopy();
            priorInput.remove("phase");priorInput.remove("previousReport");now.remove("phase");now.remove("previousReport");
            require(priorInput.equals(now)&&prior.get("standRetainedForReopen").getAsBoolean()&&!prior.get("gameplayComplete").getAsBoolean(),"Reopen changed the exact retained fixture/proofs");
            UUID uuid=UUID.fromString(prior.get("freshOwnerUuid").getAsString());owner=new ObjectInstance.Owner(uuid,fact.getAsJsonObject("actualRootState").get("registryId").getAsString(),CompositeData.cell(standRoot));
            installed=new LinkedHashMap<>();for(JsonElement raw:prior.getAsJsonArray("savedCells")){JsonObject row=raw.getAsJsonObject();installed.put(pos(row.getAsJsonArray("pos")),row.getAsJsonObject("reference"));}
            require(installed.keySet().equals(new LinkedHashSet<>(members)),"Reopen snapshots do not cover the four exact cells");
            if(legacyWhole!=null)legacyWhole.prepareReopen(prior.getAsJsonObject("retainedNativeWholeItem"));
            assertOutsideAir();require(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity,"Saved D root BE is missing");
            CompositeBlockEntity root=(CompositeBlockEntity)world.getBlockEntity(standRoot);instance=CompositeRuntime.instance(world,standRoot,world.getBlockState(standRoot),uuid,root.payload());
            activeCleanup=this::cleanup;standInitiallyAir=true;
            check(installed.entrySet().stream().allMatch(row->cell(row.getKey()).equals(row.getValue())),"real server reopen preserves all four exact native states, typed D payload and ledger refs");
            assertWhole("after real reopen");detail.addProperty("freshOwnerUuid",uuid.toString());detail.add("reopenedCells",snapshots(installed));
        }
        boolean step()throws Exception{
            refreshStandChunks("finite FOREIGN guest fixture");
            if(reopening()){if(!foreignFinished){finishReopened();foreignFinished=true;}if(legacyWhole!=null){legacyWhole.finishReopened();check(standEmpty(),"mixed FOREIGN and retained old-native whole fixtures restore the complete AIR stand");standRestored=true;activeCleanup=null;save();}return true;}
            if(!setupComplete){install();if(legacyWhole!=null)legacyWhole.install();setupComplete=true;return false;}
            // A world tick has now drained any onStateReplaced cleanup scheduled
            // by the substitution; it may not silently erase live refs.
            assertWhole("after the real callback tick");probeRollback();
            if(legacyWhole!=null)legacyWhole.savedAfterCallback();
            installed=snapshotMembers();detail.add("savedCells",snapshots(installed));detail.addProperty("standRetainedForReopen",true);detail.addProperty("gameplayComplete",false);detail.addProperty("phaseResult","SETUP_AND_SAVE_ONLY_PENDING_REAL_REOPEN");
            savedForReopen=true;activeCleanup=null;report.addProperty("rawSetBlockCalls",legacyWhole==null?1:4);save();return true;
        }
        void install()throws Exception{
            require(standEmpty(),"Foreign setup needs the original complete AIR/no-owner stand");
            JsonObject stateRow=fact.getAsJsonObject("actualRootState");BlockState state=exactState(stateRow);
            NbtCompound payload=new NbtCompound();payload.putInt("ImportedTypeVersion",1);net.minecraft.nbt.NbtList shift=new net.minecraft.nbt.NbtList();for(double n:new double[]{0,-1,0})shift.add(net.minecraft.nbt.NbtDouble.of(n));payload.put("SourceShift",shift);
            JsonObject savedPayload=fact.getAsJsonObject("rootBE").getAsJsonObject("payload");require(savedPayload.keySet().equals(Set.of("SourceShift","ImportedTypeVersion"))&&savedPayload.get("ImportedTypeVersion").getAsInt()==1&&savedPayload.getAsJsonArray("SourceShift").equals(JSON.toJsonTree(new double[]{0,-1,0})),"Exact D source payload changed");
            UUID uuid=UUID.randomUUID();owner=new ObjectInstance.Owner(uuid,stateRow.get("registryId").getAsString(),CompositeData.cell(standRoot));activeCleanup=this::cleanup;
            TransactionCore.Result placed=CompositeRuntime.place(world,standRoot,state,uuid,actor,payload);detail.add("initialPlacement",transaction(placed));save();check(placed.outcome()==TransactionCore.Outcome.COMMITTED,"fresh exact-payload D installs through existing public transaction API");
            require(canonicalState(Registries.BLOCK.getId(world.getBlockState(standRoot).getBlock()).toString(),world.getBlockState(standRoot)).equals(stateRow),"Existing source API normalized D into a different state");
            CompositeBlockEntity root=(CompositeBlockEntity)world.getBlockEntity(standRoot);require(root.payload().equals(payload),"Exact source D payload changed during fixture setup");
            instance=CompositeRuntime.instance(world,standRoot,state,uuid,root.payload());require(instance.cells().keySet().stream().map(c->standRoot.add(c.x(),c.y(),c.z())).collect(java.util.stream.Collectors.toSet()).equals(new HashSet<>(members)),"Fresh D does not have the four proven runtime members");
            Map<BlockPos,JsonObject> before=snapshotMembers();detail.add("beforeSubstitution",snapshots(before));detail.addProperty("freshOwnerUuid",uuid.toString());save();
            for(var entry:backgrounds.entrySet()){
                BlockPos point=entry.getKey();require(!point.equals(standRoot)&&world.getBlockState(point).isOf(CompositeArchitecture.CELL)&&world.getBlockEntity(point) instanceof CompositeBlockEntity helper&&helper.resident()==null,"Only a fresh nonresident test-owned helper may be substituted");
                require(CompositeRuntime.contributions(world,point).size()==1&&CompositeRuntime.contributions(world,point).get(0).owner().equals(owner),"Substitution found a foreign owner");
                if(point.equals(plainPoint))require(world.setBlockState(point,entry.getValue(),Block.FORCE_STATE|Block.NOTIFY_ALL|Block.SKIP_DROPS)||world.getBlockState(point).equals(entry.getValue()),"Authorized own-helper substitution refused");
                else{
                    CompositeBlockEntity helper=(CompositeBlockEntity)world.getBlockEntity(point);NbtCompound original=helper.createNbt();require(helper.payload().isEmpty()&&!original.contains("NativeGridBase"),"Exact fresh bridge helper has unexpected private payload/background");
                    helper.getClass().getMethod("nativeGridBase",int.class).invoke(helper,bridgeIndex);helper.markDirty();helper.publishShapeSnapshot();world.updateListeners(point,world.getBlockState(point),world.getBlockState(point),Block.NOTIFY_LISTENERS);world.getChunkManager().markForUpdate(point);
                    NbtCompound after=helper.createNbt();require(after.contains("NativeGridBase",3)&&after.getInt("NativeGridBase")==bridgeIndex,"Shared base was not attached to the exact existing helper");after.remove("NativeGridBase");check(after.equals(original),"existing fresh helper typed NBT changes only by its exact shared NativeGridBase reference");
                }
            }
            check(cell(standRoot).equals(before.get(standRoot)),"authorized mixed nonroot setup preserves exact D root/resident/typed payload");
            for(BlockPos point:members)check(cell(point).get("owners").equals(before.get(point).get("owners")),"authorized substitution preserves the original fresh D ledger ref at "+point);
            assertWhole("immediately after substitution");detail.add("afterSubstitution",snapshots(snapshotMembers()));save();
        }
        void assertWhole(String when){
            require(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity root&&owner.equals(root.resident())&&world.getBlockState(standRoot).equals(CompositeData.state(instance.rootData()))&&root.payload().equals(CompositeData.nbt(instance.rootData().blockEntityNbt())),"D root/payload changed "+when);
            for(BlockPos point:members){var entries=CompositeRuntime.contributions(world,point);require(entries.size()==1&&entries.get(0).owner().equals(owner)&&entries.get(0).rootData().equals(instance.rootData())&&entries.get(0).shape().equals(instance.at(CompositeData.cell(point))),"D exact ledger member changed "+when+": "+point);}
            VoxelShape collision=instanceUnion(instance,true),outline=instanceUnion(instance,false);
            for(var entry:backgrounds.entrySet())if(world.getBlockState(entry.getKey()).equals(entry.getValue())||entry.getKey().equals(bridgePoint)){
                if(entry.getKey().equals(bridgePoint))require(world.getBlockState(bridgePoint).isOf(CompositeArchitecture.CELL)&&world.getBlockEntity(bridgePoint) instanceof CompositeBlockEntity helper&&helper.resident()==null&&helper.payload().isEmpty()&&bridgeIndex(helper)==bridgeIndex,"Exact existing helper/background changed "+when);
                else require(world.getBlockEntity(entry.getKey())==null&&new FabricCompositeWorld(world,Map.of()).read(CompositeData.cell(entry.getKey())).kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.FOREIGN,"Plain background became a BE or helper "+when);
                BlockPos offset=entry.getKey().subtract(standRoot);JsonObject row=backgroundRows.get(entry.getKey());collision=VoxelShapes.union(collision,shape(row.getAsJsonArray("collision")).offset(offset.getX(),offset.getY(),offset.getZ()));outline=VoxelShapes.union(outline,shape(row.getAsJsonArray("outline")).offset(offset.getX(),offset.getY(),offset.getZ()));
            }
            check(same(union(members,standRoot,true),collision)&&same(union(members,standRoot,false),outline),"live FOREIGN guest collision/outline is exact D plus remaining native backgrounds "+when);
        }
        void probeRollback(){
            Map<BlockPos,JsonObject> before=snapshotMembers();BlockPos target=plainPoint;boolean[] reached={false,false};
            FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of()){@Override public boolean writeSilently(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell cell,dev.dreamwalker.bloodbornedw.runtime.CellSnapshot next){boolean ok=super.writeSilently(cell,next);if(ok&&cell.equals(CompositeData.cell(target))){require(world.getBlockState(target).equals(backgrounds.get(target))&&world.getBlockEntity(target)==null&&CompositeRuntime.contributions(world,target).isEmpty(),"Forced rollback never reached the real FOREIGN ledger detach");reached[0]=true;}if(ok&&cell.equals(owner.root())){require(reached[0]&&world.getBlockEntity(standRoot)==null&&CompositeRuntime.contributions(world,standRoot).isEmpty(),"Forced rollback did not reach root detach after the FOREIGN carrier");reached[1]=true;return false;}return ok;}};
            var preparation=CompositeRuntime.core().removal(access,owner);require(preparation.accepted()&&preparation.plan().writes().size()==4,"Exact D rollback preparation refused or changed its four write cells: "+preparation.reason());
            TransactionCore.Result result=CompositeRuntime.execute(access,preparation);detail.add("rollback",transaction(result));save();
            check(reached[0]&&reached[1]&&result.outcome()==TransactionCore.Outcome.ROLLED_BACK&&before.entrySet().stream().allMatch(row->cell(row.getKey()).equals(row.getValue())),"forced FOREIGN detach rollback restores all four exact states/payload/ledger refs");assertWhole("after rollback");
        }
        void finishReopened()throws Exception{
            assertWhole("before live background break");JsonObject rootBefore=cell(standRoot);Map<BlockPos,JsonObject> before=snapshotMembers();BlockPos target=plainPoint;
            JsonObject rayProof=new JsonObject();BlockHitResult hit=exactBackgroundRay(target,rayProof);detail.add("backgroundBreakRay",rayProof);save();
            actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
            require(CompositeRuntime.target(world,target,actor)==null,"Background interaction still selects retained D");boolean api=actor.interactionManager.tryBreakBlock(target);detail.addProperty("backgroundBreakApiReturn",api);save();
            check(world.getBlockState(target).isAir()&&world.getBlockEntity(target)==null,"ordinary static background break removes only the plain carrier while D remains live");
            check(cell(standRoot).equals(rootBefore)&&members.stream().allMatch(point->cell(point).get("owners").equals(before.get(point).get("owners"))),"background break preserves exact D root/payload and every one of its four ledger refs");assertWhole("after live background break");
            JsonObject partSearch=new JsonObject();BlockHitResult part=exactPartRay(plainPoint,partSearch);detail.add("exposedPlainMemberRay",partSearch);save();if(part!=null&&!owner.equals(CompositeRuntime.target(world,part.getBlockPos(),actor)))part=null;
            require(part!=null,"No selectable D part is accessible after background break");
            ItemStack expected=CompositeRuntime.pick(world,owner);check(!expected.isEmpty()&&expected.getCount()==1&&expected.getItem() instanceof BlockItem,"retained D picks one usable whole main item");detail.addProperty("pickedMainItem",Registries.ITEM.getId(expected.getItem()).toString());detail.addProperty("pickedMainItemNbt",String.valueOf(expected.getNbt()));
            check(CompositeRuntime.target(world,part.getBlockPos(),actor).equals(owner),"actual selected remaining part targets exactly the fresh D whole owner");performBreak(part.getBlockPos(),part);
            check(world.getBlockEntity(standRoot)==null&&members.stream().allMatch(point->CompositeRuntime.contributions(world,point).isEmpty()),"one real ray break removes D root and all four original ledger memberships");
            detail.add("afterDWholeRemovalBeforeBackgroundCleanup",snapshots(snapshotMembers()));save();
            check(world.getBlockState(bridgePoint).equals(backgrounds.get(bridgePoint))&&nativeGrid(world.getBlockState(bridgePoint))&&world.getBlockEntity(bridgePoint)==null&&CompositeRuntime.contributions(world,bridgePoint).isEmpty()&&new FabricCompositeWorld(world,Map.of()).read(CompositeData.cell(bridgePoint)).kind()==dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind.FOREIGN,"D whole break preserves the second exact background as an ordinary FOREIGN NativeGrid state with no BE or owner refs before cleanup");
            check(empty(plainPoint)&&members.stream().filter(point->!point.equals(bridgePoint)).allMatch(NativeGridFunctionChecks.this::empty),"D whole break leaves the previously broken plain cell and both other D members AIR before cleanup");
            actor.interactionManager.tryBreakBlock(bridgePoint);check(empty(bridgePoint),"the preserved second background independently breaks to AIR");
            if(legacyWhole==null){check(standEmpty(),"foreign-guest fixture restores the complete original AIR/no-BE/no-owner stand");standRestored=true;activeCleanup=null;}detail.addProperty("gameplayComplete",true);detail.addProperty("realReopenVerified",true);detail.addProperty("standRetainedForReopen",false);detail.add("finalCells",snapshots(snapshotMembers()));report.addProperty("rawSetBlockCalls",0);report.addProperty("inheritedSetupRawSetBlockCalls",legacyWhole==null?1:4);save();
        }
        BlockHitResult exactBackgroundRay(BlockPos point,JsonObject result){
            JsonArray attempts=new JsonArray();result.add("attempts",attempts);BlockState state=backgrounds.get(point);
            for(Box box:state.getBlock().getOutlineShape(state,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent()).getBoundingBoxes())for(Direction side:Direction.values()){
                Vec3d center=box.getCenter().add(point.getX(),point.getY(),point.getZ()),delta=new Vec3d(side.getOffsetX(),side.getOffsetY(),side.getOffsetZ()),eye=center.add(delta.multiply(3));BlockHitResult hit=world.raycast(new RaycastContext(eye,center.subtract(delta.multiply(.25)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));
                actor.setPosition(eye.x,eye.y-actor.getStandingEyeHeight(),eye.z);Vec3d direction=center.subtract(eye);actor.setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));actor.setHeadYaw(actor.getYaw());actor.setPitch((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
                JsonObject attempt=new JsonObject();attempt.addProperty("side",side.asString());attempt.addProperty("hit",hit.getType().name());if(hit.getType()==HitResult.Type.BLOCK)attempt.add("position",coordinates(hit.getBlockPos()));ObjectInstance.Owner selected=hit.getType()==HitResult.Type.BLOCK?CompositeRuntime.target(world,hit.getBlockPos(),actor):null;attempt.addProperty("retainedDSelected",owner.equals(selected));attempts.add(attempt);
                if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(point)&&selected==null){result.addProperty("mode","actual background outline ray + server interaction API");return hit;}
            }
            if(state.getBlock().getOutlineShape(state,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent()).isEmpty()){actor.setPosition(standRoot.getX()+8,standRoot.getY()+6,standRoot.getZ()+8);actor.setYaw(0);actor.setPitch(-90);result.addProperty("mode","server interaction API; exact background outline EMPTY, no ray PASS claimed");return null;}
            throw new IllegalStateException("No independent background face is accessible under the declared six-side authored-centre search");
        }
        Map<BlockPos,JsonObject> snapshotMembers(){Map<BlockPos,JsonObject> result=new LinkedHashMap<>();for(BlockPos point:members)result.put(point,cell(point));return result;}
        int bridgeIndex(CompositeBlockEntity helper){try{return (Integer)call(helper,"nativeGridBase");}catch(Exception failure){throw new IllegalStateException("Bound candidate lacks its existing shared-base API",failure);}}
        void assertOutsideAir(){Set<BlockPos> exact=new HashSet<>(members);if(legacyWhole!=null)exact.addAll(legacyWhole.savedCells());for(BlockPos point:BlockPos.iterate(standMin,standMax)){allowed(point);require(exact.contains(point)||empty(point),"Unrecognized data outside the declared finite related fixtures");}}
        void cleanup(){if(legacyWhole!=null)legacyWhole.cleanup();if(owner==null||foreignFinished)return;if(world.getBlockEntity(standRoot) instanceof CompositeBlockEntity root&&owner.equals(root.resident())){TransactionCore.Result result=CompositeRuntime.remove(world,owner,null,false);detail.add("cleanup",transaction(result));save();require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Foreign fixture cleanup refused: "+result.reason());}else require(members.stream().allMatch(point->CompositeRuntime.contributions(world,point).isEmpty()),"Refuse cleanup of orphaned/unrecognized D memberships");for(var entry:backgrounds.entrySet())if(world.getBlockState(entry.getKey()).equals(entry.getValue())&&CompositeRuntime.contributions(world,entry.getKey()).isEmpty())actor.interactionManager.tryBreakBlock(entry.getKey());}
        BlockState exactState(JsonObject row){Identifier id=new Identifier(row.get("registryId").getAsString());require(Registries.BLOCK.containsId(id),"Unknown exact D registry id");BlockState state=Registries.BLOCK.get(id).getDefaultState();for(var entry:row.getAsJsonObject("properties").entrySet()){Property<?> property=state.getBlock().getStateManager().getProperty(entry.getKey());require(property!=null,"Unknown exact D property");state=withProperty(state,property,entry.getValue().getAsString());}require(canonicalState(id.toString(),state).equals(row),"D property tuple is not exact/canonical");return state;}
        private <T extends Comparable<T>> BlockState withProperty(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}

        /** The exact retained W component shares this fixture's save/reopen.
         * Native source cells are authorized setup; item placement is ordinary. */
        private final class LegacyWholeItemFixture {
            private static final String POSE_KEY="p17ee1b36da539f69c340",SOURCE_PROOF_SHA="de0cf82fb53bb61beea39ebaefba67e911b1622c7525554ddd76b2fc2ec41d28";
            private final JsonObject binding,result=new JsonObject();
            private final StaticArchitectureCatalog.Pose pose;
            private final Map<BlockPos,BlockState> nativeStates=new LinkedHashMap<>();
            private final BlockPos nativeRoot=standRoot.add(0,8,0),placementRoot=nativeRoot,backgroundPoint=nativeRoot.up();
            private final BlockState background;
            private final Class<?> itemApi;
            private final Set<UUID> ownedDrops=new HashSet<>();
            private ObjectInstance.Owner placedOwner;
            private ObjectInstance placedInstance;
            private Map<BlockPos,JsonObject> savedNative;
            LegacyWholeItemFixture(JsonObject input)throws Exception{
                binding=input;require(text(input,"legacyPose","").equals(POSE_KEY)&&input.getAsJsonObject("sourceRecipeProof").get("sha256").getAsString().equals(SOURCE_PROOF_SHA),"Only the addressed native W source component is a gameplay fixture");
                JsonObject source=readBoundJson(input.getAsJsonObject("sourceRecipeProof")),resourceProof=readBoundJson(input.getAsJsonObject("resourceProof"));
                require(text(source,"status","").equals("READ_ONLY_EXACT_NATIVE_STATUE_MEMBER_AND_VISUAL_PROOF_NOT_ADMISSION")&&source.get("sourceBinding").equals(proof.get("sourceBinding")),"W source recipes belong to another immutable source");
                require(text(resourceProof,"status","").equals("PASS_EXACT_RETAINED_NATIVE_WHOLE_ITEM_RESOURCE_NOT_GAMEPLAY")&&text(resourceProof,"kind","").equals("FINITE_OBSERVED_RETAINED_NATIVE_WHOLE_ITEMS")&&resourceProof.get("sourceBinding").equals(source.get("sourceBinding"))&&resourceProof.get("originalV12Excluded").getAsBoolean()&&resourceProof.getAsJsonObject("resource").equals(input.getAsJsonObject("resource")),"Retained item list is not actual finite FULL authoring");
                JsonObject whitelist=readBoundJson(input.getAsJsonObject("resource"));require(whitelist.get("schemaVersion").getAsInt()==1&&java.util.stream.StreamSupport.stream(whitelist.getAsJsonArray("poses").spliterator(),false).map(JsonElement::getAsJsonObject).anyMatch(row->row.get("legacyPose").getAsString().equals(POSE_KEY)),"Actual retained item resource omits the representative W");
                pose=StaticArchitectureCatalog.pose(POSE_KEY);itemApi=Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridRetainedWholeItems");
                require(itemApi.getMethod("resourceSha256").invoke(null).equals(input.getAsJsonObject("resource").get("sha256").getAsString())&&(Boolean)itemApi.getMethod("allowed",StaticArchitectureCatalog.Pose.class).invoke(null,pose),"Loaded runtime retained-item whitelist differs");
                require(pose.mountY()==0&&pose.state().get(CompositeRootBlock.ROTATION)==4&&pose.state().get(CompositeRootBlock.VARIANT)==1&&pose.block().spec.id.toString().equals("bloodborne_dw:source_hold_statue"),"Exact W art/mount changed");
                require(pose.block().getClass()==ImportedRootBlock.class&&pose.block().asItem().getClass()==CompositeItem.class&&MaterialSlabs.replacement(pose.block(),item())==null,"Exact W canonical item no longer follows the existing ImportedRootBlock/CompositeItem transaction placement path");
                var helperSource=StaticArchitectureCatalog.recipes().get(8037);require(helperSource.removal(1)==7177&&helperSource.removal(0)==1901,"Source W/independent-part finite removal closure changed");
                nativeStates.put(nativeRoot.down(),StaticArchitectureCatalog.state(7177));nativeStates.put(nativeRoot,StaticArchitectureCatalog.state(7176));
                require(pose.footprint().keySet().equals(Set.of(new dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell(0,-1,0),dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)),"W no longer has the two source-bound native members");
                for(var entry:nativeStates.entrySet()){allowed(entry.getKey());require(inside(entry.getKey())&&!members.contains(entry.getKey()),"W setup overlaps another fixture or the stand edge");var recipe=StaticArchitectureCatalog.recipe(entry.getValue());require(recipe.fragments().size()==1&&recipe.fragments().get(0).pose()==pose&&entry.getKey().add(recipe.fragments().get(0).origin().x(),recipe.fragments().get(0).origin().y(),recipe.fragments().get(0).origin().z()).equals(nativeRoot),"W-only fixture recipe introduces an independent phantom component");}
                JsonObject neighbour=input.getAsJsonObject("visualNeighbourBackground");require(neighbour.equals(proof.getAsJsonObject("retainedNativeVisualBackground"))&&pos(neighbour.getAsJsonArray("sourcePosition")).equals(new BlockPos(-100,114,-643))&&pos(neighbour.getAsJsonArray("offset")).equals(new BlockPos(0,1,0))&&text(neighbour,"carrierMode","").equals("plain_native_visual_neighbour")&&neighbour.get("baseExcludesW").getAsBoolean()&&neighbour.getAsJsonObject("sourceRecipeProof").equals(input.getAsJsonObject("sourceRecipeProof")),"W visual-only neighbour must be the exact actual final C placement, with no W geometry compiled");
                background=(BlockState)Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeGridCatalog").getMethod("state",int.class).invoke(null,neighbour.get("cell").getAsInt());
                require(nativeGrid(background)&&nativeMembers(background)==null&&!background.hasBlockEntity()&&background.getFluidState().isEmpty()&&background.getLuminance()==0&&same(background.getBlock().getCollisionShape(background,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent()),shape(neighbour.getAsJsonArray("collision")))&&same(background.getBlock().getOutlineShape(background,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent()),shape(neighbour.getAsJsonArray("outline"))),"W visual neighbour differs from its final dry plain catalog cell/shapes");
                allowed(backgroundPoint);require(inside(backgroundPoint)&&!nativeStates.containsKey(backgroundPoint)&&!members.contains(backgroundPoint),"W visual neighbour is not a separate bounded stand cell");
                allowed(placementRoot);require(inside(placementRoot),"W item placement root outside the existing stand");
                result.add("binding",input.deepCopy());result.add("nativeRoot",coordinates(nativeRoot));result.add("itemPlacementRoot",coordinates(placementRoot));result.add("backgroundPoint",coordinates(backgroundPoint));result.addProperty("nativeSetupStateWrites",2);result.addProperty("backgroundSetupStateWrites",1);result.addProperty("exactCanonicalPlacementPath","ImportedRootBlock / CompositeItem / CompositeRuntime.place");result.addProperty("independentSourcePartNotInstalled",true);result.addProperty("gameplayComplete",false);detail.add("retainedNativeWholeItem",result);
            }
            ItemStack item()throws Exception{return (ItemStack)itemApi.getMethod("item",StaticArchitectureCatalog.Pose.class).invoke(null,pose);}
            ItemStack pick(BlockPos point)throws Exception{return (ItemStack)itemApi.getMethod("pick",World.class,BlockPos.class,net.minecraft.entity.player.PlayerEntity.class).invoke(null,world,point,actor);}
            Set<BlockPos> savedCells(){Set<BlockPos> cells=new LinkedHashSet<>(nativeStates.keySet());cells.add(backgroundPoint);return cells;}
            void assertBackground(String stage){check(world.getBlockState(backgroundPoint).equals(background)&&world.getBlockEntity(backgroundPoint)==null&&CompositeRuntime.contributions(world,backgroundPoint).isEmpty(),"actual final C background remains exact plain NativeGrid/no-BE/no-W-ledger "+stage);}
            void install(){guard(savedCells());for(var entry:nativeStates.entrySet())require(world.setBlockState(entry.getKey(),entry.getValue(),Block.FORCE_STATE|Block.NOTIFY_ALL|Block.SKIP_DROPS)||world.getBlockState(entry.getKey()).equals(entry.getValue()),"Own W native setup refused");require(world.setBlockState(backgroundPoint,background,Block.FORCE_STATE|Block.NOTIFY_ALL|Block.SKIP_DROPS)||world.getBlockState(backgroundPoint).equals(background),"Own actual final C background setup refused");assertBackground("at W setup");result.addProperty("setupComplete",true);save();}
            void assertNative(String stage){
                for(var entry:nativeStates.entrySet())require(world.getBlockState(entry.getKey()).equals(entry.getValue())&&world.getBlockEntity(entry.getKey())==null&&CompositeRuntime.contributions(world,entry.getKey()).isEmpty(),"Retained native W state/empty ledger changed "+stage);
                require(StaticArchitectureCatalog.matchesRoot(world.getBlockState(nativeRoot),pose),"Canonical W native resident disappeared "+stage);
                VoxelShape collision=VoxelShapes.empty(),outline=VoxelShapes.empty();for(var entry:pose.footprint().entrySet()){var at=entry.getKey();collision=VoxelShapes.union(collision,ImportedPhysicalShapes.exact(entry.getValue().collision()).offset(at.x(),at.y(),at.z()));outline=VoxelShapes.union(outline,ImportedPhysicalShapes.exact(pose.outline().getOrDefault(at,List.of())).offset(at.x(),at.y(),at.z()));}
                check(same(union(new ArrayList<>(nativeStates.keySet()),nativeRoot,true),collision)&&same(union(new ArrayList<>(nativeStates.keySet()),nativeRoot,false),outline),"retained old-native W collision/outline equals the canonical source component "+stage);
                assertBackground(stage);
            }
            Map<BlockPos,JsonObject> snapshot(){Map<BlockPos,JsonObject> rows=new LinkedHashMap<>();for(BlockPos point:savedCells())rows.put(point,cell(point));return rows;}
            void savedAfterCallback(){assertNative("after setup callback tick");savedNative=snapshot();result.add("savedCells",snapshots(savedNative));result.addProperty("standRetainedForReopen",true);save();}
            void prepareReopen(JsonObject previous){require(previous.getAsJsonObject("binding").equals(binding)&&previous.get("standRetainedForReopen").getAsBoolean()&&!previous.get("gameplayComplete").getAsBoolean(),"Saved native whole fixture/proofs changed");savedNative=new LinkedHashMap<>();for(JsonElement raw:previous.getAsJsonArray("savedCells")){JsonObject row=raw.getAsJsonObject();savedNative.put(pos(row.getAsJsonArray("pos")),row.getAsJsonObject("reference"));}require(savedNative.keySet().equals(savedCells()),"Native W/background saved snapshots are incomplete");check(savedNative.entrySet().stream().allMatch(row->cell(row.getKey()).equals(row.getValue())),"real server reopen preserves both exact old-native W recipes and actual final C background with no BE/UUID ledger");assertNative("after real reopen");result.add("reopenedCells",snapshots(snapshot()));result.addProperty("realReopenVerified",true);}
            BlockHitResult nativeRay(BlockPos target,JsonArray attempts){for(Box box:world.getBlockState(target).getOutlineShape(world,target).getBoundingBoxes())for(Direction side:Direction.values()){
                Vec3d center=box.getCenter().add(target.getX(),target.getY(),target.getZ()),delta=new Vec3d(side.getOffsetX(),side.getOffsetY(),side.getOffsetZ()),eye=center.add(delta.multiply(3));actor.setPosition(eye.x,eye.y-actor.getStandingEyeHeight(),eye.z);Vec3d direction=center.subtract(eye);actor.setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));actor.setHeadYaw(actor.getYaw());actor.setPitch((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));BlockHitResult hit=world.raycast(new RaycastContext(eye,center.subtract(delta.multiply(.25)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));var selected=hit.getType()==HitResult.Type.BLOCK?StaticArchitectureRuntime.target(world,hit.getBlockPos(),actor):null;JsonObject row=new JsonObject();row.addProperty("side",side.asString());row.addProperty("hit",hit.getType().name());if(hit.getType()==HitResult.Type.BLOCK)row.add("position",coordinates(hit.getBlockPos()));row.addProperty("selectedW",selected!=null&&selected.pose()==pose&&selected.root().equals(nativeRoot));attempts.add(row);if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(target)&&selected!=null&&selected.pose()==pose&&selected.root().equals(nativeRoot))return hit;
            }return null;}
            void capturePlaced(){result.add("actualInstalledRootBeforeGuard",cell(placementRoot));if(world.getBlockEntity(placementRoot) instanceof CompositeBlockEntity root&&root.resident()!=null){placedOwner=root.resident();placedInstance=CompositeRuntime.instance(world,placementRoot,world.getBlockState(placementRoot),placedOwner.instanceId(),root.payload());JsonArray rows=new JsonArray();for(var at:placedInstance.cells().keySet()){BlockPos point=placementRoot.add(at.x(),at.y(),at.z());JsonObject row=new JsonObject();row.add("position",coordinates(point));boolean bounded=inside(point)&&allowedChunks.contains(new ChunkPos(point).toLong());row.addProperty("insideGuard",bounded);if(bounded)row.add("reference",cell(point));rows.add(row);}result.addProperty("placedOwner",placedOwner.instanceId().toString());result.add("actualInstalledCellsBeforeGuard",rows);}save();}
            List<net.minecraft.entity.ItemEntity> drops(){return world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(nativeRoot).expand(4),entity->true);}
            void finishReopened()throws Exception{
                assertNative("before native whole pick/break");ItemStack expected=item();require(!expected.isEmpty()&&expected.getCount()==1&&expected.getItem() instanceof BlockItem,"Canonical W item is not usable");JsonArray searched=new JsonArray();BlockHitResult chosen=null;
                for(BlockPos point:nativeStates.keySet()){JsonObject part=new JsonObject();part.add("position",coordinates(point));JsonArray attempts=new JsonArray();part.add("attempts",attempts);BlockHitResult hit=nativeRay(point,attempts);part.addProperty("accessible",hit!=null);if(hit!=null){check(ItemStack.areEqual(pick(point),expected),"accessible old-native W part picks the same one canonical source item at "+point);if(chosen==null||!point.equals(nativeRoot))chosen=hit;}searched.add(part);}result.add("nativePartSearch",searched);require(chosen!=null,"No retained native W part is accessible");
                assertBackground("after native W part picking");require(nativeRay(chosen.getBlockPos(),new JsonArray())!=null,"Chosen native W ray changed before removal");require(world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS),"Survival drop QA requires actual tileDrops=true");Set<UUID> before=new HashSet<>();for(var entity:drops())before.add(entity.getUuid());TransactionCore.Result old=CompositeRuntime.lastResult();actor.changeGameMode(GameMode.SURVIVAL);try{performBreak(chosen.getBlockPos(),chosen);}finally{actor.changeGameMode(GameMode.CREATIVE);}TransactionCore.Result removed=CompositeRuntime.lastResult();require(removed!=old&&removed!=null&&removed.outcome()==TransactionCore.Outcome.COMMITTED&&removed.changedCells()==2,"Native W whole removal did not commit its two real members");check(nativeStates.keySet().stream().allMatch(NativeGridFunctionChecks.this::empty),"one real native W part break removes both source-bound component cells");assertBackground("after original native W whole break");List<net.minecraft.entity.ItemEntity> fresh=drops().stream().filter(entity->!before.contains(entity.getUuid())).toList();for(var entity:fresh)if(ItemStack.areEqual(entity.getStack(),expected))ownedDrops.add(entity.getUuid());check(fresh.size()==1&&fresh.get(0).getStack().getCount()==1&&ItemStack.areEqual(fresh.get(0).getStack(),expected),"committed survival native whole break drops exactly one canonical source item");ItemStack dropped=fresh.get(0).getStack().copy();result.addProperty("canonicalItem",Registries.ITEM.getId(dropped.getItem()).toString());result.addProperty("canonicalItemNbt",String.valueOf(dropped.getNbt()));fresh.get(0).discard();
                require(empty(placementRoot),"Canonical W item target is no longer fresh AIR");actor.setYaw(pose.state().get(CompositeRootBlock.ROTATION)*45F);actor.setHeadYaw(actor.getYaw());actor.setSneaking(false);actor.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);try{placeItem(dropped.copy(),placementRoot);}finally{capturePlaced();}
                require(placedOwner!=null&&placedInstance!=null&&world.getBlockState(placementRoot).getBlock()==pose.block()&&world.getBlockEntity(placementRoot) instanceof CompositeBlockEntity,"Exact W canonical item did not use its proven ImportedRootBlock/CompositeItem transaction path");require(placedOwner.registryId().equals(pose.block().spec.id.toString()),"W canonical item installed another registry");for(var at:placedInstance.cells().keySet()){BlockPos point=placementRoot.add(at.x(),at.y(),at.z());require(inside(point)&&!members.contains(point)&&!point.equals(backgroundPoint),"Actual W item footprint escaped the guarded unused stand or touched the separate C background");allowed(point);}var installedRoot=(CompositeBlockEntity)world.getBlockEntity(placementRoot);check(world.getBlockState(placementRoot).equals(pose.state())&&ItemStack.areEqual(((CompositeRootBlock)world.getBlockState(placementRoot).getBlock()).art(world.getBlockState(placementRoot),installedRoot.payload()),expected),"ordinary canonical W item preserves the exact registered artwork/yaw/SourceShift without source privileges");assertBackground("after ordinary canonical W reinstallation at the same root");List<BlockPos> placedCells=placedInstance.cells().keySet().stream().map(at->placementRoot.add(at.x(),at.y(),at.z())).toList();BlockPos selectable=NativeGridFunctionChecks.this.selectable(placedCells,false);breakByRay(selectable);check(placedCells.stream().allMatch(NativeGridFunctionChecks.this::empty),"placed canonical W source item remains one whole object on real ray break");assertBackground("after reinstalled W whole removal");result.addProperty("backgroundSurvivedNativePickBreakAndReinstallation",true);result.add("backgroundBeforeIndependentRemoval",cell(backgroundPoint));result.addProperty("backgroundIndependentBreakApiReturn",actor.interactionManager.tryBreakBlock(backgroundPoint));check(empty(backgroundPoint),"only the final independent background break clears the exact C stand cell");result.addProperty("standRetainedForReopen",false);result.addProperty("gameplayComplete",true);result.addProperty("survivalDropCount",1);save();
            }
            void cleanup(){for(var entity:drops())if(ownedDrops.contains(entity.getUuid()))entity.discard();if(placedOwner!=null&&world.getBlockEntity(placementRoot) instanceof CompositeBlockEntity root&&placedOwner.equals(root.resident())){for(var at:placedInstance.cells().keySet()){BlockPos point=placementRoot.add(at.x(),at.y(),at.z());require(inside(point)&&!point.equals(backgroundPoint),"Refuse cleanup outside the test-owned W cells");allowed(point);}var removed=CompositeRuntime.remove(world,placedOwner,null,false);require(removed.outcome()==TransactionCore.Outcome.COMMITTED,"Own placed W cleanup refused");}if(!nativeStates.keySet().stream().allMatch(NativeGridFunctionChecks.this::empty)){for(var entry:nativeStates.entrySet())require(world.getBlockState(entry.getKey()).equals(entry.getValue())&&world.getBlockEntity(entry.getKey())==null&&CompositeRuntime.contributions(world,entry.getKey()).isEmpty(),"Refuse cleanup of unrecognized native W data");var removed=StaticArchitectureRuntime.remove(world,nativeRoot,pose);require(removed.outcome()==TransactionCore.Outcome.COMMITTED&&nativeStates.keySet().stream().allMatch(NativeGridFunctionChecks.this::empty),"Own native W cleanup refused");}if(!empty(backgroundPoint)){assertBackground("before owned fixture cleanup");actor.interactionManager.tryBreakBlock(backgroundPoint);require(empty(backgroundPoint),"Own C background cleanup refused");}}
        }
    }
    private void finish(MinecraftServer server,Throwable failure){if(done)return;done=true;try{cleanupActive();if(standInitiallyAir&&!standRestored&&!(failure==null&&foreignGuestFixture!=null&&foreignGuestFixture.savedForReopen)){standRestored=standEmpty();check(standRestored,"stand state/NBT/owners restored at completion");}}catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}if(actor!=null)actor.getTextStream().onDisconnect();if(report==null)report=new JsonObject();report.addProperty("status",failure==null?"PASS":"FAIL");report.addProperty("standRestored",standRestored);if(failure!=null){failure.printStackTrace();report.addProperty("error",failure.toString());}save();server.stop(false);}
    private void save(){try{Files.writeString(output.resolve("functions.json"),JSON.toJson(report)+"\n");}catch(IOException failure){throw new UncheckedIOException(failure);}}
    private static Path ordinary(Path path)throws IOException{Path absolute=path.toAbsolutePath().normalize();for(Path p=absolute;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))require(!Files.isSymbolicLink(p)&&!Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther(),"Aliased QA path refused: "+p);Path real=absolute.toRealPath();require(real.equals(absolute),"Resolved QA path differs");return real;}
    private static String sha(Path file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=Files.newInputStream(file)){byte[] buffer=new byte[1<<20];for(int n;(n=stream.read(buffer))>=0;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
}
