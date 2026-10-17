package dev.dreamwalker.bloodbornedw.conversion;

import com.google.gson.*;
import com.mojang.datafixers.util.Either;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.composite.mixin.BbChunkFutureInvoker;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.Kind;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;

/** Explicit, bounded cleanup of source-plan decision 223. Inert without its own marker. */
public final class SourceBarrierCleanupAuthor implements ModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path INPUT=Path.of("dw-source-barrier-cleanup-input.json");
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("dw_source_barrier_cleanup",Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<MinecraftServer,Job> JOBS=new WeakHashMap<>();

    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            if(!Files.isRegularFile(INPUT,LinkOption.NOFOLLOW_LINKS))return;
            try{JOBS.put(server,new Job(server,json(INPUT)));}
            catch(Exception failure){throw new IllegalStateException("Source barrier cleanup initialization refused",failure);}
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{Job job=JOBS.get(server);if(job!=null&&!job.finished)try{job.tick();}catch(Exception failure){job.fail(failure);}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{Job job=JOBS.get(server);if(job!=null&&!job.finished)job.fail(new IllegalStateException("Barrier cleanup interrupted; resume from durable checkpoint"));});
        ServerLifecycleEvents.SERVER_STOPPED.register(JOBS::remove);
    }

    private static final class Job {
        final MinecraftServer server; final Path target,phase,checkpoint,report; final String operation,productionSHA,phaseSHA,sourceSHA;
        final int expectedCount,checkpointRows; final Map<String,Long> skipped=new TreeMap<>(); final List<JsonObject> samples=new ArrayList<>();
        final Map<ServerWorld,Rules> rules=new IdentityHashMap<>();
        BufferedReader stream; JsonObject current; ServerWorld ticketWorld; ChunkPos ticketChunk; CompletableFuture<Either<Chunk,ChunkHolder.Unloaded>> future; boolean probed;
        long line,removed,alreadyAbsent,checkpointLine,ticks,drainStartedTick,started=System.nanoTime(); boolean draining,finishing,finished;

        Job(MinecraftServer server,JsonObject marker)throws Exception{
            this.server=server;
            require(marker.get("schemaVersion").getAsInt()==1&&marker.get("mode").getAsString().equals("SOURCE_BARRIER_CLEANUP_AFTER_STATIC_CITY"),"Unknown cleanup marker");
            require(marker.get("explicitBarrierCleanupAuthorized").getAsBoolean(),"Cleanup requires explicit authorization");
            require(Boolean.getBoolean("dw.conversion.boundedChunks")&&ConversionChunkControl.active(server)&&ConversionChunkControl.spawnPreloadBypassed(server),"Bounded startup was not confirmed");
            operation=UUID.fromString(marker.get("operationId").getAsString()).toString();
            target=server.getSavePath(WorldSavePath.ROOT).toRealPath();
            require(target.equals(Path.of(marker.get("targetWorldPath").getAsString()).toRealPath()),"Marker belongs to another world");
            productionSHA=marker.get("productionJarSha256").getAsString();
            Path production=Path.of(System.getProperty("dw.conversion.productionJar","")).toRealPath();require(sha(production).equals(productionSHA),"Production JAR changed");
            // The coordinate generator already read and hashed the immutable SQLite once.
            // Runtime binds that recorded provenance through the small manifest; it does
            // not rescan the unchanged multi-gigabyte preparation database.
            sourceSHA=marker.get("sourcePlanSha256").getAsString();require(sourceSHA.matches("[0-9a-f]{64}"),"Invalid source-plan provenance hash");
            Path manifestPath=Path.of(marker.get("barrierManifestPath").getAsString()).toRealPath();JsonObject manifest=json(manifestPath);
            require(sha(manifestPath).equals(marker.get("barrierManifestSha256").getAsString()),"Barrier manifest changed");
            require(manifest.get("status").getAsString().equals("PASS_PROVEN_SOURCE_BARRIERS")&&manifest.get("decisionId").getAsInt()==223,"Barrier provenance is not proven");
            require(manifest.getAsJsonObject("decision").get("sourceState").getAsString().equals("minecraft:barrier"),"Cleanup source is not minecraft:barrier");
            require(manifest.get("sourcePlanSha256").getAsString().equals(sourceSHA),"Barrier coordinates belong to another plan");
            expectedCount=manifest.get("count").getAsInt();require(expectedCount==14855&&marker.get("expectedCount").getAsInt()==expectedCount,"Unexpected source barrier count");
            phase=manifestPath.getParent().resolve(manifest.get("phaseFile").getAsString()).toRealPath();phaseSHA=sha(phase);
            require(phaseSHA.equals(manifest.get("phaseSha256").getAsString())&&phaseSHA.equals(marker.get("barrierPhaseSha256").getAsString()),"Barrier coordinate phase changed");
            checkpoint=target.resolve("dw-source-barrier-cleanup-checkpoint.json");report=target.resolve("dw-source-barrier-cleanup-report.json");
            checkpointRows=number(marker,"checkpointRows",1024,1,1024);
            if(Files.exists(checkpoint,LinkOption.NOFOLLOW_LINKS)){
                JsonObject cp=json(checkpoint);identity(cp);line=cp.get("line").getAsLong();removed=cp.get("removed").getAsLong();alreadyAbsent=cp.get("alreadyAbsent").getAsLong();
                for(var entry:cp.getAsJsonObject("skipped").entrySet())skipped.put(entry.getKey(),entry.getValue().getAsLong());
            }
            require(line>=0&&line<=expectedCount,"Invalid cleanup checkpoint line");
            checkpointLine=line;
            stream=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(phase)),StandardCharsets.UTF_8));
            for(long i=0;i<line;i++)require(stream.readLine()!=null,"Checkpoint exceeds cleanup phase");
            for(ServerWorld world:server.getWorlds()){rules.put(world,new Rules(world));Rules.freeze(world);}
            System.out.println("DW_SOURCE_BARRIER_CLEANUP_START line="+line+" expected="+expectedCount);
        }

        void tick()throws Exception{
            ticks++;require(server.getPlayerManager().getPlayerList().isEmpty(),"Players cannot enter during source barrier cleanup");
            if(draining){drain();return;}
            if(overBudget()){beginDrain(false);return;}
            long deadline=System.nanoTime()+8_000_000L;
            while(System.nanoTime()<deadline){
                if(current==null){String raw=stream.readLine();if(raw==null){beginDrain(true);return;}require(raw.length()<=4096,"Oversized barrier row");current=JsonParser.parseString(raw).getAsJsonObject();}
                ServerWorld world=world(current);BlockPos pos=position(current);ChunkPos chunk=new ChunkPos(pos);
                if(ticketWorld!=world||!chunk.equals(ticketChunk)){releaseTicket();ticketWorld=world;ticketChunk=chunk;world.getChunkManager().addTicket(TICKET,chunk,0,chunk);future=null;probed=false;}
                if(!loaded(world,chunk))return;
                clean(world,pos);line++;current=null;
                if(line-checkpointLine>=checkpointRows){beginDrain(false);return;}
            }
        }

        FullMapConversionAuthor.ChunkCounts chunkCounts(){
            int loaded=0,holders=0,readable=0,pending=0,unloading=0,queue=0;
            for(ServerWorld world:rules.keySet()){
                require((Object)world.getChunkManager() instanceof FullMapConversionAuthor.ChunkControl,"Missing native chunk counters");
                var count=((FullMapConversionAuthor.ChunkControl)(Object)world.getChunkManager()).dw$chunkCounts();
                loaded=Math.addExact(loaded,count.loadedWorldChunks());holders=Math.addExact(holders,count.holders());readable=Math.addExact(readable,count.readableHolders());pending=Math.addExact(pending,count.pendingHolders());unloading=Math.addExact(unloading,count.chunksToUnload());queue=Math.addExact(queue,count.unloadQueue());
            }
            return new FullMapConversionAuthor.ChunkCounts(loaded,holders,readable,pending,unloading,queue);
        }
        boolean overBudget(){var count=chunkCounts();return count.loadedWorldChunks()>=768||Math.max(count.holders(),count.readableHolders())>=1024;}
        void beginDrain(boolean complete)throws IOException{releaseTicket();server.save(false,true,true);draining=true;finishing=complete;drainStartedTick=ticks;write(report,snapshot(complete?"FINISHING_DRAIN":"RUNNING_DRAIN"));}
        void drain()throws IOException{
            require(ticks-drainStartedTick<=2400,"Native chunk drain exceeded 2400 ticks");
            var count=chunkCounts();if(count.loadedWorldChunks()!=0||count.holders()!=0||count.readableHolders()!=0||count.pendingHolders()!=0||count.chunksToUnload()!=0||count.unloadQueue()!=0)return;
            server.save(false,true,true);count=chunkCounts();require(count.loadedWorldChunks()==0&&count.holders()==0&&count.readableHolders()==0&&count.pendingHolders()==0&&count.chunksToUnload()==0&&count.unloadQueue()==0,"Chunk state changed during final drain save");
            if(finishing){finishDrained();return;}
            write(checkpoint,snapshot("RUNNING"));write(report,snapshot("RUNNING"));checkpointLine=line;draining=false;
        }

        boolean loaded(ServerWorld world,ChunkPos chunk){
            if(future==null){ChunkStatus status=probed?ChunkStatus.FULL:ChunkStatus.EMPTY;future=((BbChunkFutureInvoker)(Object)world.getChunkManager()).bloodborneDw$profileChunkFuture(chunk.x,chunk.z,status,true);}
            if(!future.isDone())return false;
            Either<Chunk,ChunkHolder.Unloaded> result=future.getNow(null);require(result!=null&&result.left().isPresent(),"Native chunk load failed "+chunk);
            Chunk actual=result.left().orElseThrow();future=null;
            if(!probed){require(actual.getStatus().isAtLeast(ChunkStatus.FULL),"Cleanup refuses absent/non-FULL source chunk "+chunk+" status="+actual.getStatus());probed=true;return loaded(world,chunk);}
            require(world.getChunkManager().getWorldChunk(chunk.x,chunk.z)!=null,"FULL cleanup chunk is not accessible");return true;
        }

        void clean(ServerWorld world,BlockPos pos){
            require(world.isInBuildLimit(pos)&&world.getWorldBorder().contains(pos),"Barrier coordinate outside world bounds");
            BlockState currentState=world.getBlockState(pos);Cell cell=CompositeData.cell(pos);FabricCompositeWorld access=new FabricCompositeWorld(world,Map.of());CellSnapshot before=access.read(cell);
            if(currentState.isOf(Blocks.BARRIER)){
                if(before.kind()!=Kind.FOREIGN||before.resident()!=null||world.getBlockEntity(pos)!=null||!CompositeData.nbt(before.data().blockEntityNbt()).isEmpty()){
                    skip(world,pos,currentState,"ambiguous_barrier_private_state");return;
                }
                BlockState nextState=before.guests().isEmpty()?Blocks.AIR.getDefaultState():CompositeArchitecture.CELL.getDefaultState();
                Kind kind=before.guests().isEmpty()?Kind.AIR:Kind.HELPER;
                CellSnapshot next=new CellSnapshot(kind,CompositeData.data(nextState,new NbtCompound()),null,before.guests());
                var plan=new TransactionCore.Plan(access.dimensionKey(),Map.of(cell,before),Map.of(cell,next),Map.of());
                var result=CompositeRuntime.execute(access,new TransactionCore.Preparation(plan,null));
                require(result.outcome()==TransactionCore.Outcome.COMMITTED,"Barrier transaction rejected: "+result.reason());removed++;return;
            }
            if(before.resident()==null&&(currentState.isAir()||currentState.isOf(CompositeArchitecture.CELL))){alreadyAbsent++;return;}
            String id=Registries.BLOCK.getId(currentState.getBlock()).toString();String reason=id.equals(InvisibleSourceCarrierV127.ID.toString())||id.equals(InvisibleSourceCarrierV127.SOLID_ID.toString())?"preserved_invisible_source_carrier":"ambiguous_current_state:"+id;
            skip(world,pos,currentState,reason);
        }

        void skip(ServerWorld world,BlockPos pos,BlockState currentState,String reason){
            skipped.merge(reason,1L,Long::sum);
            if(samples.size()<100){JsonObject sample=new JsonObject();sample.addProperty("reason",reason);sample.addProperty("dimension",world.getRegistryKey().getValue().toString());sample.add("position",JSON.toJsonTree(new int[]{pos.getX(),pos.getY(),pos.getZ()}));sample.addProperty("state",currentState.toString());samples.add(sample);}
        }

        ServerWorld world(JsonObject row){RegistryKey<World> key=RegistryKey.of(RegistryKeys.WORLD,new Identifier(row.get("dimension").getAsString()));ServerWorld world=server.getWorld(key);require(world!=null,"Unsupported dimension "+key.getValue());return world;}
        BlockPos position(JsonObject row){JsonArray p=row.getAsJsonArray("position");require(p.size()==3,"Invalid barrier position");return new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
        void identity(JsonObject value){require(value.get("operationId").getAsString().equals(operation)&&value.get("productionJarSha256").getAsString().equals(productionSHA),"Checkpoint operation changed");require(value.get("sourcePlanSha256").getAsString().equals(sourceSHA)&&value.get("barrierPhaseSha256").getAsString().equals(phaseSHA),"Checkpoint source changed");require(Path.of(value.get("targetWorldPath").getAsString()).toAbsolutePath().normalize().equals(target),"Checkpoint world changed");}
        JsonObject snapshot(String status){JsonObject out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("status",status);out.addProperty("operationId",operation);out.addProperty("productionJarSha256",productionSHA);out.addProperty("sourcePlanSha256",sourceSHA);out.addProperty("barrierPhaseSha256",phaseSHA);out.addProperty("targetWorldPath",target.toString());out.addProperty("line",line);out.addProperty("expectedCount",expectedCount);out.addProperty("removed",removed);out.addProperty("alreadyAbsent",alreadyAbsent);out.addProperty("alreadyAbsentMeaning","Safe replay after a committed-before-checkpoint crash, or a barrier already absent in the supplied independent copy");out.add("skipped",JSON.toJsonTree(skipped));out.add("ambiguousSamples",JSON.toJsonTree(samples));out.addProperty("elapsedSeconds",(System.nanoTime()-started)/1_000_000_000d);out.addProperty("boundedChunkLoading",true);out.addProperty("maxWorkingChunks",1);out.addProperty("maxLoadedChunks",768);out.addProperty("maxHolders",1024);out.addProperty("draining",draining);out.add("nativeChunkCounts",JSON.toJsonTree(chunkCounts()));out.addProperty("sourceInvisibleCarrier5",InvisibleSourceCarrierV127.sourceStates().get(5).toString());return out;}
        void finishDrained()throws IOException{require(line==expectedCount,"Cleanup phase count changed");stream.close();stream=null;rules.forEach((world,rule)->rule.restore(world));server.save(false,true,true);require(chunkCounts().loadedWorldChunks()==0&&chunkCounts().holders()==0,"Final save retained native chunks");draining=false;finishing=false;checkpointLine=line;write(checkpoint,snapshot("PASS_COMPLETE"));write(report,snapshot("PASS_COMPLETE"));finished=true;server.stop(false);}
        void releaseTicket(){if(ticketWorld!=null&&ticketChunk!=null)ticketWorld.getChunkManager().removeTicket(TICKET,ticketChunk,0,ticketChunk);ticketWorld=null;ticketChunk=null;future=null;probed=false;}
        void fail(Exception failure){try{releaseTicket();if(stream!=null)stream.close();stream=null;rules.forEach((world,rule)->rule.restore(world));server.save(false,true,true);JsonObject out=snapshot("FAIL_STOPPED_REVIEW_REQUIRED");out.addProperty("failure",failure.toString());write(report,out);}catch(Exception reporting){failure.addSuppressed(reporting);}finished=true;dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.error("Source barrier cleanup stopped",failure);server.stop(false);}
    }

    private record Rules(boolean daylight,boolean spawning,boolean fire,boolean weather,int randomTicks){
        Rules(ServerWorld world){this(world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE),world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING),world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK),world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE),world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED));}
        static void freeze(ServerWorld world){world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,world.getServer());world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,world.getServer());world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false,world.getServer());world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,world.getServer());world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0,world.getServer());}
        void restore(ServerWorld world){world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(daylight,world.getServer());world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(spawning,world.getServer());world.getGameRules().get(GameRules.DO_FIRE_TICK).set(fire,world.getServer());world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(weather,world.getServer());world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(randomTicks,world.getServer());}
    }
    private static int number(JsonObject marker,String key,int fallback,int min,int max){int value=marker.has(key)?marker.get(key).getAsInt():fallback;require(value>=min&&value<=max,"Invalid "+key);return value;}
    private static JsonObject json(Path path)throws IOException{require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)<=4*1024*1024,"Invalid JSON input "+path);try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}}
    private static String sha(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){byte[] buffer=new byte[1024*1024];for(int count;(count=input.read(buffer))>=0;)digest.update(buffer,0,count);}return HexFormat.of().formatHex(digest.digest());}
    private static void write(Path path,JsonObject value)throws IOException{
        Path pending=path.resolveSibling(path.getFileName()+".pending");Files.writeString(pending,JSON.toJson(value)+"\n",StandardCharsets.UTF_8);
        for(int attempt=0;;attempt++)try{Files.move(pending,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return;}
        catch(AccessDeniedException denied){if(attempt>=7)throw denied;try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();denied.addSuppressed(interrupted);throw denied;}}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
