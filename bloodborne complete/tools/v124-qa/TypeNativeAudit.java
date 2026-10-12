package dev.dreamwalker.bloodbornedw.v124qa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.tool.*;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import java.nio.file.*;
import java.util.*;

/** Native production-JAR save migration and ordinary item interaction evidence.
 * The fixture world was authored by the independently bound V12.3 JAR, not by
 * this helper or by synthetic marker injection. Originals retain UUID and root.
 */
public final class TypeNativeAudit implements ModInitializer {
    private final JsonObject result=new JsonObject();
    private final JsonArray interactions=new JsonArray(), visualDifferences=new JsonArray();
    private JsonObject saved; private JsonArray fixtures;
    private Path fixturePath; private boolean done,reopen;
    private int ticks,stage,loaded,verified,itemIndex,commandChecks,linkChecks,ownerCells,outlineCells,boundsChecks;
    private double maximumBoundsDeviation;
    private ServerPlayerEntity interactionActor;
    private final Map<UUID,Owner> expectedOwners=new HashMap<>();
    private final Set<UUID> fixtureIds=new HashSet<>();
    private final List<PendingProbe> pendingProbes=new ArrayList<>();
    private final JsonArray pendingEvidence=new JsonArray();
    private int pendingIndex;
    private static final class PendingProbe {
        final JsonObject row,evidence=new JsonObject();final Owner oldOwner;
        final Map<Cell,CompositeData.Contribution> before;final Set<Cell> required;
        final Cell helper;final boolean helperFirst;int phase,started;
        PendingProbe(JsonObject row,Owner oldOwner,Map<Cell,CompositeData.Contribution> before,Set<Cell> required,Cell helper,boolean helperFirst){this.row=row;this.oldOwner=oldOwner;this.before=before;this.required=required;this.helper=helper;this.helperFirst=helperFirst;}
    }
    @Override public void onInitialize(){
        try{Class.forName("dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases",false,getClass().getClassLoader());}
        catch(ClassNotFoundException baseline){return;}
        if(FabricLoader.getInstance().getEnvironmentType()==EnvType.CLIENT)TypeComparisonClientAudit.prepareBaselineBlocks();
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!server.isDedicated()||done||++ticks<30)return;
            try{
                ServerWorld world=server.getOverworld();
                if(saved==null)initialize(world);
                if(stage==-1){pendingProbe(world);return;}
                if(stage==0){for(int n=0;n<8&&loaded<fixtures.size();n++)load(world,fixtures.get(loaded++).getAsJsonObject());if(loaded==fixtures.size())stage=1;write("RUNNING_LOAD");return;}
                if(stage==1){
                    for(int n=0;n<6&&verified<fixtures.size();n++){
                        JsonObject row=fixtures.get(verified).getAsJsonObject();
                        if(!ready(world,row)){
                            V124QaEvidence.require(ticks<3000,"Migration did not complete after loading every old/new cell: "+row.get("oldRegistry")+"/"+row.getAsJsonObject("mapping").get("oldVariant"));
                            if(ticks%20==0)write("RUNNING_WAIT_MIGRATION");return;
                        }
                        verify(world,row);verified++;
                    }
                    if(verified<fixtures.size()){if(verified%30<6)write("RUNNING_VERIFY");return;}
                    auditLedger(world);stage=2;write("PASS_SAVED_MIGRATION");return;
                }
                if(stage==2){
                    if(!reopen)for(int n=0;n<3&&itemIndex<fixtures.size();n++)ordinaryItem(world,fixtures.get(itemIndex).getAsJsonObject(),itemIndex++);
                    if(!reopen&&itemIndex<fixtures.size()){if(itemIndex%30<3)write("RUNNING_ITEMS");return;}
                    auditLedger(world);finish(world);
                }
            }catch(Throwable failure){fail(failure);}
        });
    }
    private void initialize(ServerWorld world)throws Exception{
        V124QaEvidence.require(V124QaEvidence.mode("audit"),"Explicit new production audit mode required");
        V124QaEvidence.require(V124QaEvidence.productionSha().equals(V124QaEvidence.candidateSha()),"Audit must load exactly the bound candidate JAR");
        V124QaEvidence.require(world.getServer().getSaveProperties().getLevelName().equals("isolated-smoke-world"),"Isolated legacy fixture world required");
        fixturePath=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v124-fixture.json");
        V124QaEvidence.require(Files.isRegularFile(fixturePath),"Actual independently authored V12.3 save fixture missing");
        saved=JsonParser.parseString(Files.readString(fixturePath)).getAsJsonObject();fixtures=saved.getAsJsonArray("fixtures");
        V124QaEvidence.require(saved.get("beforeProductionJarSha256").getAsString().equals(V124QaEvidence.baselineSha()),"Saved baseline artifact SHA differs from explicit QA input");
        V124QaEvidence.require(saved.has("artExpectationVersion")&&saved.get("artExpectationVersion").getAsInt()==1,"Saved fixture predates exact authored stair geometry/UV expectations; re-author with the current independent baseline addon");
        V124QaEvidence.require(fixtures.size()==V124QaEvidence.mapping().getAsJsonArray("mappings").size(),"Every reviewed old exact state must have one genuine old-save fixture");
        Map<String,JsonObject> exactMappings=new HashMap<>();for(JsonElement raw:V124QaEvidence.mapping().getAsJsonArray("mappings")){JsonObject row=raw.getAsJsonObject();exactMappings.put(row.get("oldRegistryId").getAsString()+"/"+row.get("oldVariant").getAsInt(),row);}
        for(JsonElement raw:fixtures){JsonObject row=raw.getAsJsonObject(),old=row.getAsJsonObject("mapping");String key=old.get("oldRegistryId").getAsString()+"/"+old.get("oldVariant").getAsInt();V124QaEvidence.require(old.equals(exactMappings.remove(key)),"Saved legacy fixture expectations do not match this final manifest: "+key+"; re-author or explicitly rebind the independent expectations");}
        V124QaEvidence.require(exactMappings.isEmpty(),"Final manifest contains an untested old exact state");
        V124QaEvidence.require(!ImportedTypeAliases.mappings().isEmpty(),"Production alias resource is mandatory");
        reopen=saved.has("afterProductionJarSha256");
        if(reopen)V124QaEvidence.require(saved.get("afterProductionJarSha256").getAsString().equals(V124QaEvidence.productionSha()),"Idempotence reopen must use identical candidate JAR");
        result.addProperty("schema","dreamwalker-v124-native-type-save-audit-v1");
        result.addProperty("beforeProductionJarSha256",V124QaEvidence.baselineSha());result.addProperty("afterProductionJarSha256",V124QaEvidence.productionSha());
        result.addProperty("actualV123SavedFixtures",fixtures.size());result.addProperty("reopen",reopen);result.add("interactions",interactions);result.add("sourceBoundsDifferences",visualDifferences);
        result.addProperty("sourceWorldChanged",false);result.addProperty("authorCollisionExport","NOT_SUPPLIED_NOT_APPLIED");result.addProperty("physicalMouseKeyboard","NOT_RUN");
        for(JsonElement raw:fixtures){JsonObject row=raw.getAsJsonObject();UUID uuid=UUID.fromString(row.get("uuid").getAsString());V124QaEvidence.require(fixtureIds.add(uuid),"Repeated saved UUID");expectedOwners.put(uuid,new Owner(uuid,row.get("newRegistry").getAsString(),CompositeData.cell(root(row))));}
        result.add("nativePendingChunkEvidence",pendingEvidence);
        if(!reopen){preparePendingProbes(world);if(!pendingProbes.isEmpty())stage=-1;}
        else result.addProperty("nativePendingChunkOrdering","NOT_RUN_REOPEN_ALREADY_CANONICAL");
    }
    private static boolean full(ServerWorld world,Cell cell){return world.getChunkManager().getWorldChunk(cell.x()>>4,cell.z()>>4)!=null;}
    private static long chunk(Cell cell){return ChunkPos.toLong(cell.x()>>4,cell.z()>>4);}
    private void preparePendingProbes(ServerWorld world)throws Exception{
        var ledger=CompositeLedger.get(world);Map<UUID,Map<Cell,CompositeData.Contribution>> savedCells=new HashMap<>();
        for(Cell cell:ledger.cells())for(var entry:ledger.at(cell))if(fixtureIds.contains(entry.owner().instanceId()))savedCells.computeIfAbsent(entry.owner().instanceId(),unused->new TreeMap<>()).put(cell,entry);
        Set<Long> selectedChunks=new HashSet<>();Set<String> selectedTypes=new HashSet<>();
        for(int index=fixtures.size()-1;index>=0&&pendingProbes.size()<2;index--){
            JsonObject row=fixtures.get(index).getAsJsonObject();Cell root=CompositeData.cell(root(row));UUID uuid=UUID.fromString(row.get("uuid").getAsString());
            if(full(world,root)||selectedChunks.contains(chunk(root))||selectedTypes.contains(row.get("oldRegistry").getAsString()))continue;
            Map<Cell,CompositeData.Contribution> before=savedCells.get(uuid);if(before==null||before.isEmpty())continue;
            Owner owner=before.values().iterator().next().owner();
            if(!owner.registryId().equals(row.get("oldRegistry").getAsString())||before.values().stream().anyMatch(e->CompositeData.nbt(e.rootData().blockEntityNbt()).getInt("ImportedTypeVersion")>=1))continue;
            var canonical=(CompositeRootBlock)Registries.BLOCK.get(new Identifier(row.get("newRegistry").getAsString()));
            var anticipated=CompositeRuntime.instance(world,CompositeData.pos(root),state(canonical,row.getAsJsonObject("expectedState")),uuid,StringNbtReader.parse(row.get("expectedPayloadNbt").getAsString()));
            Set<Cell> required=new TreeSet<>(before.keySet());anticipated.cells().keySet().forEach(c->required.add(root.add(c)));
            // Foreign roots touched by an existing carrier must also stay deferred.
            for(Cell cell:List.copyOf(required))for(var entry:ledger.at(cell))required.add(entry.owner().root());
            Cell helper=before.keySet().stream().filter(c->chunk(c)!=chunk(root)&&!full(world,c)&&!selectedChunks.contains(chunk(c))).findFirst().orElse(null);
            if(helper==null)continue;
            boolean helperFirst=pendingProbes.size()==1;PendingProbe probe=new PendingProbe(row,owner,Map.copyOf(before),Set.copyOf(required),helper,helperFirst);
            probe.evidence.addProperty("order",helperFirst?"HELPER_FIRST":"ROOT_ONLY");probe.evidence.addProperty("uuid",uuid.toString());probe.evidence.addProperty("oldRegistry",owner.registryId());probe.evidence.addProperty("newRegistry",row.get("newRegistry").getAsString());probe.evidence.addProperty("savedCells",before.size());probe.evidence.addProperty("rootChunk",chunk(root));probe.evidence.addProperty("helperChunk",chunk(helper));
            pendingEvidence.add(probe.evidence);pendingProbes.add(probe);selectedChunks.add(chunk(root));selectedChunks.add(chunk(helper));selectedTypes.add(owner.registryId());
        }
        if(pendingProbes.size()<2)result.addProperty("nativePendingChunkProbeSelection",pendingProbes.isEmpty()?"NOT_RUN_NO_UNLOADED_CROSS_CHUNK_SAVED_ASSEMBLY_AFTER_STARTUP":"PARTIAL_ONLY_ONE_UNLOADED_CROSS_CHUNK_SAVED_ASSEMBLY_AFTER_STARTUP");
        else result.addProperty("nativePendingChunkProbeSelection","TWO_DISTINCT_SAVED_OWNERS_AND_TYPES");
    }
    private void pendingProbe(ServerWorld world)throws Exception{
        if(pendingIndex>=pendingProbes.size()){stage=0;write("PASS_PENDING_PROBE_PHASE_COMPLETE");return;}
        PendingProbe probe=pendingProbes.get(pendingIndex);Cell root=probe.oldOwner.root();
        if(probe.phase==0){
            // No forced ticket here: its neighbor radius would conceal the phase.
            world.getChunk(CompositeData.pos(probe.helperFirst?probe.helper:root));probe.started=ticks;probe.phase=1;write("RUNNING_PENDING_PARTIAL_LOAD");return;
        }
        if(probe.phase==1){
            long missing=probe.required.stream().filter(c->!full(world,c)).count();probe.evidence.addProperty("unloadedRequiredCellsDuringProbe",missing);
            if(missing==0||probe.helperFirst&&full(world,root)){
                probe.evidence.addProperty("partialPhase","NOT_RUN_AUTOMATIC_CHUNK_TICKETS_LOADED_ALL_REQUIRED_CELLS_OR_ROOT");
            }else{
                V124QaEvidence.require(ImportedTypeMigration.pending(world,probe.oldOwner),"Partial saved assembly was not kept pending "+probe.oldOwner);
                for(var entry:probe.before.entrySet())V124QaEvidence.require(CompositeLedger.get(world).at(entry.getKey()).stream().filter(e->e.owner().instanceId().equals(probe.oldOwner.instanceId())).toList().equals(List.of(entry.getValue())),"Partial chunk load changed old owned ledger/NBT before atomic completion "+entry.getKey());
                if(!probe.helperFirst){
                    V124QaEvidence.require(full(world,root),"Root-only fixture root did not reach FULL");var own=(CompositeBlockEntity)world.getBlockEntity(CompositeData.pos(root));
                    V124QaEvidence.require(probe.oldOwner.equals(own.resident())&&own.payload().equals(StringNbtReader.parse(probe.row.get("oldPayloadNbt").getAsString())),"Root-only deferred load changed UUID/root/typed payload");
                    V124QaEvidence.require(Registries.BLOCK.getId(world.getBlockState(CompositeData.pos(root)).getBlock()).toString().equals(probe.oldOwner.registryId()),"Deferred root changed registry before assembly commit");
                }
                probe.evidence.addProperty("partialPhase","PASS_REAL_CHUNK_CALLBACK_DEFERRED_WITH_UNCHANGED_OLD_MEMBERSHIP");
            }
            load(world,probe.row);probe.phase=2;write("RUNNING_PENDING_COMPLETE_LOAD");return;
        }
        V124QaEvidence.require(ticks-probe.started<300,"Focused pending chunk completion timed out "+probe.oldOwner);
        if(!ready(world,probe.row)){if(ticks%20==0)write("RUNNING_PENDING_COMPLETION_WAIT");return;}
        var own=(CompositeBlockEntity)world.getBlockEntity(CompositeData.pos(root));
        V124QaEvidence.require(expectedOwners.get(probe.oldOwner.instanceId()).equals(own.resident()),"Pending completion changed saved UUID/root");
        V124QaEvidence.require(!ImportedTypeMigration.pending(world,probe.oldOwner)&&!ImportedTypeMigration.pending(world,own.resident()),"Completed canonical assembly remained in pending migration index");
        V124QaEvidence.require(own.payload().equals(StringNbtReader.parse(probe.row.get("expectedPayloadNbt").getAsString())),"Pending completion changed exact typed saved payload");
        probe.evidence.addProperty("completion","PASS_CANONICAL_SAME_UUID_ROOT_AND_TYPED_PAYLOAD");probe.evidence.addProperty("elapsedTicks",ticks-probe.started);pendingIndex++;write("RUNNING_PENDING_NEXT_ORDER");
    }
    private void load(ServerWorld world,JsonObject row)throws Exception{
        BlockPos root=root(row);force(world,root);JsonObject mapping=row.getAsJsonObject("mapping");
        CompositeRootBlock next=(CompositeRootBlock)Registries.BLOCK.get(new Identifier(row.get("newRegistry").getAsString()));
        BlockState expected=state(next,row.getAsJsonObject("expectedState"));NbtCompound payload=StringNbtReader.parse(row.get("expectedPayloadNbt").getAsString());
        for(Cell cell:CompositeRuntime.instance(world,root,expected,UUID.fromString(row.get("uuid").getAsString()),payload).cells().keySet())halo(world,root.add(cell.x(),cell.y(),cell.z()));
        // The ledger was read before loading the root. It includes the saved BEFORE
        // footprint even when the canonical spec's current geometry differs.
        UUID uuid=UUID.fromString(row.get("uuid").getAsString());var ledger=CompositeLedger.get(world);
        for(Cell cell:ledger.cells())if(ledger.at(cell).stream().anyMatch(e->e.owner().instanceId().equals(uuid)))halo(world,CompositeData.pos(cell));
        if(row.has("leverUuid"))halo(world,root.add(5,0,5));
    }
    private boolean ready(ServerWorld world,JsonObject row){
        BlockPos root=root(row);if(!(world.getBlockEntity(root) instanceof CompositeBlockEntity own)||own.resident()==null)return false;
        return own.resident().registryId().equals(row.get("newRegistry").getAsString())&&own.payload().getInt("ImportedTypeVersion")==1;
    }
    private void verify(ServerWorld world,JsonObject row)throws Exception{
        BlockPos root=root(row);UUID uuid=UUID.fromString(row.get("uuid").getAsString());Owner owner=expectedOwners.get(uuid);
        CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);BlockState current=world.getBlockState(root);
        V124QaEvidence.require(owner.equals(own.resident()),"Saved UUID/root changed: "+row.get("oldId"));
        V124QaEvidence.require(Registries.BLOCK.getId(current.getBlock()).toString().equals(owner.registryId()),"Saved canonical registry differs");
        JsonObject expected=row.getAsJsonObject("expectedState"),actual=V124QaEvidence.state(current);
        for(var field:expected.entrySet())V124QaEvidence.require(actual.has(field.getKey())&&actual.get(field.getKey()).getAsString().equals(field.getValue().getAsString()),"Saved art/state differs "+row.get("oldId")+" "+field.getKey()+" expected="+field.getValue()+" actual="+actual.get(field.getKey()));
        NbtCompound expectedPayload=StringNbtReader.parse(row.get("expectedPayloadNbt").getAsString());
        V124QaEvidence.require(expectedPayload.equals(own.payload()),"Typed saved payload changed "+row.get("oldId")+" expected="+expectedPayload+" actual="+own.payload());
        V124QaEvidence.require(!ImportedTypeAliases.needsMigration(current,own.payload()),"Canonical saved state still requests legacy mapping");
        ObjectInstance object=CompositeRuntime.instance(world,root,current,uuid,own.payload());
        for(var part:object.cells().entrySet()){
            Cell cell=owner.root().add(part.getKey());var entries=CompositeLedger.get(world).at(cell);var matching=entries.stream().filter(e->e.owner().instanceId().equals(uuid)).toList();
            V124QaEvidence.require(matching.size()==1&&matching.get(0).owner().equals(owner),"Exact canonical owned cell missing/duplicated "+cell+" "+row.get("oldId"));
            V124QaEvidence.require(matching.get(0).shape().equals(part.getValue())&&matching.get(0).rootData().equals(object.rootData()),"Saved ledger canonical geometry/NBT/state differs "+cell);ownerCells++;
        }
        assertNative(world,root);ToolTarget oldTarget=target(world,row,row.get("oldRegistry").getAsString());
        V124QaEvidence.require(oldTarget.valid(world.getServer()),"Previously selected debug target lost saved instance");
        if(row.has("commandOwnerUuid"))command(world,row,oldTarget);
        if(row.has("leverUuid"))mechanism(world,row);
        var visual=V124QaEvidence.renderBounds((CompositeRootBlock)current.getBlock(),current,own.payload());
        if(visual.isPresent()){
            row.add("afterBounds",V124QaEvidence.array(visual.get()));row.addProperty("afterRenderBoundsStatus","PASS_SOURCE_JSON_VERTICES");
            if(row.has("oldBounds")){boundsChecks++;JsonArray before=row.getAsJsonArray("oldBounds");double difference=0;for(int i=0;i<6;i++)difference=Math.max(difference,Math.abs(before.get(i).getAsDouble()-visual.get()[i]));maximumBoundsDeviation=Math.max(maximumBoundsDeviation,difference);
                row.addProperty("sourceBoundsMaximumDeviation",difference);if(difference>1e-6){JsonObject diff=new JsonObject();diff.addProperty("oldId",row.get("oldId").getAsString());diff.addProperty("newId",row.get("newId").getAsString());diff.addProperty("maximumDeviation",difference);diff.add("before",before.deepCopy());diff.add("after",V124QaEvidence.array(visual.get()));visualDifferences.add(diff);}}
        }else row.addProperty("afterRenderBoundsStatus","NOT_RUN_GENERATED_OR_BUILTIN_MESH");
        row.add("afterState",actual);row.addProperty("afterPayloadNbt",own.payload().toString());row.addProperty("canonicalCellCount",object.cells().size());row.addProperty("status",reopen?"PASS_REOPEN":"PASS_SAVED_MIGRATED");
    }
    private void command(ServerWorld world,JsonObject row,ToolTarget oldTarget){
        ToolCommands.View view=ToolCommands.view(world.getServer(),oldTarget);int spent=reopen?2:1;
        V124QaEvidence.require(view.text().equals("time query daytime")&&view.personalLimit()==2&&view.globalLimit()==3&&view.globalUsed()==spent,"Saved debug command/counters were reset by alias migration");
        ServerPlayerEntity player=actor(world,UUID.fromString(row.get("commandOwnerUuid").getAsString()));
        try{ToolCommands.forgetPlayer(world.getServer(),player.getUuid());
            boolean fired=ToolCommands.fire(player,target(world,row,row.get("newRegistry").getAsString()));
            V124QaEvidence.require(fired!=reopen,reopen?"Saved personal limit was lost after reopen":"Canonical registry did not resolve old command binding");
            ToolCommands.forgetPlayer(world.getServer(),player.getUuid());V124QaEvidence.require(!ToolCommands.fire(player,oldTarget),"Previously spent personal count was lost");
            V124QaEvidence.require(ToolCommands.view(world.getServer(),oldTarget).globalUsed()==2,"Command usage counter not preserved exactly");commandChecks++;row.addProperty("commandGlobalUsedAfterAudit",2);
        }finally{ToolCommands.forgetPlayer(world.getServer(),player.getUuid());player.discard();}
    }
    private void mechanism(ServerWorld world,JsonObject row){
        UUID leverId=UUID.fromString(row.get("leverUuid").getAsString());V124QaEvidence.require(world.getEntity(leverId) instanceof RpObjectEntity,"Saved RP lever disappeared");RpObjectEntity lever=(RpObjectEntity)world.getEntity(leverId);
        var ref=new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),UUID.fromString(row.get("uuid").getAsString()),root(row).asLong(),row.get("oldRegistry").getAsString());
        V124QaEvidence.require(MechanismLinks.inspect(world.getServer(),ref).availability()==MechanismLinks.Availability.LOADED,"Old mechanism reference became stale after alias migration");
        V124QaEvidence.require(MechanismLinks.targets(lever).stream().anyMatch(t->t.key().equals(ref.key())&&t.root()==ref.root()&&ImportedTypeAliases.equivalent(t.registryId(),ref.registryId())),"Persistent mechanism link was removed/reset");
        if(row.has("eventProbe")){
            V124QaEvidence.require(ObjectPolicies.leversOnly(world.getServer(),ref)&&ObjectPolicies.commands(world.getServer(),ref,true).equals(List.of(row.get("eventCommand").getAsString()))&&ObjectPolicies.commands(world.getServer(),ref,false).equals(List.of(row.get("eventCommand").getAsString())),"Saved policies/event command lists were reset by migration");
            var objective=world.getScoreboard().getObjective("v124_events");V124QaEvidence.require(objective!=null,"Saved event scoreboard objective disappeared");String probe=row.get("eventProbe").getAsString();int count=world.getScoreboard().getPlayerScore(probe,objective).getScore();V124QaEvidence.require(count==(reopen?2:0),"Save loading/type migration fired an open/close event");
            ServerPlayerEntity manual=actor(world,UUID.randomUUID());try{V124QaEvidence.require(!ObjectPolicies.allowManual(manual,ref),"Saved levers-only policy stopped applying");}finally{manual.discard();}
            if(!reopen){boolean prior=world.getBlockState(root(row)).get(CompositeRootBlock.OPEN);
                V124QaEvidence.require(MechanismLinks.applyEffect(world.getServer(),ref,!prior,false)&&MechanismLinks.applyEffect(world.getServer(),ref,prior,false),"Old lever target cannot toggle canonical installed type");
                V124QaEvidence.require(world.getScoreboard().getPlayerScore(probe,objective).getScore()==2,"Preserved open/close event commands did not fire exactly once per real transition");}
            row.addProperty("eventCountAfterAudit",2);
        }
        linkChecks++;
    }
    private void ordinaryItem(ServerWorld world,JsonObject row,int number)throws Exception{
        JsonObject mapping=row.getAsJsonObject("mapping");CompositeRootBlock old=(CompositeRootBlock)Registries.BLOCK.get(new Identifier(row.get("oldRegistry").getAsString()));
        CompositeRootBlock canonical=(CompositeRootBlock)Registries.BLOCK.get(new Identifier(row.get("newRegistry").getAsString()));
        // Reuse one fully separate station and one unspawned packet-sink actor.
        // Keeping thousands of empty test chunks/player trackers loaded would
        // measure the helper's retention, not production save compatibility.
        BlockPos root=new BlockPos(2048,180,2048);if(interactionActor==null)interactionActor=actor(world,UUID.randomUUID());ServerPlayerEntity player=interactionActor;Set<BlockPos> supports=new HashSet<>();
        JsonObject evidence=new JsonObject();evidence.addProperty("oldId",row.get("oldId").getAsString());evidence.addProperty("oldVariant",mapping.get("oldVariant").getAsInt());evidence.addProperty("primaryId",row.get("newId").getAsString());
        try{
            NbtCompound payload=StringNbtReader.parse(row.get("expectedPayloadNbt").getAsString());BlockState anticipated=state(canonical,row.getAsJsonObject("expectedState"));
            var footprint=CompositeRuntime.instance(world,root,anticipated,UUID.randomUUID(),payload).cells();
            for(Cell cell:footprint.keySet())halo(world,root.add(cell.x(),cell.y(),cell.z()));halo(world,root);
            world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);supports.add(root.down());
            if(canonical.spec.requiredSupport){BlockPos support=root.add(canonical.spec.supportOffset.x(),canonical.spec.supportOffset.y(),canonical.spec.supportOffset.z());halo(world,support);world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);supports.add(support);}
            ItemStack raw=new ItemStack(old.asItem(),3);NbtCompound art=raw.getOrCreateSubNbt("BlockStateTag");art.putString("variant",mapping.get("oldVariant").getAsString());
            art.putString("profile",row.getAsJsonObject("oldState").get("profile").getAsString());art.putString("open",row.getAsJsonObject("oldState").get("open").getAsString());raw.getOrCreateNbt().putString("SavedCustomName","retained_art_"+number);
            ItemStack converted=ImportedTypeAliases.canonicalStack(raw);V124QaEvidence.require(converted.isOf(canonical.asItem())&&converted.getCount()==3&&converted.getNbt().getString("SavedCustomName").equals("retained_art_"+number),"Alias inventory stack count/custom data lost");
            V124QaEvidence.require(ImportedTypeAliases.canonicalStack(converted)==converted,"Canonical inventory item was mapped twice");
            String expectedLegacyArt=V124QaEvidence.historicalStair(row.get("oldRegistry").getAsString())?row.get("oldRegistry").getAsString()+"|"+mapping.get("oldVariant").getAsInt():null;
            if(expectedLegacyArt!=null)V124QaEvidence.require(converted.getNbt().getString("ImportedStairLegacyArt").equals(expectedLegacyArt),"Raw legacy stair stack lost exact source geometry/UV selector");
            ItemStack roundTrip=ItemStack.fromNbt(raw.writeNbt(new NbtCompound()));V124QaEvidence.require(roundTrip.isOf(canonical.asItem())&&roundTrip.getNbt().getInt("ImportedTypeVersion")==1,"Native inventory load did not canonicalize exact old variant");
            // Raw old stack deliberately bypasses fromNbt: this exercises the ordinary
            // BlockItem redirection itself, independently from inventory migration.
            pose(player,root.getX()+.5,root.getY()+8,root.getZ()-4,number&7);player.setStackInHand(Hand.MAIN_HAND,raw);
            var placed=raw.getItem().useOnBlock(context(player,root));V124QaEvidence.require(placed.isAccepted()&&world.getBlockState(root).isOf(canonical),"Raw alias ordinary placement failed "+row.get("oldId")+"/"+mapping.get("oldVariant")+" "+CompositeRuntime.lastResult());
            CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);Owner owner=own.resident();BlockState actual=world.getBlockState(root);
            V124QaEvidence.require(owner.registryId().equals(canonical.spec.id.toString())&&own.payload().getInt("ImportedTypeVersion")==1,"Ordinary alias placed a legacy owner");
            V124QaEvidence.require(actual.get(CompositeRootBlock.PROFILE).asString().equals(art.getString("profile")),"Ordinary alias placement lost material profile");
            V124QaEvidence.require(!own.payload().contains("CustomEvent")&&!own.payload().containsUuid("LinkedInstance"),"Ordinary item restored saved-instance privileges");
            if(expectedLegacyArt!=null)V124QaEvidence.require(own.payload().getString("ImportedStairLegacyArt").equals(expectedLegacyArt),"Ordinary legacy stair placement lost its reviewed artistic selector");
            CompositeSourceShift.Shift installedShift=CompositeSourceShift.local(own.payload());
            assertNative(world,root);BlockHitResult hit=aim(world,root,owner,player);ItemStack picked=world.getBlockState(hit.getBlockPos()).getBlock().getPickStack(world,hit.getBlockPos(),world.getBlockState(hit.getBlockPos()));
            V124QaEvidence.require(picked.isOf(canonical.asItem())&&picked.getNbt()!=null&&picked.getNbt().getInt("ImportedTypeVersion")==1,"Native artistic pick returned a retired type/state");
            V124QaEvidence.require(!picked.getNbt().contains("CompositePayload")&&!picked.getNbt().contains("BlockEntityTag"),"Ordinary artistic pick exposed private owner payload");
            V124QaEvidence.require(ImportedTypeAliases.readArtShift(picked.getNbt()).equals(installedShift),"Native picked item lost canonical art anchoring");
            V124QaEvidence.require(ImportedTypeAliases.readLegacyStairArt(picked.getNbt()).equals(ImportedTypeAliases.readLegacyStairArt(own.payload())),"Native picked stair lost exact legacy source UV selector");
            ObjectInstance before=CompositeRuntime.instance(world,root,actual,owner.instanceId(),own.payload());player.interactionManager.tryBreakBlock(hit.getBlockPos());CompositeRuntime.drain(world);removed(world,before);
            boolean top=actual.getBlock() instanceof ImportedStairBlock&&ImportedStairShapes.half(actual)==net.minecraft.block.enums.BlockHalf.TOP;
            if(top){world.setBlockState(root.up(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);supports.add(root.up());}
            pose(player,root.getX()+.5,root.getY()+8,root.getZ()-4,number&7);player.setStackInHand(Hand.MAIN_HAND,picked);ItemUsageContext copyContext=top?new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(root.up()),Direction.DOWN,root.up(),false)):context(player,root);
            V124QaEvidence.require(picked.getItem().useOnBlock(copyContext).isAccepted(),"Native picked item cannot install a fresh copy");
            CompositeBlockEntity copy=(CompositeBlockEntity)world.getBlockEntity(root);Owner copyOwner=copy.resident();V124QaEvidence.require(copyOwner.registryId().equals(canonical.spec.id.toString())&&!copyOwner.instanceId().equals(owner.instanceId()),"Copied item reused UUID or retired type");assertNative(world,root);
            V124QaEvidence.require(CompositeSourceShift.local(copy.payload()).equals(installedShift),"Native copied item lost exact canonical art shift");
            V124QaEvidence.require(ImportedTypeAliases.readLegacyStairArt(copy.payload()).equals(ImportedTypeAliases.readLegacyStairArt(picked.getNbt())),"Native copied stair lost its art-only legacy selector");
            BlockState copyState=world.getBlockState(root);for(var field:actual.getEntries().entrySet()){
                if(field.getKey().getName().equals("rotation")||actual.getBlock() instanceof ImportedStairBlock&&field.getKey().getName().equals("variant"))continue;
                V124QaEvidence.require(field.getValue().equals(copyState.getEntries().get(field.getKey())),"Native copied item lost material/mount/art state "+field.getKey().getName());
            }
            if(actual.getBlock() instanceof ImportedStairBlock)V124QaEvidence.require(ImportedStairShapes.half(actual)==ImportedStairShapes.half(copyState),"Native copied stair lost clicked half");
            ObjectInstance copied=CompositeRuntime.instance(world,root,world.getBlockState(root),copyOwner.instanceId(),copy.payload());BlockHitResult copyHit=aim(world,root,copyOwner,player);player.interactionManager.tryBreakBlock(copyHit.getBlockPos());CompositeRuntime.drain(world);removed(world,copied);
            evidence.addProperty("status","PASS");evidence.addProperty("inventoryFromNbtRawItemPlaceNativeRayPickBreakCopy","PASS");evidence.addProperty("exactSourceShiftHalfMaterialMountAndArtState","PASS");evidence.addProperty("copiedStairShape","DERIVED_FROM_NEIGHBORS");evidence.addProperty("protectedInstancePayload","PASS_ART_ONLY");evidence.addProperty("newUuid",copyOwner.instanceId().toString());interactions.add(evidence);
        }finally{for(BlockPos support:supports)if(world.getBlockState(support).isOf(Blocks.STONE)&&CompositeLedger.get(world).at(CompositeData.cell(support)).isEmpty())world.setBlockState(support,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}
    }
    private BlockHitResult aim(ServerWorld world,BlockPos root,Owner owner,ServerPlayerEntity player){
        BlockState state=world.getBlockState(root);CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);var selection=ImportedSelectionShapes.footprint((CompositeRootBlock)state.getBlock(),state,own.payload());
        record Probe(BlockPos cell,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box box,boolean managed){}
        List<Probe> probes=new ArrayList<>();
        for(var entry:selection.entrySet()){
            BlockPos cell=root.add(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());
            if(CompositeRuntime.contributions(world,cell).stream().noneMatch(c->c.owner().equals(owner)))continue;
            BlockState carrier=world.getBlockState(cell);boolean managed=carrier.getBlock() instanceof CompositeRootBlock||carrier.isOf(CompositeArchitecture.CELL);
            for(var box:entry.getValue())probes.add(new Probe(cell,box,managed));
        }
        probes.sort(Comparator.<Probe>comparingInt(p->p.managed()?1:0).reversed().thenComparing(Comparator.comparingDouble((Probe p)->largestFace(p.box())).reversed()));
        JsonArray attempts=new JsonArray();
        for(Probe probe:probes.subList(0,Math.min(12,probes.size()))){
            var box=probe.box();BlockPos cell=probe.cell();Vec3d centre=new Vec3d(cell.getX()+(box.minX()+box.maxX())/2,cell.getY()+(box.minY()+box.maxY())/2,cell.getZ()+(box.minZ()+box.maxZ())/2);
            List<Direction> faces=new ArrayList<>(List.of(Direction.UP,Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.DOWN));
            faces.sort(Comparator.comparingDouble((Direction face)->faceArea(box,face)).reversed());
            for(Direction face:faces){
                // A thin floor box may have its lower clipped half inside the
                // native support. Aim from an exposed face instead of insisting
                // on a horizontal ray through the stone at that box's midpoint.
                Vec3d outward=new Vec3d(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());Vec3d eye=centre.add(outward.multiply(2.5));
                float yaw=face.getAxis()==Direction.Axis.Y?0:face.getOpposite().asRotation(),pitch=face==Direction.UP?90:face==Direction.DOWN?-90:0;
                player.refreshPositionAndAngles(eye.x,eye.y-player.getStandingEyeHeight(),eye.z,yaw,pitch);player.setHeadYaw(yaw);player.setBodyYaw(yaw);player.prevHeadYaw=yaw;player.prevBodyYaw=yaw;player.prevYaw=yaw;player.prevPitch=pitch;
                Vec3d direction=player.getRotationVec(1);V124QaEvidence.require(direction.squaredDistanceTo(outward.multiply(-1))<1e-10,"Native ray helper actor pose differs");
                BlockHitResult hit=world.raycast(new RaycastContext(player.getEyePos(),player.getEyePos().add(direction.multiply(5)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                Owner selected=hit.getType()==HitResult.Type.BLOCK?CompositeRuntime.target(world,hit.getBlockPos(),player):null;
                if(owner.equals(selected))return hit;
                JsonObject attempt=new JsonObject();attempt.addProperty("probeCell",cell.toShortString());attempt.addProperty("face",face.asString());attempt.addProperty("managedCarrier",probe.managed());attempt.addProperty("eye",player.getEyePos().toString());attempt.addProperty("hitType",hit.getType().toString());attempt.addProperty("hitCell",hit.getBlockPos().toShortString());attempt.addProperty("hitPosition",hit.getPos().toString());attempt.addProperty("selected",selected==null?"NONE":selected.toString());attempts.add(attempt);
            }
        }
        JsonObject diagnostic=new JsonObject();diagnostic.addProperty("owner",owner.toString());diagnostic.addProperty("state",state.toString());diagnostic.addProperty("payload",own.payload().toString());diagnostic.add("realNativeRayAttempts",attempts);result.add("rayDiagnostic",diagnostic);
        throw new AssertionError("Native outline rays from exposed candidate faces did not select canonical UUID "+owner);
    }
    private static double largestFace(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box box){double x=box.maxX()-box.minX(),y=box.maxY()-box.minY(),z=box.maxZ()-box.minZ();return Math.max(x*y,Math.max(x*z,y*z));}
    private static double faceArea(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box box,Direction face){double x=box.maxX()-box.minX(),y=box.maxY()-box.minY(),z=box.maxZ()-box.minZ();return switch(face.getAxis()){case X->y*z;case Y->x*z;case Z->x*y;};}
    private void assertNative(ServerWorld world,BlockPos root){
        BlockState state=world.getBlockState(root);CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);ObjectInstance object=CompositeRuntime.instance(world,root,state,own.resident().instanceId(),own.payload());boolean passable=object.cells().values().stream().allMatch(f->f.collision().isEmpty());
        for(Cell cell:object.cells().keySet()){BlockPos p=root.add(cell.x(),cell.y(),cell.z());BlockState carrier=world.getBlockState(p);if(!(carrier.getBlock() instanceof CompositeRootBlock)&&!carrier.isOf(CompositeArchitecture.CELL))continue;
            VoxelShape outline=carrier.getOutlineShape(world,p),physical=carrier.getCollisionShape(world,p);
            if(passable){V124QaEvidence.require(physical.isEmpty(),"Passable type became impassable");V124QaEvidence.require(outline.simplify().getBoundingBoxes().size()<=1,"Passable outline has detailed fragments");}
            else V124QaEvidence.require(!VoxelShapes.matchesAnywhere(outline,physical,BooleanBiFunction.NOT_SAME),"Actual native outline differs from physical collision "+p);outlineCells++;}
    }
    private void auditLedger(ServerWorld world){
        var ledger=CompositeLedger.get(world);Map<UUID,Set<Cell>> membership=new HashMap<>();
        for(Cell cell:ledger.cells())for(var entry:ledger.at(cell)){
            if(fixtureIds.contains(entry.owner().instanceId())){V124QaEvidence.require(entry.owner().equals(expectedOwners.get(entry.owner().instanceId())),"Retired/orphan owner contribution remains in saved ledger "+cell);membership.computeIfAbsent(entry.owner().instanceId(),k->new TreeSet<>()).add(cell);}
            else V124QaEvidence.require(!entry.owner().registryId().startsWith("bloodborne_dw:source_"),"Temporary ordinary item owner remained after native deletion "+entry.owner());
        }
        for(var entry:expectedOwners.entrySet()){
            Owner owner=entry.getValue();BlockPos root=CompositeData.pos(owner.root());CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);ObjectInstance object=CompositeRuntime.instance(world,root,world.getBlockState(root),owner.instanceId(),own.payload());Set<Cell> expected=new TreeSet<>();object.cells().keySet().forEach(c->expected.add(owner.root().add(c)));
            V124QaEvidence.require(expected.equals(membership.get(owner.instanceId())),"Global saved-owner footprint has leftovers or holes "+owner);}
    }
    private static void removed(ServerWorld world,ObjectInstance object){for(Cell offset:object.cells().keySet())V124QaEvidence.require(CompositeRuntime.contributions(world,CompositeData.pos(object.owner().root().add(offset))).stream().noneMatch(e->e.owner().equals(object.owner())),"Ordinary native delete left owned membership");}
    private void finish(ServerWorld world)throws Exception{
        done=true;if(interactionActor!=null)interactionActor.discard();saved.addProperty("afterProductionJarSha256",V124QaEvidence.productionSha());saved.addProperty("status",reopen?"PASS_REOPEN":"PASS_SAVED_MIGRATION_AND_ORDINARY_ITEMS");V124QaEvidence.write(fixturePath,saved);
        result.addProperty("savedUuidRootStateTypedNbtProfileMountAndSourceShift","PASS");result.addProperty("globalLedgerNoRetiredOwnersOrOrphans","PASS");result.addProperty("oldToolSelectionCommandsAndCounters",commandChecks>0?"PASS":"NOT_APPLICABLE");result.addProperty("oldMechanismLinksPoliciesAndTransitionEvents",linkChecks>0?"PASS":"NOT_APPLICABLE");
        result.addProperty("savedCommandBindingsChecked",commandChecks);result.addProperty("savedMechanismLinksChecked",linkChecks);result.addProperty("exactOwnerCellsChecked",ownerCells);result.addProperty("nativeOutlineCellsChecked",outlineCells);result.addProperty("sourceVertexBoundsCompared",boundsChecks);result.addProperty("sourceBoundsMaximumDeviation",maximumBoundsDeviation);
        result.addProperty("visualEquivalence","REQUIRES_NATIVE_CLIENT_IMAGE_COMPARISON");result.addProperty("ordinaryAliasExactStateInteractions",reopen?"NOT_RUN_REOPEN_ONLY":Integer.toString(interactions.size()));
        world.getServer().save(false,true,true);write(reopen?"PASS_REOPEN":"PASS");Files.writeString(Path.of("qa-normal-stop.request"),"V12.4 type audit completed\n");
    }
    private void fail(Throwable failure){done=true;if(interactionActor!=null)interactionActor.discard();result.addProperty("failure",failure.toString());java.io.StringWriter trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));result.addProperty("stackTrace",trace.toString());write("FAIL");try{Files.writeString(Path.of("qa-normal-stop.request"),"V12.4 type audit failed\n");}catch(Exception ignored){}}
    private void write(String status){result.addProperty("status",status);result.addProperty("ticks",ticks);result.addProperty("loadedFixtures",loaded);result.addProperty("verifiedFixtures",verified);result.addProperty("ordinaryInteractions",itemIndex);V124QaEvidence.write(Path.of("v124-native-output.json"),result);}
    private static BlockPos root(JsonObject row){JsonArray xyz=row.getAsJsonArray("root");return new BlockPos(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt());}
    private static ToolTarget target(ServerWorld world,JsonObject row,String registry){return new ToolTarget(ToolTarget.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),UUID.fromString(row.get("uuid").getAsString()),root(row).asLong(),registry);}
    private static ItemUsageContext context(ServerPlayerEntity player,BlockPos root){return new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofBottomCenter(root),Direction.UP,root.down(),false));}
    private static void halo(ServerWorld world,BlockPos p){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)force(world,p.add(x,0,z));}
    private static void force(ServerWorld world,BlockPos p){world.setChunkForced(p.getX()>>4,p.getZ()>>4,true);world.getChunk(p);}
    private static void pose(ServerPlayerEntity player,double x,double y,double z,int yaw){player.refreshPositionAndAngles(x,y,z,yaw*45,0);player.setHeadYaw(yaw*45);player.setBodyYaw(yaw*45);player.prevHeadYaw=yaw*45;player.prevBodyYaw=yaw*45;player.prevYaw=yaw*45;player.prevPitch=0;}
    private static BlockState state(CompositeRootBlock block,JsonObject properties){BlockState result=block.getDefaultState();for(var entry:properties.entrySet()){var key=block.getStateManager().getProperty(entry.getKey());if(key!=null)result=parsed(result,key,entry.getValue().getAsString());}return result;}
    private static <T extends Comparable<T>> BlockState parsed(BlockState state,net.minecraft.state.property.Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}
    private static ServerPlayerEntity actor(ServerWorld world,UUID uuid){var player=new ServerPlayerEntity(world.getServer(),world,new GameProfile(uuid,"V124Audit"));player.networkHandler=new ServerPlayNetworkHandler(world.getServer(),new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> p){}@Override public void send(Packet<?> p,PacketCallbacks c){}},player);player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowModifyWorld=true;return player;}
}
