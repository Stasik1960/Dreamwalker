package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornerp.object.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Optional QA add-on using only APIs present in unchanged d5d7828 production. */
public final class BaseV10ComplaintProbeBootstrap implements ModInitializer {
    public static final String INPUT="base-v10-probe-input.json",OUTPUT="base-v10-probe-output.json";
    public static final String GUARD="QA_ONLY_FRESH_BASE_V10_INTERNAL_SERVER_LOGIC";
    public static final String BASELINE_SHA="cabd5b35777a4c69f6f95e4b0363ca5d33daadaddba08dbcd379fe03a1a753ee";
    public static final String BASELINE_COMMIT="d5d78282b5df3d274a49be8e5b947bedc6bf3ecc";
    private static final String SCOPE="INTERNAL_SERVER_LOGIC_NOT_CLIENT_INPUT";
    private static final Map<MinecraftServer,Run> RUNS=new WeakHashMap<>();
    private static Path path(String file){return FabricLoader.getInstance().getGameDir().resolve(file);}
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if(!Files.isRegularFile(path(INPUT)))return;try{RUNS.put(server,new Run(server));}catch(Throwable failure){fail(null,failure);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{Run run=RUNS.get(server);if(run==null)return;try{if(++run.age==20){run.probe();run.finish();RUNS.remove(server);}}catch(Throwable failure){run.restore();fail(run,failure);RUNS.remove(server);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{Run run=RUNS.remove(server);if(run!=null){run.restore();fail(run,new IllegalStateException("Stopped before bounded baseline probe completed"));}});
    }
    private static final class Run {
        final MinecraftServer server;final ServerWorld world;final JsonObject report=new JsonObject();final JsonArray rows=new JsonArray();final Map<Long,Boolean> forced=new LinkedHashMap<>();
        final ServerPlayerEntity actor;RpObjectEntity door;int age;boolean restored;
        Run(MinecraftServer server)throws Exception{
            this.server=server;world=server.getOverworld();require(Files.size(path(INPUT))<=4096,"Bounded explicit marker required");JsonObject input=JsonParser.parseString(Files.readString(path(INPUT),StandardCharsets.UTF_8)).getAsJsonObject();
            require(input.get("schema").getAsString().equals("dw-base-v10-probe-input-v1")&&input.get("guard").getAsString().equals(GUARD)&&input.get("baselineCommit").getAsString().equals(BASELINE_COMMIT),"Exact baseline probe schema, guard and full commit required");
            require(input.get("productionJarSha256").getAsString().equals(BASELINE_SHA),"Only explicitly frozen unchanged baseline JAR is accepted");
            require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world")&&world.getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator&&!Files.exists(path(OUTPUT)),"Fresh isolated flat world without prior probe output required");
            report.addProperty("schema","dw-base-v10-probe-output-v1");report.addProperty("baselineCommit",BASELINE_COMMIT);report.addProperty("scope",SCOPE);report.addProperty("status","RUNNING");report.addProperty("markerSha256",ReviewV10ProofSupport.sha(Files.readAllBytes(path(INPUT))));ReviewV10ProofSupport.artifact(input,report);report.add("observations",rows);
            report.addProperty("clientInputGuiRendering","NOT_RUN");report.addProperty("ordinaryProductionCodeModified",false);report.addProperty("fixtureScope","In-memory item/state queries and one temporary actual RP entity; no world block edits");
            actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"BaseV10Probe")){@Override public boolean hasPermissionLevel(int level){return level<=4;}};
            actor.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}},actor);actor.changeGameMode(GameMode.CREATIVE);actor.getAbilities().allowModifyWorld=true;actor.setPosition(16,200,16);
            try{for(int x=1;x<=2;x++)for(int z=1;z<=2;z++){ChunkPos chunk=new ChunkPos(x,z);forced.put(chunk.toLong(),world.getForcedChunks().contains(chunk.toLong()));world.setChunkForced(x,z,true);world.getChunk(x,z);}}catch(RuntimeException|Error failure){restore();throw failure;}
        }
        void probe()throws Exception{
            observe("90008_from_nbt_stays_retired_item","ItemStack.fromNbt retains bloodborne_dw:builder_tool before any normal tick/use",()->{
                ItemStack old=Registries.ITEM.get(new Identifier("bloodborne_dw:builder_tool")).getDefaultStack();require(!old.isEmpty(),"Baseline retired registry missing");old.getOrCreateNbt().putInt("BuilderAction",6);ItemStack loaded=ItemStack.fromNbt(old.writeNbt(new NbtCompound()));JsonObject actual=new JsonObject();String registry=Registries.ITEM.getId(loaded.getItem()).toString();actual.addProperty("registry",registry);actual.addProperty("BuilderAction",loaded.getOrCreateNbt().getInt("BuilderAction"));return new Observation(registry.equals("bloodborne_dw:builder_tool"),actual);
            });
            observe("90002_rotate45_allows_odd_yaw","Native 90002 rotate45 changes yaw0 to yaw1 (45 degrees)",()->{
                var state=PrototypeWallArchitecture.WALL.getDefaultState().with(PrototypeWallBlock.ROTATION,0);var rotated=PrototypeWallArchitecture.WALL.rotate45(state);JsonObject actual=new JsonObject();actual.addProperty("beforeYaw",0);actual.addProperty("afterYaw",rotated.get(PrototypeWallBlock.ROTATION));actual.addProperty("registry",Registries.BLOCK.getId(state.getBlock()).toString());return new Observation((rotated.get(PrototypeWallBlock.ROTATION)&1)==1,actual);
            });
            observe("90002_without_bottom_support_is_refused","90002 canPlaceAt is false above an actual AIR block",()->{
                BlockPos root=new BlockPos(24,200,24);require(world.getBlockState(root).isAir()&&world.getBlockState(root.down()).isAir(),"Untouched air fixture required");var state=PrototypeWallArchitecture.WALL.getDefaultState();boolean allowed=state.canPlaceAt(world,root);JsonObject actual=new JsonObject();actual.addProperty("canPlaceAt",allowed);actual.addProperty("below",Registries.BLOCK.getId(world.getBlockState(root.down()).getBlock()).toString());return new Observation(!allowed,actual);
            });
            observe("90006_diagonal_player_collision_empty_without_mounts","Diagonal ladder returns empty player collision despite all four mounting neighbors being AIR",()->{
                BlockPos root=new BlockPos(25,200,25);for(Direction direction:Direction.Type.HORIZONTAL)require(world.getBlockState(root.offset(direction)).isAir(),"Unsupported diagonal fixture requires AIR neighbors");var state=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.DIAGONAL,true).with(PrototypeLadderBlock.SOURCE_CLONE,false).with(PrototypeLadderBlock.FREESTANDING,false);var playerCollision=state.getCollisionShape(world,root,ShapeContext.of(actor));var ordinary=state.getCollisionShape(world,root,ShapeContext.absent());JsonObject actual=new JsonObject();actual.addProperty("playerCollisionEmpty",playerCollision.isEmpty());actual.addProperty("absentContextBoxes",ordinary.getBoundingBoxes().size());actual.addProperty("supportingNeighbors",0);return new Observation(playerCollision.isEmpty()&&!ordinary.isEmpty(),actual);
            });
            observe("rp_open_then_close_accepted_same_tick","Actual door setOpen(true), then setOpen(false), accepts both before any animation tick",()->{
                door=ObjectRegistry.TYPES.get("door_1").create(world);require(door!=null,"Existing baseline RP factory required");door.refreshPositionAndAngles(36.5,200,36.5,0,0);require(world.spawnEntity(door),"Temporary baseline RP fixture refused");boolean opened=door.setOpen(true),openState=door.isOpen(),closed=door.setOpen(false);JsonObject actual=new JsonObject();actual.addProperty("uuid",door.getUuidAsString());actual.addProperty("openAccepted",opened);actual.addProperty("stateAfterOpen",openState);actual.addProperty("immediateCloseAccepted",closed);actual.addProperty("stateAfterImmediateClose",door.isOpen());actual.addProperty("interveningEntityTicks",0);return new Observation(opened&&openState&&closed&&!door.isOpen(),actual);
            });
            observe("90020_south_uv_reuses_north_region_not_historical_source","window03 south UV is the horizontally mirrored north UV region, not original source south UV",()->{
                JsonObject source=model("base/source/minecraft/block/hold/window_03.json"),current=model("base/v10_windows/window_03.json");var before=source.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");var now=current.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");JsonArray north=now.getAsJsonObject("north").getAsJsonArray("uv"),south=now.getAsJsonObject("south").getAsJsonArray("uv"),oldSouth=before.getAsJsonObject("south").getAsJsonArray("uv");JsonArray mirror=new JsonArray();for(int index:new int[]{2,1,0,3})mirror.add(north.get(index).deepCopy());JsonObject actual=new JsonObject();actual.add("northUv",north.deepCopy());actual.add("southUv",south.deepCopy());actual.add("historicalSourceSouthUv",oldSouth.deepCopy());actual.add("horizontallyMirroredNorthUv",mirror);actual.addProperty("southDiffersFromHistoricalSource",!south.equals(oldSouth));return new Observation(south.equals(mirror)&&!south.equals(oldSouth),actual);
            });
        }
        void observe(String id,String expectation,Probe probe){JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("expectedObservation",expectation);row.addProperty("scope",SCOPE);try{Observation result=probe.run();row.addProperty("expectedObservationPresent",result.present());row.addProperty("status",result.present()?"EXPECTED_OBSERVATION_PRESENT":"EXPECTED_OBSERVATION_NOT_PRESENT");row.add("actual",result.actual());}catch(Throwable failure){row.addProperty("expectedObservationPresent",false);row.addProperty("status","PROBE_FAILED");row.addProperty("error",failure.toString());}rows.add(row);}
        void restore(){if(restored)return;restored=true;if(door!=null&&!door.isRemoved())door.discard();actor.discard();for(var entry:forced.entrySet()){ChunkPos chunk=new ChunkPos(entry.getKey());world.setChunkForced(chunk.x,chunk.z,entry.getValue());}report.addProperty("temporaryFixtureRemoved",door==null||door.isRemoved());report.addProperty("previousChunkForceFlagsRestored",true);}
        void finish()throws Exception{restore();boolean all=rows.size()==6;for(var row:rows)all&=row.getAsJsonObject().get("expectedObservationPresent").getAsBoolean();report.addProperty("status",all?"EXPECTED_BASELINE_OBSERVATIONS_PRESENT":"EXPECTED_BASELINE_OBSERVATIONS_INCOMPLETE");report.addProperty("expectedObservationCount",6);report.addProperty("observedCount",rows.size());ReviewV10ProofSupport.write(path(OUTPUT),report);}
    }
    @FunctionalInterface private interface Probe {Observation run()throws Exception;}
    private record Observation(boolean present,JsonObject actual){}
    private static JsonObject model(String relative)throws Exception{try(InputStream stream=BaseV10ComplaintProbeBootstrap.class.getResourceAsStream("/assets/bloodborne_dw/models/"+relative)){require(stream!=null,"Exact baseline model resource missing: "+relative);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}}
    private static void fail(Run run,Throwable failure){try{JsonObject report=run==null?new JsonObject():run.report;report.addProperty("schema","dw-base-v10-probe-output-v1");report.addProperty("scope",SCOPE);report.addProperty("status","BASELINE_PROBE_REFUSED_OR_FAILED");report.addProperty("error",failure.toString());if(!Files.exists(path(OUTPUT)))ReviewV10ProofSupport.write(path(OUTPUT),report);}catch(Exception ignored){}}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
