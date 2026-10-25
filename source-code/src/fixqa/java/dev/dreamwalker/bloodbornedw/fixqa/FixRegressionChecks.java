package dev.dreamwalker.bloodbornedw.fixqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.registry.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Runs on a disposable world, through actual game APIs and optional WorldEdit. */
public final class FixRegressionChecks implements ModInitializer {
    private static final BlockPos A=new BlockPos(4096,200,4096),B=A.add(2,0,0),C=A.add(4,0,0),W=A.add(16,0,0);
    private static final Item THROWING=new BlockItem(Blocks.STONE,new Item.Settings()){@Override protected BlockState getPlacementState(ItemPlacementContext c){throw new ProbeFailure();}};
    private static final class ProbeFailure extends RuntimeException {}
    private final JsonObject report=new JsonObject();private final JsonArray checks=new JsonArray();private Path out;
    private boolean watching,denyLate,armed;private BlockPos denied;private final Set<BlockPos> after=new HashSet<>();private int beforeFirst,beforeLast,canceled,ticks;
    private void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks.add(name);}
    @Override public void onInitialize(){
        String path=System.getProperty("dw.fix.qa.output");if(path==null)return;out=Path.of(path);
        Registry.register(Registries.ITEM,new Identifier("bloodborne_dw","qa_throwing_placement"),THROWING);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server,world)->{if(world.getRegistryKey()==World.OVERWORLD){world.setSpawnPos(new BlockPos(4096,80,4096),0);world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);}});
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,s,be)->{if(!watching)return true;beforeFirst++;return denyLate||!pos.equals(denied);});
        PlayerBlockBreakEvents.BEFORE.register((w,p,pos,s,be)->{if(!watching)return true;beforeLast++;return !denyLate||!pos.equals(denied);});
        PlayerBlockBreakEvents.AFTER.register((w,p,pos,s,be)->{if(watching)after.add(pos.toImmutable());});
        PlayerBlockBreakEvents.CANCELED.register((w,p,pos,s,be)->{if(watching)canceled++;});
        ServerLifecycleEvents.SERVER_STARTED.register(server->armed=true);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{if(armed&&++ticks>=20){armed=false;run(server);}});
    }
    private ServerPlayerEntity actor(MinecraftServer server,ServerWorld world){var actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"FixProbe")){
        @Override public boolean hasPermissionLevel(int level){return level<=4;}
        @Override public void sendMessage(Text text,boolean overlay){}
    };new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),actor);actor.changeGameMode(GameMode.SURVIVAL);actor.setPosition(A.getX()+8,A.getY(),A.getZ()+8);return actor;}
    private void run(MinecraftServer server){
        try{Files.createDirectories(out);var world=server.getOverworld();for(var p:List.of(A,B,C,W))world.getChunk(p);var actor=actor(server,world);
            check(ItemStack.fromNbt(null).isEmpty(),"nullable NBT keeps vanilla EMPTY contract without crash");
            var foreign=new NbtCompound();foreign.putString("id","minecraft:stone");foreign.putByte("Count",(byte)1);var original=foreign.copy();check(ItemStack.fromNbt(foreign).isOf(Items.STONE)&&foreign.equals(original),"foreign NBT is not rewritten");
            boolean drops=world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS);world.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,server);
            try{
                if(Boolean.getBoolean("dw.fix.qa.only.we")){for(var p:List.of(A,B,C)){check(world.getBlockState(p).isAir()||world.getBlockState(p).getBlock() instanceof NativePhysicsBlock,"only known QA palette cells reset");world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}}
                else{preview(world);noCollide(world,actor);reach(actor);wholeBreaks(world,actor);compositeBreak(world,actor);fallback(world,actor);}
                if(!Boolean.getBoolean("dw.fix.qa.skip.we"))new FixWorldEditChecks(out,this::check).run(world);else report.addProperty("worldEdit","SKIPPED_IN_THIS_PROFILE");
            }finally{world.getGameRules().get(GameRules.DO_TILE_DROPS).set(drops,server);}
            actor.getTextStream().onDisconnect();report.addProperty("status","PASS");
        }catch(Throwable failure){failure.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",failure.toString());}
        report.add("checks",checks);try{Files.writeString(out.resolve("server-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}
        if(System.getProperty("dw.fix.qa.server.stop")!=null)server.stop(false);
    }
    private void preview(ServerWorld world){
        var state=NativeGridCatalog.state(14);var view=new BlockView(){public BlockEntity getBlockEntity(BlockPos p){return null;}public BlockState getBlockState(BlockPos p){return state;}public net.minecraft.fluid.FluidState getFluidState(BlockPos p){return state.getFluidState();}public int getHeight(){return 384;}public int getBottomY(){return -64;}};
        var stack=state.getBlock().getPickStack(view,A,state);check(!stack.isEmpty()&&stack.getNbt().contains(NativeVisuals.ITEM_KEY,3),"foreign preview BlockView pick is safe and explicit");
    }
    private void noCollide(ServerWorld world,ServerPlayerEntity actor){
        actor.setStackInHand(Hand.MAIN_HAND,new ItemStack(THROWING));check(NoCollidePlacement.arm(actor),"test actual held-stack no-collide lease");
        var context=new ItemPlacementContext(world,actor,Hand.MAIN_HAND,actor.getMainHandStack(),new BlockHitResult(Vec3d.ofCenter(A),Direction.UP,A,false));
        boolean caught=false;try{((BlockItem)THROWING).place(context);}catch(ProbeFailure expected){caught=true;}
        check(caught&&!NoCollidePlacement.ignoringBlocks(),"placement exception unwinds global no-collide depth");NoCollidePlacement.invalidate(actor);actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
    }
    private void reach(ServerPlayerEntity actor){
        if(!FabricLoader.getInstance().isModLoaded("reach-entity-attributes"))throw new AssertionError("ReachEntityAttributes is required for this regression profile");
        var attack=actor.getAttributeInstance(Registries.ATTRIBUTE.get(new Identifier("reach-entity-attributes","attack_range")));var block=actor.getAttributeInstance(Registries.ATTRIBUTE.get(new Identifier("reach-entity-attributes","reach")));
        check(attack!=null&&block!=null,"effective reach attributes are available");double a=attack.getBaseValue(),b=block.getBaseValue();
        try{attack.setBaseValue(5);block.setBaseValue(5);check(InteractionReach.attack(actor)>=8&&InteractionReach.block(actor)>=9.5,"server selection includes increased attack and block reach");}finally{attack.setBaseValue(a);block.setBaseValue(b);}
    }
    private void wholeBreaks(ServerWorld world,ServerPlayerEntity actor){
        var object=NativeGridCatalog.cells().stream().map(c->NativeGridCatalog.membership(c.index())).filter(Objects::nonNull).map(NativeGridCatalog.Membership::object).filter(o->o.cells().size()>1&&o.cells().size()<=12).findFirst().orElseThrow();
        var neighbor=object.cells().stream().filter(m->!m.offset().equals(BlockPos.ORIGIN)).findFirst().orElseThrow();
        for(boolean late:List.of(false,true)){
            for(var member:object.cells()){var p=W.add(member.offset());check(world.getBlockState(p).isAir(),"whole test stand initially clear");world.setBlockState(p,NativeGridCatalog.state(member.cell()),Block.FORCE_STATE|Block.SKIP_DROPS);}
            denied=W.add(neighbor.offset());denyLate=late;watching=true;after.clear();canceled=beforeFirst=beforeLast=0;
            boolean result;try{result=actor.interactionManager.tryBreakBlock(W);}finally{watching=false;}
            check(!result&&after.isEmpty()&&canceled>0,"foreign BEFORE denial "+(late?"late":"early")+" cancels without AFTER");
            for(var member:object.cells())check(world.getBlockState(W.add(member.offset())).equals(NativeGridCatalog.state(member.cell())),"denied whole footprint unchanged");
            denied=null;watching=true;after.clear();beforeFirst=beforeLast=0;
            try{check(actor.interactionManager.tryBreakBlock(W),"allowed whole break succeeds");}finally{watching=false;}
            check(beforeFirst>0&&beforeLast>0&&after.size()==object.cells().size(),"all protection listeners and one AFTER per removed cell");
            for(var member:object.cells())check(world.getBlockState(W.add(member.offset())).isAir(),"allowed whole footprint removed atomically");
            var drops=world.getEntitiesByClass(ItemEntity.class,new Box(W).expand(12),e->true);check(drops.size()==1&&ItemStack.areEqual(drops.get(0).getStack(),NativeGridRuntime.item(object.baseCell())),"one canonical survival drop, without duplicate vanilla drop");drops.forEach(ItemEntity::discard);
        }
    }
    private void fallback(ServerWorld world,ServerPlayerEntity actor){
        var state=Registries.BLOCK.get(new Identifier("bloodborne_dw","source_fallback_carrier")).getDefaultState().with(SourceFallbackCarrierV1271.SOURCE,113);var picked=state.getBlock().getPickStack(world,A,state);
        check(!picked.isEmpty()&&state.equals(SourceFallbackCarrierV1271.itemState(picked)),"fallback pick preserves exact source, bank and profile");
        actor.setStackInHand(Hand.MAIN_HAND,picked);var context=new ItemPlacementContext(world,actor,Hand.MAIN_HAND,picked,new BlockHitResult(Vec3d.ofCenter(C),Direction.UP,C,false));var pos=context.getBlockPos();check(((BlockItem)picked.getItem()).place(context).isAccepted()&&world.getBlockState(pos).equals(state),"picked fallback decoration places its exact visible state");world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.FORCE_STATE|Block.SKIP_DROPS);actor.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
    }
    private void compositeBreak(ServerWorld world,ServerPlayerEntity actor){
        var root=W.add(32,0,0);var state=CompositeArchitecture.kindBlock("prototype_double_door").getDefaultState();var object=CompositeRuntime.instance(world,root,state,UUID.randomUUID(),new NbtCompound());
        for(var offset:object.cells().keySet())world.getChunk((root.getX()+offset.x())>>4,(root.getZ()+offset.z())>>4);
        var access=new FabricCompositeWorld(world,Map.of(object.owner(),object));var placement=CompositeRuntime.core().placement(access,object,List.of());check(CompositeRuntime.execute(access,placement).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"V12 composite test door placed through original transaction");
        var snapshots=new LinkedHashMap<dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell,dev.dreamwalker.bloodbornedw.runtime.CellSnapshot>();for(var offset:object.cells().keySet()){var c=object.owner().root().add(offset);snapshots.put(c,new FabricCompositeWorld(world,Map.of()).read(c));}
        denied=snapshots.keySet().stream().map(CompositeData::pos).filter(p->!p.equals(root)).findFirst().orElseThrow();denyLate=true;watching=true;after.clear();canceled=0;
        try{check(!actor.interactionManager.tryBreakBlock(root),"V12 composite break honors foreign footprint denial");}finally{watching=false;}
        check(after.isEmpty()&&canceled>0,"denied V12 break emits no successful AFTER");for(var entry:snapshots.entrySet())check(entry.getValue().equals(new FabricCompositeWorld(world,Map.of()).read(entry.getKey())),"V12 owner and service data unchanged after denial");
        var expected=CompositeRuntime.pick(world,object.owner());denied=null;watching=true;after.clear();
        try{check(actor.interactionManager.tryBreakBlock(root),"allowed V12 composite break succeeds");}finally{watching=false;}
        check(after.size()==snapshots.size(),"V12 composite successful AFTER covers its complete footprint");for(var c:snapshots.keySet())check(world.getBlockState(CompositeData.pos(c)).isAir()&&CompositeRuntime.contributions(world,CompositeData.pos(c)).isEmpty(),"V12 complete removal preserves no orphan service parts");
        var drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(12),e->true);check(drops.size()==1&&ItemStack.areEqual(drops.get(0).getStack(),expected),"V12 composite has one original item drop");drops.forEach(ItemEntity::discard);
    }
}
