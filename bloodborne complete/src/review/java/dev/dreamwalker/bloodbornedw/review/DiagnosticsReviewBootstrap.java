package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Explicit optional QA companion. Production has no marker, fake actor or PersistentState authoring. */
public final class DiagnosticsReviewBootstrap implements ModInitializer {
    private static Session active;
    @Override public void onInitialize(){ServerLifecycleEvents.SERVER_STARTED.register(server->{Path input=Path.of("review-diagnostics-input.json");if(Files.isRegularFile(input))try{active=new Session(server,JsonParser.parseString(Files.readString(input)).getAsJsonObject());}catch(Throwable failure){failed(failure);}});ServerTickEvents.END_SERVER_TICK.register(server->{if(active!=null&&active.server==server)try{active.tick();}catch(Throwable failure){active.fail(failure);active=null;}});}
    private static void failed(Throwable failure){JsonObject report=new JsonObject();report.addProperty("status","FAIL_SERVER_DIAGNOSTICS");report.addProperty("error",failure.toString());write(report);failure.printStackTrace();}
    private static void write(JsonObject value){try{Files.writeString(Path.of("review-diagnostics-output.json"),new GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    private static final class Session {
        final MinecraftServer server;final ServerWorld world;final JsonObject input,out=new JsonObject(),checks=new JsonObject();final String mode;final List<Target> targets=new ArrayList<>();final Map<Long,Boolean> forced=new LinkedHashMap<>();final List<Long> ticks=new ArrayList<>();long allTicks,tickSum,ticksDropped,startedNs;int age;boolean running,opened,closed,saved,ending;String before,after;Markers markers;ServerPlayerEntity actor;CompletableFuture<Path> exported;
        Session(MinecraftServer server,JsonObject input){this.server=server;world=server.getOverworld();this.input=input;mode=input.get("mode").getAsString();require(Set.of("ENABLED","DISABLED","REENTER").contains(mode),"Unknown diagnostics review mode");require(input.get("schemaVersion").getAsInt()==1&&input.get("seconds").getAsInt()==60,"Explicit default60 marker required");require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world"),"Only isolated derived review world permitted");require(input.getAsJsonArray("targets").size()>0&&input.getAsJsonArray("targets").size()<=16,"Bounded existing target list");
            for(JsonElement raw:input.getAsJsonArray("targets")){JsonObject row=raw.getAsJsonObject();BlockPos root=pos(row.getAsJsonArray("root"));ChunkPos chunk=new ChunkPos(root);forced.putIfAbsent(chunk.toLong(),world.getForcedChunks().contains(chunk.toLong()));world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(chunk.x,chunk.z);targets.add(new Target(row,root));}
            out.addProperty("schema","dreamwalker-server-diagnostics-review-v1");out.addProperty("mode",mode);out.addProperty("defaultSeconds",60);out.addProperty("manualClientInteraction","NOT_RUN_DEDICATED_QA; separate ordinary client proof required");out.addProperty("actualPlayerJoin","NOT_RUN_FAKE_ACTOR_IS_NOT_NETWORK_JOIN; separate actual client proof required");out.addProperty("productionWorldCreation","NONE_EXISTING_TARGETS_ONLY");out.add("checks",checks);
        }
        void tick()throws Exception{
            age++;if(!running&&age>=40){begin();return;}if(!running)return;long elapsed=System.nanoTime()-startedNs;
            if(!opened&&elapsed>=2_000_000_000L){for(Target target:targets)if(target.balance())operate(target);opened=true;check("existingTargetsOpened",true);}
            if(opened&&!closed&&elapsed>=4_000_000_000L){for(Target target:targets)if(target.balance())operate(target);closed=true;check("existingTargetsClosedToOriginal",digest().equals(before));}
            if(!saved&&elapsed>=5_000_000_000L){require(server.save(false,true,true),"Actual active-window save failed");saved=true;check("actualSaveDuringWindow",true);}
            long duration=mode.equals("REENTER")?8_000_000_000L:60_000_000_000L;
            if(!ending&&elapsed>=duration){ending=true;DwDiagnostics.reviewTickObserver(server,null);out.addProperty("observedWallNs",elapsed);out.add("observedBaselineTicks",baseline());after=digest();out.addProperty("balancedTargetStateBefore",before);out.addProperty("balancedTargetStateAfter",after);check("balancedExistingTargetState",before.equals(after));
                if(mode.equals("ENABLED"))check("default60AutomaticallyStopped",!Boolean.TRUE.equals(DwDiagnostics.status(server).get("enabled"))&&DwDiagnostics.status(server).get("stopReason").equals("AUTO_DURATION_EXPIRED"));
                if(mode.equals("REENTER")){command("bb diagnostics stop");check("explicitStop",!DwDiagnostics.enabled(world));}
                restoreForced();markers.expected=after;markers.targets=targetRows();markers.markDirty();require(server.save(false,true,true),"Actual final save failed");out.addProperty("actualSave",true);
                if(mode.equals("DISABLED")){check("recordingRemainedOff",!DwDiagnostics.enabled(world));finish(null);return;}exported=DwDiagnostics.export(server);
            }
            if(ending&&exported!=null&&exported.isDone()){Path path=exported.join();require(path!=null&&Files.isRegularFile(path),"Actual local diagnostic export failed");finish(path);}
        }
        void begin(){
            for(Target target:targets)target.resolve(world);
            markers=world.getPersistentStateManager().getOrCreate(Markers::read,()->{if(mode.equals("REENTER"))throw new IllegalStateException("Persisted diagnostic identity markers missing after restart");return new Markers();},"bloodborne_dw_qa_diagnostics");
            before=digest();if(mode.equals("REENTER")){check("persistedIdentityAndState",markers.expected.equals(before)&&markers.targets.equals(targetRows()));out.addProperty("reenterPersistedIdentityEquality",true);}
            actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"DiagnosticsQA"));actor.networkHandler=new ServerPlayNetworkHandler(server,new SilentConnection(),actor);actor.changeGameMode(GameMode.CREATIVE);actor.getAbilities().allowModifyWorld=true;
            var source=server.getCommandSource().withWorld(world);var node=server.getCommandManager().getDispatcher().getRoot().getChild("bb").getChild("diagnostics");boolean permission=node!=null&&!node.canUse(source.withLevel(0))&&node.canUse(source.withLevel(2));out.addProperty("operatorPermissionChecks",permission);check("operatorPermission2",permission);
            DwDiagnostics.stop(server,"QA_SETUP");check("initiallyDisabled",!DwDiagnostics.enabled(world));
            if(!mode.equals("DISABLED")){command("bb diagnostics start");check("defaultCommandStarted",DwDiagnostics.enabled(world));out.addProperty("sessionId",String.valueOf(DwDiagnostics.status(server).get("sessionId")));command("bb diagnostics status");command("bb diagnostics mark existing problem targets; isolated QA");for(Target target:targets){if(target.rp!=null)DwDiagnostics.snapshot(world,actor,target.rp,Map.of("note",target.name()));else DwDiagnostics.snapshot(world,null,target.root,Map.of("note",target.name()));}
                Target nativeTarget=targets.stream().filter(t->t.rp==null&&!(world.getBlockState(t.root).getBlock() instanceof CompositeRootBlock)).findFirst().orElse(null);if(nativeTarget!=null){ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);tool.getOrCreateNbt().putInt("BuilderAction",BuildingTool.Action.DIAGNOSTICS.ordinal());actor.setStackInHand(Hand.MAIN_HAND,tool);BlockState state=world.getBlockState(nativeTarget.root);require(tool.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(nativeTarget.root),Direction.NORTH,nativeTarget.root,false))).isAccepted(),"Actual diagnostic builder action failed");check("readOnlyToolSnapshot",world.getBlockState(nativeTarget.root).equals(state));}}
            startedNs=System.nanoTime();running=true;DwDiagnostics.reviewTickObserver(server,ns->{allTicks++;tickSum+=ns;if(ticks.size()<4096)ticks.add(ns);else ticksDropped++;});out.addProperty("warmupTicks",age);out.addProperty("comparisonTickScope","Whole MinecraftServer.tick HEAD to RETURN; subsequent ticks retained; transition/API startup/stop own overhead separately in core export");
        }
        void operate(Target target){
            actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            if(target.rp!=null){var box=target.rp.selectionBoxes().get(0);actor.setPosition(box.getCenter().x,box.minY,box.maxZ+3);require(target.rp.interact(actor,Hand.MAIN_HAND).isAccepted(),"Ordinary RP target interaction rejected");return;}
            BlockState state=world.getBlockState(target.root);require(state.getBlock() instanceof CompositeRootBlock&&state.contains(CompositeRootBlock.OPEN),"Balanced architecture target must be real openable root");var be=(CompositeBlockEntity)world.getBlockEntity(target.root);var expectedOwner=be.resident();require(expectedOwner!=null,"Balanced architecture owner missing: "+target.name()+" root="+target.root);var object=CompositeRuntime.instance(target.root,state,expectedOwner.instanceId(),be.payload());Vec3d hit=null;BlockPos hitCell=null;
            for(var cell:object.cells().entrySet())if(!cell.getValue().selection().isEmpty()){var box=cell.getValue().selection().get(0);hitCell=target.root.add(cell.getKey().x(),cell.getKey().y(),cell.getKey().z());hit=new Vec3d(target.root.getX()+cell.getKey().x()+(box.minX()+box.maxX())/2,target.root.getY()+cell.getKey().y()+(box.minY()+box.maxY())/2,target.root.getZ()+cell.getKey().z()+(box.minZ()+box.maxZ())/2);break;}
            require(hit!=null&&hitCell!=null,"Authored selection available");actor.setPosition(hit.x,hit.y-actor.getStandingEyeHeight(),hit.z+3);Vec3d delta=hit.subtract(actor.getEyePos());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));actor.setYaw(yaw);actor.setHeadYaw(yaw);actor.setPitch(0);var chosen=CompositeRuntime.targetReadOnly(world,hitCell,actor);require(expectedOwner.equals(chosen),"Ordinary architecture ray selected wrong owner: "+target.name()+" root="+target.root+" hitCell="+hitCell+" expected="+expectedOwner+" actual="+chosen);boolean open=state.get(CompositeRootBlock.OPEN);BlockState hitState=world.getBlockState(hitCell);require(hitState.getBlock().onUse(hitState,world,hitCell,actor,Hand.MAIN_HAND,new BlockHitResult(hit,Direction.SOUTH,hitCell,false)).isAccepted(),"Ordinary architecture onUse rejected: "+target.name()+" root="+target.root+" hitCell="+hitCell+" carrier="+hitState+" owner="+chosen);require(world.getBlockState(target.root).get(CompositeRootBlock.OPEN)!=open,"Actual architecture OPEN changed");
        }
        void command(String command){require(server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(world),command)>0,"Actual command failed: "+command);}
        JsonObject baseline(){JsonObject result=new JsonObject();result.addProperty("allCount",allTicks);result.addProperty("allSumNs",tickSum);result.addProperty("retained",ticks.size());result.addProperty("dropped",ticksDropped);result.addProperty("scope","QA observer identical in on/off modes; no GPU/block percent inference");JsonArray raw=new JsonArray();ticks.forEach(raw::add);result.add("rawNs",raw);return result;}
        String digest(){return hash(new Gson().toJson(targetRows()).getBytes(StandardCharsets.UTF_8));}
        JsonArray targetRows(){JsonArray result=new JsonArray();for(Target target:targets){JsonObject row=new JsonObject();row.addProperty("name",target.name());row.add("root",target.row.getAsJsonArray("root"));
            if(target.rp!=null){var rp=target.rp;row.addProperty("instanceId",rp.getUuid().toString());row.addProperty("asset",rp.assetId());row.addProperty("open",rp.isOpen());row.addProperty("locked",rp.isLocked());row.addProperty("dogsVisible",rp.dogsVisible());row.addProperty("scale",rp.objectScale());row.addProperty("yaw",rp.getYaw());row.addProperty("x",rp.getX());row.addProperty("y",rp.getY());row.addProperty("z",rp.getZ());row.addProperty("woodPulseActive",rp.woodGatePulseActive());}
            else{BlockState state=world.getBlockState(target.root);row.addProperty("state",state.toString());if(world.getBlockEntity(target.root) instanceof CompositeBlockEntity be&&be.resident()!=null)row.addProperty("instanceId",be.resident().instanceId().toString());else row.addProperty("instanceId","root:"+target.root.getX()+","+target.root.getY()+","+target.root.getZ());}result.add(row);}return result;}
        void restoreForced(){for(var entry:forced.entrySet()){ChunkPos chunk=new ChunkPos(entry.getKey());world.setChunkForced(chunk.x,chunk.z,entry.getValue());}out.addProperty("forcedFlagsRestored",forced.entrySet().stream().allMatch(e->world.getForcedChunks().contains(e.getKey())==e.getValue()));}
        void finish(Path path)throws Exception{DwDiagnostics.reviewTickObserver(server,null);if(path!=null){out.addProperty("exportZipPath",path.toAbsolutePath().toString());out.addProperty("exportZipSha256",hash(Files.readAllBytes(path)));check("exportExists",true);}out.add("diagnosticStatus",JsonParser.parseString(new Gson().toJson(DwDiagnostics.status(server))).getAsJsonObject());out.add("targetStateRows",targetRows());out.addProperty("status","PASS_SERVER_DIAGNOSTICS_"+mode);out.addProperty("checksPassed",checks.entrySet().stream().allMatch(e->e.getValue().getAsBoolean()));actor.discard();write(out);active=null;}
        void fail(Throwable failure){DwDiagnostics.reviewTickObserver(server,null);DwDiagnostics.stop(server,"QA_FAILURE");restoreForced();out.addProperty("status","FAIL_SERVER_DIAGNOSTICS");out.addProperty("error",failure.toString());write(out);if(actor!=null)actor.discard();failure.printStackTrace();}
        void check(String name,boolean value){checks.addProperty(name,value);require(value,name);}
    }
    private static final class Target {final JsonObject row;final BlockPos root;RpObjectEntity rp;Target(JsonObject row,BlockPos root){this.row=row;this.root=root;}String name(){return row.get("name").getAsString();}boolean balance(){return row.has("balancedInteraction")&&row.get("balancedInteraction").getAsBoolean();}void resolve(ServerWorld world){if(!row.get("kind").getAsString().equals("rp")){require(world.isChunkLoaded(root)&&!world.getBlockState(root).isAir(),"Existing native/architecture target missing "+name());return;}String asset=row.get("asset").getAsString();double closest=4096;for(Entity entity:world.iterateEntities())if(entity instanceof RpObjectEntity candidate&&candidate.assetId().equals(asset)){double distance=candidate.squaredDistanceTo(Vec3d.ofCenter(root));if(distance<closest){closest=distance;rp=candidate;}}require(rp!=null,"Existing RP target missing "+name());}}
    private static final class Markers extends PersistentState {String expected="";JsonArray targets=new JsonArray();static Markers read(NbtCompound tag){Markers value=new Markers();value.expected=tag.getString("Expected");value.targets=JsonParser.parseString(tag.getString("Targets")).getAsJsonArray();return value;}@Override public NbtCompound writeNbt(NbtCompound tag){tag.putString("Expected",expected);tag.putString("Targets",new Gson().toJson(targets));return tag;}}
    private static final class SilentConnection extends ClientConnection {SilentConnection(){super(NetworkSide.SERVERBOUND);}@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}}
    private static BlockPos pos(JsonArray v){return new BlockPos(v.get(0).getAsInt(),v.get(1).getAsInt(),v.get(2).getAsInt());}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception failure){throw new IllegalStateException(failure);}}
}
