package dev.dreamwalker.bloodbornedw.nativegridqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

/** Only integrations changed by the new physical representation: a real old
 * helper's base detach/rollback and a retained native whole item's lifecycle. */
public final class NativeVisualIntegrationChecks implements ModInitializer {
    private static final BlockPos C=new BlockPos(16,200,15),W=new BlockPos(40,200,40);
    private final JsonArray checks=new JsonArray();private final JsonObject report=new JsonObject();private Path out;private boolean armed,done;private int ticks;
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks.add(message);}
    private void save()throws Exception{Files.writeString(out.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}
    @Override public void onInitialize(){String path=System.getProperty("dw.nativevisual.integration.output");if(path==null)return;out=Path.of(path);
        ServerLifecycleEvents.SERVER_STARTED.register(server->{server.getOverworld().getChunk(C);for(int x=2;x<=3;x++)for(int z=2;z<=3;z++)server.getOverworld().getChunk(x,z);armed=true;});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(!armed||done||++ticks<20)return;try{run(server);report.addProperty("status","PASS");}catch(Throwable failure){failure.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",failure.toString());}finally{done=true;report.add("checks",checks);try{save();}catch(Exception failure){throw new RuntimeException(failure);}server.stop(false);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{if(!done)return;Thread exit=new Thread(()->{try{server.getThread().join(30000);if(server.getThread().isAlive())return;report.addProperty("normalServerThreadFinished",true);save();System.exit(report.get("status").getAsString().equals("PASS")?0:1);}catch(Exception failure){failure.printStackTrace();}},"NativeVisualIntegrationExit");exit.setDaemon(true);exit.start();});
    }
    private void run(MinecraftServer server)throws Exception{
        var world=server.getOverworld();check(world.getBlockState(C).isAir()&&world.getBlockEntity(C)==null&&CompositeRuntime.contributions(world,C).isEmpty(),"old-helper test cell is genuinely free");
        int visual=-1;for(int i=0;i<NativeGridCatalog.cells().size();i++)if(NativeVisualLayout.physics(i)>=0){try{NativeGridBridge.validate(i,CompositeArchitecture.CELL.getDefaultState());visual=i;break;}catch(IllegalArgumentException incompatible){}}
        check(visual>=0,"existing logical background is compatible with the real old helper");
        world.setBlockState(C,CompositeArchitecture.CELL.getDefaultState(),Block.NOTIFY_ALL);
        var helper=(CompositeBlockEntity)world.getBlockEntity(C);helper.nativeGridBase(visual);helper.markDirty();
        var at=CompositeData.cell(C);var adapter=new FabricCompositeWorld(world,Map.of());var before=adapter.read(at);adapter.prepareRollback(Map.of(at,before));
        var empty=new CellSnapshot(CellSnapshot.Kind.AIR,CompositeData.data(Blocks.AIR.getDefaultState(),new net.minecraft.nbt.NbtCompound()),null,List.of());
        check(adapter.writeSilently(at,empty),"terminal old-helper detach commits");
        check(world.getBlockEntity(C)==null&&world.getBlockState(C).equals(NativeGridCatalog.state(visual))&&NativeVisuals.required(world,C)==visual,"old helper exposes exact plain background with its visual index");
        var cold=new FabricCompositeWorld(world,Map.of()).read(at);check(cold.kind()==CellSnapshot.Kind.FOREIGN&&CompositeData.nbt(cold.data().blockEntityNbt()).getInt(NativeVisuals.ITEM_KEY)==visual,"fresh foreign snapshot preserves finite visual identity");
        check(adapter.restoreSilently(at,before)&&world.getBlockEntity(C) instanceof CompositeBlockEntity restored&&restored.nativeGridBase()==visual&&NativeVisuals.index(world,C)==-1,"rollback restores real helper data and removes the provisional plain index");
        check(adapter.read(at).equals(before),"old helper logical snapshot is restored exactly");adapter.releaseRollback();
        var detach=new FabricCompositeWorld(world,Map.of());var original=detach.read(at);detach.prepareRollback(Map.of(at,original));check(detach.writeSilently(at,empty),"repeat committed detach keeps the same background");detach.publishCommitted(List.of(at));detach.releaseRollback();
        check(world.getBlockEntity(C)==null&&NativeVisuals.required(world,C)==visual,"background remains after committed old-helper removal");
        world.setBlockState(C,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);check(NativeVisuals.index(world,C)==-1&&CompositeRuntime.contributions(world,C).isEmpty(),"helper fixture cleanup leaves no metadata or owners");

        NativeGridCatalog.WholeObject object=null;
        for(var candidate:NativeGridCatalog.wholeObjects())if(candidate.cells().size()<=32&&candidate.cells().stream().allMatch(member->Math.abs(member.offset().getX())<=3&&Math.abs(member.offset().getZ())<=3&&Math.abs(member.offset().getY())<=6)){object=candidate;break;}
        check(object!=null,"finite retained whole template fits the separate empty stand");
        for(var member:object.cells()){var p=W.add(member.offset());check(world.getBlockState(p).isAir()&&world.getBlockEntity(p)==null&&CompositeRuntime.contributions(world,p).isEmpty(),"whole stand cell is free at "+p);}
        var player=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"WholeVisualProbe")){
            @Override public boolean hasPermissionLevel(int level){return level<=4;}
            @Override public void sendMessage(Text text,boolean overlay){}
        };new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),player);player.changeGameMode(GameMode.SURVIVAL);player.setPosition(W.getX()+6,W.getY(),W.getZ()+6);
        var expected=NativeGridRuntime.item(object.baseCell());player.setStackInHand(Hand.MAIN_HAND,expected.copy());
        var hit=new BlockHitResult(Vec3d.ofCenter(W),Direction.UP,W,false);var context=new ItemPlacementContext(world,player,Hand.MAIN_HAND,player.getMainHandStack(),hit);
        check(((BlockItem)expected.getItem()).place(context).isAccepted(),"retained whole canonical item places through its original path");
        BlockPos selected=W;
        for(var member:object.cells()){var p=W.add(member.offset());check(world.getBlockState(p).equals(NativeGridCatalog.state(member.cell()))&&NativeVisuals.index(world,p)==-1,"whole role remains a real old state without a visual table entry");if(!member.offset().equals(BlockPos.ORIGIN))selected=p;}
        check(ItemStack.areEqual(world.getBlockState(selected).getBlock().getPickStack(world,selected,world.getBlockState(selected)),expected),"picking a whole helper returns the one canonical whole item");
        boolean drops=world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS);world.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,server);
        try{player.interactionManager.tryBreakBlock(selected);for(var member:object.cells())check(world.getBlockState(W.add(member.offset())).isAir(),"breaking one whole member removes the entire object");var items=world.getEntitiesByClass(ItemEntity.class,new Box(W).expand(10),entity->true);check(items.size()==1&&ItemStack.areEqual(items.get(0).getStack(),expected),"whole survival break drops exactly one unchanged canonical item");for(var item:items)item.discard();}
        finally{world.getGameRules().get(GameRules.DO_TILE_DROPS).set(drops,server);player.getTextStream().onDisconnect();}
        report.addProperty("wholeTemplate",object.key());report.addProperty("wholeCells",object.cells().size());server.save(false,true,true);report.addProperty("standRestored",true);
    }
}
