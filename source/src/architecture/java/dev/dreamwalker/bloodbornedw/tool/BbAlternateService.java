package dev.dreamwalker.bloodbornedw.tool;

import com.mojang.datafixers.util.Either;
import dev.dreamwalker.bloodbornedw.architecture.BuildPermissions;
import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.dimension.DimensionType;

/** Profile-only edits. A radius job visits existing chunks with a bounded tick
 * budget, and never discovers other dimensions or an entire saved world. */
public final class BbAlternateService {
    public static final int MAX_RADIUS=300;
    private static final Map<ServerWorld,Map<Long,Set<UUID>>> RP_CHUNKS=new WeakHashMap<>();
    private static final Map<ServerWorld,Map<UUID,Long>> RP_POSITIONS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Job> JOBS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Status> LAST=new WeakHashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("dw_bb_alt",Comparator.comparingLong(ChunkPos::toLong),1200);
    private static final Pattern REGION=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    // A cancelled job checks its flag before another record. A single worker
    // also prevents rapid start/cancel commands from stacking concurrent disk readers.
    private static final ExecutorService PROFILE_IO=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"dw-bb-profile-io");thread.setDaemon(true);return thread;});
    private static boolean initialized;
    public record Visit(ServerWorld world,ChunkPos chunk) {}
    public record Status(boolean running,int changed,int failed,int chunksDone,int chunksTotal) {}
    private record Discovery(List<Visit> visits,Set<Long> saved,Set<Long> full) {}
    private enum Request { READY, WAITING, UNAVAILABLE }
    private static final class Job {
        final ServerPlayerEntity player;final ServerWorld world;final String profile;final Vec3d center;final int radius;final String typeFilter;
        final CompletableFuture<Discovery> discovery;final AtomicBoolean cancelled;final Path regionDirectory;
        final Map<ChunkPos,CompletableFuture<Either<Chunk,ChunkHolder.Unloaded>>> requested=new LinkedHashMap<>();
        final Map<Long,Boolean> helperChecks=new HashMap<>();
        CompletableFuture<Boolean> helperRead;Long helperKey;
        Discovery found;Visit current;TargetCursor cursor;ToolTarget pending;int index,changed,failed,startedTick,waitTicks;
        Job(ServerPlayerEntity player,String profile,int radius,ToolTarget sameType,CompletableFuture<Discovery> discovery,AtomicBoolean cancelled){this.player=player;world=player.getServerWorld();this.profile=profile;this.radius=radius;typeFilter=sameType==null?null:typeKey(sameType,player.getServer());center=player.getPos();this.discovery=discovery;this.cancelled=cancelled;regionDirectory=DimensionType.getSaveDirectory(world.getRegistryKey(),player.getServer().getSavePath(WorldSavePath.ROOT)).resolve("region");}
    }
    /** Only one active chunk's cursor is retained; no radius-cubed cell list. */
    private static final class TargetCursor {
        final ServerWorld world;final WorldChunk chunk;final ChunkSection[] sections;final String dimension;final Iterator<UUID> rp;
        int section,cell;boolean reviewed,finished;
        TargetCursor(ServerWorld world,WorldChunk chunk){this.world=world;this.chunk=chunk;sections=chunk.getSectionArray();dimension=world.getRegistryKey().getValue().toString();rp=List.copyOf(RP_CHUNKS.getOrDefault(world,Map.of()).getOrDefault(chunk.getPos().toLong(),Set.of())).iterator();}
        ToolTarget next(long deadline){
            while(section<sections.length){
                if(System.nanoTime()>=deadline)return null;
                var current=sections[section];if(current==null||!reviewed&&!current.hasAny(BbAlternateService::supports)){section++;cell=0;reviewed=false;continue;}reviewed=true;
                while(cell<4096){
                    if((cell&31)==0&&System.nanoTime()>=deadline)return null;
                    int index=cell++,x=index&15,z=(index>>>4)&15,y=index>>>8;var state=current.getBlockState(x,y,z);if(!supports(state))continue;
                    var pos=new BlockPos(chunk.getPos().getStartX()+x,(world.getBottomSectionCoord()+section)*16+y,chunk.getPos().getStartZ()+z);
                    if(DwUtilityBlocks.marker(state)||ImportedCompactBlock.compact(state))return new ToolTarget(ToolTarget.Kind.BLOCK,dimension,null,pos.asLong(),Registries.BLOCK.getId(state.getBlock()).toString());
                    var owner=VerticalMount.isArchitecture(state)?VerticalMount.owner(world,pos):null;
                    return owner==null?new ToolTarget(ToolTarget.Kind.BLOCK,dimension,null,pos.asLong(),Registries.BLOCK.getId(state.getBlock()).toString()):new ToolTarget(ToolTarget.Kind.ARCHITECTURE,dimension,owner.instanceId(),pos.asLong(),owner.registryId());
                }section++;cell=0;reviewed=false;
            }
            while(rp.hasNext()){if(System.nanoTime()>=deadline)return null;var entity=world.getEntity(rp.next());if(entity instanceof RpObjectEntity)return ToolTarget.entity(entity);}
            finished=true;return null;
        }
    }
    private BbAlternateService(){}
    public static void initialize(){if(initialized)return;initialized=true;
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof RpObjectEntity){RP_POSITIONS.computeIfAbsent(world,unused->new HashMap<>()).put(entity.getUuid(),entity.getChunkPos().toLong());RP_CHUNKS.computeIfAbsent(world,unused->new HashMap<>()).computeIfAbsent(entity.getChunkPos().toLong(),unused->new HashSet<>()).add(entity.getUuid());}});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof RpObjectEntity){var positions=RP_POSITIONS.get(world);var old=positions==null?null:positions.remove(entity.getUuid());if(old!=null)removeRp(world,old,entity.getUuid());if(positions!=null&&positions.isEmpty())RP_POSITIONS.remove(world);}});
        ServerTickEvents.END_SERVER_TICK.register(BbAlternateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{cancel(server);LAST.remove(server);RP_CHUNKS.keySet().removeIf(world->world.getServer()==server);RP_POSITIONS.keySet().removeIf(world->world.getServer()==server);});
    }
    private static void removeRp(ServerWorld world,long chunk,UUID uuid){var chunks=RP_CHUNKS.get(world);if(chunks==null)return;var entities=chunks.get(chunk);if(entities!=null){entities.remove(uuid);if(entities.isEmpty())chunks.remove(chunk);}if(chunks.isEmpty())RP_CHUNKS.remove(world);}
    public static void rpMoved(RpObjectEntity entity){if(!(entity.getWorld() instanceof ServerWorld world))return;var positions=RP_POSITIONS.get(world);var old=positions==null?null:positions.get(entity.getUuid());long next=entity.getChunkPos().toLong();if(old==null||old==next)return;removeRp(world,old,entity.getUuid());positions.put(entity.getUuid(),next);RP_CHUNKS.computeIfAbsent(world,unused->new HashMap<>()).computeIfAbsent(next,unused->new HashSet<>()).add(entity.getUuid());}
    public static boolean validRadius(int radius){return radius>=1&&radius<=MAX_RADIUS;}
    public static boolean supports(BlockState state){return ToolTarget.modRegistry(Registries.BLOCK.getId(state.getBlock()))&&profile(state,"base")!=null&&profile(state,"alt")!=null;}
    public static String profile(BlockState state){for(var entry:state.getEntries().entrySet())if(entry.getKey().getName().equals("profile"))return entry.getValue() instanceof StringIdentifiable value?value.asString():entry.getValue().toString();return null;}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static BlockState profile(BlockState state,String requested){if(!ToolTarget.modRegistry(Registries.BLOCK.getId(state.getBlock()))||!Set.of("base","alt").contains(requested))return null;for(Property property:state.getProperties())if(property.getName().equals("profile")){var parsed=property.parse(requested);if(parsed.isPresent())return state.with(property,(Comparable)parsed.get());}return null;}
    public static String profile(ToolTarget target,MinecraftServer server){if(target==null||!target.valid(server))return null;if(target.kind()==ToolTarget.Kind.RP&&target.entity(server) instanceof RpObjectEntity rp)return rp.alternateVisual()?"alt":"base";return target.kind()==ToolTarget.Kind.ARCHITECTURE||target.kind()==ToolTarget.Kind.BLOCK?profile(target.world(server).getBlockState(BlockPos.fromLong(target.root()))):null;}
    public static boolean change(ServerPlayerEntity player,ToolTarget target,String requested){
        if(target==null||!target.valid(player.getServer())||!Set.of("base","alt").contains(requested)||!target.dimension().equals(player.getServerWorld().getRegistryKey().getValue().toString()))return false;
        var world=target.world(player.getServer());var pos=target.kind()==ToolTarget.Kind.RP?BlockPos.ofFloored(target.position(player.getServer())):BlockPos.fromLong(target.root());if(!BuildPermissions.canEdit(world,player,pos))return false;
        if(target.kind()==ToolTarget.Kind.RP&&target.entity(player.getServer()) instanceof RpObjectEntity rp){boolean next=requested.equals("alt");if(rp.alternateVisual()==next)return false;rp.setAlternateVisual(next);return true;}
        if(target.kind()!=ToolTarget.Kind.BLOCK&&target.kind()!=ToolTarget.Kind.ARCHITECTURE)return false;
        var before=world.getBlockState(pos);var after=profile(before,requested);if(after==null||after.equals(before))return false;
        if(target.kind()==ToolTarget.Kind.ARCHITECTURE){var owner=VerticalMount.owner(world,pos);return owner!=null&&owner.instanceId().equals(target.uuid())&&CompositeRuntime.transition(world,owner,after,player).outcome()==TransactionCore.Outcome.COMMITTED;}
        return DwUtilityBlocks.marker(before)?DwUtilityBlocks.replaceCarrier(world,pos,after,player):world.setBlockState(pos,after,Block.NOTIFY_ALL);
    }
    public static boolean start(ServerPlayerEntity player,String requested,int radius){return start(player,requested,radius,null);}
    public static boolean start(ServerPlayerEntity player,String requested,int radius,ToolTarget sameType){
        if(!BuildPermissions.canEdit(player)||!Set.of("base","alt").contains(requested)||!validRadius(radius))return false;
        if(sameType!=null&&(!sameType.valid(player.getServer())||!sameType.dimension().equals(player.getServerWorld().getRegistryKey().getValue().toString())||profile(sameType,player.getServer())==null))return false;
        var server=player.getServer();if(JOBS.containsKey(server)){player.sendMessage(Text.literal("BB: замена вида уже выполняется. /bb cancel прекращает её."),false);return false;}
        var world=player.getServerWorld();Vec3d center=player.getPos();Set<Long> loaded=new HashSet<>();
        int minX=MathHelper.floor((center.x-radius)/16),maxX=MathHelper.floor((center.x+radius)/16),minZ=MathHelper.floor((center.z-radius)/16),maxZ=MathHelper.floor((center.z+radius)/16);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(intersects(new ChunkPos(x,z),center,radius)&&world.getChunkManager().getWorldChunk(x,z)!=null)loaded.add(ChunkPos.toLong(x,z));
        Path path=DimensionType.getSaveDirectory(world.getRegistryKey(),server.getSavePath(WorldSavePath.ROOT));
        var cancelled=new AtomicBoolean();
        var discovery=CompletableFuture.supplyAsync(()->discover(world,path,center,radius,loaded,cancelled::get),PROFILE_IO);JOBS.put(server,new Job(player,requested,radius,sameType,discovery,cancelled));
        player.sendMessage(Text.literal("BB: замена на "+requested+" в радиусе "+radius+" запущена. Существующие чанки обрабатываются по очереди."),false);return true;
    }
    /** Actual native loader/transactions with a small deterministic fixture queue. */
    static boolean startVisits(ServerPlayerEntity player,String requested,int radius,List<Visit> visits){if(!validRadius(radius)||!Set.of("base","alt").contains(requested)||JOBS.containsKey(player.getServer()))return false;var saved=new HashSet<Long>();for(var visit:visits)saved.add(visit.chunk.toLong());JOBS.put(player.getServer(),new Job(player,requested,radius,null,CompletableFuture.completedFuture(new Discovery(List.copyOf(visits),Set.copyOf(saved),Set.copyOf(saved))),new AtomicBoolean()));return true;}
    private static boolean intersects(ChunkPos chunk,Vec3d center,int radius){double dx=Math.max(0,Math.abs(chunk.getStartX()+8-center.x)-8),dz=Math.max(0,Math.abs(chunk.getStartZ()+8-center.z)-8);return dx*dx+dz*dz<=(double)radius*radius;}
    private static Discovery discover(ServerWorld world,Path path,Vec3d center,int radius,Set<Long> loaded,BooleanSupplier cancelled){
        Set<Long> saved=new HashSet<>(),positions=new TreeSet<>(loaded);Path region=path.resolve("region");
        if(Files.isDirectory(region,LinkOption.NOFOLLOW_LINKS))try{
            int minRx=MathHelper.floor((center.x-radius)/512)-1,maxRx=MathHelper.floor((center.x+radius)/512)+1,minRz=MathHelper.floor((center.z-radius)/512)-1,maxRz=MathHelper.floor((center.z+radius)/512)+1;
            for(int rx=minRx;rx<=maxRx;rx++)for(int rz=minRz;rz<=maxRz;rz++){
                checkCancelled(cancelled);var file=region.resolve("r."+rx+"."+rz+".mca");if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                // Marginal headers remain an index for out-of-radius owner helpers.
                // Their NBT is decoded only when that exact helper is needed.
                saved.addAll(savedChunks(file));
                positions.addAll(savedFullChunks(file,key->!loaded.contains(key)&&intersects(new ChunkPos(key),center,radius),cancelled));
            }
        }catch(IOException failure){throw new UncheckedIOException("Cannot enumerate existing BB profile chunks",failure);}
        // Entity-only region records cannot create terrain: only existing block
        // chunks enter the loader. Radius remains capped in both command and API.
        checkCancelled(cancelled);
        return new Discovery(positions.stream().map(key->new Visit(world,new ChunkPos(key))).toList(),Set.copyOf(saved),Set.copyOf(positions));
    }
    static Set<Long> savedChunks(Path file)throws IOException{
        var match=REGION.matcher(file.getFileName().toString());if(!match.matches())return Set.of();int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));byte[] header;try(var input=Files.newInputStream(file)){header=input.readNBytes(4096);}if(header.length==0&&Files.size(file)==0)return Set.of();if(header.length!=4096)throw new IOException("Truncated region header: "+file);
        Set<Long> chunks=new HashSet<>();for(int i=0;i<1024;i++){int p=i*4,sector=((header[p]&255)<<16)|((header[p+1]&255)<<8)|(header[p+2]&255);if(sector>=2&&(header[p+3]&255)>0)chunks.add(ChunkPos.toLong(rx*32+(i&31),rz*32+(i>>5)));}return chunks;
    }
    /** Read-only region IO on the discovery worker. A saved ProtoChunk is not
     * eligible: requesting FULL for it would finish terrain generation. Native
     * NBT is decoded one record at a time with a bounded allocation tracker. */
    static Set<Long> savedFullChunks(Path file)throws IOException{
        return savedFullChunks(file,key->true,()->false);
    }
    /** Select records before allocating or inflating their payloads. */
    static Set<Long> savedFullChunks(Path file,LongPredicate included,BooleanSupplier cancelled)throws IOException{
        var match=REGION.matcher(file.getFileName().toString());if(!match.matches())return Set.of();int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));Set<Long> result=new HashSet<>();
        try(var region=new RandomAccessFile(file.toFile(),"r")){
            if(region.length()==0)return Set.of();if(region.length()<4096)throw new IOException("Truncated region header: "+file);byte[] header=new byte[4096];region.readFully(header);
            for(int index=0;index<1024;index++){
                checkCancelled(cancelled);int x=rx*32+(index&31),z=rz*32+(index>>5);long key=ChunkPos.toLong(x,z);if(!included.test(key))continue;
                int p=index*4,sector=((header[p]&255)<<16)|((header[p+1]&255)<<8)|(header[p+2]&255),sectors=header[p+3]&255;if(sector<2||sectors==0)continue;
                if(fullRecord(region,file,new ChunkPos(x,z),sector,sectors,cancelled))result.add(key);
            }
        }return result;
    }
    /** Exact cold-helper approval. Called only by the single IO worker, before adding a ticket. */
    static boolean savedFullChunk(Path file,ChunkPos chunk,BooleanSupplier cancelled)throws IOException{
        checkCancelled(cancelled);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return false;
        try(var region=new RandomAccessFile(file.toFile(),"r")){
            if(region.length()<4096)throw new IOException("Truncated region header: "+file);int index=(chunk.x&31)+(chunk.z&31)*32;region.seek(index*4L);int location=region.readInt(),sector=location>>>8,sectors=location&255;
            return sector>=2&&sectors>0&&fullRecord(region,file,chunk,sector,sectors,cancelled);
        }
    }
    private static boolean fullRecord(RandomAccessFile region,Path file,ChunkPos chunk,int sector,int sectors,BooleanSupplier cancelled)throws IOException{
        checkCancelled(cancelled);long offset=(long)sector*4096;if(offset+5>region.length())throw new IOException("Truncated saved chunk record: "+file);region.seek(offset);int length=region.readInt(),format=region.readUnsignedByte();if(length<1||length>(long)sectors*4096-4||offset+4L+length>region.length())throw new IOException("Invalid saved chunk length: "+file);
        InputStream encoded;
        if((format&128)!=0){Path external=file.getParent().resolve("c."+chunk.x+"."+chunk.z+".mcc");if(length!=1||!Files.isRegularFile(external,LinkOption.NOFOLLOW_LINKS)||Files.size(external)>16_777_216)throw new IOException("Invalid external saved chunk: "+external);encoded=Files.newInputStream(external);}
        else{byte[] payload=new byte[length-1];region.readFully(payload);encoded=new ByteArrayInputStream(payload);}
        try(encoded;InputStream decoded=switch(format&127){case 1->new GZIPInputStream(encoded);case 2->new InflaterInputStream(encoded);case 3->encoded;default->throw new IOException("Unsupported saved chunk compression: "+file);};var input=new DataInputStream(decoded)){
            NbtCompound data=NbtIo.read(input,new NbtTagSizeTracker(16_777_216));checkCancelled(cancelled);String status=data.getString("Status");if(status.isEmpty()&&data.contains("Level",NbtElement.COMPOUND_TYPE))status=data.getCompound("Level").getString("Status");return status.equals("minecraft:full")||status.equals("full");
        }
    }
    private static void checkCancelled(BooleanSupplier cancelled){if(cancelled.getAsBoolean())throw new CancellationException("BB profile IO cancelled");}
    private static String typeKey(ToolTarget target,MinecraftServer server){if(target.kind()==ToolTarget.Kind.RP)return "rp:"+target.registry();var entry=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(target.world(server).getBlockState(BlockPos.fromLong(target.root())));return "block:"+(entry==null?target.registry():entry.temporaryId());}
    public static Status status(MinecraftServer server){var job=JOBS.get(server);return job==null?LAST.getOrDefault(server,new Status(false,0,0,0,0)):new Status(true,job.changed,job.failed,job.index,job.found==null?0:job.found.visits.size());}
    public static boolean discovering(MinecraftServer server){var job=JOBS.get(server);return job!=null&&job.found==null;}
    public static boolean cancel(MinecraftServer server){var job=JOBS.remove(server);if(job==null)return false;job.cancelled.set(true);job.discovery.cancel(false);if(job.helperRead!=null)job.helperRead.cancel(false);release(job);LAST.put(server,new Status(false,job.changed,job.failed,job.index,job.found==null?0:job.found.visits.size()));return true;}
    private static Request request(Job job,ChunkPos chunk){
        if(job.requested.containsKey(chunk))return Request.READY;if(job.requested.size()>=64)return Request.UNAVAILABLE;var manager=job.world.getChunkManager();long key=chunk.toLong();
        if(manager.getWorldChunk(chunk.x,chunk.z)==null&&!job.found.full.contains(key)){
            if(!job.found.saved.contains(key))return Request.UNAVAILABLE;
            if(job.helperRead!=null){if(!job.helperRead.isDone())return Request.WAITING;job.helperChecks.put(job.helperKey,job.helperRead.join());job.helperRead=null;job.helperKey=null;}
            Boolean full=job.helperChecks.get(key);
            if(full==null){if(job.helperChecks.size()>=64)return Request.UNAVAILABLE;job.helperKey=key;Path file=job.regionDirectory.resolve("r."+chunk.getRegionX()+"."+chunk.getRegionZ()+".mca");job.helperRead=CompletableFuture.supplyAsync(()->{try{return savedFullChunk(file,chunk,job.cancelled::get);}catch(IOException failure){throw new UncheckedIOException(failure);}},PROFILE_IO);return Request.WAITING;}
            if(!full)return Request.UNAVAILABLE;
        }
        // FULL accessibility loads native entity storage without promoting the
        // chunk to entity ticking or generating a ticking-neighbour ring.
        manager.addTicket(TICKET,chunk,0,chunk);
        try{job.requested.put(chunk,((dev.dreamwalker.bloodbornedw.composite.mixin.BbChunkFutureInvoker)manager).bloodborneDw$profileChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true));return Request.READY;}
        catch(RuntimeException failure){manager.removeTicket(TICKET,chunk,0,chunk);manager.removeTicket(ChunkTicketType.UNKNOWN,chunk,0,chunk);throw failure;}
    }
    private static void release(Job job){for(var pos:job.requested.keySet()){job.world.getChunkManager().removeTicket(TICKET,pos,0,pos);job.world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,pos,0,pos);}job.requested.clear();job.helperChecks.clear();if(job.helperRead!=null&&job.helperRead.isDone()){job.helperRead=null;job.helperKey=null;}}
    private static void tick(MinecraftServer server){var job=JOBS.get(server);if(job==null)return;if(!BuildPermissions.canEdit(job.player)||job.player.isRemoved()||job.player.getServerWorld()!=job.world){cancel(server);return;}
        try{
            if(job.found==null){if(!job.discovery.isDone())return;job.found=job.discovery.join();}
            if(job.current==null){if(job.index>=job.found.visits.size()){finish(server,job);return;}job.current=job.found.visits.get(job.index);job.startedTick=server.getTicks();job.waitTicks=0;if(request(job,job.current.chunk)==Request.UNAVAILABLE){job.failed++;next(job);}return;}
            if(server.getTicks()-job.startedTick>1200){job.failed++;next(job);return;}
            if(job.requested.values().stream().anyMatch(future->!future.isDone()))return;
            if(job.requested.values().stream().anyMatch(future->future.join().left().isEmpty())){job.failed++;next(job);return;}
            if(!((dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess)job.world).bloodborne_dw$profileEntityManager().isLoaded(job.current.chunk.toLong()))return;
            if(job.waitTicks++<2)return;
            if(job.cursor==null){var chunk=job.world.getChunkManager().getWorldChunk(job.current.chunk.x,job.current.chunk.z);if(chunk==null){job.failed++;next(job);return;}job.cursor=new TargetCursor(job.world,chunk);}
            long deadline=System.nanoTime()+4_000_000L;int expensive=0;
            for(int n=0;n<256;n++){
                if(n>0&&System.nanoTime()>=deadline)return;if(job.pending==null)job.pending=job.cursor.next(deadline);if(job.pending==null){if(job.cursor.finished)next(job);return;}
                var target=job.pending;var root=BlockPos.fromLong(target.root());
                if(!target.valid(server)||target.position(server).squaredDistanceTo(job.center)>(double)job.radius*job.radius||job.typeFilter!=null&&!job.typeFilter.equals(typeKey(target,server))){job.pending=null;continue;}
                String before=profile(target,server);if(before==null||before.equals(job.profile)){job.pending=null;continue;}
                if(target.kind()!=ToolTarget.Kind.BLOCK&&expensive>=12)return;
                if(target.kind()==ToolTarget.Kind.ARCHITECTURE&&job.world.getBlockEntity(root) instanceof CompositeBlockEntity own){
                    var after=profile(job.world.getBlockState(root),job.profile);var instance=CompositeRuntime.instance(job.world,root,after,target.uuid(),own.payload());boolean available=true;
                    for(var cell:instance.cells().keySet()){var requested=request(job,new ChunkPos(CompositeData.pos(instance.owner().root().add(cell))));if(requested==Request.WAITING)return;if(requested==Request.UNAVAILABLE){available=false;break;}}
                    if(!available){job.failed++;job.pending=null;continue;}if(job.requested.values().stream().anyMatch(future->!future.isDone()))return;
                    if(job.requested.keySet().stream().anyMatch(chunk->job.world.getChunkManager().getWorldChunk(chunk.x,chunk.z)==null))return;
                }
                job.pending=null;if(target.kind()!=ToolTarget.Kind.BLOCK)expensive++;if(change(job.player,target,job.profile))job.changed++;else job.failed++;
            }
        }catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.error("BB radius profile job stopped after {} edits",job.changed,failure);job.player.sendMessage(Text.literal("BB: замена остановлена из-за ошибки; изменено "+job.changed+", подробности в журнале."),false);cancel(server);}
    }
    private static void next(Job job){release(job);job.current=null;job.cursor=null;job.pending=null;job.index++;}
    private static void finish(MinecraftServer server,Job job){cancel(server);job.player.sendMessage(Text.literal("BB: вид "+job.profile+" — изменено "+job.changed+", отклонено "+job.failed+", чанков "+job.index+"."),false);}
}
