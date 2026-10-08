package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Mechanisms use the same checked atomic door transition as an ordinary player's click. */
public final class CompositeMechanismBridge implements MechanismLinks.ArchitectureBridge {
    private CompositeMechanismBridge(){}
    public static void initialize(){MechanismLinks.architectureBridge(new CompositeMechanismBridge());}
    public static MechanismLinks.TargetRef reference(ServerWorld world,dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner){return new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),CompositeData.pos(owner.root()).asLong(),owner.registryId());}
    @Override public MechanismLinks.Status inspect(ServerWorld world,MechanismLinks.TargetRef target){
        BlockPos pos=BlockPos.fromLong(target.root());if(!world.isChunkLoaded(pos))return new MechanismLinks.Status(MechanismLinks.Availability.UNLOADED,false,false);
        BlockState state=world.getBlockState(pos);if(!(state.getBlock() instanceof CompositeRootBlock block)||!block.spec.id.getPath().equals("prototype_double_door")||!block.spec.id.toString().equals(target.registryId())||!(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity)||entity.resident()==null||!entity.resident().instanceId().equals(target.instance())||!entity.resident().registryId().equals(target.registryId())||!CompositeData.pos(entity.resident().root()).equals(pos))return new MechanismLinks.Status(MechanismLinks.Availability.STALE,false,false);
        return new MechanismLinks.Status(MechanismLinks.Availability.LOADED,state.get(CompositeRootBlock.OPEN),false);
    }
    @Override public boolean apply(ServerWorld world,MechanismLinks.TargetRef target,boolean open){
        if(inspect(world,target).availability()!=MechanismLinks.Availability.LOADED)return false;BlockPos pos=BlockPos.fromLong(target.root());CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(pos);
        return CompositeRuntime.transition(world,entity.resident(),world.getBlockState(pos).with(CompositeRootBlock.OPEN,open),null).outcome()==TransactionCore.Outcome.COMMITTED;
    }
}
