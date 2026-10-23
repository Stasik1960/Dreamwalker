package dev.dreamwalker.bloodbornedw.nativegrid;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Four source-bound legacy pairs, using only the existing approved matcher.
 * Opt-in full-city helper phase; it is absent from the production mod.
 * No done row may be used until every actual merged anchor has been checked.
 */
final class NativeGridAssemblyRelight {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("native_grid_assembly_light", Comparator.comparingLong(ChunkPos::toLong));
    private final MinecraftServer server;
    private final Path phaseFile, requestPath, worldPath;
    private final String requestHash, runtimeHash, catalogHash;
    private final List<JsonObject> groups = new ArrayList<>();
    private final List<JsonObject> outcomes = new ArrayList<>();
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final List<CompletableFuture<Chunk>> loading = new ArrayList<>();
    private final boolean resumedSavedRows;
    private ServerWorld world;
    private int cursor, state, ticks, started;
    private boolean complete;
    private String lastResult = "not_attempted";

    NativeGridAssemblyRelight(MinecraftServer server, JsonObject binding, Set<String> queued,
            boolean resumedSavedRows, Path worldPath, String runtimeHash, String catalogHash, Path output) throws Exception {
        this.server=server; this.resumedSavedRows=resumedSavedRows; this.worldPath=worldPath;
        this.runtimeHash=runtimeHash; this.catalogHash=catalogHash;
        require(binding.has("fullCityFreshLight") && binding.get("fullCityFreshLight").getAsBoolean(), "Assembly phase is full-city only");
        JsonObject reference=binding.getAsJsonObject("assemblyRequest");require(reference!=null,"Full-city light requires its exact four-assembly request");
        requestPath=Path.of(reference.get("path").getAsString()).toAbsolutePath().normalize();requestHash=reference.get("sha256").getAsString();
        require(requestPath.getParent().equals(output.getParent()) && Files.size(requestPath)<=1024*1024 && hash(requestPath).equals(requestHash),"Assembly request path/bytes differ");
        phaseFile=output.resolveSibling(output.getFileName()+".assemblies.json");
        require(phaseFile.equals(Path.of(binding.get("assemblyPhaseReceipt").getAsString()).toAbsolutePath().normalize()),"Assembly phase namespace differs");
        JsonObject request=GSON.fromJson(Files.readString(requestPath),JsonObject.class);
        require(request.get("schemaVersion").getAsInt()==1 && request.get("kind").getAsString().equals("EXACT_FOUR_ORIGINAL_ASSEMBLIES_BEFORE_FULL_RELIGHT") && request.get("freshFullLightOnly").getAsBoolean(),"Unknown assembly request schema");
        require(worldPath.equals(Path.of(request.get("target").getAsString()).toAbsolutePath().normalize()) && runtimeHash.equals(request.getAsJsonObject("runtime").get("sha256").getAsString()) && catalogHash.equals(request.getAsJsonObject("catalog").get("sha256").getAsString()),"Assembly request belongs to another world/runtime/catalogue");
        require(request.get("requiredGroups").getAsInt()==4 && request.get("requiredOriginalOwners").getAsInt()==8 && request.getAsJsonArray("groups").size()==4,"Only the four proved assembly pairs are authorized");
        JsonObject algorithm=request.getAsJsonObject("matcherAlgorithm");
        try(InputStream stream=ImportedAssemblyV126.class.getResourceAsStream("/"+algorithm.get("classResource").getAsString())) {
            require(stream!=null && hash(stream).equals(algorithm.get("originalClassSha256").getAsString()),"Loaded approved assembly matcher changed");
        }
        Set<String> keys=new HashSet<>();Set<UUID> owners=new HashSet<>();
        for(JsonElement value:request.getAsJsonArray("groups")) {
            JsonObject group=value.getAsJsonObject();require(keys.add(group.get("groupKey").getAsString()),"Repeated assembly group");
            require(group.getAsJsonArray("components").size()==2 && group.getAsJsonArray("retiredOwnerUuids").size()==1,"An approved pair must have one surviving primary and one retired upper");
            for(JsonElement part:group.getAsJsonArray("components"))require(owners.add(owner(part.getAsJsonObject()).instanceId()),"Repeated assembly resident identity");
            JsonObject primary=group.getAsJsonArray("components").get(0).getAsJsonObject();
            require(primary.get("ownerUuid").getAsString().equals(group.get("primaryOwnerUuid").getAsString()) && primary.get("registryId").getAsString().equals(group.get("expectedRegistryId").getAsString()) && primary.getAsJsonArray("root").equals(group.getAsJsonArray("root")),"Primary assembly identity/root mismatch");
            var recipe=ImportedAssemblyV126.recipes().stream().filter(r->r.key().equals(group.get("recipeKey").getAsString())).findFirst().orElseThrow(()->new IllegalStateException("Unknown exact original assembly recipe"));
            require(recipe.primaryRegistryId().equals(group.get("expectedRegistryId").getAsString()) && recipe.installationPose()==group.get("expectedInstallationPose").getAsInt(),"Loaded primary/installation pose differs from proof");
            Set<BlockPos> cells=cells(group);require(!cells.isEmpty() && cells.size()<=256 && cells.contains(pos(group.getAsJsonArray("root"))),"Unbounded/empty assembly own footprint");
            Set<Long> chunks=new HashSet<>();for(BlockPos at:cells)chunks.add(new ChunkPos(at).toLong());
            Set<Long> declared=new HashSet<>();for(JsonElement part:group.getAsJsonArray("ownedChunks")){JsonArray at=part.getAsJsonArray();require(at.size()==2,"Malformed assembly chunk");require(declared.add(ChunkPos.toLong(at.get(0).getAsInt(),at.get(1).getAsInt())),"Repeated assembly chunk");}
            require(chunks.equals(declared) && chunks.size()<=16,"Assembly tickets must equal its finite declared own chunks");
            groups.add(group);
        }
        for(JsonElement value:request.getAsJsonArray("requiredLightChunks")) {
            JsonObject row=value.getAsJsonObject();String line=row.get("dimension").getAsString()+"\t"+row.get("x").getAsInt()+"\t"+row.get("z").getAsInt();require(queued.contains(line),"Assembly/light neighbor is absent from the actual pending native queue: "+line);
        }
        if(Files.exists(phaseFile)) {
            JsonObject previous=GSON.fromJson(Files.readString(phaseFile),JsonObject.class);
            require(previous.get("requestSha256").getAsString().equals(requestHash) && previous.get("runtimeSha256").getAsString().equals(runtimeHash) && previous.get("catalogSha256").getAsString().equals(catalogHash),"Saved assembly phase belongs to another request/runtime/catalogue");
            if(resumedSavedRows)require(previous.get("status").getAsString().equals("PASS") && previous.get("saveFlushed").getAsBoolean(),"Saved native done rows lack a durable successful assembly phase");
        } else require(!resumedSavedRows,"Native done rows predate the mandatory actual assembly phase");
    }

