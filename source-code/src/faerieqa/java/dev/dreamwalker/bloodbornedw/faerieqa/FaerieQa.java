package dev.dreamwalker.bloodbornedw.faerieqa;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class FaerieQa implements ModInitializer {
    public static final BlockPos BASE=new BlockPos(12288,220,12288);
    public static final int WIDTH=32,HEIGHT=16;
    private final JsonArray checks=new JsonArray();private Path out;private boolean started,done;private int age;
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
    @Override public void onInitialize(){String dir=System.getProperty("dw.faerie.qa.output");if(dir==null)return;out=Path.of(dir);ServerWorldEvents.LOAD.register((s,w)->{if(w.getRegistryKey()==World.OVERWORLD){w.setSpawnPos(BASE.add(16,4,12),180);w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);}});ServerLifecycleEvents.SERVER_STARTED.register(s->started=true);ServerTickEvents.END_SERVER_TICK.register(s->{if(!started||done||++age<20)return;done=true;JsonObject report=new JsonObject();try{run(s);report.addProperty("status","PASS");}catch(Throwable t){t.printStackTrace();report.addProperty("status","FAIL");report.addProperty("error",t.toString());}report.add("checks",checks);try{Files.writeString(out.resolve("server-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}if(Boolean.getBoolean("dw.faerie.qa.stop"))s.stop(false);});}
    private ServerPlayerEntity actor(MinecraftServer s,ServerWorld w){var p=new ServerPlayerEntity(s,w,new GameProfile(UUID.randomUUID(),"VeilProbe")){@Override public void sendMessage(Text text,boolean action){}@Override public boolean hasPermissionLevel(int level){return true;}};new ServerPlayNetworkHandler(s,new ClientConnection(NetworkSide.SERVERBOUND),p);p.changeGameMode(GameMode.CREATIVE);p.setPosition(BASE.getX()-3,BASE.getY(),BASE.getZ()+8);return p;}
    private ActionResult place(ServerPlayerEntity p,Block block,BlockPos target){var stack=new ItemStack(block);p.getInventory().setStack(p.getInventory().selectedSlot,stack);var hit=new BlockHitResult(Vec3d.ofBottomCenter(target),Direction.UP,target.down(),false);return ((BlockItem)stack.getItem()).place(new ItemPlacementContext(new ItemUsageContext(p,Hand.MAIN_HAND,hit)));}
    private void run(MinecraftServer server){var w=server.getOverworld();w.getChunk(BASE);var p=actor(server,w);var pos=BASE.add(-2,0,3);
        if(Boolean.getBoolean("dw.faerie.qa.verify")){for(int x=0;x<WIDTH;x++)for(int y=0;y<HEIGHT;y++){var cell=BASE.add(x,y,0);w.getChunk(cell);check(w.getBlockState(cell).isOf(FaerieVeilBlock.BLOCK)&&w.getBlockEntity(cell)==null,"saved wall no block entity "+x+","+y);}return;}
        for(int x=-8;x<=44;x++)for(int z=-8;z<=20;z++)w.setBlockState(BASE.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        check(place(p,FaerieVeilBlock.BLOCK,pos).isAccepted(),"veil placed on actual aimed surface");check(FaerieVeilBlock.BLOCK.getStateManager().getStates().size()==1,"one state for entire animated composition");check(!w.getBlockState(pos).hasBlockEntity()&&w.getBlockEntity(pos)==null,"no block entity");
        for(var mode:List.of(GameMode.CREATIVE,GameMode.SURVIVAL,GameMode.ADVENTURE)){p.changeGameMode(mode);var state=w.getBlockState(pos);check(state.getOutlineShape(w,pos,ShapeContext.of(p)).isEmpty()==(mode!=GameMode.CREATIVE),"targeting "+mode);check(state.getCollisionShape(w,pos,ShapeContext.of(p)).isEmpty(),"player passes through "+mode);if(mode!=GameMode.CREATIVE)check(!p.interactionManager.tryBreakBlock(pos)&&w.getBlockState(pos).isOf(FaerieVeilBlock.BLOCK),"noncreative server break denied "+mode);}
        p.changeGameMode(GameMode.CREATIVE);check(p.interactionManager.tryBreakBlock(pos)&&w.getBlockState(pos).isAir(),"ordinary creative removal");check(place(p,FaerieVeilBlock.BLOCK,pos).isAccepted(),"restore single fixture");check(w.getBlockState(pos).getOpacity(w,pos)==15&&!w.getBlockState(pos).isTransparent(w,pos),"opaque normal light occlusion");check(w.getBlockState(pos).getPickStack(w,pos).isOf(FaerieVeilBlock.ITEM),"middle pick gives real veil item");
        int r=0;for(var block:List.of(DwUtilityBlocks.BARRIER,DwUtilityBlocks.LIGHT,DwUtilityBlocks.DARKNESS,DwUtilityBlocks.WATER)){var target=BASE.add(r++*3,0,5);check(place(p,block,target).isAccepted()&&w.getBlockState(target).isOf(block),"shared marker item placement regression "+block.kind);}
        for(boolean first:List.of(false,true)){
            var root=BASE.add(first?16:0,0,-4);var source=CompositeArchitecture.kindBlock("source_hold_bench").getDefaultState();var id=UUID.randomUUID();var object=CompositeRuntime.instance(w,root,source,id,ImportedTypeAliases.markPayload(null));var delta=object.cells().entrySet().stream().filter(e->e.getKey().y()==0&&!e.getKey().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)&&!e.getValue().collision().isEmpty()).findFirst().orElseThrow().getKey();var target=root.add(delta.x(),delta.y(),delta.z());
            if(first)check(place(p,FaerieVeilBlock.BLOCK,target).isAccepted(),"veil before source geometry");check(CompositeRuntime.place(w,root,source,id,p,ImportedTypeAliases.markPayload(null)).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"source enters veil or free space "+first);var before=w.getBlockEntity(root).createNbt();
            if(!first)check(place(p,FaerieVeilBlock.BLOCK,target).isAccepted(),"veil ignores existing source protrusions");check(before.equals(w.getBlockEntity(root).createNbt())&&w.getBlockState(target).isOf(FaerieVeilBlock.BLOCK)&&!CompositeRuntime.contributions(w,target).isEmpty(),"UUID/root/data/physical guest retained "+first);check(w.getBlockEntity(target)==null,"foreign veil carrier has no synthetic BE "+first);
            check(p.interactionManager.tryBreakBlock(target)&&!CompositeRuntime.contributions(w,target).isEmpty(),"remove only veil retaining owned object "+first);
        }
        for(int x=0;x<WIDTH;x++)for(int y=0;y<HEIGHT;y++)w.setBlockState(BASE.add(x,y,0),FaerieVeilBlock.BLOCK.getDefaultState(),Block.NOTIFY_ALL);
        for(int z=0;z<16;z++)for(int y=0;y<8;y++)w.setBlockState(BASE.add(40,y,z),FaerieVeilBlock.BLOCK.getDefaultState(),Block.NOTIFY_ALL);
        check(w.getBlockEntity(BASE)==null,"large wall has no block entity/ticker path");
    }
}
