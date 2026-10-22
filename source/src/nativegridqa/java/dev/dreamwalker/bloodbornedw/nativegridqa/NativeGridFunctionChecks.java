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
    private JsonObject plan, report;
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
        require(previous.get("planSha256").getAsString().equals(bound.getAsJsonObject("previousPlan").get("sha256").getAsString())&&previous.get("productionSha256").equals(plan.get("productionSha256")),"Continuation evidence belongs to another plan/runtime");
        JsonObject current=plan.deepCopy(),prior=parentPlan.deepCopy();current.remove("continuation");current.remove("output");prior.remove("output");require(current.equals(prior),"Continuation may change only output and its explicit evidence binding");
        JsonArray oldActions=previous.getAsJsonArray("actions");require(oldActions.size()==1&&text(oldActions.get(0).getAsJsonObject(),"kind","").equals("staticCell")&&text(oldActions.get(0).getAsJsonObject(),"status","").equals("PASS"),"Only the failed door plus not-yet-run tree/statue may continue");
        JsonArray inherited=new JsonArray();int failures=0;
        for(JsonElement raw:previous.getAsJsonArray("checks")){JsonObject row=raw.getAsJsonObject();if(row.get("pass").getAsBoolean())inherited.add(row.deepCopy());else{failures++;require(row.get("name").getAsString().startsWith("registered picked BlockItem places at "),"Continuation contains another failed check");}}
        require(failures==1&&inherited.size()==bound.get("inheritedSuccessfulChecks").getAsInt(),"Continuation successful-check count changed");
        if(bound.has("diagnosticPreviousAttempt")){JsonObject diagnostic=readBoundJson(bound.getAsJsonObject("diagnosticPreviousAttempt"));require(text(diagnostic,"status","").equals("FAIL")&&diagnostic.get("standRestored").getAsBoolean()&&diagnostic.get("normalServerThreadFinished").getAsBoolean()&&diagnostic.get("productionSha256").equals(plan.get("productionSha256")),"The intervening failed diagnostic run must remain exact, restored and normally stopped");}
        report.add("continuation",bound.deepCopy());report.addProperty("priorRunStatus","FAIL");report.add("inheritedSuccessfulChecks",inherited);report.add("inheritedSuccessfulActions",oldActions.deepCopy());continuation=true;
    }
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
        for(BlockPos point:BlockPos.iterate(standMin,standMax)) { allowed(point); require(empty(point),"Stand is not genuinely empty: "+point); }
        require(inside(standRoot),"Stand root outside guard");
        standInitiallyAir=true;report.addProperty("standAirCellsVerified",volume);report.add("stand",stand.deepCopy());
        actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"NativeGridProbe")) {
            @Override public boolean hasPermissionLevel(int level) { return level<=4; }
            @Override public void sendMessage(Text message,boolean overlay) { if(placementMessages!=null)placementMessages.add(message.getString()); }
        };
        new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),actor);
        actor.changeGameMode(GameMode.CREATIVE); actor.setPosition(standRoot.getX()+6,standRoot.getY(),standRoot.getZ()+6);
        report.addProperty("status","RUNNING"); save();
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
        activeCleanup=()->{if(!source.nativeGrid()){ObjectInstance.Owner owner=installed[0];if(owner==null&&world.getBlockEntity(standRoot) instanceof CompositeBlockEntity entity)owner=entity.resident();if(owner!=null)CompositeRuntime.remove(world,owner,null,false);}else for(BlockPos point:guardedFootprint)if(nativeGrid(world.getBlockState(point)))world.breakBlock(point,false);if(bridge[0]!=null&&world.getBlockState(bridge[0].pos()).equals(bridge[0].background())&&CompositeRuntime.contributions(world,bridge[0].pos()).isEmpty())world.breakBlock(bridge[0].pos(),false);};
        try{
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
                boolean before=world.getBlockState(standRoot).get(CompositeRootBlock.OPEN);useByRay(footprint);
                check(world.getBlockState(standRoot).get(CompositeRootBlock.OPEN)!=before,"door right click changes one whole door open state");
                VoxelShape open=union(footprint,standRoot,true);openCollision=open;openOutline=union(footprint,standRoot,false);check(!same(open,closed),"door open collision moves with the leaf");
                useByRay(footprint);check(world.getBlockState(standRoot).get(CompositeRootBlock.OPEN)==before&&same(union(footprint,standRoot,true),closed),"door second right click restores closed-state exact collision");
            }
            if(candidate&&kind.equals("door")){
                // Repeat ordinary door motion with the actual persistent floor
                // helper background. Selection stays above that solid floor.
                require(input.has("bridgeFixture"),"Candidate door needs the one bound NativeGridBase fixture");
                require(openCollision!=null&&openOutline!=null,"The preceding AIR door cycle must supply exact open-shape references");
                bridge[0]=attachBridge(input.getAsJsonObject("bridgeFixture"),source,installed[0],guardedFootprint,bridge);
                BlockPos offset=bridge[0].pos().subtract(standRoot);
                VoxelShape expected=VoxelShapes.union(closed,bridge[0].collision().offset(offset.getX(),offset.getY(),offset.getZ()));
                VoxelShape expectedOutline=VoxelShapes.union(closedOutline,bridge[0].outline().offset(offset.getX(),offset.getY(),offset.getZ()));
                closed=union(footprint,standRoot,true);closedOutline=union(footprint,standRoot,false);
                check(same(closed,expected)&&same(closedOutline,expectedOutline),"candidate bridge is the exact shared background plus retained closed-door shape");
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
            List<BlockPos> doorTargets=bridge[0]==null?footprint:footprint.stream().filter(p->p.getY()!=bridge[0].pos().getY()).toList();
            BlockPos hit=selectable(kind.equals("door")?doorTargets:footprint,input.has("requireCrossChunk")&&input.get("requireCrossChunk").getAsBoolean());breakByRay(hit);
            for(BlockPos point:footprint)if(bridge[0]!=null&&point.equals(bridge[0].pos()))check(world.getBlockState(point).equals(bridge[0].background())&&world.getBlockEntity(point)==null,"whole door break retains only its exact ordinary shared background");else check(empty(point),"whole "+kind+" break clears every authored/helper cell at "+point);
            if(installed[0]!=null)for(BlockPos point:footprint)check(CompositeRuntime.contributions(world,point).stream().noneMatch(c->c.owner().instanceId().equals(installed[0].instanceId())),"whole "+kind+" break retires its local contributions");
            if(bridge[0]!=null){if(bridge[0].outline().isEmpty()){actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);check(actor.interactionManager.tryBreakBlock(bridge[0].pos()),"server interaction API accepts independent background break; exact original outline is EMPTY so no ray assertion applies");}else breakByRay(bridge[0].pos());check(empty(bridge[0].pos()),"ordinary independent background break returns the bridge fixture cell to AIR");}
            JsonObject result=new JsonObject();result.addProperty("kind",kind);result.addProperty("status","PASS");result.add("source",coordinates(source.root()));result.addProperty("picked",picked.toString());result.addProperty("cells",source.offsets().size());result.add("collision",boxes(closed));result.add("outline",boxes(closedOutline));result.add("breakCell",coordinates(hit));result.addProperty("placementPath","registered picked item.useOnBlock -> BlockItem.place");actions.add(result);
            if(bridge[0]!=null){JsonObject bound=new JsonObject();bound.addProperty("cell",bridge[0].index());bound.add("pos",coordinates(bridge[0].pos()));bound.add("helperOffset",coordinates(bridge[0].pos().subtract(standRoot)));bound.addProperty("persistentInBothStates",true);bound.addProperty("installedOnFreshOwnedHelperOnly",true);bound.addProperty("openCloseScope","normal AIR cycle followed by actual open/close with the bound persistent floor-helper background; selection above floor");bound.addProperty("backgroundRestorePath","existing transaction adapter across open/close and whole break; final independent ordinary break");bound.addProperty("independentBreakMode",bridge[0].outline().isEmpty()?"server interaction API; ray not applicable: exact original outline EMPTY":"native selection ray + server interaction API");result.add("bridgeFixture",bound);}
        }finally{cleanupActive();check(guardedFootprint.stream().allMatch(this::empty)&&standEmpty(),kind+" fixture returns its actual cells and complete finite stand exactly");}
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
        require(state.isOf(source.state().getBlock())&&state.get(CompositeRootBlock.ROTATION).equals(source.state().get(CompositeRootBlock.ROTATION))&&state.get(CompositeRootBlock.VARIANT).equals(source.state().get(CompositeRootBlock.VARIANT)),"Manual door must retain the bound source art and yaw");
        var oldShift=CompositeSourceShift.world(source.state(),source.payload());var newShift=CompositeSourceShift.world(state,payload);
        double x=sourceOffset.getX()+newShift.x()-oldShift.x(),y=sourceOffset.getY()+newShift.y()-oldShift.y()+payload.getDouble("MountY")-source.payload().getDouble("MountY"),z=sourceOffset.getZ()+newShift.z()-oldShift.z();
        require(Math.abs(x-Math.rint(x))<1e-7&&Math.abs(y-Math.rint(y))<1e-7&&Math.abs(z-Math.rint(z))<1e-7,"Manual persistent helper translation must be integral");
        BlockPos offset=new BlockPos((int)Math.rint(x),(int)Math.rint(y),(int)Math.rint(z));
        JsonObject helperPlacement=new JsonObject();helperPlacement.add("originalSourceHelperOffset",coordinates(sourceOffset));helperPlacement.add("actualManualHelperOffset",coordinates(offset));helperPlacement.addProperty("manualSourceShiftCopied",false);report.add("bridgePlacement",helperPlacement);
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
    private void breakByRay(BlockPos target){refreshStandChunks("selected break");BlockHitResult hit=ray(target);actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);actor.interactionManager.tryBreakBlock(hit.getBlockPos());}
    private BlockHitResult ray(BlockPos target){List<Box> selection=world.getBlockState(target).getOutlineShape(world,target).getBoundingBoxes();require(!selection.isEmpty(),"No native selection at "+target);Vec3d center=selection.get(0).getCenter().add(target.getX(),target.getY(),target.getZ());Vec3d eye=center.add(0,0,-3);actor.setPosition(eye.x,eye.y-actor.getStandingEyeHeight(),eye.z);Vec3d delta=center.subtract(eye);actor.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));actor.setHeadYaw(actor.getYaw());actor.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));var hit=world.raycast(new RaycastContext(eye,center.add(0,0,1),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));require(hit.getType()==HitResult.Type.BLOCK&&inside(hit.getBlockPos()),"Native selection ray missed guarded object");return hit;}
    private BlockPos selectable(List<BlockPos> footprint,boolean across){return footprint.stream().filter(p->!world.getBlockState(p).getOutlineShape(world,p).isEmpty()).sorted(Comparator.comparingInt(p->across&&new ChunkPos(p).toLong()!=new ChunkPos(standRoot).toLong()?0:1)).findFirst().orElseThrow(()->new IllegalStateException("No selectable whole-object cell"));}
    private void cleanupActive(){Runnable cleanup=activeCleanup;activeCleanup=null;if(cleanup!=null)cleanup.run();}
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
    private void finish(MinecraftServer server,Throwable failure){if(done)return;done=true;try{cleanupActive();if(standInitiallyAir&&!standRestored){standRestored=standEmpty();check(standRestored,"stand state/NBT/owners restored at completion");}}catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}if(actor!=null)actor.getTextStream().onDisconnect();if(report==null)report=new JsonObject();report.addProperty("status",failure==null?"PASS":"FAIL");report.addProperty("standRestored",standRestored);if(failure!=null){failure.printStackTrace();report.addProperty("error",failure.toString());}save();server.stop(false);}
    private void save(){try{Files.writeString(output.resolve("functions.json"),JSON.toJson(report)+"\n");}catch(IOException failure){throw new UncheckedIOException(failure);}}
    private static Path ordinary(Path path)throws IOException{Path absolute=path.toAbsolutePath().normalize();for(Path p=absolute;p!=null;p=p.getParent())if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))require(!Files.isSymbolicLink(p)&&!Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther(),"Aliased QA path refused: "+p);Path real=absolute.toRealPath();require(real.equals(absolute),"Resolved QA path differs");return real;}
    private static String sha(Path file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream stream=Files.newInputStream(file)){byte[] buffer=new byte[1<<20];for(int n;(n=stream.read(buffer))>=0;)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
}
