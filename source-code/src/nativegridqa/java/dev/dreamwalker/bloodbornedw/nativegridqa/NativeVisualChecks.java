package dev.dreamwalker.bloodbornedw.nativegridqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.registry.Registries;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Finite tests in a disposable converted world. No benchmark or visual edits
 * to existing city objects. A second real VM checks the saved stand on reopen. */
public final class NativeVisualChecks implements ModInitializer {
    private final JsonArray checks=new JsonArray();private JsonObject report=new JsonObject();private Path output,statePath;private String phase;private boolean done,armed;private int ticks;
    private static final BlockPos A=new BlockPos(15,200,15),B=new BlockPos(16,200,15);
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks.add(message);}
    private static boolean same(VoxelShape a,VoxelShape b){return !VoxelShapes.matchesAnywhere(a,b,BooleanBiFunction.NOT_SAME);}
    private void save()throws Exception{Files.writeString(output.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}
    @Override public void onInitialize(){
        String value=System.getProperty("dw.nativevisualqa.output");if(value==null)return;
        output=Path.of(value);statePath=Path.of(System.getProperty("dw.nativevisualqa.state"));phase=System.getProperty("dw.nativevisualqa.phase");
        ServerLifecycleEvents.SERVER_STARTED.register(server->{server.getOverworld().getChunk(A);server.getOverworld().getChunk(B);armed=true;});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(!armed||done||++ticks<20)return;try{run(server);report.addProperty("status","PASS");}catch(Throwable failure){failure.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",failure.toString());}finally{done=true;report.add("checks",checks);report.addProperty("phase",phase);try{save();}catch(Exception failure){throw new RuntimeException(failure);}server.stop(false);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{if(!done)return;Thread exit=new Thread(()->{try{server.getThread().join(30000);if(server.getThread().isAlive())return;report.addProperty("normalServerThreadFinished",true);save();System.exit(report.get("status").getAsString().equals("PASS")?0:1);}catch(Exception failure){failure.printStackTrace();}},"NativeVisualQaExit");exit.setDaemon(true);exit.start();});
    }
    private ServerPlayerEntity actor(MinecraftServer server,ServerWorld world){var actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"VisualProbe")){
        @Override public boolean hasPermissionLevel(int level){return level<=4;}
        @Override public void sendMessage(Text text,boolean overlay){}
    };new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),actor);actor.changeGameMode(GameMode.SURVIVAL);actor.setPosition(A.getX()+5,A.getY(),A.getZ()+5);return actor;}
    private List<ItemEntity> drops(ServerWorld world,BlockPos p){return world.getEntitiesByClass(ItemEntity.class,new Box(p).expand(3),entity->true);}
    private void run(MinecraftServer server)throws Exception{
        ServerWorld world=server.getOverworld();world.getChunk(A);world.getChunk(B);
        if(phase.equals("reopen")){
            JsonObject saved=JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();int visual=saved.get("visual").getAsInt();
            check(world.getBlockState(A).equals(NativeGridCatalog.state(visual))&&world.getBlockState(B).equals(NativeGridCatalog.state(visual)),"physical states survive real VM restart on both sides of chunk boundary");
            check(NativeVisuals.required(world,A)==visual&&NativeVisuals.required(world,B)==visual,"chunk visual indices survive save and cold reopen");
            check(ItemStack.areEqual(world.getBlockState(A).getBlock().getPickStack(world,A,world.getBlockState(A)),NativePhysicsBlock.item(visual)),"reopened pick item keeps exact visual variant");
            for(BlockPos p:List.of(A,B,A.down()))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            check(NativeVisuals.index(world,A)==-1&&NativeVisuals.index(world,B)==-1,"removal clears both chunk tables");
            check(NativeVisuals.data(world.getChunk(A)).cells()==saved.get("beforeA").getAsInt()&&NativeVisuals.data(world.getChunk(B)).cells()==saved.get("beforeB").getAsInt(),"test stand cleanup restores original visual table counts");
            server.save(false,true,true);report.addProperty("standRestored",true);return;
        }
        boolean resumed=phase.equals("resume-create");check(phase.equals("create")||resumed,"explicit finite test phase");
        for(BlockPos p:List.of(A,B,A.down()))check(world.getBlockState(p).isAir()&&world.getBlockEntity(p)==null&&CompositeRuntime.contributions(world,p).isEmpty(),"test stand is empty at "+p);
        int beforeA=NativeVisuals.data(world.getChunk(A)).cells(),beforeB=NativeVisuals.data(world.getChunk(B)).cells();
        int shapes=0;for(int visual=0;!resumed&&visual<NativeGridCatalog.cells().size();visual++){
            var cell=NativeGridCatalog.cell(visual);var s=NativeGridCatalog.state(visual);
            checkFast(same(s.getCollisionShape(world,A),cell.collision()),"collision",visual);
            checkFast(same(s.getOutlineShape(world,A),cell.outline())&&same(s.getCullingShape(world,A),cell.culling()),"outline/culling",visual);
            checkFast(s.getOpacity(world,A)==cell.optics().opacity()&&s.getLuminance()==cell.optics().luminance()&&s.isOpaque()==cell.optics().opaque()&&s.getFluidState().equals(cell.fluid()),"optics/fluid",visual);shapes++;
            if(NativeGridCatalog.membership(visual)!=null)checkFast(Registries.BLOCK.getId(s.getBlock()).toString().equals("bloodborne_dw:native_grid_"+(visual/128))&&s.getEntries().entrySet().stream().anyMatch(e->e.getKey().getName().equals("part")&&e.getValue().toString().equals(Integer.toString(cell.index()%128))),"whole ID/role",visual);
        }
        report.addProperty("exactCellPhysicsCheckedThisRun",shapes);if(resumed)report.addProperty("inheritedExactPhysicsReport",System.getProperty("dw.nativevisualqa.inheritedPhysics"));else {report.addProperty("exactCellPhysicsChecked",shapes);checks.add("all logical visual types resolve to exact shapes, optics and fluids; whole IDs and roles unchanged");}
        int first=-1,second=-1;Map<Integer,Integer> seen=new HashMap<>();
        for(int visual=0;visual<NativeGridCatalog.cells().size();visual++){int physical=NativeVisualLayout.physics(visual);if(physical<0||!NativeGridCatalog.cell(visual).fluid().isEmpty())continue;Integer previous=seen.putIfAbsent(physical,visual);if(previous!=null&&NativeGridCatalog.cell(previous).mesh()!=NativeGridCatalog.cell(visual).mesh()){first=previous;second=visual;break;}}
        check(first>=0&&second>=0,"two different visual meshes share one exact physical state");
        check(NativeVisuals.write(world,A,first,Block.NOTIFY_ALL),"place first visual");var physical=world.getBlockState(A);var immutable=NativeVisuals.data(world.getChunk(A));
        check(NativeVisuals.write(world,A,second,Block.NOTIFY_ALL)&&world.getBlockState(A).equals(physical)&&NativeVisuals.required(world,A)==second,"same-state visual replacement updates metadata");
        check(immutable.at(A)==first,"render snapshot remains immutable after same-state update");
        ItemStack expected=NativePhysicsBlock.item(second);check(ItemStack.areEqual(physical.getBlock().getPickStack(world,A,physical),expected),"pick returns exact finite visual item");
        NbtCompound old=new NbtCompound();old.putString("id","bloodborne_dw:native_grid_"+(second/128));old.putByte("Count",(byte)1);var tag=new NbtCompound();var stateTag=new NbtCompound();stateTag.putString("part",Integer.toString(second%128));tag.put("BlockStateTag",stateTag);old.put("tag",tag);var untouched=old.copy();
        check(ItemStack.areEqual(ItemStack.fromNbt(old),expected)&&old.equals(untouched),"old ordinary item migrates on read without mutating saved caller NBT");
        var player=actor(server,world);boolean tileDrops=world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS);world.getGameRules().get(GameRules.DO_TILE_DROPS).set(true,server);
        try{
            check(player.interactionManager.tryBreakBlock(A)&&world.getBlockState(A).isAir(),"real survival player break removes physical state");
            var items=drops(world,A);report.addProperty("observedDropCount",items.size());check(items.size()==1&&ItemStack.areEqual(items.get(0).getStack(),expected),"afterBreak drops exact former visual despite cleared chunk entry");for(var entity:items)entity.discard();
        }finally{world.getGameRules().get(GameRules.DO_TILE_DROPS).set(tileDrops,server);}
        check(world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)==tileDrops,"original map drop rule restored after isolated drop test");
        world.setBlockState(A.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);player.setStackInHand(Hand.MAIN_HAND,expected.copy());
        var hit=new BlockHitResult(Vec3d.ofCenter(A.down()).add(0,.5,0),Direction.UP,A.down(),false);var context=new ItemPlacementContext(world,player,Hand.MAIN_HAND,player.getMainHandStack(),hit);
        check(((BlockItem)expected.getItem()).place(context).isAccepted()&&NativeVisuals.required(world,A)==second&&world.getBlockState(A).equals(physical),"ordinary dropped item reinstalls exact visual and physics");
        check(NativeVisuals.write(world,B,second,Block.NOTIFY_ALL),"second chunk stores visual independently");
        var chunk=world.getChunk(A);var packet=new ChunkData((net.minecraft.world.chunk.WorldChunk)chunk);var delivered=NativeVisualData.read(packet.getHeightmap().getCompound(NativeVisualData.KEY),chunk);check(delivered.at(A)==second,"vanilla initial chunk packet contains matching visual table");
        JsonObject saved=new JsonObject();saved.addProperty("visual",second);saved.addProperty("first",first);saved.addProperty("beforeA",beforeA);saved.addProperty("beforeB",beforeB);Files.writeString(statePath,saved.toString());
        player.getTextStream().onDisconnect();server.save(false,true,true);report.addProperty("standSavedForReopen",true);report.addProperty("visual",second);
    }
    private static void checkFast(boolean condition,String what,int visual){if(!condition)throw new AssertionError(what+" differs for visual "+visual);}
}
