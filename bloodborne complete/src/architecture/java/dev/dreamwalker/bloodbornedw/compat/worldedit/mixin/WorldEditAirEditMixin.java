package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import com.sk89q.jnbt.StringTag;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditNativeWrites;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.ForcedBlockRemoval;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Change only explicit air writes after WorldEdit has captured its history. */
@Mixin(value=WorldEditBridge.ObjectExtent.class,remap=false,priority=1100)
public abstract class WorldEditAirEditMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow @Final private boolean outsideHistory;

    @Inject(method="setBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private void dreamwalker$clearAir(BlockVector3 v,BlockStateHolder<?> value,CallbackInfoReturnable<Boolean> callback){
        if(outsideHistory||!value.getBlockType().getId().equals("minecraft:air")||!world.getServer().isOnThread())return;
        var tag=value.toBaseBlock().getNbtData();
        if(tag!=null&&(tag.containsKey("DreamwalkerWorldEditSnapshot")||tag.containsKey("DreamwalkerWorldEditRpType")))return;
        BlockPos pos=new BlockPos(v.getX(),v.getY(),v.getZ());
        if(!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos))return;
        world.getChunk(pos);
        var owners=new ArrayList<>(ForcedBlockRemoval.ownersAt(world,pos));
        var cell=new Box(pos);
        var entities=world.getEntitiesByClass(RpObjectEntity.class,cell.expand(32),entity->
                BlockPos.ofFloored(entity.getPos()).equals(pos)||entity.selectionBoxes().stream().anyMatch(part->part.intersects(cell)));
        boolean clearNative=true;
        var selectionTag=tag==null?null:tag.getValue().get("DreamwalkerWorldEditSelection");
        if(selectionTag instanceof StringTag selection){
            var match=CompositeData.nbt(Base64.getDecoder().decode(selection.getValue()));
            Set<UUID> selectedOwners=new HashSet<>(),selectedEntities=new HashSet<>();
            match.getList("Owners",11).forEach(id->selectedOwners.add(NbtHelper.toUuid(id)));
            match.getList("Entities",11).forEach(id->selectedEntities.add(NbtHelper.toUuid(id)));
            owners.removeIf(owner->!selectedOwners.contains(owner.instanceId()));
            entities=new ArrayList<>(entities.stream().filter(entity->selectedEntities.contains(entity.getUuid())).toList());
            clearNative=match.getBoolean("Native");
        }
        if(owners.isEmpty()&&entities.isEmpty()&&(!clearNative||!ForcedBlockRemoval.hasDreamWalkerData(world,pos)))return;
        if(clearNative&&selectionTag==null&&(ForcedBlockRemoval.nativeBridgeVisual(world,pos)>=0
                ||ForcedBlockRemoval.retainedFrame(world,pos)!=null)){
            // Normal removal turns an emptied bridge carrier back into its native
            // visual block. Explicit air must clear that background too.
            boolean changed=ForcedBlockRemoval.clearAllAt(world,pos,null);
            for(var entity:entities)entity.removeObject();
            callback.setReturnValue(changed||!entities.isEmpty());
            return;
        }
        boolean changed=false,committed=false;
        try{
            if(!(clearNative&&world.getBlockEntity(pos) instanceof SourceLadderBlockEntity)){
                var air=CompositeData.data(Blocks.AIR.getDefaultState(),new NbtCompound());
                var result=WorldEditObjectTransactions.replace(world,owners,List.of(),clearNative?Map.of(pos,air):Map.of());
                if(result.outcome()==TransactionCore.Outcome.COMMITTED){
                    changed=result.changedCells()>0;
                    committed=!clearNative||world.getBlockState(pos).isOf(Blocks.AIR)
                            &&!ForcedBlockRemoval.hasDreamWalkerData(world,pos);
                }
            }
        }catch(RuntimeException failure){DreamwalkerBb.LOG.debug("Normal WorldEdit air write failed at {}",pos,failure);}
        if(!committed)changed=(clearNative&&selectionTag==null?ForcedBlockRemoval.clearAllAt(world,pos,null)
                :ForcedBlockRemoval.force(world,pos,owners,clearNative,null))||changed;
        for(var entity:entities)entity.removeObject();
        callback.setReturnValue(changed||!entities.isEmpty());
    }

    @Inject(method="setBlock",at=@At("RETURN"),remap=false)
    private void dreamwalker$restoreBridgeHistory(BlockVector3 v,BlockStateHolder<?> value,
            CallbackInfoReturnable<Boolean> callback) throws InputParseException {
        if(outsideHistory||!callback.getReturnValueZ())return;
        var tag=value.toBaseBlock().getNbtData();
        if(tag==null||!(tag.getValue().get("DreamwalkerWorldEditSnapshot") instanceof StringTag encoded))return;
        var snapshot=CompositeData.nbt(Base64.getDecoder().decode(encoded.getValue()));
        int[] anchor=snapshot.getIntArray("Anchor");
        if(anchor.length!=3||anchor[0]!=v.getX()||anchor[1]!=v.getY()||anchor[2]!=v.getZ())return;
        BlockPos pos=new BlockPos(v.getX(),v.getY(),v.getZ());
        if(snapshot.contains(WorldEditNativeWrites.BRIDGE_VISUAL,NbtElement.INT_TYPE)
                &&!ForcedBlockRemoval.restoreNativeBridge(world,pos,
                snapshot.getInt(WorldEditNativeWrites.BRIDGE_VISUAL)))
            throw new InputParseException("Could not restore the original DreamWalker city background");
        if(snapshot.contains(WorldEditNativeWrites.RETAINED_FRAME,NbtElement.COMPOUND_TYPE)){
            var data=CompositeData.data(snapshot.getCompound(WorldEditNativeWrites.RETAINED_FRAME));
            ForcedBlockRemoval.restoreRetainedFrame(world,pos,CompositeData.state(data));
        }
    }
}
