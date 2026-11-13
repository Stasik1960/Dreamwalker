package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.composite.mixin.CreativeRemovalNativeBridgeAccess;
import dev.dreamwalker.bloodbornedw.composite.mixin.CreativeRemovalNativeCatalogAccess;
import dev.dreamwalker.bloodbornedw.composite.mixin.CreativeRemovalNativeVisualAccess;
import dev.dreamwalker.bloodbornedw.composite.mixin.CreativeRemovalRetainedFrameAccess;
import dev.dreamwalker.bloodbornedw.composite.mixin.CreativeRemovalWriteAccess;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/** Server-side deletion when saved ownership cannot pass the usual object transaction. */
public final class ForcedBlockRemoval {
    private static final int REMOVE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static final boolean NATIVE_GRID = available("NativeGridBridge");
    private static final boolean RETAINED_FRAMES = available("RetainedFrames");
    private static final ThreadLocal<CreativeTarget> CREATIVE_TARGET = new ThreadLocal<>();

    private record CreativeTarget(ServerWorld world, BlockPos pos, PlayerEntity player) {}

    private ForcedBlockRemoval() {}

    private static boolean available(String name) {
        try {
            Class.forName(ForcedBlockRemoval.class.getPackageName() + "." + name, false, ForcedBlockRemoval.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    public static int nativeBridgeVisual(ServerWorld world, BlockPos pos) {
        return NATIVE_GRID ? CreativeRemovalNativeBridgeAccess.dreamwalker$base(world, pos) : -1;
    }

    public static BlockState nativeBridgeState(int visual) {
        return NATIVE_GRID && visual >= 0 ? CreativeRemovalNativeCatalogAccess.dreamwalker$state(visual) : null;
    }

    public static int nativeVisual(ServerWorld world, BlockPos pos) {
        return NATIVE_GRID ? CreativeRemovalNativeVisualAccess.dreamwalker$index(world, pos) : -1;
    }

    public static boolean writeNativeVisual(ServerWorld world, BlockPos pos, int visual, int flags) {
        return NATIVE_GRID && CreativeRemovalNativeVisualAccess.dreamwalker$write(world, pos, visual, flags);
    }

    public static boolean restoreNativeBridge(ServerWorld world, BlockPos pos, int visual) {
        if (!NATIVE_GRID || visual < 0) return false;
        BlockState state = world.getBlockState(pos);
        if (nativeVisual(world, pos) == visual && state.equals(nativeBridgeState(visual))) return true;
        if (state.isAir()) return writeNativeVisual(world, pos, visual, Block.NOTIFY_ALL);
        if (!(world.getBlockEntity(pos) instanceof CompositeBlockEntity carrier)) return false;
        if (nativeBridgeVisual(world, pos) == visual) return true;
        try {
            CreativeRemovalNativeBridgeAccess.dreamwalker$validate(visual, state);
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
        NbtCompound data = carrier.createNbt();
        data.putInt("NativeGridBase", visual);
        carrier.readNbt(data);
        carrier.markDirty();
        world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        world.getChunkManager().markForUpdate(pos);
        world.getChunkManager().getLightingProvider().checkBlock(pos);
        return nativeBridgeVisual(world, pos) == visual;
    }

    public static BlockState retainedFrame(ServerWorld world, BlockPos pos) {
        if (!RETAINED_FRAMES) return null;
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : CreativeRemovalRetainedFrameAccess.dreamwalker$get(chunk, pos);
    }

    public static void restoreRetainedFrame(ServerWorld world, BlockPos pos, BlockState state) {
        if (RETAINED_FRAMES) {
            CreativeRemovalRetainedFrameAccess.dreamwalker$put(world.getWorldChunk(pos), pos, state);
        }
    }

    public static boolean hasDreamWalkerData(ServerWorld world, BlockPos pos) {
        return isModBlock(world.getBlockState(pos))
                || hasCompositeLayers(world, pos)
                || nativeVisual(world, pos) >= 0;
    }

    public static boolean hasCompositeLayers(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isOf(CompositeArchitecture.CELL)
                || !CompositeRuntime.contributions(world, pos).isEmpty()
                || nativeBridgeVisual(world, pos) >= 0 || retainedFrame(world, pos) != null;
    }

    public static boolean runCreativeBreak(ServerWorld world, BlockPos pos, PlayerEntity player, BooleanSupplier action) {
        CreativeTarget previous = CREATIVE_TARGET.get();
        CREATIVE_TARGET.set(new CreativeTarget(world, pos.toImmutable(), player));
        try {
            return action.getAsBoolean();
        } finally {
            if (previous == null) CREATIVE_TARGET.remove();
            else CREATIVE_TARGET.set(previous);
        }
    }

    public static PlayerEntity creativeOrphanActor(ServerWorld world, BlockPos pos) {
        CreativeTarget target = CREATIVE_TARGET.get();
        return target != null && target.world() == world && target.pos().equals(pos) ? target.player() : null;
    }

    public static boolean isOrphanHelper(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isOf(CompositeArchitecture.CELL)) return false;
        return hasNoLiveOwners(world, pos);
    }

    public static boolean hasNoLiveOwners(ServerWorld world, BlockPos pos) {
        return ownersAt(world, pos).stream().noneMatch(owner -> liveOrUnloaded(world, owner));
    }

    private static boolean liveOrUnloaded(ServerWorld world, Owner owner) {
        BlockPos root = CompositeData.pos(owner.root());
        return !world.isChunkLoaded(root)
                || world.getBlockEntity(root) instanceof CompositeBlockEntity carrier && owner.equals(carrier.resident());
    }

    public static boolean isModBlock(BlockState state) {
        String namespace=Registries.BLOCK.getId(state.getBlock()).getNamespace();
        return namespace.equals("bloodborne_dw")||namespace.equals("bloodborne_rp");
    }

    public static List<Owner> ownersAt(ServerWorld world,BlockPos pos) {
        Set<Owner> owners=new TreeSet<>();
        for(var entry:CompositeRuntime.contributions(world,pos))owners.add(entry.owner());
        if(world.getBlockEntity(pos) instanceof CompositeBlockEntity be){
            for(var entry:be.contributions())owners.add(entry.owner());
            if(be.resident()!=null)owners.add(be.resident());
        }
        return List.copyOf(owners);
    }

    public static boolean clearAllAt(ServerWorld world, BlockPos pos, PlayerEntity player) {
        if (!accessible(world, pos, player)) return false;
        Cell cell = CompositeData.cell(pos);
        Set<Owner> selected = new LinkedHashSet<>();
        for (var entry : CompositeRuntime.contributions(world, pos)) {
            if (!ImportedWholeSourceOpticsV127.emptyReplaceable(cell, List.of(entry))) selected.add(entry.owner());
        }
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity carrier) {
            for (var entry : carrier.contributions()) {
                if (!ImportedWholeSourceOpticsV127.emptyReplaceable(cell, List.of(entry))) selected.add(entry.owner());
            }
            if (carrier.resident() != null) selected.add(carrier.resident());
        }
        for (var iterator = selected.iterator(); iterator.hasNext();) {
            Owner owner = iterator.next();
            if (!accessible(world, CompositeData.pos(owner.root()), player)) continue;
            try {
                if (CompositeRuntime.remove(world, owner, player, false).outcome()
                        == dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED) iterator.remove();
            } catch (RuntimeException failure) {
                DreamwalkerBb.LOG.debug("Could not transactionally retire {} at {}", owner, pos, failure);
            }
        }
        if (player != null) {
            // A failed live-owner transaction can be a protected break footprint.
            // The force path is reserved for the selected cell and stale ownership.
            if (selected.stream().anyMatch(owner -> liveOrUnloaded(world, owner))) return false;
            return force(world, pos, List.of(), true, player);
        }
        return force(world, pos, selected, true, null);
    }

    /** Explicit air writes clear the target even if there is no valid owner root. */
    public static boolean force(ServerWorld world,BlockPos pos,Collection<Owner> requested,boolean clearTarget,PlayerEntity player) {
        if (clearTarget && !accessible(world, pos, player)) return false;
        Set<Owner> retired=new LinkedHashSet<>(requested);
        if(clearTarget&&world.getBlockEntity(pos) instanceof CompositeBlockEntity be&&be.resident()!=null)retired.add(be.resident());
        // A creative fallback may clear the cell even when a part across a
        // protection boundary cannot be touched by this player.
        retired.removeIf(owner->!accessible(world,CompositeData.pos(owner.root()),player));
        Map<Cell,List<Owner>> detach=new TreeMap<>();
        Set<BlockPos> roots=new LinkedHashSet<>();
        BlockPos ladderPeer=null;
        if(clearTarget&&world.getBlockEntity(pos) instanceof SourceLadderBlockEntity source&&source.owner()!=null){
            BlockPos other=pos.equals(source.root())?source.backing():pos.equals(source.backing())?source.root():null;
            if(other!=null&&accessible(world,other,player)&&world.getBlockEntity(other) instanceof SourceLadderBlockEntity peer
                    &&source.owner().equals(peer.owner())&&source.root().equals(peer.root())&&source.backing().equals(peer.backing())){
                ladderPeer=other;
                if(peer.resident()!=null)retired.add(peer.resident());
            }
        }
        for(Owner owner:retired){
            BlockPos root=CompositeData.pos(owner.root());
            try{
                world.getChunk(root);
                var be=world.getBlockEntity(root);
                if(be instanceof CompositeBlockEntity cached&&owner.equals(cached.resident())&&isModBlock(world.getBlockState(root)))roots.add(root);
                Set<Cell> members=new TreeSet<>();
                members.add(owner.root());
                var ledger=CompositeLedger.get(world);
                var saved=ledger.savedShardMemberships(Set.of(owner)).get(owner);
                if(saved!=null){
                    for(Cell offset:saved.keySet())members.add(owner.root().add(offset));
                }else{
                    var known=CompositeRuntime.contributions(world,pos).stream().filter(entry->owner.equals(entry.owner())).findFirst().orElse(null);
                    var data=known!=null ? known.rootData() : be instanceof CompositeBlockEntity cached && owner.equals(cached.resident())
                            ? CompositeData.data(world.getBlockState(root), cached.payload()) : null;
                    if(data!=null){
                        var object=CompositeRuntime.instance(world,root,CompositeData.state(data),owner.instanceId(),CompositeData.nbt(data.blockEntityNbt()));
                        for(Cell offset:object.cells().keySet())members.add(owner.root().add(offset));
                    }
                }
                for(Cell cell:members)if(accessible(world,CompositeData.pos(cell),player))
                    detach.computeIfAbsent(cell,key->new ArrayList<>()).add(owner);
            }catch(RuntimeException failure){
                DreamwalkerBb.LOG.debug("Could not enumerate saved parts of {}",owner,failure);
            }
            detach.computeIfAbsent(CompositeData.cell(pos),key->new ArrayList<>()).add(owner);
        }
        if(ladderPeer!=null)roots.add(ladderPeer);
        Set<Cell> changed=new LinkedHashSet<>();
        var writing=CreativeRemovalWriteAccess.dreamwalker$writing();
        CompositeShapeSnapshots.beginBatch(world);
        writing.set(writing.get()+1);
        try{
            SourceLadderRuntime.runInternalWrite(()->{
                if (clearTarget) clearCell(world, pos, changed);
                for(BlockPos root:roots) {
                    if (!clearTarget || !root.equals(pos)) detach(world, root, retired, true, changed);
                }
                detach.forEach((cell,owners)->{
                    BlockPos member=CompositeData.pos(cell);
                    if((!clearTarget || !member.equals(pos)) && !roots.contains(member)) detach(world,member,owners,false,changed);
                });
            });
        }finally{
            writing.set(writing.get()-1);
            CompositeShapeSnapshots.endBatch(world);
        }
        for(Owner owner:retired)try{
            var root=CompositeData.pos(owner.root());
            if(!(world.getBlockEntity(root) instanceof CompositeBlockEntity be)||!owner.equals(be.resident()))
                MechanismLinks.removedArchitecture(world.getServer(),new MechanismLinks.TargetRef(
                        MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),
                        owner.instanceId(),root.asLong(),owner.registryId()));
        }catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Could not retire links for {}",owner,failure);}
        try{CompositeNetworking.publish(world,List.copyOf(changed));}
        catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Could not publish removed ownership at {}",pos,failure);}
        BuildingNeighbors.publishShapeChanges(world,changed);
        for(Cell cell:changed){
            BlockPos member=CompositeData.pos(cell);
            try{world.updateNeighbors(member,world.getBlockState(member).getBlock());}
            catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Could not notify neighbors of removal at {}",member,failure);}
        }
        return clearTarget ? world.getBlockState(pos).isOf(Blocks.AIR) && !hasDreamWalkerData(world, pos) : !changed.isEmpty();
    }

    private static boolean accessible(ServerWorld world,BlockPos pos,PlayerEntity player){
        return world.isInBuildLimit(pos)&&world.getWorldBorder().contains(pos)&&(player==null||world.canPlayerModifyAt(player,pos));
    }

    private static void clearCell(ServerWorld world,BlockPos pos,Set<Cell> changed){
        Cell cell=CompositeData.cell(pos);
        BlockState before=world.getBlockState(pos);
        try{
            var ledger=CompositeLedger.get(world);
            for(var entry:ledger.at(cell))world.getChunk(CompositeData.pos(entry.owner().root()));
            if(!ledger.at(cell).isEmpty()){
                ledger.put(cell,List.of());
                changed.add(cell);
            }
        }catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Could not clear saved ownership at {}",pos,failure);}
        try{
            if (retainedFrame(world, pos) != null) {
                CreativeRemovalRetainedFrameAccess.dreamwalker$remove(world.getWorldChunk(pos), pos);
                changed.add(cell);
            }
            int nativeIndex = nativeVisual(world, pos);
            if(world.getBlockEntity(pos)!=null){
                world.removeBlockEntity(pos);
                changed.add(cell);
            }
            if(world.setBlockState(pos,Blocks.AIR.getDefaultState(),REMOVE_FLAGS))changed.add(cell);
            if (nativeIndex >= 0 && world.getBlockState(pos).isOf(Blocks.AIR)) {
                if (nativeVisual(world, pos) >= 0) CreativeRemovalNativeVisualAccess.dreamwalker$stage(world, pos, -1);
                CreativeRemovalNativeVisualAccess.dreamwalker$publish(world, pos);
                changed.add(cell);
            }
        }catch(RuntimeException failure){
            DreamwalkerBb.LOG.debug("Block removal callback failed at {}",pos,failure);
            if(!world.getBlockState(pos).equals(before)){
                changed.add(cell);
                world.updateListeners(pos,before,world.getBlockState(pos),Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void detach(ServerWorld world,BlockPos pos,Collection<Owner> owners,boolean removeResident,Set<Cell> changed){
        Cell cell=CompositeData.cell(pos);
        try{
            var ledger=CompositeLedger.get(world);
            var before=CompositeRuntime.contributions(world,pos);
            var kept=before.stream().filter(entry->!owners.contains(entry.owner())).toList();
            var entity=world.getBlockEntity(pos);
            if(!kept.equals(before)){
                if(!ledger.at(cell).isEmpty())ledger.put(cell,kept);
                changed.add(cell);
            }
            if (removeResident || kept.isEmpty() && changed.contains(cell) && world.getBlockState(pos).isOf(CompositeArchitecture.CELL)) {
                replaceRetiredCarrier(world, pos, kept);
                changed.add(cell);
            } else if (entity instanceof CompositeBlockEntity be && changed.contains(cell)) {
                be.set(be.resident(), kept, be.payload());
                world.getChunkManager().markForUpdate(pos);
            }
        }catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Could not detach saved ownership at {}",pos,failure);}
    }

    private static void replaceRetiredCarrier(ServerWorld world, BlockPos pos, List<CompositeData.Contribution> kept) {
        int base = nativeBridgeVisual(world, pos);
        BlockState helper = CompositeArchitecture.CELL.getDefaultState();
        if (base >= 0) {
            boolean canRetainHelper = false;
            if (!kept.isEmpty()) {
                try {
                    CreativeRemovalNativeBridgeAccess.dreamwalker$validate(base, helper);
                    canRetainHelper = true;
                } catch (IllegalArgumentException unsupported) {
                    // Keep unrelated owners in the ledger on a native carrier.
                }
            }
            if (!canRetainHelper) {
                if (!writeNativeVisual(world, pos, base, REMOVE_FLAGS)) {
                    throw new IllegalStateException("Could not restore native background at " + pos);
                }
                return;
            }
        }
        world.setBlockState(pos, kept.isEmpty() ? Blocks.AIR.getDefaultState() : helper, REMOVE_FLAGS);
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity carrier) {
            carrier.set(null, kept, new NbtCompound());
            if (base >= 0) restoreNativeBridge(world, pos, base);
            world.getChunkManager().markForUpdate(pos);
        }
    }
}
