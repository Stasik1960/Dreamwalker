package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;

/** Bounded native-state operations for the finite static catalog. A component
 * is located by a shared pose and relative root, with no saved identity.
 * All partial overlap removal states are finite, shared catalog recipes. */
public final class StaticArchitectureRuntime {
    public record Component(BlockPos root,StaticArchitectureCatalog.Pose pose){public Component{root=root.toImmutable();}}
    // A bounded ordinary lifecycle work queue, not saved ownership or geometry.
    // Loaded native palettes reconstruct deferred work after save/restart.
    private static final Map<ServerWorld,LinkedHashMap<Component,Boolean>> CLEANUP=new WeakHashMap<>();
    private static boolean initialized;
    private StaticArchitectureRuntime(){}
    public static void initialize(){
        if(initialized)return;initialized=true;
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(!(state.getBlock() instanceof StaticArchitectureBlock))return true;
            if(world instanceof ServerWorld server)breakAt(server,pos,player);
            return false;
        });
        ServerTickEvents.END_WORLD_TICK.register(StaticArchitectureRuntime::drain);
        ServerChunkEvents.CHUNK_LOAD.register(StaticArchitectureRuntime::loadedChunk);
    }
    public static Component target(ServerWorld world,BlockPos pos,PlayerEntity player){
        WorldChunk loaded=world.getChunkManager().getWorldChunk(pos.getX()>>4,pos.getZ()>>4);if(loaded==null)return null;
        BlockState state=loaded.getBlockState(pos);if(!StaticArchitectureCatalog.contains(state))return null;
        var recipe=StaticArchitectureCatalog.recipe(state);Component best=null;double distance=Double.POSITIVE_INFINITY;
        Vec3d start=player==null?null:player.getEyePos(),end=player==null?null:start.add(player.getRotationVec(1).multiply(8));
        for(int i=0;i<recipe.fragments().size();i++){
            var part=recipe.fragments().get(i);Cell origin=part.origin();BlockPos root=pos.add(origin.x(),origin.y(),origin.z());
            var boxes=part.pose().outline().getOrDefault(new Cell(-origin.x(),-origin.y(),-origin.z()),List.of());
            var hit=player==null?null:ImportedPhysicalShapes.exact(boxes).raycast(start,end,pos);
            double next=player==null?i:hit==null?Double.POSITIVE_INFINITY:hit.getPos().squaredDistanceTo(start);
            if(next<distance){distance=next;best=new Component(root,part.pose());}
        }
        return best;
    }
    public static TransactionCore.Result breakAt(ServerWorld world,BlockPos pos,PlayerEntity player){Component component=target(world,pos,player);return component==null?rejected("no_static_part_at_hit"):remove(world,component.root,component.pose);}
    public static TransactionCore.Result remove(ServerWorld world,BlockPos root,StaticArchitectureCatalog.Pose pose){return remove(world,root,pose,false);}
    private static TransactionCore.Result remove(ServerWorld world,BlockPos root,StaticArchitectureCatalog.Pose pose,boolean cleanup){
        if(!world.getServer().isOnThread())throw new IllegalStateException("Static mutation requires server thread");
        TreeSet<Cell> touched=new TreeSet<>();Cell rootCell=CompositeData.cell(root);for(Cell relative:pose.footprint().keySet())touched.add(rootCell.add(relative));
        if(touched.size()>4096)return rejected("static_component_cell_budget");
        for(Cell cell:touched)if(world.getChunkManager().getWorldChunk(cell.x()>>4,cell.z()>>4)==null)return rejected("static_component_chunk_unloaded:"+cell);
        StaticArchitectureWorldAccess access=new StaticArchitectureWorldAccess(world,touched,false,0);
        TreeMap<Cell,CellSnapshot> expected=new TreeMap<>(),writes=new TreeMap<>();
        try{
            if(!cleanup&&!StaticArchitectureCatalog.matchesRoot(world.getBlockState(root),pose))return rejected("static_root_missing");
            for(Cell cell:touched){
                CellSnapshot before=access.read(cell);expected.put(cell,before);BlockState state=CompositeData.state(before.data());
                // A command's new block/inventory is never overwritten by
                // cleanup of the component it replaced.
                if(!StaticArchitectureCatalog.contains(state)){if(cleanup)continue;return rejected("static_component_cell_replaced:"+cell);}
                if(before.resident()!=null||!before.guests().isEmpty()||world.getBlockEntity(CompositeData.pos(cell))!=null)return rejected("static_part_has_foreign_data:"+cell);
                var recipe=StaticArchitectureCatalog.recipe(state);int member=-1;
                for(int i=0;i<recipe.fragments().size();i++){var part=recipe.fragments().get(i);if(part.pose()==pose&&cell.add(part.origin()).equals(rootCell)){member=i;break;}}
                if(member<0){if(cleanup)continue;return rejected("static_component_recipe_mismatch:"+cell);}
                BlockState next=StaticArchitectureCatalog.without(state,member);
                writes.put(cell,new CellSnapshot(next.isAir()?CellSnapshot.Kind.AIR:CellSnapshot.Kind.FOREIGN,CompositeData.data(next,new NbtCompound()),null,List.of()));
            }
            if(writes.isEmpty())return new TransactionCore.Result(TransactionCore.Outcome.COMMITTED,0,"static_already_removed");
            return CompositeRuntime.execute(access,new TransactionCore.Preparation(new TransactionCore.Plan(access.dimensionKey(),expected,writes,Map.of()),null));
        }catch(RuntimeException failure){return rejected("static_prepare_failed:"+failure.getMessage());}
    }
    private static TransactionCore.Result rejected(String reason){return new TransactionCore.Result(TransactionCore.Outcome.REJECTED,0,reason);}
    public static void replaced(ServerWorld world,BlockPos cell,BlockState previous){
        if(CompositeRuntime.writing()||!StaticArchitectureCatalog.contains(previous))return;
        for(var part:StaticArchitectureCatalog.recipe(previous).fragments()){Cell origin=part.origin();queue(world,new Component(cell.add(origin.x(),origin.y(),origin.z()),part.pose()),true);}
    }
    private static void queue(ServerWorld world,Component component,boolean forced){var work=CLEANUP.computeIfAbsent(world,ignored->new LinkedHashMap<>());if(work.size()<4096||work.containsKey(component))work.merge(component,forced,(a,b)->a||b);}
    private static void drain(ServerWorld world){
        var work=CLEANUP.get(world);if(work==null)return;int count=0;
        for(var entry:List.copyOf(work.entrySet())){
            if(count>=64)break;Component component=entry.getKey();
            if(!entry.getValue()){
                WorldChunk rootChunk=world.getChunkManager().getWorldChunk(component.root.getX()>>4,component.root.getZ()>>4);if(rootChunk==null)continue;
                if(StaticArchitectureCatalog.matchesRoot(rootChunk.getBlockState(component.root),component.pose)){work.remove(component);continue;}
            }
            count++;var result=remove(world,component.root,component.pose,true);
            if(result.outcome()==TransactionCore.Outcome.COMMITTED)work.remove(component);
        }
        if(work.isEmpty())CLEANUP.remove(world);
    }
    private static void loadedChunk(ServerWorld world,WorldChunk chunk){
        // Native chunk-load lifecycle repairs command/crash-created orphans.
        // Only actual loaded sections with these new blocks are scanned;
        // no chunks are requested and no position registry is saved.
        var sections=chunk.getSectionArray();
        for(int index=0;index<sections.length;index++){
            var section=sections[index];if(section==null||!section.hasAny(StaticArchitectureCatalog::contains))continue;
            int baseY=(world.getBottomSectionCoord()+index)<<4;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                var state=section.getBlockState(x,y,z);if(!StaticArchitectureCatalog.contains(state))continue;
                BlockPos cell=new BlockPos(chunk.getPos().getStartX()+x,baseY+y,chunk.getPos().getStartZ()+z);
                for(var part:StaticArchitectureCatalog.recipe(state).fragments()){
                    Cell relative=part.origin();BlockPos root=cell.add(relative.x(),relative.y(),relative.z());
                    // CHUNK_LOAD runs before this chunk's FULL future is
                    // published. A world lookup here can wait on that same
                    // future. Read its supplied native palette directly and
                    // inspect other chunks only through the nonblocking API.
                    WorldChunk rootChunk=(root.getX()>>4)==chunk.getPos().x&&(root.getZ()>>4)==chunk.getPos().z?chunk:world.getChunkManager().getWorldChunk(root.getX()>>4,root.getZ()>>4);
                    if(rootChunk==null||!StaticArchitectureCatalog.matchesRoot(rootChunk.getBlockState(root),part.pose()))queue(world,new Component(root,part.pose()),false);
                }
            }
        }
    }
}
