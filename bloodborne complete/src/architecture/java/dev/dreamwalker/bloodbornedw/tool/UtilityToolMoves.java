package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Two-cell utility relocation with rollback, guest geometry and typed command counters. */
final class UtilityToolMoves {
    private record Saved(BlockState state,NbtCompound entity,List<CompositeData.Contribution> entries) {}
    private UtilityToolMoves(){}
    static boolean move(ServerPlayerEntity player,ToolTarget target,int delta){
        if(target.kind()!=ToolTarget.Kind.BLOCK||Math.abs(delta)!=1||!target.valid(player.getServer()))return false;
        var world=target.world(player.getServer());var source=BlockPos.fromLong(target.root());var destination=source.up(delta);var marker=world.getBlockState(source);
        if(!DwUtilityBlocks.marker(marker)||!world.isChunkLoaded(destination)||!world.isInBuildLimit(destination)||!world.getWorldBorder().contains(destination)||!BuildPermissions.canEdit(world,player,source)||!BuildPermissions.canEdit(world,player,destination))return false;
        var carrier=world.getBlockState(destination);if(!(carrier.isAir()||carrier.isOf(CompositeArchitecture.CELL)))return false;
        if(world.getBlockEntity(destination) instanceof CompositeBlockEntity own&&own.resident()!=null)return false;
        Saved beforeSource=save(world,source),beforeDestination=save(world,destination);NbtCompound commands=ToolCommands.get(player.getServer()).writeNbt(new NbtCompound());
        CompositeShapeSnapshots.beginBatch(world);
        try{
            if(!DwUtilityBlocks.replaceCarrier(world,destination,marker,player))return false;
            if(!DwUtilityBlocks.replaceCarrier(world,source,beforeSource.entries.isEmpty()?Blocks.AIR.getDefaultState():CompositeArchitecture.CELL.getDefaultState(),player)){restore(world,destination,beforeDestination);restore(world,source,beforeSource);ToolCommands.restoreAssemblyMigration(player.getServer(),commands);return false;}
            if(world.getBlockEntity(destination) instanceof CompositeBlockEntity own&&beforeSource.entity!=null){NbtCompound payload=beforeSource.entity.getCompound("payload");if(!payload.isEmpty())own.set(null,beforeDestination.entries,payload);}
            var moved=new ToolTarget(ToolTarget.Kind.BLOCK,target.dimension(),null,destination.asLong(),target.registry());ToolCommands.restoreBlockRetarget(player.getServer(),commands,target,moved);CompositeNetworking.publish(world,List.of(CompositeData.cell(source),CompositeData.cell(destination)));return true;
        }catch(RuntimeException failure){restore(world,destination,beforeDestination);restore(world,source,beforeSource);ToolCommands.restoreAssemblyMigration(player.getServer(),commands);dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Utility height edit rolled back",failure);return false;}
        finally{CompositeShapeSnapshots.endBatch(world);}
    }
    private static Saved save(ServerWorld world,BlockPos pos){var entity=world.getBlockEntity(pos);return new Saved(world.getBlockState(pos),entity==null?null:entity.createNbt(),List.copyOf(CompositeRuntime.contributions(world,pos)));}
    private static void restore(ServerWorld world,BlockPos pos,Saved saved){world.setBlockState(pos,saved.state,Block.FORCE_STATE|Block.NOTIFY_LISTENERS);if(saved.entity!=null&&world.getBlockEntity(pos)!=null)world.getBlockEntity(pos).readNbt(saved.entity.copy());CompositeLedger.get(world).put(CompositeData.cell(pos),saved.entries);CompositeNetworking.publish(world,List.of(CompositeData.cell(pos)));world.updateNeighbors(pos,saved.state.getBlock());}
}
