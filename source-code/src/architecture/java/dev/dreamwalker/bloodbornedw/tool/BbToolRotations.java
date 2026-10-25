package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/** Uses each object's declared orientation schema; failed rotations are atomic. */
public final class BbToolRotations {
    private BbToolRotations(){}
    public static boolean rotate(ServerPlayerEntity player,ToolTarget target){
        if(!BbToolService.canEdit(player)||target==null||!target.valid(player.getServer()))return false;
        if(target.kind()==ToolTarget.Kind.RP&&target.entity(player.getServer()) instanceof RpObjectEntity rp)return rp.rotateObject(player,rp.getYaw()+45);
        if(target.kind()!=ToolTarget.Kind.BLOCK&&target.kind()!=ToolTarget.Kind.ARCHITECTURE)return false;
        var world=target.world(player.getServer());var pos=BlockPos.fromLong(target.root());
        if(!BuildPermissions.canEdit(world,player,pos))return false;var state=world.getBlockState(pos);
        if(ImportedCompactBlock.compact(state)){
            // One transaction replaces the selected palette with the rotated
            // owned object. Failed edits cannot materialize or drop its data.
            var next=state.with(ImportedCompactBlock.COMPACT,false).with(CompositeRootBlock.ROTATION,(state.get(CompositeRootBlock.ROTATION)+1)%8);
            var payload=new NbtCompound();payload.putInt(ImportedTypeAliases.VERSION_KEY,ImportedTypeAliases.VERSION);
            var obj=CompositeRuntime.instance(world,pos,next,UUID.randomUUID(),payload);
            var commands=ToolCommands.get(player.getServer()).writeNbt(new NbtCompound());
            var result=WorldEditObjectTransactions.replace(world,List.of(),List.of(obj));
            if(result.outcome()!=TransactionCore.Outcome.COMMITTED)return false;
            var own=new ToolTarget(ToolTarget.Kind.ARCHITECTURE,target.dimension(),obj.owner().instanceId(),pos.asLong(),obj.owner().registryId());
            ToolCommands.restoreBlockRetarget(player.getServer(),commands,target,own);return true;
        }
        if(state.getBlock() instanceof CompositeRootBlock block){
            var owner=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(world,pos);if(owner==null)return false;
            var next=block instanceof ThinWindowRootBlock?GlazingTypes.step90(state):state.with(CompositeRootBlock.ROTATION,(state.get(CompositeRootBlock.ROTATION)+1)%8);
            return CompositeRuntime.transition(world,owner,next,player).outcome()==TransactionCore.Outcome.COMMITTED;
        }
        if(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock wall){
            var next=wall.rotate45(state);var owner=dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.owner(world,pos);
            if(owner!=null)return CompositeRuntime.transition(world,owner,next,player).outcome()==TransactionCore.Outcome.COMMITTED;
            var boxes=next.getCollisionShape(world,pos).getBoundingBoxes().stream().map(b->b.offset(pos)).toList();
            return PlacementPhysics.conflict(world,boxes,null,player,pos)==null&&world.setBlockState(pos,next,Block.NOTIFY_ALL);
        }
        if(state.getBlock() instanceof PrototypeLadderBlock ladder)return PrototypeArchitecture.edit(world,pos,state,ladder.step45(state),player);
        var next=state.rotate(BlockRotation.CLOCKWISE_90);if(next.equals(state))return false;
        var boxes=next.getCollisionShape(world,pos).getBoundingBoxes().stream().map(b->b.offset(pos)).toList();
        if(PlacementPhysics.conflict(world,boxes,null,player,pos)!=null)return false;
        return world.setBlockState(pos,next,Block.NOTIFY_ALL);
    }
}
