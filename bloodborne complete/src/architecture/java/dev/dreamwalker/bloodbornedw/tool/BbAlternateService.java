package dev.dreamwalker.bloodbornedw.tool;

import com.mojang.datafixers.util.Either;
import dev.dreamwalker.bloodbornedw.architecture.BuildPermissions;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
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

/** Profile edits only. Whole-map jobs read saved region headers off-thread and
 * visit existing chunks one at a time; no radius-cubed cell walk or permanent force-load. */
public final class BbAlternateService {
    private static final Map<ServerWorld,Set<Long>> LOADED=new WeakHashMap<>();
    private static final Map<ServerWorld,Map<Long,Set<UUID>>> RP_CHUNKS=new WeakHashMap<>();
    private static final Map<ServerWorld,Map<UUID,Long>> RP_POSITIONS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Job> JOBS=new WeakHashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("dw_bb_alt",Comparator.comparingLong(ChunkPos::toLong),1200);
    private static final Pattern REGION=Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static boolean initialized;
    public record Visit(ServerWorld world,ChunkPos chunk) {}
    public record Status(boolean running,int changed,int failed,int chunksDone,int chunksTotal) {}
    private record Directory(ServerWorld world,Path path,Set<Long> loaded) {}
    private static final class Job {
        final ServerPlayerEntity player;final String profile;final Vec3d center;final int radius;
        final CompletableFuture<List<Visit>> discovery;
        TargetCursor cursor;ToolTarget pending;
        final Map<ChunkPos,CompletableFuture<Either<Chunk,ChunkHolder.Unloaded>>> requested=new LinkedHashMap<>();
        List<Visit> visits;Visit current;int index,changed,failed,waitTicks,startedTick;
        Job(ServerPlayerEntity player,String profile,int radius,CompletableFuture<List<Visit>> discovery){this.player=player;this.profile=profile;this.radius=radius;center=player.getPos();this.discovery=discovery;}
    }
    /** Only the active chunk's section position and loaded RP UUIDs are kept.
     * Scanning empty/large palettes cooperates with the same edit tick budget. */
    private static final class TargetCursor {
        final ServerWorld world;final WorldChunk chunk;final ChunkSection[] sections;final String dimension;
        final Iterator<UUID> rp;int section,cell;boolean reviewed,finished;
        TargetCursor(ServerWorld world,WorldChunk chunk){this.world=world;this.chunk=chunk;sections=chunk.getSectionArray();dimension=world.getRegistryKey().getValue().toString();rp=List.copyOf(RP_CHUNKS.getOrDefault(world,Map.of()).getOrDefault(chunk.getPos().toLong(),Set.of())).iterator();}
        ToolTarget next(long deadline){
            while(section<sections.length){
                if(System.nanoTime()>=deadline)return null;
                var current=sections[section];if(current==null||!reviewed&&!current.hasAny(BbAlternateService::supports)){section++;cell=0;reviewed=false;continue;}reviewed=true;
                while(cell<4096){
                    if((cell&31)==0&&System.nanoTime()>=deadline)return null;
                    int index=cell++,x=index&15,z=(index>>>4)&15,y=index>>>8;var state=current.getBlockState(x,y,z);if(!supports(state))continue;
                    var pos=new BlockPos(chunk.getPos().getStartX()+x,(world.getBottomSectionCoord()+section)*16+y,chunk.getPos().getStartZ()+z);
                    // Utility markers remain their own target even when they
                    // carry unrelated guest geometry; compact cubes stay BLOCK.
                    if(DwUtilityBlocks.marker(state)||ImportedCompactBlock.compact(state))return new ToolTarget(ToolTarget.Kind.BLOCK,dimension,null,pos.asLong(),Registries.BLOCK.getId(state.getBlock()).toString());
                    var owner=VerticalMount.owner(world,pos);return owner==null?new ToolTarget(ToolTarget.Kind.BLOCK,dimension,null,pos.asLong(),Registries.BLOCK.getId(state.getBlock()).toString()):new ToolTarget(ToolTarget.Kind.ARCHITECTURE,dimension,owner.instanceId(),pos.asLong(),owner.registryId());
                }
                section++;cell=0;reviewed=false;
            }
            while(rp.hasNext()){if(System.nanoTime()>=deadline)return null;var entity=world.getEntity(rp.next());if(entity instanceof RpObjectEntity){var target=ToolTarget.entity(entity);if(target!=null)return target;}}
            finished=true;return null;
        }
    }
    private static final Map<MinecraftServer,Status> LAST=new WeakHashMap<>();
    private BbAlternateService(){}
    public static void initialize(){if(initialized)return;initialized=true;
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->LOADED.computeIfAbsent(world,unused->new HashSet<>()).add(chunk.getPos().toLong()));
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{var chunks=LOADED.get(world);if(chunks!=null){chunks.remove(chunk.getPos().toLong());if(chunks.isEmpty())LOADED.remove(world);}});
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof RpObjectEntity){RP_POSITIONS.computeIfAbsent(world,unused->new HashMap<>()).put(entity.getUuid(),entity.getChunkPos().toLong());RP_CHUNKS.computeIfAbsent(world,unused->new HashMap<>()).computeIfAbsent(entity.getChunkPos().toLong(),unused->new HashSet<>()).add(entity.getUuid());}});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof RpObjectEntity){var positions=RP_POSITIONS.get(world);var old=positions==null?null:positions.remove(entity.getUuid());if(old!=null)removeRp(world,old,entity.getUuid());if(positions!=null&&positions.isEmpty())RP_POSITIONS.remove(world);}});
        ServerTickEvents.END_SERVER_TICK.register(BbAlternateService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{cancel(server);LAST.remove(server);LOADED.keySet().removeIf(world->world.getServer()==server);RP_CHUNKS.keySet().removeIf(world->world.getServer()==server);RP_POSITIONS.keySet().removeIf(world->world.getServer()==server);});
    }
    private static void removeRp(ServerWorld world,long chunk,UUID uuid){var chunks=RP_CHUNKS.get(world);if(chunks==null)return;var entities=chunks.get(chunk);if(entities!=null){entities.remove(uuid);if(entities.isEmpty())chunks.remove(chunk);}if(chunks.isEmpty())RP_CHUNKS.remove(world);}
    /** Position callbacks maintain the index; no periodic world/entity scan. */
    public static void rpMoved(RpObjectEntity entity){if(!(entity.getWorld() instanceof ServerWorld world))return;var positions=RP_POSITIONS.get(world);var old=positions==null?null:positions.get(entity.getUuid());long next=entity.getChunkPos().toLong();if(old==null||old==next)return;removeRp(world,old,entity.getUuid());positions.put(entity.getUuid(),next);RP_CHUNKS.computeIfAbsent(world,unused->new HashMap<>()).computeIfAbsent(next,unused->new HashSet<>()).add(entity.getUuid());}
    public static boolean supports(BlockState state){return ToolTarget.modRegistry(Registries.BLOCK.getId(state.getBlock()))&&profile(state)!=null;}
    public static String profile(BlockState state){for(var entry:state.getEntries().entrySet())if(entry.getKey().getName().equals("profile"))return entry.getValue() instanceof StringIdentifiable value?value.asString():entry.getValue().toString();return null;}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static BlockState profile(BlockState state,String requested){if(!ToolTarget.modRegistry(Registries.BLOCK.getId(state.getBlock()))||!Set.of("base","alt").contains(requested))return null;for(Property property:state.getProperties())if(property.getName().equals("profile")){var parsed=property.parse(requested);if(parsed.isPresent())return state.with(property,(Comparable)parsed.get());}return null;}
    public static String profile(ToolTarget target,MinecraftServer server){if(target==null||!target.valid(server))return null;if(target.kind()==ToolTarget.Kind.RP&&target.entity(server) instanceof RpObjectEntity rp)return rp.alternateVisual()?"alt":"base";return target.kind()==ToolTarget.Kind.ARCHITECTURE||target.kind()==ToolTarget.Kind.BLOCK?profile(target.world(server).getBlockState(BlockPos.fromLong(target.root()))):null;}
    public static boolean change(ServerPlayerEntity player,ToolTarget target,String requested){
        if(target==null||!target.valid(player.getServer())||!Set.of("base","alt").contains(requested))return false;
        var world=target.world(player.getServer());var pos=target.kind()==ToolTarget.Kind.RP?BlockPos.ofFloored(target.position(player.getServer())):BlockPos.fromLong(target.root());if(!BuildPermissions.canEdit(world,player,pos))return false;
        if(target.kind()==ToolTarget.Kind.RP&&target.entity(player.getServer()) instanceof RpObjectEntity rp){boolean next=requested.equals("alt");if(rp.alternateVisual()==next)return false;rp.setAlternateVisual(next);return true;}
        if(target.kind()!=ToolTarget.Kind.BLOCK&&target.kind()!=ToolTarget.Kind.ARCHITECTURE)return false;
        var before=world.getBlockState(pos);var after=profile(before,requested);if(after==null||after.equals(before))return false;
        if(target.kind()==ToolTarget.Kind.ARCHITECTURE){var owner=VerticalMount.owner(world,pos);return owner!=null&&owner.instanceId().equals(target.uuid())&&CompositeRuntime.transition(world,owner,after,player).outcome()==TransactionCore.Outcome.COMMITTED;}
        return world.setBlockState(pos,after,Block.NOTIFY_ALL);
    }
    /** Loaded object selection for the historical same-ID radius mode. */
    public static List<ToolTarget> sameTypeNear(ServerPlayerEntity player,ToolTarget selected,int radius){
        var world=selected.world(player.getServer());var result=new LinkedHashMap<String,ToolTarget>();
        for(long key:LOADED.getOrDefault(world,Set.of())){var chunk=new ChunkPos(key);double dx=Math.max(0,Math.abs((chunk.getStartX()+8)-player.getX())-8),dz=Math.max(0,Math.abs((chunk.getStartZ()+8)-player.getZ())-8);if(dx*dx+dz*dz>(double)radius*radius)continue;
            var loaded=world.getChunkManager().getWorldChunk(chunk.x,chunk.z);if(loaded!=null)for(var candidate:targets(world,loaded)){if(candidate.position(player.getServer()).squaredDistanceTo(player.getPos())>(double)radius*radius)continue;if(sameType(selected,candidate,player.getServer()))result.put(candidate.key(),candidate);}}
        // A directly installed fixture or just-attached native owner may precede a CHUNK_LOAD callback.
        for(var owner:CompositeShapeSnapshots.attachedOwners(world)){var pos=CompositeData.pos(owner.root());if(!world.isChunkLoaded(pos))continue;var candidate=ToolTarget.block(world,pos,player);if(candidate!=null&&candidate.position(player.getServer()).squaredDistanceTo(player.getPos())<=(double)radius*radius&&sameType(selected,candidate,player.getServer()))result.put(candidate.key(),candidate);}
        if(selected.position(player.getServer()).squaredDistanceTo(player.getPos())<=(double)radius*radius)result.put(selected.key(),selected);return List.copyOf(result.values());
    }
    private static boolean sameType(ToolTarget a,ToolTarget b,MinecraftServer server){if(a.kind()==ToolTarget.Kind.RP||b.kind()==ToolTarget.Kind.RP)return a.kind()==b.kind()&&a.registry().equals(b.registry());var x=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(a.world(server).getBlockState(BlockPos.fromLong(a.root())));var y=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(b.world(server).getBlockState(BlockPos.fromLong(b.root())));return x!=null&&y!=null?x.temporaryId().equals(y.temporaryId()):a.registry().equals(b.registry());}
    public static boolean start(ServerPlayerEntity player,String requested,int radius){
        if(!BuildPermissions.canEdit(player)||!Set.of("base","alt").contains(requested)||radius==0)return false;
        var server=player.getServer();if(JOBS.containsKey(server)){player.sendMessage(Text.literal("BB: замена вида уже выполняется. /bb cancel прекращает её."),false);return false;}
        List<Directory> dirs=new ArrayList<>();for(var world:server.getWorlds()){if(radius>0&&world!=player.getServerWorld())continue;var loaded=new HashSet<>(LOADED.getOrDefault(world,Set.of()));for(var owner:CompositeShapeSnapshots.attachedOwners(world))loaded.add(new ChunkPos(CompositeData.pos(owner.root())).toLong());loaded.addAll(RP_CHUNKS.getOrDefault(world,Map.of()).keySet());dirs.add(new Directory(world,DimensionType.getSaveDirectory(world.getRegistryKey(),server.getSavePath(WorldSavePath.ROOT)),Set.copyOf(loaded)));}
        Vec3d center=player.getPos();var discovery=CompletableFuture.supplyAsync(()->discover(dirs)).thenApply(visits->radius<0?visits:visits.stream().filter(visit->{double dx=Math.max(0,Math.abs((visit.chunk.getStartX()+8)-center.x)-8),dz=Math.max(0,Math.abs((visit.chunk.getStartZ()+8)-center.z)-8);return dx*dx+dz*dz<=(double)radius*radius;}).toList());JOBS.put(server,new Job(player,requested,radius,discovery));player.sendMessage(Text.literal("BB: запущена замена на "+requested+(radius<0?" по всей сохранённой карте во всех измерениях":" в радиусе "+radius)+". Чанки обрабатываются последовательно."),false);return true;
    }
    /** Package-private seam uses actual chunk/profile transactions without scanning other concurrently running test fixtures. */
    static boolean startVisits(ServerPlayerEntity player,String requested,List<Visit> visits){var server=player.getServer();if(JOBS.containsKey(server))return false;JOBS.put(server,new Job(player,requested,-1,CompletableFuture.completedFuture(List.copyOf(visits))));return true;}
    private static List<Visit> discover(List<Directory> dirs){var result=new ArrayList<Visit>();for(var dir:dirs){Set<Long> positions=new TreeSet<>(dir.loaded);for(String folder:List.of("region","entities")){Path path=dir.path.resolve(folder);if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;try(var files=Files.newDirectoryStream(path,"r.*.*.mca")){for(var file:files){if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;positions.addAll(savedChunks(file));}}catch(IOException failure){throw new UncheckedIOException("Cannot enumerate saved BB profile chunks: "+path,failure);}}for(long pos:positions)result.add(new Visit(dir.world,new ChunkPos(pos)));}return List.copyOf(result);}
    static Set<Long> savedChunks(Path file)throws IOException{var match=REGION.matcher(file.getFileName().toString());if(!match.matches())return Set.of();int rx=Integer.parseInt(match.group(1)),rz=Integer.parseInt(match.group(2));byte[] header;try(var input=Files.newInputStream(file)){header=input.readNBytes(4096);}if(header.length!=4096)throw new IOException("Truncated region header: "+file);Set<Long> chunks=new HashSet<>();for(int i=0;i<1024;i++){int p=i*4,sector=((header[p]&255)<<16)|((header[p+1]&255)<<8)|(header[p+2]&255);if(sector>=2&&(header[p+3]&255)>0)chunks.add(ChunkPos.toLong(rx*32+(i&31),rz*32+(i>>5)));}return chunks;}
    public static Status status(MinecraftServer server){var job=JOBS.get(server);return job==null?LAST.getOrDefault(server,new Status(false,0,0,0,0)):new Status(true,job.changed,job.failed,job.index,job.visits==null?0:job.visits.size());}
    public static boolean cancel(MinecraftServer server){var job=JOBS.remove(server);if(job==null)return false;release(job);LAST.put(server,new Status(false,job.changed,job.failed,job.index,job.visits==null?0:job.visits.size()));return true;}
    private static void request(Job job,ChunkPos chunk){if(job.requested.containsKey(chunk))return;var manager=job.current.world.getChunkManager();manager.addTicket(TICKET,chunk,2,chunk);job.requested.put(chunk,((dev.dreamwalker.bloodbornedw.composite.mixin.BbChunkFutureInvoker)manager).bloodborneDw$profileChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true));}
    private static void release(Job job){if(job.current!=null)for(var pos:job.requested.keySet()){job.current.world.getChunkManager().removeTicket(TICKET,pos,2,pos);job.current.world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,pos,0,pos);}job.requested.clear();}
    private static void tick(MinecraftServer server){var job=JOBS.get(server);if(job==null)return;
        if(!BuildPermissions.canEdit(job.player)||job.player.isRemoved()){cancel(server);return;}
        try{
            if(job.visits==null){if(!job.discovery.isDone())return;job.visits=job.discovery.join();}
            if(job.current==null){if(job.index>=job.visits.size()){finish(server,job);return;}job.current=job.visits.get(job.index);job.startedTick=server.getTicks();job.waitTicks=0;request(job,job.current.chunk);return;}
            if(server.getTicks()-job.startedTick>1200){job.failed++;dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("BB ALT job timed out at {} {}",job.current.world.getRegistryKey().getValue(),job.current.chunk);next(job);return;}
            if(job.requested.values().stream().anyMatch(future->!future.isDone()))return;
            if(job.requested.values().stream().anyMatch(future->future.join().left().isEmpty())){job.failed++;next(job);return;}
            if(job.waitTicks++<2)return;
            if(job.cursor==null){var chunk=job.current.world.getChunkManager().getWorldChunk(job.current.chunk.x,job.current.chunk.z);if(chunk==null){job.failed++;next(job);return;}job.cursor=new TargetCursor(job.current.world,chunk);}
            // Native compact palettes need no owner UUID, ledger or BE. Do not
            // retain millions of visited cell keys: visits/per-chunk targets
            // are unique and explicit BASE/ALT assignments are idempotent.
            // At most512 cheap candidates/16 owner or RP edits, with an8ms
            // cooperative budget; a single transaction is never interrupted.
            long deadline=System.nanoTime()+8_000_000L;int expensive=0;
            for(int n=0;n<512;n++){
                if(n>0&&System.nanoTime()>=deadline)return;
                if(job.pending==null)job.pending=job.cursor.next(deadline);
                if(job.pending==null){if(job.cursor.finished)next(job);return;}
                var target=job.pending;var world=target.world(server);var root=BlockPos.fromLong(target.root());if(job.radius>0&&target.position(server).squaredDistanceTo(job.center)>(double)job.radius*job.radius){job.pending=null;continue;}
                if(target.kind()!=ToolTarget.Kind.BLOCK&&expensive>=16)return;
                if(target.kind()==ToolTarget.Kind.ARCHITECTURE&&world.getBlockEntity(root) instanceof CompositeBlockEntity own&&target.valid(server)){
                    var before=world.getBlockState(root);var after=profile(before,job.profile);if(after!=null&&!after.equals(before)){var instance=CompositeRuntime.instance(world,root,after,target.uuid(),own.payload());for(var cell:instance.cells().keySet()){var pos=CompositeData.pos(instance.owner().root().add(cell));request(job,new ChunkPos(pos));}if(job.requested.values().stream().anyMatch(future->!future.isDone()))return;}
                }
                job.pending=null;if(!target.valid(server))continue;String before=profile(target,server);if(before==null||before.equals(job.profile))continue;if(target.kind()!=ToolTarget.Kind.BLOCK)expensive++;if(change(job.player,target,job.profile))job.changed++;else{job.failed++;dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("BB ALT profile edit rejected at {} {} {}",target.dimension(),root,target.registry());}
            }
        }catch(RuntimeException failure){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.error("BB profile job stopped after {} edits",job.changed,failure);job.player.sendMessage(Text.literal("BB: замена остановлена из-за ошибки; изменено "+job.changed+", подробности в журнале."),false);cancel(server);}
    }
    private static void next(Job job){release(job);job.current=null;job.cursor=null;job.pending=null;job.index++;}
    private static void finish(MinecraftServer server,Job job){cancel(server);job.player.sendMessage(Text.literal("BB: вид "+job.profile+" — изменено "+job.changed+", отклонено "+job.failed+", чанков "+job.index+". Ванильные и чужие объекты не изменялись."),false);}
    private static List<ToolTarget> targets(ServerWorld world,WorldChunk chunk){var result=new ArrayList<ToolTarget>();var cursor=new TargetCursor(world,chunk);while(!cursor.finished){var target=cursor.next(Long.MAX_VALUE);if(target!=null)result.add(target);}return List.copyOf(result);}
    /** Missing NEW authored alternates receive this documented, geometry-neutral darker tone. */
    public static boolean generatedAlternate(CompositeRootBlock block,BlockState state,List<CompositeSpec.Part> parts){return block.spec.id.getPath().startsWith("source_")&&state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT&&!parts.isEmpty()&&parts.stream().allMatch(part->part.model().equals(part.altModel()));}
    public static final float ALT_RED=.60f,ALT_GREEN=.67f,ALT_BLUE=.72f;
}