    boolean tick() throws Exception {
        if(complete)return true;ticks++;
        if(cursor==groups.size()) {
            server.save(false,true,true);JsonObject phase=new JsonObject();phase.addProperty("schemaVersion",1);phase.addProperty("status","PASS");phase.addProperty("actualRuntimeQueries",true);phase.addProperty("saveFlushed",true);
            phase.addProperty("world",worldPath.toString());phase.addProperty("runtimeSha256",runtimeHash);phase.addProperty("catalogSha256",catalogHash);phase.addProperty("requestSha256",requestHash);phase.addProperty("beforeNativeDoneRows",!resumedSavedRows);phase.addProperty("resumedWorldReverified",resumedSavedRows);phase.add("outcomes",GSON.toJsonTree(outcomes));atomic(phaseFile,phase);complete=true;return true;
        }
        JsonObject group=groups.get(cursor);
        if(state==0) {
            world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(group.get("worldDimension").getAsString())));require(world!=null,"Assembly dimension is absent");
            for(JsonElement value:group.getAsJsonArray("ownedChunks")){JsonArray at=value.getAsJsonArray();ChunkPos chunk=new ChunkPos(at.get(0).getAsInt(),at.get(1).getAsInt());tickets.add(chunk);world.getChunkManager().addTicket(TICKET,chunk,0,chunk);}
            started=ticks;state=1;return false;
        }
        require(ticks-started<20*120,"Assembly merge deferred/timed out at "+group.get("groupKey")+": "+lastResult);
        if(state==1 && ticks-started>=2) {
            for(ChunkPos chunk:tickets)loading.add(world.getChunkManager().getChunkFutureSyncOnMainThread(chunk.x,chunk.z,ChunkStatus.FULL,false).thenApply(v->v.left().orElseThrow(()->new IllegalStateException("Assembly own chunk failed FULL load: "+chunk))));state=2;return false;
        }
        if(state==2 && loading.stream().allMatch(CompletableFuture::isDone)) {
            for(var future:loading)future.join();state=3;return false;
        }
        if(state==3) {
            JsonObject outcome=observe(group);
            if(outcome!=null) {outcomes.add(outcome);release();cursor++;state=0;lastResult="not_attempted";return false;}
            require(!resumedSavedRows,"A resumed assembly no longer matches its saved merged state; existing light done rows cannot be reused: "+lastResult);
            if((ticks-started)%20==0) {
                BlockPos root=pos(group.getAsJsonArray("root"));require(world.getBlockEntity(root) instanceof CompositeBlockEntity,"Assembly anchor has no original CompositeBE: "+root);CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);Owner expected=owner(group.getAsJsonArray("components").get(0).getAsJsonObject());require(expected.equals(own.resident()),"Assembly anchor identity changed before native matcher");
                // Existing approved migration API. It performs its normal exact
                // match, all-cell loaded checks, and atomic owner transaction.
                var result=ImportedAssemblyV126.migrateLoadedRoot(world,own.resident());lastResult=result.outcome()+":"+result.reason();
            }
        }
        return false;
    }

    private JsonObject observe(JsonObject group) {
        BlockPos root=pos(group.getAsJsonArray("root"));Owner primary=owner(group.getAsJsonArray("components").get(0).getAsJsonObject());
        if(!(world.getBlockEntity(root) instanceof CompositeBlockEntity own) || !primary.equals(own.resident())){lastResult="primary_resident_mismatch";return null;}
        var state=world.getBlockState(root);String registry=Registries.BLOCK.getId(state.getBlock()).toString();
        if(!registry.equals(group.get("expectedRegistryId").getAsString()) || !ImportedAssemblyV126.complete(state) || CompositeSpec.poseVariant(state)!=group.get("expectedInstallationPose").getAsInt()){lastResult="installation_pose_not_complete";return null;}
        var payload=own.payload();var marker=payload.getCompound(ImportedAssemblyV126.PAYLOAD_KEY);
        if(marker.getInt("Version")!=ImportedAssemblyV126.VERSION || !marker.getString("Recipe").equals(group.get("recipeKey").getAsString())){lastResult="approved_recipe_marker_missing";return null;}
        Set<BlockPos> declared=cells(group);var instance=CompositeRuntime.instance(world,root,state,primary.instanceId(),payload);
        for(Cell offset:instance.cells().keySet())require(declared.contains(CompositeData.pos(primary.root().add(offset))),"Merged footprint escapes its declared light cells: "+primary.root().add(offset));
        Set<Owner> retired=new HashSet<>();for(int i=1;i<group.getAsJsonArray("components").size();i++)retired.add(owner(group.getAsJsonArray("components").get(i).getAsJsonObject()));
        Set<UUID> oldIds=new HashSet<>();for(Owner owner:retired)oldIds.add(owner.instanceId());CompositeLedger ledger=CompositeLedger.get(world);
        for(BlockPos at:declared) {
            if(world.getBlockEntity(at) instanceof CompositeBlockEntity be) {
                require(at.equals(root) || !primary.equals(be.resident()),"Primary assembly has a second resident inside its finite footprint: "+at);
                if(be.resident()!=null && oldIds.contains(be.resident().instanceId())){lastResult="upper_resident_not_retired:"+at;return null;}
                if(be.contributions().stream().anyMatch(v->oldIds.contains(v.owner().instanceId()))){lastResult="upper_BE_contribution_not_retired:"+at;return null;}
            }
            if(ledger.at(CompositeData.cell(at)).stream().anyMatch(v->oldIds.contains(v.owner().instanceId()))){lastResult="upper_ledger_contribution_not_retired:"+at;return null;}
        }
        require(ledger.sharded(),"Full source assembly membership proof requires its original sharded ledger");
        if(ledger.savedShardMemberships(retired).values().stream().anyMatch(v->!v.isEmpty())){lastResult="upper_saved_membership_not_retired";return null;}
        var membership=ledger.savedShardMemberships(Set.of(primary)).get(primary);
        if(membership==null || !membership.equals(instance.cells())){lastResult="primary_whole_saved_membership_incomplete";return null;}
        for(var entry:membership.entrySet()) {
            Cell at=primary.root().add(entry.getKey());require(declared.contains(CompositeData.pos(at)),"Merged saved membership escapes declared cells");
            if(ledger.at(at).stream().noneMatch(v->v.owner().equals(primary) && v.shape().equals(entry.getValue()))){lastResult="primary_whole_cell_contribution_incomplete:"+at;return null;}
        }
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("groupKey",group.get("groupKey").getAsString());result.add("root",group.get("root").deepCopy());result.addProperty("primaryOwnerUuid",group.get("primaryOwnerUuid").getAsString());result.addProperty("registryId",registry);result.addProperty("installationPose",CompositeSpec.poseVariant(state));result.addProperty("recipeKey",marker.getString("Recipe"));result.add("retiredOwnerUuids",group.get("retiredOwnerUuids").deepCopy());result.addProperty("oldUpperResidentAndContributionAbsent",true);result.addProperty("oldUpperSavedMembershipAbsent",true);result.addProperty("mergedFootprintWithinDeclaredCells",true);result.addProperty("primaryWholeMembershipComplete",true);return result;
    }

    JsonObject reference() throws Exception {require(complete,"Actual assembly phase is incomplete");JsonObject ref=new JsonObject();ref.addProperty("path",phaseFile.toString());ref.addProperty("sha256",hash(phaseFile));return ref;}
    void release(){if(world!=null)for(ChunkPos chunk:tickets)world.getChunkManager().removeTicket(TICKET,chunk,0,chunk);tickets.clear();loading.clear();}
    private static Set<BlockPos> cells(JsonObject group){Set<BlockPos> result=new HashSet<>();for(JsonElement value:group.getAsJsonArray("ownedCells"))require(result.add(pos(value.getAsJsonArray())),"Repeated assembly cell");return result;}
    private static BlockPos pos(JsonArray v){require(v.size()==3,"Malformed exact position");return new BlockPos(v.get(0).getAsInt(),v.get(1).getAsInt(),v.get(2).getAsInt());}
    private static Owner owner(JsonObject value){JsonArray v=value.getAsJsonArray("ownerIntArray");require(v.size()==4,"Malformed original owner UUID");ByteBuffer b=ByteBuffer.allocate(16);for(JsonElement n:v)b.putInt(n.getAsInt());b.flip();UUID uuid=new UUID(b.getLong(),b.getLong());return new Owner(uuid,value.get("registryId").getAsString(),CompositeData.cell(pos(value.getAsJsonArray("root"))));}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException(message);}
    private static String hash(Path path)throws Exception{try(InputStream stream=Files.newInputStream(path)){return hash(stream);}}
    private static String hash(InputStream stream)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[1024*1024];int count;while((count=stream.read(bytes))!=-1)digest.update(bytes,0,count);return HexFormat.of().formatHex(digest.digest());}
    private static void atomic(Path path,JsonObject value)throws Exception{Path tmp=path.resolveSibling(path.getFileName()+".writing");try(FileChannel channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){ByteBuffer data=StandardCharsets.UTF_8.encode(GSON.toJson(value)+"\n");while(data.hasRemaining())channel.write(data);channel.force(true);}Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
}
