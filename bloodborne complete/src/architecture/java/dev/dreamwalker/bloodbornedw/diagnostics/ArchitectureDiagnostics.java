package dev.dreamwalker.bloodbornedw.diagnostics;

import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Recording adapters keep ordinary placement checks and world transactions authoritative. */
public final class ArchitectureDiagnostics {
    private static final AtomicLong SAMPLES=new AtomicLong();
    private static final class Placement {final Map<String,Object> fields;String reason="native_placement_rejected; precise_stage_unavailable";Placement(Map<String,Object> fields){this.fields=fields;}}
    private static final ThreadLocal<Placement> ITEM=new ThreadLocal<>();
    private ArchitectureDiagnostics(){}
    public static boolean enabled(World world){return world instanceof ServerWorld server&&DwDiagnostics.enabled(server);}
    public static String type(BlockState state){var entry=DebugCatalogue.entry(state);return entry==null?"UNASSIGNED":entry.temporaryId();}
    public static String type(ItemStack stack){var entry=DebugCatalogue.entry(stack);return entry==null?"UNASSIGNED":entry.temporaryId();}
    public static long begin(World world,boolean sample){return enabled(world)&&(!sample||(SAMPLES.getAndIncrement()&31)==0)?System.nanoTime():0;}
    public static void finish(World world,BlockState state,BlockPos pos,String section,long started){
        finish(world,state,"",pos,section,started);
    }
    public static void finish(World world,BlockState state,String instance,BlockPos pos,String section,long started){
        if(started!=0&&world instanceof ServerWorld server)DwDiagnostics.measured(server,state==null?"UNASSIGNED":type(state),instance,pos,section,System.nanoTime()-started);
    }
    public static String rootId(BlockPos pos){return "root:"+pos.getX()+","+pos.getY()+","+pos.getZ();}
    public static Map<String,Object> state(World world,BlockPos root){
        if(!world.isChunkLoaded(root))return Map.of("loaded",false);
        return state(world.getBlockState(root));
    }
    public static Map<String,Object> state(BlockState state){
        Map<String,Object> result=new LinkedHashMap<>();result.put("registry",Registries.BLOCK.getId(state.getBlock()).toString());
        result.put("typeId",type(state));Map<String,Object> properties=new TreeMap<>();
        state.getEntries().forEach((key,value)->properties.put(key.getName(),value.toString()));result.put("properties",properties);return result;
    }
    public static void refusal(String reason){Placement placement=ITEM.get();if(placement!=null)placement.reason=reason;}
    /** Capture the actual hand/context once, including explicit legacy-item redirection. */
    public static <T> T withItem(ItemPlacementContext context,java.util.function.Supplier<T> operation){
        if(!enabled(context.getWorld())||ITEM.get()!=null)return operation.get();
        ItemStack stack=context.getStack();Map<String,Object> fields=new LinkedHashMap<>();fields.put("heldItem",Registries.ITEM.getId(stack.getItem()).toString());
        fields.put("heldTypeId",type(stack));fields.put("hand",context.getHand().name());fields.put("clickedFace",context.getSide().asString());
        fields.put("placementOrigin","ordinary_item_context");ITEM.set(new Placement(fields));try{return operation.get();}finally{ITEM.remove();}
    }
    public static ActionResult nativePlacement(ItemPlacementContext context,java.util.function.Supplier<ActionResult> operation){
        if(!enabled(context.getWorld())||ITEM.get()!=null)return operation.get();
        return withItem(context,()->{World world=context.getWorld();BlockPos root=context.getBlockPos();Map<String,Object> before=state(world,root);long started=begin(world,false);
            try{ActionResult result=operation.get();BlockState identity=result.isAccepted()?world.getBlockState(root):(context.getStack().getItem() instanceof net.minecraft.item.BlockItem item?item.getBlock().getDefaultState():net.minecraft.block.Blocks.AIR.getDefaultState());
                event(world,identity,rootId(root),root,"place",before,state(world,root),result.isAccepted()?"COMMITTED":"REJECTED",result.isAccepted()?"":ITEM.get().reason,context.getPlayer());return result;
            }finally{finish(world,world.isChunkLoaded(root)?world.getBlockState(root):null,root,"architecture.native_placement",started);}});
    }
    public static void event(World world,BlockState typeState,String instance,BlockPos root,String action,Map<String,Object> before,Map<String,Object> after,String result,String reason,PlayerEntity player){
        if(!(world instanceof ServerWorld server)||!DwDiagnostics.enabled(server))return;
        Map<String,Object> previous=new LinkedHashMap<>(before);
        Placement placement=ITEM.get();if(placement!=null)previous.putAll(placement.fields);
        else if(player!=null){previous.put("actorCurrentMainhand",Registries.ITEM.getId(player.getMainHandStack().getItem()).toString());previous.put("placementOrigin","no_item_context; source_or_programmatic_operation");}
        DwDiagnostics.record(server,type(typeState),instance,root,action,previous,after,result,reason==null?"":reason);
    }
    public static void result(ServerWorld world,BlockState typeState,String instance,BlockPos root,String action,Map<String,Object> before,TransactionCore.Result result,PlayerEntity player){
        if(result.outcome()==TransactionCore.Outcome.ROLLED_BACK||result.outcome()==TransactionCore.Outcome.ROLLBACK_FAILED)
            DwDiagnostics.error(world,type(typeState),instance,root,"TRANSACTION_"+result.outcome(),result.reason(),null);
        if(DwDiagnostics.enabled(world))event(world,typeState,instance,root,action,before,state(world,root),result.outcome().name(),result.reason(),player);
    }
}
