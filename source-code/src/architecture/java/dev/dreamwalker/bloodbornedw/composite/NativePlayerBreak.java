package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;

/** Execute custom removals at vanilla's removeBlock step, after every normal
 * permission/BEFORE listener. Never mutate the world from BEFORE itself. */
public final class NativePlayerBreak {
    private record Before(BlockState state,BlockEntity entity){}
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    public static final class Scope {
        final Scope parent;final ServerWorld world;final PlayerEntity player;final BlockPos clicked;
        final Map<BlockPos,Before> extra=new LinkedHashMap<>();final Before target;boolean handled;
        Scope(ServerWorld world,PlayerEntity player,BlockPos pos){parent=CURRENT.get();this.world=world;this.player=player;clicked=pos.toImmutable();target=new Before(world.getBlockState(pos),world.getBlockEntity(pos));}
    }
    private NativePlayerBreak(){}
    public static Scope begin(ServerWorld world,PlayerEntity player,BlockPos pos){var scope=new Scope(world,player,pos);CURRENT.set(scope);return scope;}
    public static void end(Scope scope,boolean success){
        CURRENT.set(scope.parent);if(scope.parent==null)CURRENT.remove();
        if(success&&scope.handled)for(var e:scope.extra.entrySet())PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(scope.world,scope.player,e.getKey(),e.getValue().state,e.getValue().entity);
    }
    public static boolean handled(){return CURRENT.get()!=null&&CURRENT.get().handled;}
    public static final class Refused extends RuntimeException {public Refused(){super(null,null,false,false);}}
    public static void canceled(Scope scope){PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(scope.world,scope.player,scope.clicked,scope.target.state,scope.target.entity);}
    public static boolean allowCurrent(ServerWorld world,Collection<Cell> cells){var scope=CURRENT.get();return scope==null||scope.world!=world||allowCells(world,scope.player,cells);}
    public static boolean allowCells(ServerWorld world,PlayerEntity player,Collection<Cell> cells){return allow(world,player,cells.stream().map(CompositeData::pos).toList());}
    public static boolean allow(ServerWorld world,PlayerEntity player,Collection<BlockPos> cells){
        var scope=CURRENT.get();if(scope==null||scope.world!=world||scope.player!=player)return true;
        for(BlockPos pos:cells){
            if(!player.getAbilities().allowModifyWorld||!world.canPlayerModifyAt(player,pos))return false;
            if(pos.equals(scope.clicked)||scope.extra.containsKey(pos))continue;
            var before=new Before(world.getBlockState(pos),world.getBlockEntity(pos));
            if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,pos,before.state,before.entity)){
                PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(world,player,pos,before.state,before.entity);return false;
            }
            scope.extra.put(pos.toImmutable(),before);
        }
        return true;
    }
    /** null delegates an ordinary block to vanilla; false is a handled refusal. */
    public static Boolean remove(ServerWorld world,BlockPos pos){
        var scope=CURRENT.get();if(scope==null||scope.world!=world||!scope.clicked.equals(pos))return null;
        var player=scope.player;var state=world.getBlockState(pos);boolean drop=!player.isCreative()&&player.canHarvest(state);
        if(player.isCreative()&&dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.logged(world,pos)){scope.handled=true;return dev.dreamwalker.bloodbornedw.architecture.FauxWaterlogging.remove(world,pos,player);}
        if(state.getBlock() instanceof NativeGridBlock block&&NativeGridCatalog.membership(block.cell(state).index())!=null){scope.handled=true;return NativeGridRuntime.breakWhole(world,pos,block.cell(state).index(),player,drop);}
        if(state.getBlock() instanceof StaticArchitectureBlock){scope.handled=true;return StaticArchitectureRuntime.breakAt(world,pos,player,drop).outcome()==TransactionCore.Outcome.COMMITTED;}
        if(dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.marker(state)){scope.handled=true;return dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks.remove(world,pos,player);}
        var owner=CompositeRuntime.target(world,pos,player);
        if(owner==null&&NativeGridBridge.base(world,pos)>=0&&NativeGridBridge.backgroundHit(world,pos,player)){scope.handled=true;return NativeGridBridge.breakBackground(world,pos,player);}
        if(owner==null)owner=CompositeRuntime.soleNativeOwner(world,pos);
        if(owner!=null){scope.handled=true;return CompositeRuntime.remove(world,owner,player,drop).outcome()==TransactionCore.Outcome.COMMITTED;}
        if(state.getBlock() instanceof CompositeRootBlock||state.getBlock() instanceof CompositeCellBlock){scope.handled=true;return false;}
        return null;
    }
}
