package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;

/** Full world/BE snapshots plus an owner ledger; commits never replace foreign carriers. */
public class FabricCompositeWorld implements TransactionCore.WorldAccess {
    private record Original(BlockState state,NbtCompound fullNbt,List<CompositeData.Contribution> contributions,CellSnapshot snapshot){}
    public final ServerWorld world;
    private final Map<Owner,ObjectInstance> incoming;
    private final Map<Cell,Original> originals=new TreeMap<>();
    public FabricCompositeWorld(ServerWorld world,Map<Owner,ObjectInstance> incoming){this.world=world;this.incoming=Map.copyOf(incoming);}
    @Override public void assertMutationThread(){if(!world.getServer().isOnThread())throw new IllegalStateException("Composite mutation requires the server thread");}
    @Override public String dimensionKey(){return world.getRegistryKey().getValue().toString();}
    @Override public boolean isLoaded(Cell cell){return world.isChunkLoaded(CompositeData.pos(cell));}
    @Override public boolean inBounds(Cell cell){BlockPos pos=CompositeData.pos(cell);return world.isInBuildLimit(pos)&&world.getWorldBorder().contains(pos);}
    @Override public CellSnapshot read(Cell cell){
        if(!isLoaded(cell))throw new IllegalStateException("Read would load a composite chunk: "+cell);
        BlockPos pos=CompositeData.pos(cell);BlockState state=world.getBlockState(pos);BlockEntity entity=world.getBlockEntity(pos);
        NbtCompound full=entity==null?new NbtCompound():entity.createNbt();List<CompositeData.Contribution> contributions=CompositeLedger.get(world).at(cell);
        if(contributions.isEmpty()&&entity instanceof CompositeBlockEntity cached)contributions=cached.contributions();
        Owner resident=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(state)&&entity instanceof CompositeBlockEntity cached?cached.resident():null;
        List<Owner> guests=new ArrayList<>();for(var entry:contributions)if(!entry.owner().equals(resident))guests.add(entry.owner());
        // Custom technical AirBlocks may carry source lighting or another mod's data.
        // Only the three vanilla empty states are free cells for helper replacement.
        boolean free=state.isOf(Blocks.AIR)||state.isOf(Blocks.CAVE_AIR)||state.isOf(Blocks.VOID_AIR);
        Kind kind=resident!=null?Kind.ROOT:state.isOf(CompositeArchitecture.CELL)||free&&!guests.isEmpty()?Kind.HELPER:free?Kind.AIR:Kind.FOREIGN;
        NbtCompound payload=entity instanceof CompositeBlockEntity cached&&(resident!=null||state.isOf(CompositeArchitecture.CELL))?cached.payload():full;
        CellSnapshot snapshot=new CellSnapshot(kind,CompositeData.data(state,payload),resident,guests);
        originals.putIfAbsent(cell,new Original(state,full.copy(),List.copyOf(contributions),snapshot));return snapshot;
    }
    @Override public Optional<ObjectInstance> describeRoot(Owner owner,CellSnapshot root){
        if(!owner.equals(root.resident()))return Optional.empty();BlockState state=CompositeData.state(root.data());
        if(!dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isArchitecture(state)||!net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString().equals(owner.registryId()))return Optional.empty();
        var object=CompositeRuntime.instance(world,CompositeData.pos(owner.root()),state,owner.instanceId(),CompositeData.nbt(root.data().blockEntityNbt()));
        // Native V9 palettes had no extra owner cells. Their newly persistent
        // UUID starts with the existing root only; first height edit creates
        // the complete clipped ledger atomically instead of pretending it existed.
        if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.isNative(state)&&CompositeLedger.get(world).at(owner.root()).stream().noneMatch(entry->entry.owner().equals(owner))&&world.getBlockEntity(CompositeData.pos(owner.root())) instanceof CompositeBlockEntity cached&&cached.contributions().isEmpty())object=new ObjectInstance(owner,object.rootData(),Map.of(Cell.ORIGIN,object.cells().get(Cell.ORIGIN)));
        return Optional.of(object);
    }
    @Override public boolean supportsGuestBindings(CellSnapshot carrier){return carrier.kind()!=Kind.AIR;}
    @Override public boolean allowsReviewedOverlap(ObjectInstance incoming,ObjectInstance existing,Cell cell){
        if(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.heightEdit())return true;
        if(dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstance(incoming.owner().instanceId()))return true;
        CellSnapshot previous=read(incoming.owner().root());
        if(incoming.owner().equals(previous.resident())) {
            ObjectInstance original=describeRoot(incoming.owner(),previous).orElse(null);
            if(original!=null&&original.at(cell)!=null){
                var old=original.at(cell).collision().stream().map(b->new net.minecraft.util.math.Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())).toList();
                boolean retained=true;
                for(var a:incoming.at(cell).collision())for(var b:existing.at(cell).collision())retained&=dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.retainedIntersection(
                    new net.minecraft.util.math.Box(a.minX(),a.minY(),a.minZ(),a.maxX(),a.maxY(),a.maxZ()),new net.minecraft.util.math.Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()),old);
                if(retained)return true;
            }
        }
        // Negligible penetration is contact. Active volume overlap otherwise needs explicit initial migration.
        return !incoming.at(cell).collides(existing.at(cell));
    }
    @Override public boolean intersectsEntities(Cell cell,Footprint proposed){
        var pos=CompositeData.pos(cell);var boxes=CompositeRuntime.shape(proposed.collision()).getBoundingBoxes().stream().map(box->box.offset(pos)).toList();
        boolean initial=incoming.keySet().stream().anyMatch(owner->dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope.initialInstance(owner.instanceId()));
        return dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.entityConflict(world,boxes,null,initial)!=null;
    }
    @Override public boolean needsRefresh(Cell cell,CellSnapshot before,CellSnapshot next){return incoming.keySet().stream().anyMatch(owner->owner.equals(next.resident())||next.guests().contains(owner));}
    @Override public boolean restoreSilently(Cell cell,CellSnapshot previous){Original original=originals.get(cell);if(original==null||!original.snapshot.equals(previous))throw new IllegalStateException("Missing rollback snapshot "+cell);BlockPos pos=CompositeData.pos(cell);world.setBlockState(pos,original.state,Block.FORCE_STATE|Block.SKIP_DROPS);BlockEntity entity=world.getBlockEntity(pos);if(entity!=null){entity.readNbt(original.fullNbt.copy());entity.markDirty();}CompositeLedger.get(world).put(cell,original.contributions);return true;}
    @Override public boolean writeSilently(Cell cell,CellSnapshot next){
        BlockPos pos=CompositeData.pos(cell);Original original=originals.get(cell);
        BlockState nextState=CompositeData.state(next.data());
        if(next.kind()==Kind.FOREIGN){
            // Foreign walls/inventories stay the same native block and native block entity.
            if(!world.getBlockState(pos).equals(nextState))return false;
            BlockEntity foreign=world.getBlockEntity(pos);NbtCompound actual=foreign==null?new NbtCompound():foreign.createNbt();
            if(!actual.equals(CompositeData.nbt(next.data().blockEntityNbt())))return false;
        }else world.setBlockState(pos,nextState,Block.FORCE_STATE|Block.SKIP_DROPS);
        List<Owner> owners=new ArrayList<>(next.guests());if(next.resident()!=null)owners.add(next.resident());
        List<CompositeData.Contribution> entries=new ArrayList<>();
        for(Owner owner:owners){ObjectInstance instance=incoming.get(owner);
            if(instance==null&&isLoaded(owner.root()))instance=describeRoot(owner,read(owner.root())).orElse(null);
            if(instance!=null){Footprint footprint=instance.at(cell);if(footprint==null)throw new IllegalStateException("Owner has no contribution at "+cell);entries.add(new CompositeData.Contribution(owner,instance.rootData(),footprint));}
            else {var cached=CompositeLedger.get(world).at(cell).stream().filter(entry->entry.owner().equals(owner)).findFirst().orElseThrow(()->new IllegalStateException("Missing deferred owner cache "+owner));entries.add(cached);}
        }
        CompositeLedger.get(world).put(cell,entries);
        BlockEntity entity=world.getBlockEntity(pos);
        if(next.kind()!=Kind.FOREIGN&&entity instanceof CompositeBlockEntity cached){
            // A source ladder's private UUID/root/backing and opaque Original
            // provenance belong to its existing installation, never to an item.
            // Transaction detach/recreate must restore that typed role before
            // replacing the public owner contribution payload.
            if(entity instanceof dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity&&original!=null&&next.resident()!=null&&next.resident().equals(original.snapshot.resident()))cached.readNbt(original.fullNbt.copy());
            cached.set(next.resident(),entries,CompositeData.nbt(next.data().blockEntityNbt()));
        }
        return true;
    }
    @Override public void publishCommitted(List<Cell> cells){
        for(Cell cell:cells){BlockPos pos=CompositeData.pos(cell);BlockState state=world.getBlockState(pos);Original original=originals.get(cell);world.updateListeners(pos,original==null?state:original.state,state,Block.NOTIFY_LISTENERS);if(world.getBlockEntity(pos) instanceof CompositeBlockEntity)world.getChunkManager().markForUpdate(pos);}
        CompositeNetworking.publish(world,cells);
        for(Cell cell:cells){BlockPos pos=CompositeData.pos(cell);world.updateNeighbors(pos,world.getBlockState(pos).getBlock());}
    }
}
