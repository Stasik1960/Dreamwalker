package dev.dreamwalker.bloodbornedw.architecture.ladder_source;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Bounded, two-cell source ladder installation; no palette rewrite and no per-entity ticker. */
public final class SourceLadderRuntime {
    public static SourceBackingBlock BACKING;
    public static BlockEntityType<SourceLadderBlockEntity> ENTITY;
    private static boolean initialized;
    private static final ThreadLocal<Boolean> WRITING=ThreadLocal.withInitial(()->false);
    private static final Map<ServerWorld,Set<BlockPos>> CHECKS=new WeakHashMap<>();
    private static final Map<ServerWorld,Set<ChunkPos>> NEIGHBOR_CHUNKS=new WeakHashMap<>();
    private SourceLadderRuntime(){}
    public static boolean writing(){return WRITING.get();}
    /** Trusted bounded journal restore only: suppress callbacks/drop while restoring both snapshotted roles. */
    public static void runInternalWrite(Runnable action){boolean previous=WRITING.get();WRITING.set(true);try{Objects.requireNonNull(action).run();}finally{WRITING.set(previous);}}
    public static void initialize(){
        if(initialized)return;
        BACKING=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("source_ladder_backing"),new SourceBackingBlock());
        ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,PrototypeArchitecture.id("source_ladder_owner"),FabricBlockEntityTypeBuilder.create(SourceLadderBlockEntity::new,PrototypeArchitecture.LADDER,BACKING).build());
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity)->{
            if(!rootState(state)&&!state.isOf(BACKING))return true;
            BlockPos root=resolveRoot(world,pos);if(root==null)return false;
            SourceLadderBlockEntity owner=entity(world,root);
            return owner!=null&&accessible(world,pos,player)&&accessible(world,root,player)&&accessible(world,owner.backing(),player);
        });
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            // Only the supplied chunk is safe during CHUNK_LOAD; its holder future is not complete yet.
            for(BlockEntity entity:chunk.getBlockEntities().values())if(entity instanceof SourceLadderBlockEntity source)CHECKS.computeIfAbsent(world,w->new HashSet<>()).add(source.getPos().toImmutable());
            int cx=chunk.getPos().x,cz=chunk.getPos().z;
            for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)NEIGHBOR_CHUNKS.computeIfAbsent(world,w->new HashSet<>()).add(new ChunkPos(x,z));
        });
        ServerTickEvents.END_WORLD_TICK.register(world->{
            Set<ChunkPos> neighbors=NEIGHBOR_CHUNKS.remove(world);
            if(neighbors!=null)for(ChunkPos pos:neighbors){var chunk=world.getChunkManager().getWorldChunk(pos.x,pos.z);if(chunk!=null)for(BlockEntity entity:chunk.getBlockEntities().values())if(entity instanceof SourceLadderBlockEntity source)CHECKS.computeIfAbsent(world,w->new HashSet<>()).add(source.getPos().toImmutable());}
            Set<BlockPos> checks=CHECKS.remove(world);if(checks!=null)for(BlockPos pos:checks)validateLoaded(world,pos);
        });
        initialized=true;
    }
    /** expected states and typed block-entity NBT must come from the same recognized original pair. */
    public record Installation(BlockPos visualCell,BlockPos physicalCell,BlockState originalVisual,BlockState originalPhysical,NbtCompound visualBlockEntity,NbtCompound physicalBlockEntity,int variant,PrototypeLadderBlock.Profile profile,UUID owner){}
    public record Result(boolean committed,String reason,UUID owner){}
    private record Snapshot(BlockPos pos,BlockState state,NbtCompound entity){}
    private static boolean accessible(World world,BlockPos pos,PlayerEntity player){return world.isChunkLoaded(pos)&&world.isInBuildLimit(pos)&&world.getWorldBorder().contains(pos)&&(player==null||player.getAbilities().allowModifyWorld&&world.canPlayerModifyAt(player,pos));}
    private static Snapshot snapshot(World world,BlockPos pos){BlockEntity be=world.getBlockEntity(pos);return new Snapshot(pos.toImmutable(),world.getBlockState(pos),be==null?null:be.createNbtWithId());}
    private static boolean nbtEquals(World world,BlockPos pos,NbtCompound expected){BlockEntity be=world.getBlockEntity(pos);return expected==null?be==null:be!=null&&be.createNbtWithId().equals(expected);}
    private static void restore(World world,Snapshot snapshot){world.setBlockState(snapshot.pos,snapshot.state,Block.NOTIFY_LISTENERS);if(snapshot.entity!=null){BlockEntity entity=world.getBlockEntity(snapshot.pos);if(entity==null)throw new IllegalStateException("Cannot restore source block entity");entity.readNbt(snapshot.entity.copy());entity.markDirty();}}
    private static SourceLadderBlockEntity entity(BlockView world,BlockPos pos){return world.getBlockEntity(pos) instanceof SourceLadderBlockEntity source?source:null;}
    private static boolean same(SourceLadderBlockEntity a,SourceLadderBlockEntity b){return a!=null&&b!=null&&a.owner()!=null&&a.owner().equals(b.owner())&&a.root().equals(b.root())&&a.backing().equals(b.backing());}
    private static boolean rootState(BlockState state){return state.isOf(PrototypeArchitecture.LADDER)&&state.get(PrototypeLadderBlock.SOURCE_CLONE);}
    public static BlockPos resolveRoot(BlockView world,BlockPos anyPart){
        SourceLadderBlockEntity source=entity(world,anyPart);if(source==null||source.owner()==null)return null;
        boolean isRoot=anyPart.equals(source.root()),isBacking=anyPart.equals(source.backing());
        if(!isRoot&&!isBacking||isRoot&&!rootState(world.getBlockState(anyPart))||isBacking&&!world.getBlockState(anyPart).isOf(BACKING))return null;
        if(world instanceof World loaded&&(!loaded.isChunkLoaded(source.root())||!loaded.isChunkLoaded(source.backing())))return null;
        SourceLadderBlockEntity root=entity(world,source.root()),backing=entity(world,source.backing());
        return rootState(world.getBlockState(source.root()))&&world.getBlockState(source.backing()).isOf(BACKING)&&same(source,root)&&same(root,backing)?source.root():null;
    }
    public static Result install(ServerWorld world,Installation request,PlayerEntity player){
        BlockPos visual=request.visualCell.toImmutable(),physical=request.physicalCell.toImmutable();
        if(!accessible(world,visual,player)||!accessible(world,physical,player))return new Result(false,"UNLOADED_OUTSIDE_OR_DENIED",null);
        if(!request.originalVisual.isOf(Blocks.BEEHIVE)||request.originalVisual.get(BeehiveBlock.HONEY_LEVEL)!=1||!request.originalPhysical.isOf(Blocks.LADDER)||request.variant<0||request.variant>2||request.profile==null)return new Result(false,"NOT_RECOGNIZED_SOURCE_PAIR",null);
        Direction sourceFacing=request.originalVisual.get(BeehiveBlock.FACING),physicalFacing=request.originalPhysical.get(LadderBlock.FACING);
        if(physicalFacing!=sourceFacing.getOpposite()||!physical.equals(visual.offset(sourceFacing.getOpposite())))return new Result(false,"SOURCE_PAIR_ORIENTATION_MISMATCH",null);
        if(!world.getBlockState(visual).equals(request.originalVisual)||!world.getBlockState(physical).equals(request.originalPhysical)||!nbtEquals(world,visual,request.visualBlockEntity)||!nbtEquals(world,physical,request.physicalBlockEntity))return new Result(false,"SOURCE_CHANGED",null);
        BlockState root=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FACING,physicalFacing).with(PrototypeLadderBlock.WATERLOGGED,request.originalPhysical.get(LadderBlock.WATERLOGGED)).with(PrototypeLadderBlock.SOURCE_CLONE,true).with(PrototypeLadderBlock.VARIANT,request.variant).with(PrototypeLadderBlock.PROFILE,request.profile);
        if(!root.canPlaceAt(world,physical)||!world.doesNotIntersectEntities(null,root.getCollisionShape(world,physical).offset(physical.getX(),physical.getY(),physical.getZ()))||!world.doesNotIntersectEntities(null,net.minecraft.util.shape.VoxelShapes.fullCube().offset(visual.getX(),visual.getY(),visual.getZ())))return new Result(false,"UNSUPPORTED_OR_ENTITY_CONFLICT",null);
        Snapshot a=snapshot(world,visual),b=snapshot(world,physical);UUID owner=request.owner==null?UUID.randomUUID():request.owner;
        NbtCompound provenance=new NbtCompound();provenance.put("VisualState",NbtHelper.fromBlockState(request.originalVisual));provenance.put("PhysicalState",NbtHelper.fromBlockState(request.originalPhysical));if(request.visualBlockEntity!=null)provenance.put("VisualBlockEntity",request.visualBlockEntity.copy());if(request.physicalBlockEntity!=null)provenance.put("PhysicalBlockEntity",request.physicalBlockEntity.copy());
        WRITING.set(true);try{
            if(!world.setBlockState(visual,BACKING.getDefaultState(),Block.NOTIFY_LISTENERS)||!world.setBlockState(physical,root,Block.NOTIFY_LISTENERS))throw new IllegalStateException("State write refused");
            SourceLadderBlockEntity rootEntity=entity(world,physical),backingEntity=entity(world,visual);if(rootEntity==null||backingEntity==null)throw new IllegalStateException("Missing owner entity");
            rootEntity.set(owner,physical,visual,provenance);backingEntity.set(owner,physical,visual,provenance);
            if(!root.canPlaceAt(world,physical)||resolveRoot(world,physical)==null)throw new IllegalStateException("Installed pair failed validation");
        }catch(RuntimeException failure){restore(world,a);restore(world,b);return new Result(false,"ROLLED_BACK: "+failure.getMessage(),null);}finally{WRITING.set(false);}
        world.updateListeners(visual,a.state,world.getBlockState(visual),Block.NOTIFY_LISTENERS);world.updateListeners(physical,b.state,root,Block.NOTIFY_LISTENERS);world.updateNeighbors(visual,BACKING);world.updateNeighbors(physical,PrototypeArchitecture.LADDER);
        return new Result(true,"COMMITTED",owner);
    }
    public static boolean edit(World world,BlockPos root,BlockState before,BlockState next,PlayerEntity player){
        if(dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world,player,root)&&world.getBlockEntity(root) instanceof SourceLadderBlockEntity mounted&&!mounted.contributions().isEmpty()&&world.getBlockState(root).equals(before)&&before.isOf(next.getBlock()))return world.isClient||dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.transition((ServerWorld)world,mounted.resident(),next,player).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED;
        BlockPos resolved=resolveRoot(world,root);SourceLadderBlockEntity source=entity(world,root);
        if(resolved==null||!resolved.equals(root)||source==null||!rootState(before)||!rootState(next)||!world.getBlockState(root).equals(before)||player==null||!accessible(world,root,player)||!accessible(world,source.backing(),player))return false;
        for(Direction side:PrototypeLadderBlock.backingDirections(next))if(!accessible(world,root.offset(side),null))return false;
        if(!next.canPlaceAt(world,root)||!world.doesNotIntersectEntities(null,next.getCollisionShape(world,root).offset(root.getX(),root.getY(),root.getZ())))return false;
        if(world.isClient)return true;
        // Fixed backing never follows rotation. Rendering compensation follows the physical yaw.
        return before.equals(next)||world.setBlockState(root,next,Block.NOTIFY_ALL);
    }
    /** A replacement callback runs before the replaced part's BE is discarded. */
    public static void partReplaced(ServerWorld world,BlockPos pos,BlockState original){removeInternal(world,pos,null,true,true,original);}
    public static boolean remove(ServerWorld world,BlockPos part,PlayerEntity player,boolean drop){return removeInternal(world,part,player,drop,false,null);}
    private static boolean removeInternal(ServerWorld world,BlockPos part,PlayerEntity player,boolean drop,boolean replacing,BlockState original){
        if(writing())return false;SourceLadderBlockEntity source=entity(world,part);if(source==null||source.owner()==null)return false;
        BlockPos root=source.root(),backing=source.backing();
        BlockState role=replacing?original:world.getBlockState(part);
        if(!part.equals(root)&&!part.equals(backing)||role==null||part.equals(root)&&!rootState(role)||part.equals(backing)&&!role.isOf(BACKING))return false;
        if(!accessible(world,part,player))return false;
        if(player!=null&&(!accessible(world,root,player)||!accessible(world,backing,player)))return false;
        SourceLadderBlockEntity rootEntity=world.isChunkLoaded(root)?entity(world,root):null;BlockState state=world.isChunkLoaded(root)?world.getBlockState(root):null;
        // WorldChunk publishes the replacement palette before this callback. Its old state is authoritative for loot.
        if(replacing&&part.equals(root)&&original!=null&&rootState(original))state=original;
        ItemStack item=state!=null&&rootState(state)&&same(source,rootEntity)?PrototypeArchitecture.LADDER.artisticStack(state):ItemStack.EMPTY;
        var retiring=rootEntity!=null&&same(source,rootEntity)?rootEntity.resident():null;boolean retiredByTransaction=false;
        WRITING.set(true);try{
            if(rootEntity!=null&&!rootEntity.contributions().isEmpty()&&rootState(world.getBlockState(root))){
                var removed=dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.removeOwnedContributions(world,rootEntity.resident(),player,false);
                if(removed.outcome()!=dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED)return false;
                retiredByTransaction=true;
            }
            else if(replacing&&rootEntity!=null&&!rootEntity.contributions().isEmpty())dev.dreamwalker.bloodbornedw.composite.CompositeRuntime.cleanupNow(world,root);
            for(BlockPos member:List.of(root,backing))if(world.isChunkLoaded(member)&&same(source,entity(world,member))&&(!replacing||!member.equals(part))){BlockState old=world.getBlockState(member);if(rootState(old)||old.isOf(BACKING))world.setBlockState(member,rootState(old)&&old.get(PrototypeLadderBlock.WATERLOGGED)?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}
        }finally{WRITING.set(false);}
        if(retiring!=null&&!retiredByTransaction)dev.dreamwalker.bloodbornedw.link.MechanismLinks.removedArchitecture(world.getServer(),new dev.dreamwalker.bloodbornedw.link.MechanismLinks.TargetRef(dev.dreamwalker.bloodbornedw.link.MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),retiring.instanceId(),root.asLong(),retiring.registryId()));
        if(drop&&!item.isEmpty())Block.dropStack(world,root,item);return true;
    }
    private static void validateLoaded(ServerWorld world,BlockPos pos){
        if(world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4)==null)return;SourceLadderBlockEntity source=entity(world,pos);if(source==null)return;
        if(world.getChunkManager().getWorldChunk(source.root().getX()>>4,source.root().getZ()>>4)==null||world.getChunkManager().getWorldChunk(source.backing().getX()>>4,source.backing().getZ()>>4)==null)return;
        if(resolveRoot(world,pos)==null)removeInternal(world,pos,null,true,false,null);
    }
}
