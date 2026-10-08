package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import net.minecraft.util.WorldSavePath;

/** QA-only bounded source transactions. This class is absent from the production mod. */
public final class SourceReviewBootstrap {
    private static final int SILENT=Block.FORCE_STATE|Block.SKIP_DROPS;
    private record Snapshot(BlockPos pos,BlockState state,BlockEntity instance,NbtCompound nbt,List<CompositeData.Contribution> ledger){}
    private record Request(JsonObject json,BlockPos root,BlockState target,NbtCompound payload,UUID owner,List<BlockPos> members,Set<BlockPos> writes){}
    private static final class Markers extends PersistentState {
        NbtCompound values=new NbtCompound();
        static Markers read(NbtCompound nbt){Markers state=new Markers();state.values=nbt.getCompound("Instances").copy();return state;}
        @Override public NbtCompound writeNbt(NbtCompound nbt){nbt.put("Instances",values.copy());return nbt;}
    }
    private SourceReviewBootstrap(){}
    public static void author(MinecraftServer server){
        Path input=Path.of("source-review-input.json"),output=Path.of("source-review-output.json");if(!Files.isRegularFile(input))return;
        JsonObject report=new JsonObject();report.addProperty("schema","dreamwalker-bounded-source-runtime-v1");
        report.addProperty("visualAcceptance","PENDING_USER_REVIEW");report.addProperty("originalArchiveWritten",false);
        JsonArray rows=new JsonArray();report.add("objects",rows);ServerWorld world=server.getOverworld();
        Map<BlockPos,Snapshot> journal=new LinkedHashMap<>(),nativeGuard=new LinkedHashMap<>();List<Request> installed=new ArrayList<>();
        Markers markers=null;NbtCompound markersBefore=null;ServerPlayerEntity physicsPlayer=null;
        try{
            JsonObject doc=JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            if(doc.get("schemaVersion").getAsInt()!=1||!doc.get("authoringGuard").getAsString().equals("SOURCE_COPY_EXPLICIT_MEMBERS_ONLY"))throw new IllegalArgumentException("Explicit bounded-source guard required");
            if(doc.has("reviewRevision")&&!doc.get("reviewRevision").getAsString().equals("V7"))verifyDescriptorBytes(doc,report);
            if(!server.getSaveProperties().getLevelName().equals(doc.get("allowedLevelName").getAsString()))throw new IllegalArgumentException("Unexpected source-review world name");
            Path save=server.getSavePath(WorldSavePath.ROOT);JsonObject marker=JsonParser.parseString(Files.readString(save.resolve("dreamwalker-source-copy.json"))).getAsJsonObject();
            if(!marker.get("sourceFixtureSha256").equals(doc.get("sourceFixtureSha256"))||!marker.get("sourceArchiveSha256").equals(doc.get("sourceArchiveSha256"))||!marker.get("preloadAuditStatus").getAsString().equals("PASS_ALL_ENTITY_AND_INVENTORY_CONTEXTS_RESOLVED"))throw new IllegalArgumentException("Pre-load typed source audit marker mismatch");
            if(!marker.has("occupiedBlockAuditStatus")||!marker.get("occupiedBlockAuditStatus").getAsString().equals("PASS_ALL_OCCUPIED_BLOCK_PROVIDERS_RESOLVED"))throw new IllegalArgumentException("Complete source occupied-block provider audit required");
            report.add("sourceFixtureSha256",doc.get("sourceFixtureSha256"));report.add("sourceArchiveSha256",doc.get("sourceArchiveSha256"));report.add("excludedInstances",doc.get("excludedInstances"));
            report.addProperty("sourceNbtSerializationNormalization","Live createNbtWithId omits x/y/z and chunk-loader keepPacked byte0; every raw typed field remains in SourceMembers/RawSourceMembers provenance");
            List<Request> requests=new ArrayList<>();Set<BlockPos> allMembers=new HashSet<>(),roots=new HashSet<>();Set<ChunkPos> chunks=new HashSet<>();
            JsonArray technicalLights=new JsonArray();for(JsonElement raw:doc.getAsJsonArray("technicalLightCells")){
                JsonObject cell=raw.getAsJsonObject();BlockPos p=pos(cell.getAsJsonArray("pos"));world.getChunk(p);chunks.add(new ChunkPos(p));BlockState state=world.getBlockState(p);
                if(!Registries.BLOCK.getId(state.getBlock()).toString().equals(cell.get("afterState").getAsString())||state.getLuminance()!=9||state.isAir()||!state.isReplaceable()||!state.getCollisionShape(world,p).isEmpty()||!state.getOutlineShape(world,p).isEmpty()||world.getBlockEntity(p)!=null)throw new IllegalArgumentException("Source technical light9 parity failed at "+p);
                JsonObject checked=cell.deepCopy();checked.addProperty("actualState",state.toString());checked.addProperty("runtimeParity","PASS_LIGHT9_EMPTY_COLLISION_EMPTY_SELECTION_REPLACEABLE_NOT_ISAIR_NO_BE");technicalLights.add(checked);
            }if(technicalLights.size()!=2)throw new IllegalArgumentException("Exactly two source technical light cells required");report.add("technicalLights",technicalLights);
            for(JsonElement value:doc.getAsJsonArray("objects")){
                JsonObject row=value.getAsJsonObject();BlockPos root=pos(row.getAsJsonArray("root"));String kind=row.get("kind").getAsString();
                if(!roots.add(root))throw new IllegalArgumentException("Duplicate source root");world.getChunk(root);
                List<BlockPos> members=new ArrayList<>();for(JsonElement item:row.getAsJsonArray("members")){BlockPos p=pos(item.getAsJsonObject().getAsJsonArray("pos"));if(!allMembers.add(p))throw new IllegalArgumentException("Duplicate source member");members.add(p);world.getChunk(p);}
                UUID owner=UUID.fromString(row.get("ownerUuid").getAsString());NbtCompound payload=new NbtCompound();payload.putString("ReviewMigrationKey",row.get("key").getAsString());payload.putString("ReviewMigrationSignature",row.get("migrationSignature").getAsString());
                NbtList source=new NbtList();for(JsonElement item:row.getAsJsonArray("members")){JsonObject itemRow=item.getAsJsonObject();NbtCompound saved=new NbtCompound();BlockPos p=pos(itemRow.getAsJsonArray("pos"));saved.putIntArray("Pos",new int[]{p.getX(),p.getY(),p.getZ()});saved.put("State",NbtHelper.fromBlockState(state(itemRow)));NbtCompound raw=sourceNbt(itemRow);if(raw!=null)saved.put("OriginalTypedBlockEntity",raw);source.add(saved);}payload.put("SourceMembers",source);
                BlockState target=null;Set<BlockPos> writes=new HashSet<>(members);writes.add(root);
                if(!kind.equals("source_ladder_pair")){
                    if(kind.equals("prototype_wall")){target=Registries.BLOCK.get(new Identifier("bloodborne_dw",kind)).getDefaultState();for(var art:row.getAsJsonObject("art").entrySet())target=with(target,target.getBlock().getStateManager().getProperty(art.getKey()),art.getValue().getAsString());}
                    else{
                        CompositeRootBlock block=CompositeArchitecture.kindBlock(kind);target=block.getDefaultState().with(CompositeRootBlock.ROTATION,row.get("rotation").getAsInt()).with(CompositeRootBlock.VARIANT,row.get("variant").getAsInt()).with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.BASE).with(CompositeRootBlock.OPEN,false);
                        NbtList shift=new NbtList();for(JsonElement axis:row.getAsJsonArray("sourceShift"))shift.add(NbtDouble.of(axis.getAsDouble()));payload.put("SourceShift",shift);
                        if(block.spec.requiredSupport){BlockPos support=root.add(block.spec.supportOffset.x(),block.spec.supportOffset.y(),block.spec.supportOffset.z());world.getChunk(support);Double height=CompositeRuntime.topHeight(world,support,null);if(height==null||Math.abs(height-1)>1e-9)throw new IllegalArgumentException("Source anchor requires proven full-height support at "+support);payload.putDouble("MountY",height-1);}
                        for(Cell cell:CompositeSourceShift.footprint(block.spec,target,payload).keySet())writes.add(root.add(cell.x(),cell.y(),cell.z()));
                    }
                }
                for(BlockPos p:writes){world.getChunk(p);chunks.add(new ChunkPos(p));}
                requests.add(new Request(row,root,target,payload,owner,List.copyOf(members),Set.copyOf(writes)));
            }
            if(requests.size()!=doc.get("expectedObjectCount").getAsInt()||allMembers.size()!=doc.get("sourceMemberCount").getAsInt())throw new IllegalArgumentException("Source membership totals mismatch");
            markers=world.getPersistentStateManager().getOrCreate(Markers::read,Markers::new,"dreamwalker_review_source_migrations");markersBefore=markers.values.copy();
            boolean complete=true;for(Request request:requests)complete&=already(world,request,markers);
            if(complete){for(Request request:requests){JsonObject row=request.json.deepCopy();row.addProperty("status","ALREADY_APPLIED_NO_OP");rows.add(row);}report.addProperty("status","PASS_SOURCE_MIGRATION_ALREADY_APPLIED_NO_OP");report.addProperty("idempotence","PASS_PERSISTED_SIGNATURE_UUID_AND_ALL_FOOTPRINT_BINDINGS");return;}
            if(!markers.values.isEmpty())throw new IllegalArgumentException("Partial or conflicting source migration marker set; no source cells are consumed");
            // All source states are checked before any mutation; no pre-existing owner is consumed.
            for(Request request:requests){
                if(request.json.has("expectedRoot"))preflight(world,request.json.getAsJsonObject("expectedRoot"),report);
                for(JsonElement member:request.json.getAsJsonArray("members"))preflight(world,member.getAsJsonObject(),report);
                for(BlockPos p:request.writes){Snapshot snapshot=snapshot(world,p);if(!snapshot.ledger.isEmpty()||snapshot.instance instanceof CompositeBlockEntity||snapshot.instance instanceof SourceLadderBlockEntity)throw new IllegalArgumentException("Pre-existing ownership at "+p);journal.putIfAbsent(p,snapshot);}
            }
            for(JsonElement context:doc.getAsJsonArray("nonmemberPreconditions")){JsonObject cell=context.getAsJsonObject();preflight(world,cell,report);world.getChunk(pos(cell.getAsJsonArray("pos")));}
            // Read every native non-air block/BE in the touched chunks. AIR service-cell changes are reported separately.
            for(ChunkPos chunk:chunks)for(int y=world.getBottomY();y<world.getTopY();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                BlockPos p=new BlockPos(chunk.getStartX()+x,y,chunk.getStartZ()+z);if(!allMembers.contains(p)&&!roots.contains(p)&&!world.getBlockState(p).isAir())nativeGuard.put(p,snapshot(world,p));
            }
            report.add("transactionFailureChecks",failureChecks(world,requests,journal,nativeGuard));
            physicsPlayer=new ServerPlayerEntity(server,world,new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:SourceReviewQA".getBytes(StandardCharsets.UTF_8)),"SourceReviewQA"));
            report.add("sourceNativeFabricMovements",moves(world,physicsPlayer,doc.getAsJsonArray("sourcePhysicsMovements")));
            int itemDropsBefore=itemCount(world);
            for(int batchAttempt=0;batchAttempt<2;batchAttempt++){
            for(Request request:requests){
                JsonObject row=request.json.deepCopy();String kind=request.json.get("kind").getAsString();
                if(kind.equals("source_ladder_pair")){
                    BlockPos visual=pos(request.json.getAsJsonArray("visual")),physical=pos(request.json.getAsJsonArray("physical"));Snapshot a=journal.get(visual),b=journal.get(physical);
                    var result=SourceLadderRuntime.install(world,new SourceLadderRuntime.Installation(visual,physical,a.state,b.state,a.nbt,b.nbt,request.json.get("variant").getAsInt(),PrototypeLadderBlock.Profile.BASE,request.owner),null);
                    if(!result.committed())throw new IllegalStateException("Source ladder rejected "+request.json.get("key")+": "+result.reason());
                    for(BlockPos p:request.members){SourceLadderBlockEntity entity=(SourceLadderBlockEntity)world.getBlockEntity(p);NbtCompound provenance=entity.provenance();provenance.putString("ReviewMigrationSignature",request.json.get("migrationSignature").getAsString());provenance.put("RawSourceMembers",request.payload.get("SourceMembers").copy());entity.set(request.owner,physical,visual,provenance);}
                    row.addProperty("placementPath","SourceLadderRuntime.install exact two-cell source pair");
                }else{
                    for(BlockPos p:request.members)world.setBlockState(p,Blocks.AIR.getDefaultState(),SILENT);
                    if(kind.equals("prototype_wall")){if(!world.setBlockState(request.root,request.target,SILENT))throw new IllegalStateException("Source manual wall write failed");row.addProperty("placementPath","Explicit one-member manual source wall state");}
                    else{var result=dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstances(Set.of(request.owner),()->CompositeRuntime.place(world,request.root,request.target,request.owner,null,request.payload));if(result.outcome()!=Outcome.COMMITTED)throw new IllegalStateException("Source composite rejected "+request.json.get("key")+": "+result.reason());row.addProperty("placementPath","Exact-member journal + instance-scoped initial CompositeRuntime.place");}
                }
                installed.add(request);row.addProperty("actualState",world.getBlockState(request.root).toString());row.addProperty("status","COMMITTED");row.addProperty("ownerUuid",request.owner.toString());rows.add(row);
                NbtCompound saved=new NbtCompound();saved.putUuid("Owner",request.owner);saved.putLong("Root",request.root.asLong());saved.putString("Signature",request.json.get("migrationSignature").getAsString());saved.putString("TargetState",world.getBlockState(request.root).toString());markers.values.put(request.json.get("key").getAsString(),saved);
            }
            if(batchAttempt==0){
                // Deliberately fail AFTER every composite, wall and source ladder committed, then restore all41 objects.
                restoreBatch(world,installed,journal,nativeGuard);markers.values=markersBefore.copy();while(rows.size()>0)rows.remove(rows.size()-1);installed.clear();
                boolean intact=itemCount(world)==itemDropsBefore;for(Snapshot snapshot:journal.values())intact&=sameData(world,snapshot);for(Snapshot snapshot:nativeGuard.values())intact&=equal(world,snapshot);
                if(!intact)throw new IllegalStateException("Injected complete41-object rollback changed source/native NBT, ledger or dropped items");
                report.addProperty("injectedWholeBatchRollback","PASS_41_OBJECTS_110_SOURCE_CELLS_STATE_TYPED_NBT_LEDGER_NO_ITEM_DROPS");
            }
            }
            report.add("convertedClosedMovements",moves(world,physicsPlayer,doc.getAsJsonArray("sourcePhysicsMovements")));
            JsonArray openAttempts=new JsonArray(),closeAttempts=new JsonArray();report.add("sourceOpenAttempts",openAttempts);report.add("sourceCloseAttempts",closeAttempts);List<Request> opened=new ArrayList<>();
            for(Request request:requests)if(request.target!=null&&request.target.getBlock() instanceof CompositeRootBlock block&&block.spec.openable){
                if(!already(world,request,markers))throw new IllegalStateException("Source open attempt started with invalid typed owner/mask: "+request.json.get("key"));
                CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(request.root);BlockState next=request.target.with(CompositeRootBlock.OPEN,true);
                var previous=CompositeRuntime.instance(request.root,request.target,entity.resident().instanceId(),entity.payload());var proposed=CompositeRuntime.instance(request.root,next,entity.resident().instanceId(),entity.payload());
                Map<BlockPos,Snapshot> attemptGuard=new LinkedHashMap<>();Set<BlockPos> guarded=new HashSet<>(request.writes);for(Cell cell:previous.cells().keySet())guarded.add(request.root.add(cell.x(),cell.y(),cell.z()));for(Cell cell:proposed.cells().keySet())guarded.add(request.root.add(cell.x(),cell.y(),cell.z()));
                for(BlockPos p:guarded){if(!world.isChunkLoaded(p))throw new IllegalStateException("Source open attempt requires loaded guard cell: "+p);attemptGuard.put(p,snapshot(world,p));}
                Map<Cell,List<CompositeData.Contribution>> ledgerBefore=ledgerSnapshot(world);int dropsBefore=itemCount(world);
                var result=CompositeRuntime.transition(world,entity.resident(),next,null);JsonObject attempt=new JsonObject();attempt.add("key",request.json.get("key"));attempt.addProperty("ownerUuid",request.owner.toString());attempt.add("root",request.json.get("root"));attempt.addProperty("operation","OPEN");attempt.addProperty("outcome",result.outcome().name());attempt.addProperty("reason",result.reason());attempt.addProperty("changedCells",result.changedCells());attempt.addProperty("actualAfterState",world.getBlockState(request.root).toString());openAttempts.add(attempt);
                if(result.outcome()==Outcome.COMMITTED){if(!world.getBlockState(request.root).equals(next))throw new IllegalStateException("Committed source open did not install requested state");attempt.addProperty("status","OPENED_COMMITTED");opened.add(request);}
                else verifyExpectedWoodWindowRefusal(world,request,result,previous,proposed,attemptGuard,ledgerBefore,nativeGuard,dropsBefore,markers,attempt);
            }
            if(opened.stream().noneMatch(request->request.target.getBlock() instanceof CompositeRootBlock block&&block.spec.id.getPath().equals("prototype_double_door")))throw new IllegalStateException("Source double-door OPEN must actually commit");
            JsonArray openMoves=moves(world,physicsPlayer,doc.getAsJsonArray("sourcePhysicsMovements"));report.add("convertedOpenMovements",openMoves);for(JsonElement raw:openMoves){JsonObject move=raw.getAsJsonObject();String purpose=move.get("purpose").getAsString();if((purpose.equals("source_door_center")||purpose.equals("source_door_side"))&&!move.get("fabricUnobstructed").getAsBoolean())throw new IllegalStateException("Actually opened source door blocked required native player passage: "+purpose);}
            for(Request request:opened){CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(request.root);var result=CompositeRuntime.transition(world,entity.resident(),request.target,null);JsonObject attempt=new JsonObject();attempt.add("key",request.json.get("key"));attempt.addProperty("operation","CLOSE");attempt.addProperty("outcome",result.outcome().name());attempt.addProperty("reason",result.reason());closeAttempts.add(attempt);if(result.outcome()!=Outcome.COMMITTED)throw new IllegalStateException("Source close proposal rejected "+request.json.get("key")+": "+result.reason());}
            // Stable publish includes consumed source cells outside the target sparse footprint.
            for(var entry:journal.entrySet()){BlockPos p=entry.getKey();world.updateListeners(p,entry.getValue().state,world.getBlockState(p),Block.NOTIFY_LISTENERS);world.updateNeighbors(p,world.getBlockState(p).getBlock());}
            JsonArray changedNative=changes(world,nativeGuard);report.add("nativeNonmemberChanges",changedNative);report.addProperty("nativeNonmemberBlocksCompared",nativeGuard.size());
            if(!changedNative.isEmpty())throw new IllegalStateException("Native nonmember state/BE changed during bounded migration; exact journal restoration required");
            for(Request request:requests)if(!already(world,request,markers))throw new IllegalStateException("Persisted source migration signature/owner/mask check failed: "+request.json.get("key"));
            markers.markDirty();report.addProperty("idempotence","PASS_SECOND_CALL_SIGNATURE_UUID_AND_ALL_FOOTPRINT_BINDINGS_NO_OP");
            JsonArray commands=new JsonArray();for(JsonElement value:doc.getAsJsonArray("commands")){String command=value.getAsString();if(!(command.startsWith("gamerule ")||command.equals("defaultgamemode creative")||command.startsWith("setworldspawn ")||command.equals("time set noon")||command.equals("weather clear")))throw new IllegalArgumentException("Unexpected source QA command");server.getCommandManager().getDispatcher().execute(command,server.getCommandSource());ReviewSceneBootstrap.verifyGamerule(world,command);commands.add(command);}report.add("commands",commands);
            report.addProperty("status","PASS_BOUNDED_SOURCE_MIGRATION_REQUIRES_PRODUCTION_REOPEN");report.addProperty("objectCount",requests.size());report.addProperty("sourceMemberCount",allMembers.size());report.addProperty("nativeNonmemberPreservation","PASS_LOADED_BEFORE_AFTER_STATE_ALL_NBT_AND_SAME_BE_INSTANCE");
            report.addProperty("limit","Original pre-load bytes and loaded-state comparison are separate; no claim that vanilla DFU or RP entity save preserves unknown Forge fields.");
        }catch(Throwable error){
            report.addProperty("status","FAIL_BOUNDED_SOURCE_MIGRATION");report.addProperty("error",error.toString());error.printStackTrace();
            try{
                restoreBatch(world,installed,journal,nativeGuard);
                if(markers!=null&&markersBefore!=null){markers.values=markersBefore;markers.markDirty();}
                CompositeRuntime.drain(world);boolean restored=true;for(Snapshot snapshot:journal.values())restored&=sameData(world,snapshot);for(Snapshot snapshot:nativeGuard.values())restored&=equal(world,snapshot);
                report.addProperty("rollback",restored?"PASS_ALL_CAPTURED_SOURCE_NATIVE_STATE_NBT_AND_LEDGER_RESTORED":"FAIL_RESTORATION_MISMATCH");
            }catch(Throwable rollback){report.addProperty("rollback","FAIL: "+rollback);rollback.printStackTrace();}
        }finally{
            if(physicsPlayer!=null)physicsPlayer.discard();try{Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(IOException error){throw new IllegalStateException(error);}System.out.println("Dreamwalker bounded source review: "+report.get("status"));
        }
    }
    private static JsonArray moves(ServerWorld world,ServerPlayerEntity player,JsonArray samples){JsonArray result=new JsonArray();for(JsonElement value:samples){JsonObject row=value.getAsJsonObject().deepCopy();JsonArray s=row.getAsJsonArray("start"),d=row.getAsJsonArray("delta");Vec3d start=new Vec3d(s.get(0).getAsDouble(),s.get(1).getAsDouble(),s.get(2).getAsDouble()),delta=new Vec3d(d.get(0).getAsDouble(),d.get(1).getAsDouble(),d.get(2).getAsDouble());for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)world.getChunk(BlockPos.ofFloored(start).add(dx*16,0,dz*16));player.setPosition(start);player.move(MovementType.SELF,delta);Vec3d moved=player.getPos().subtract(start);JsonArray actual=new JsonArray();actual.add(moved.x);actual.add(moved.y);actual.add(moved.z);row.add("fabricDisplacement",actual);row.addProperty("fabricUnobstructed",moved.squaredDistanceTo(delta)<1e-10);result.add(row);}return result;}
    private static boolean already(ServerWorld world,Request r,Markers markers){String key=r.json.get("key").getAsString();if(!markers.values.contains(key,NbtElement.COMPOUND_TYPE))return false;NbtCompound saved=markers.values.getCompound(key);if(!saved.containsUuid("Owner")||!saved.getUuid("Owner").equals(r.owner)||saved.getLong("Root")!=r.root.asLong()||!saved.getString("Signature").equals(r.json.get("migrationSignature").getAsString())||!saved.getString("TargetState").equals(world.getBlockState(r.root).toString()))return false;
        if(r.json.get("kind").getAsString().equals("source_ladder_pair")){if(!r.root.equals(SourceLadderRuntime.resolveRoot(world,r.root)))return false;for(BlockPos p:r.members)if(!(world.getBlockEntity(p) instanceof SourceLadderBlockEntity be)||!r.owner.equals(be.owner())||!be.provenance().getString("ReviewMigrationSignature").equals(r.json.get("migrationSignature").getAsString())||!Objects.equals(be.provenance().get("RawSourceMembers"),r.payload.get("SourceMembers")))return false;return true;}
        if(r.target.getBlock() instanceof CompositeRootBlock block){
            Owner expected=new Owner(r.owner,block.spec.id.toString(),CompositeData.cell(r.root));
            if(!world.getBlockState(r.root).equals(r.target)||!(world.getBlockEntity(r.root) instanceof CompositeBlockEntity be)||!expected.equals(be.resident())||!be.payload().equals(r.payload))return false;
            Map<Cell,dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Footprint> mask=CompositeSourceShift.footprint(block.spec,r.target,r.payload);
            Set<Cell> found=new HashSet<>();for(Cell absolute:CompositeLedger.get(world).cells()){
                List<CompositeData.Contribution> owned=CompositeLedger.get(world).at(absolute).stream().filter(c->c.owner().equals(expected)).toList();if(owned.isEmpty())continue;if(owned.size()!=1)return false;
                Cell local=absolute.subtract(expected.root());var shape=mask.get(local);var contribution=owned.get(0);
                if(shape==null||!shape.equals(contribution.shape())||!CompositeData.state(contribution.rootData()).equals(r.target)||!CompositeData.nbt(contribution.rootData().blockEntityNbt()).equals(r.payload))return false;found.add(local);
            }return found.equals(mask.keySet());
        }return world.getBlockState(r.root).equals(r.target);}
    private static Snapshot snapshot(ServerWorld world,BlockPos p){BlockEntity entity=world.getBlockEntity(p);return new Snapshot(p.toImmutable(),world.getBlockState(p),entity,entity==null?null:entity.createNbtWithId().copy(),List.copyOf(CompositeLedger.get(world).at(CompositeData.cell(p))));}
    private static boolean sameData(ServerWorld world,Snapshot s){BlockEntity entity=world.getBlockEntity(s.pos);return world.getBlockState(s.pos).equals(s.state)&&(s.nbt==null?entity==null:entity!=null&&s.nbt.equals(entity.createNbtWithId()))&&CompositeLedger.get(world).at(CompositeData.cell(s.pos)).equals(s.ledger);}
    private static Map<Cell,List<CompositeData.Contribution>> ledgerSnapshot(ServerWorld world){Map<Cell,List<CompositeData.Contribution>> result=new HashMap<>();for(Cell cell:CompositeLedger.get(world).cells())result.put(cell,List.copyOf(CompositeLedger.get(world).at(cell)));return result;}
    private static void verifyExpectedWoodWindowRefusal(ServerWorld world,Request request,dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Result result,dev.dreamwalker.bloodbornedw.runtime.ObjectInstance previous,dev.dreamwalker.bloodbornedw.runtime.ObjectInstance proposed,Map<BlockPos,Snapshot> attemptGuard,Map<Cell,List<CompositeData.Contribution>> ledgerBefore,Map<BlockPos,Snapshot> nativeGuard,int dropsBefore,Markers markers,JsonObject attempt){
        BlockPos obstruction=new BlockPos(-30,37,-1121);Vec3d witness=new Vec3d(-29.25,37.5,-1120.1);
        if(!request.json.get("key").getAsString().equals("wood_window")||!request.root.equals(new BlockPos(-32,37,-1120))||result.outcome()!=Outcome.REJECTED||result.changedCells()!=0||!Objects.equals(result.reason(),"support_or_policy:solid_native_overlap:-30, 37, -1121"))throw new IllegalStateException("Unexpected source open refusal "+request.json.get("key")+": "+result.reason());
        Snapshot original=nativeGuard.get(obstruction);if(original==null||!original.state.isOf(Blocks.BRICKS)||original.instance!=null||original.nbt!=null||!equal(world,original))throw new IllegalStateException("Expected source window obstruction is not the original unchanged native bricks");
        var beforeBoxes=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.physicalBoxes(previous);var afterBoxes=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.physicalBoxes(proposed);var nativeBoxes=CompositeRuntime.nativeCollision(world,obstruction,ShapeContext.absent()).getBoundingBoxes().stream().map(box->box.offset(obstruction)).toList();
        boolean newlySolid=afterBoxes.stream().anyMatch(box->box.contains(witness))&&beforeBoxes.stream().noneMatch(box->box.contains(witness))&&nativeBoxes.stream().anyMatch(box->box.contains(witness));
        boolean newIntersection=afterBoxes.stream().anyMatch(box->nativeBoxes.stream().anyMatch(nativeBox->dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.overlaps(box,nativeBox)&&!dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.retainedIntersection(box,nativeBox,beforeBoxes)));
        boolean typedCellsUnchanged=attemptGuard.values().stream().allMatch(snapshot->sameData(world,snapshot)&&equal(world,snapshot));boolean ledgerUnchanged=ledgerBefore.equals(ledgerSnapshot(world));boolean nativeUnchanged=changes(world,nativeGuard).isEmpty();boolean ownerMaskUnchanged=already(world,request,markers);boolean noDrops=itemCount(world)==dropsBefore;
        attempt.addProperty("newPhysicalVolumeWitness",newlySolid&&newIntersection);JsonArray point=new JsonArray();point.add(witness.x);point.add(witness.y);point.add(witness.z);attempt.add("worldWitness",point);attempt.addProperty("nativeObstruction",obstruction.toShortString());attempt.addProperty("nativeObstructionState",world.getBlockState(obstruction).toString());attempt.addProperty("typedCellsAndSameBeInstancesUnchanged",typedCellsUnchanged);attempt.addProperty("allLedgerEntriesUnchanged",ledgerUnchanged);attempt.addProperty("allNativeNonmembersUnchanged",nativeUnchanged);attempt.addProperty("typedOwnerPayloadAndExactMaskUnchanged",ownerMaskUnchanged);attempt.addProperty("itemDropsUnchanged",noDrops);attempt.addProperty("opened",false);
        if(!newlySolid||!newIntersection||!typedCellsUnchanged||!ledgerUnchanged||!nativeUnchanged||!ownerMaskUnchanged||!noDrops)throw new IllegalStateException("Expected source wood-window refusal failed geometry or complete preservation proof: "+attempt);
        attempt.addProperty("status","EXPECTED_REFUSED_NEW_NATIVE_SOLID_VOLUME_ALL_TYPED_STATE_UNCHANGED");
    }
    private static boolean equal(ServerWorld world,Snapshot s){BlockEntity entity=world.getBlockEntity(s.pos);return world.getBlockState(s.pos).equals(s.state)&&entity==s.instance&&(s.nbt==null?entity==null:entity!=null&&s.nbt.equals(entity.createNbtWithId()));}
    private static void restore(ServerWorld world,Snapshot s){if(!world.getBlockState(s.pos).equals(s.state))world.setBlockState(s.pos,s.state,SILENT);BlockEntity entity=world.getBlockEntity(s.pos);if(s.nbt!=null&&(entity==null||!s.nbt.equals(entity.createNbtWithId()))){if(entity==null)throw new IllegalStateException("Missing source BE during restoration at "+s.pos);entity.readNbt(s.nbt.copy());entity.markDirty();}CompositeLedger.get(world).put(CompositeData.cell(s.pos),s.ledger);world.updateListeners(s.pos,world.getBlockState(s.pos),s.state,Block.NOTIFY_LISTENERS);}
    private static JsonArray changes(ServerWorld world,Map<BlockPos,Snapshot> snapshots){JsonArray rows=new JsonArray();for(Snapshot s:snapshots.values())if(!equal(world,s)){JsonObject row=new JsonObject();row.addProperty("pos",s.pos.toShortString());row.addProperty("before",s.state.toString());row.addProperty("after",world.getBlockState(s.pos).toString());row.addProperty("sameBeInstance",world.getBlockEntity(s.pos)==s.instance);row.addProperty("sameTypedNbt",s.nbt==null?world.getBlockEntity(s.pos)==null:world.getBlockEntity(s.pos)!=null&&s.nbt.equals(world.getBlockEntity(s.pos).createNbtWithId()));rows.add(row);}return rows;}
    private static void preflight(ServerWorld world,JsonObject cell,JsonObject report)throws IOException{BlockPos p=pos(cell.getAsJsonArray("pos"));world.getChunk(p);if(!world.getBlockState(p).equals(state(cell)))throw new IllegalArgumentException("Exact source state changed at "+p+": "+world.getBlockState(p));NbtCompound source=sourceNbt(cell);BlockEntity be=world.getBlockEntity(p);if(source==null){if(be!=null)throw new IllegalArgumentException("Unexpected source BE at "+p);return;}if(be==null)throw new IllegalArgumentException("Original source BE missing at "+p);NbtCompound normalized=source.copy();normalized.remove("keepPacked");normalized.remove("x");normalized.remove("y");normalized.remove("z");NbtCompound loaded=be.createNbtWithId();if(!loaded.equals(normalized)){JsonArray diff=report.has("loadedSourceNbtNormalizations")?report.getAsJsonArray("loadedSourceNbtNormalizations"):new JsonArray();JsonObject row=new JsonObject();row.addProperty("pos",p.toShortString());row.addProperty("rawOriginal",source.toString());row.addProperty("loadedBefore",loaded.toString());diff.add(row);report.add("loadedSourceNbtNormalizations",diff);throw new IllegalArgumentException("Unproven source BE normalization beyond keepPacked/position at "+p);}}
    private static NbtCompound sourceNbt(JsonObject cell)throws IOException{return !cell.has("sourceNbt")||cell.get("sourceNbt").isJsonNull()?null:NbtIo.read(new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(cell.get("sourceNbt").getAsString()))));}
    private static BlockPos pos(JsonArray value){if(value.size()!=3)throw new IllegalArgumentException("Expected3 source coordinates");BlockPos p=new BlockPos(value.get(0).getAsInt(),value.get(1).getAsInt(),value.get(2).getAsInt());if(Math.abs(p.getX())>512||p.getZ()<-1200||p.getZ()>0||p.getY()<-64||p.getY()>320)throw new IllegalArgumentException("Source coordinate outside declared reviewed areas");return p;}
    private static BlockState state(JsonObject row){Identifier id=new Identifier(row.get("block").getAsString());if(!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unknown source block ID");BlockState result=Registries.BLOCK.get(id).getDefaultState();for(var p:row.getAsJsonObject("properties").entrySet())result=with(result,result.getBlock().getStateManager().getProperty(p.getKey()),p.getValue().getAsString());return result;}
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value){if(property==null)throw new IllegalArgumentException("Unknown source property");return state.with(property,property.parse(value).orElseThrow());}
    private static JsonArray failureChecks(ServerWorld world,List<Request> requests,Map<BlockPos,Snapshot> journal,Map<BlockPos,Snapshot> nativeGuard)throws IOException{
        JsonArray report=new JsonArray();Request first=requests.stream().filter(r->r.target!=null&&r.target.getBlock() instanceof CompositeRootBlock).findFirst().orElseThrow();int drops=itemCount(world);
        JsonObject changed=first.json.getAsJsonArray("members").get(0).getAsJsonObject().deepCopy();changed.addProperty("block","minecraft:diamond_block");changed.add("properties",new JsonObject());boolean refused=false;try{preflight(world,changed,new JsonObject());}catch(IllegalArgumentException expected){refused=true;}if(!refused)throw new IllegalStateException("Changed source-state precondition was accepted");checkRow(report,"CHANGED_SOURCE_STATE_REJECTED",true);
        JsonObject bee=requests.stream().filter(r->r.json.get("kind").getAsString().equals("source_ladder_pair")).findFirst().orElseThrow().json.getAsJsonArray("members").get(0).getAsJsonObject().deepCopy();NbtCompound raw=sourceNbt(bee);raw.putString("UnrecognizedAddedSourceField","must-reject");ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(raw,new DataOutputStream(bytes));bee.addProperty("sourceNbt",Base64.getEncoder().encodeToString(bytes.toByteArray()));refused=false;try{preflight(world,bee,new JsonObject());}catch(IllegalArgumentException expected){refused=true;}if(!refused)throw new IllegalStateException("Changed source typed BE precondition was accepted");checkRow(report,"CHANGED_SOURCE_TYPED_NBT_REJECTED",true);
        var occupied=CompositeRuntime.place(world,first.root,first.target,first.owner,null,first.payload);if(occupied.outcome()!=Outcome.REJECTED)throw new IllegalStateException("Source FOREIGN root was accepted without exact member clear");checkRow(report,"UNCLEARED_SOURCE_ROOT_REJECTED",true);
        for(String stage:List.of("PARTIAL_EXPLICIT_MEMBER_CLEAR","AFTER_COMMITTED_RUNTIME_PLACEMENT")){
            try{
                int limit=stage.startsWith("PARTIAL")?Math.min(3,first.members.size()):first.members.size();for(int i=0;i<limit;i++)world.setBlockState(first.members.get(i),Blocks.AIR.getDefaultState(),SILENT);
                if(stage.startsWith("AFTER")){var placed=dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstances(Set.of(first.owner),()->CompositeRuntime.place(world,first.root,first.target,first.owner,null,first.payload));if(placed.outcome()!=Outcome.COMMITTED)throw new IllegalStateException("Injected placement precondition failed: "+placed.reason());}
                throw new IOException("INJECTED_BOUNDED_SOURCE_FAILURE_"+stage);
            }catch(IOException expected){
                if(world.getBlockEntity(first.root) instanceof CompositeBlockEntity be&&be.resident()!=null)CompositeRuntime.remove(world,be.resident(),null,false);
                SourceLadderRuntime.runInternalWrite(()->{for(BlockPos p:first.writes)restore(world,journal.get(p));for(Snapshot snapshot:nativeGuard.values())if(!equal(world,snapshot))restore(world,snapshot);});CompositeRuntime.drain(world);
                boolean intact=itemCount(world)==drops;for(BlockPos p:first.writes)intact&=sameData(world,journal.get(p));for(Snapshot snapshot:nativeGuard.values())intact&=equal(world,snapshot);if(!intact)throw new IllegalStateException("Injected failure restoration mismatch: "+stage);checkRow(report,"INJECTED_"+stage+"_STATE_NBT_LEDGER_NO_DROPS_RESTORED",true);
            }
        }return report;
    }
    private static void checkRow(JsonArray rows,String name,boolean pass){JsonObject row=new JsonObject();row.addProperty("case",name);row.addProperty("status",pass?"PASS":"FAIL");rows.add(row);}
    private static void verifyDescriptorBytes(JsonObject doc,JsonObject report)throws Exception{
        if(!doc.has("descriptorSha256"))throw new IllegalArgumentException("Versioned source review requires exact production descriptor hashes");
        JsonObject declared=doc.getAsJsonObject("descriptorSha256"),verified=new JsonObject();Set<String> actual=new HashSet<>();
        for(CompositeRootBlock block:CompositeArchitecture.blocks())actual.add(block.spec.id.getPath());
        if(!declared.keySet().equals(actual))throw new IllegalArgumentException("Source review descriptor coverage mismatch");
        for(String kind:new TreeSet<>(actual)){
            if(!kind.matches("prototype_[a-z_0-9]+"))throw new IllegalArgumentException("Unsafe source review descriptor key");
            String expected=declared.get(kind).getAsString();if(!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid descriptor SHA256: "+kind);
            try(InputStream bytes=CompositeArchitecture.class.getResourceAsStream("/bloodborne_dw/composite/"+kind+".json")){
                if(bytes==null)throw new IllegalArgumentException("Missing production descriptor: "+kind);
                String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                if(!digest.equals(expected))throw new IllegalArgumentException("Source review descriptor changed before authoring: "+kind+" expected="+expected+" actual="+digest);
                verified.addProperty(kind,digest);
            }
        }
        report.add("exactProductionDescriptorSha256",verified);report.addProperty("productionDescriptorGuard","PASS_BEFORE_QA_WORLD_CELL_READS_AND_MUTATIONS");
    }
    private static int itemCount(ServerWorld world){return world.getEntitiesByClass(ItemEntity.class,new Box(-512,-64,-1200,512,320,0),entity->true).size();}
    private static void restoreBatch(ServerWorld world,List<Request> installed,Map<BlockPos,Snapshot> journal,Map<BlockPos,Snapshot> nativeGuard){
        List<Request> reversed=new ArrayList<>(installed);Collections.reverse(reversed);for(Request request:reversed){if(request.json.get("kind").getAsString().equals("source_ladder_pair"))SourceLadderRuntime.remove(world,request.root,null,false);else if(world.getBlockEntity(request.root) instanceof CompositeBlockEntity entity&&entity.resident()!=null)CompositeRuntime.remove(world,entity.resident(),null,false);}
        SourceLadderRuntime.runInternalWrite(()->{for(Snapshot snapshot:journal.values())restore(world,snapshot);for(Snapshot snapshot:nativeGuard.values())if(!equal(world,snapshot))restore(world,snapshot);});CompositeRuntime.drain(world);
    }
}
