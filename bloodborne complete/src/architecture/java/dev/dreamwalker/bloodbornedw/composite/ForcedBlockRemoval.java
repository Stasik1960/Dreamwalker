package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.composite.mixin.CreativeRemovalWriteAccess;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Server-side deletion when saved ownership cannot pass the usual object transaction. */
public final class ForcedBlockRemoval {
    private ForcedBlockRemoval() {}

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

    public static boolean creativeFallback(ServerWorld world,BlockPos pos,PlayerEntity player) {
        Owner hit=null;
        try{
            if(world.getBlockEntity(pos) instanceof CompositeBlockEntity be)hit=be.resident();
            if(hit==null)hit=CompositeRuntime.target(world,pos,player);
        }catch(RuntimeException failure){
            DreamwalkerBb.LOG.debug("Could not resolve creative target at {}",pos,failure);
        }
        return force(world,pos,hit==null?List.of():List.of(hit),true,player);
    }

    /** Explicit air writes clear the target even if there is no valid owner root. */
    public static boolean force(ServerWorld world,BlockPos pos,Collection<Owner> requested,boolean clearTarget,PlayerEntity player) {
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
                    if(known!=null){
                        var data=known.rootData();
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
        if(clearTarget)roots.add(pos);
        Set<Cell> changed=new LinkedHashSet<>();
        var writing=CreativeRemovalWriteAccess.dreamwalker$writing();
        CompositeShapeSnapshots.beginBatch(world);
        writing.set(writing.get()+1);
        try{
            SourceLadderRuntime.runInternalWrite(()->{
                for(BlockPos root:roots)clearCell(world,root,changed);
                detach.forEach((cell,owners)->{
                    BlockPos member=CompositeData.pos(cell);
                    if(!roots.contains(member))detach(world,member,owners,changed);
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
        return clearTarget?world.getBlockState(pos).isOf(Blocks.AIR)&&!changed.isEmpty():!changed.isEmpty();
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
            if(world.getBlockEntity(pos)!=null){
                world.removeBlockEntity(pos);
                changed.add(cell);
            }
            if(world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE|Block.SKIP_DROPS))changed.add(cell);
        }catch(RuntimeException failure){
            DreamwalkerBb.LOG.debug("Block removal callback failed at {}",pos,failure);
            if(!world.getBlockState(pos).equals(before)){
                changed.add(cell);
                world.updateListeners(pos,before,world.getBlockState(pos),Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void detach(ServerWorld world,BlockPos pos,Collection<Owner> owners,Set<Cell> changed){
        Cell cell=CompositeData.cell(pos);
        try{
            var ledger=CompositeLedger.get(world);
            var before=CompositeRuntime.contributions(world,pos);
            var kept=before.stream().filter(entry->!owners.contains(entry.owner())).toList();
            var entity=world.getBlockEntity(pos);
            if(!kept.equals(before)){
                if(!ledger.at(cell).isEmpty())ledger.put(cell,kept);
                if(entity instanceof CompositeBlockEntity be)be.set(be.resident(),kept,be.payload());
                changed.add(cell);
            }
            if(kept.isEmpty()&&world.getBlockState(pos).isOf(CompositeArchitecture.CELL))clearCell(world,pos,changed);
            else if(entity instanceof CompositeBlockEntity&&changed.contains(cell))world.getChunkManager().markForUpdate(pos);
        }catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Could not detach saved ownership at {}",pos,failure);}
    }
}
