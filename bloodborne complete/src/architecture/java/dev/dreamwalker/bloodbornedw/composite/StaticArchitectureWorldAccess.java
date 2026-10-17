package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.CellSnapshot;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;

/** Scoped native-part writes through the unchanged typed rollback adapter.
 * The caller validates the complete fresh owner/cell read set before execute.
 * There are no retained world positions or snapshots after the operation. */
public class StaticArchitectureWorldAccess extends FabricCompositeWorld {
    private final Set<Cell> scope;
    private final boolean allowLegacy;
    private final int failAfterWrites;
    private int writes;
    public StaticArchitectureWorldAccess(ServerWorld world,Set<Cell> scope,boolean allowLegacy,int failAfterWrites){
        super(world,Map.of());this.scope=Set.copyOf(scope);this.allowLegacy=allowLegacy;this.failAfterWrites=failAfterWrites;
        if(scope.isEmpty()||scope.size()>8192||failAfterWrites<0)throw new IllegalArgumentException("Static write scope budget");
    }
    @Override public boolean writeSilently(Cell cell,CellSnapshot next){
        if(!CompositeRuntime.writing()||!scope.contains(cell)||!isLoaded(cell)||next.resident()!=null||!next.guests().isEmpty())return false;
        var pos=CompositeData.pos(cell);var previous=world.getBlockState(pos);var target=CompositeData.state(next.data());
        boolean legacy=previous.getBlock() instanceof CompositeRootBlock||previous.isOf(CompositeArchitecture.CELL);
        boolean free=previous.isOf(Blocks.AIR)||previous.isOf(Blocks.CAVE_AIR)||previous.isOf(Blocks.VOID_AIR);
        if(!(previous.getBlock() instanceof StaticArchitectureBlock)&&!free&&!(allowLegacy&&legacy))return false;
        if(!CompositeData.nbt(next.data().blockEntityNbt()).isEmpty())return false;
        if(next.kind()==CellSnapshot.Kind.FOREIGN){
            if(!(target.getBlock() instanceof StaticArchitectureBlock))return false;
            world.setBlockState(pos,target,Block.FORCE_STATE|Block.SKIP_DROPS);
        }else if(next.kind()!=CellSnapshot.Kind.AIR||!target.isOf(Blocks.AIR))return false;
        boolean result=super.writeSilently(cell,next);
        return result&&++writes!=failAfterWrites;
    }
}
