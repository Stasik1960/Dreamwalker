package dev.dreamwalker.bloodbornedw.utilityqa;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

public final class UtilityQa implements ModInitializer {
    public static final BlockPos BASE=new BlockPos(8192,220,8192);
    public static final List<DwUtilityBlocks.MarkerBlock> blocks(){return List.of(DwUtilityBlocks.BARRIER,DwUtilityBlocks.LIGHT,DwUtilityBlocks.DARKNESS,DwUtilityBlocks.WATER);}
    public static BlockPos cell(int n){return BASE.add(n*4,0,0);}
    public static BlockPos host(int n){return BASE.add(n*4,0,-8);}
    private final JsonArray checks=new JsonArray();private final JsonObject report=new JsonObject();private Path out;private MinecraftServer server;private int age;private boolean started,done;
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
    private void save(String status,Throwable error){report.addProperty("status",status);report.add("checks",checks);if(error!=null){report.addProperty("error",error.toString());error.printStackTrace();}try{Files.createDirectories(out);Files.writeString(out.resolve("server-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
    @Override public void onInitialize(){String dir=System.getProperty("dw.utility.qa.output");if(dir==null)return;out=Path.of(dir);ServerWorldEvents.LOAD.register((s,w)->{if(w.getRegistryKey()==World.OVERWORLD){w.setSpawnPos(BASE.up(3),0);w.setTimeOfDay(6000);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,s);w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,s);}});ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;started=true;});ServerTickEvents.END_SERVER_TICK.register(s->{if(!started||done)return;try{if(++age==20){if(Boolean.getBoolean("dw.utility.qa.verify")){verify(s.getOverworld());finish(null);}else initial(s.getOverworld());}if(age==80&&!Boolean.getBoolean("dw.utility.qa.verify")){delayed(s.getOverworld());finish(null);}}catch(Throwable t){finish(t);}});}
    private void finish(Throwable error){done=true;save(error==null?"PASS":"FAIL",error);if(Boolean.getBoolean("dw.utility.qa.stop"))server.stop(false);}
    private ServerPlayerEntity actor(ServerWorld w){var p=new ServerPlayerEntity(server,w,new GameProfile(UUID.randomUUID(),"UtilityProbe")){@Override public void sendMessage(Text text,boolean action){}@Override public boolean hasPermissionLevel(int level){return true;}};new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND),p);p.changeGameMode(GameMode.CREATIVE);p.setPosition(BASE.getX(),BASE.getY()+2,BASE.getZ()+3);return p;}
    private ActionResult place(ServerPlayerEntity p,DwUtilityBlocks.MarkerBlock block,BlockPos target){var stack=new ItemStack(block);p.getInventory().setStack(p.getInventory().selectedSlot,stack);var hit=new BlockHitResult(Vec3d.ofBottomCenter(target),Direction.UP,target.down(),false);return ((BlockItem)stack.getItem()).place(new ItemPlacementContext(new ItemUsageContext(p,Hand.MAIN_HAND,hit)));}
    private void initial(ServerWorld w){
        var p=actor(w);for(int x=-5;x<=70;x++)for(int z=-12;z<=20;z++)w.setBlockState(BASE.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        int i=0;for(var block:blocks()){
            var pos=cell(i++);check(place(p,block,pos).isAccepted(),"place helper at actual aimed stone face: "+block.kind);check(w.getBlockState(pos).isOf(block),"surface anchor exact: "+block.kind);
            for(var mode:List.of(GameMode.CREATIVE,GameMode.SURVIVAL,GameMode.ADVENTURE)){
                p.changeGameMode(mode);var shape=w.getBlockState(pos).getOutlineShape(w,pos,ShapeContext.of(p));check(shape.isEmpty()==(mode!=GameMode.CREATIVE),"selection "+block.kind+" "+mode);
                if(mode!=GameMode.CREATIVE)check(!p.interactionManager.tryBreakBlock(pos)&&w.getBlockState(pos).isOf(block),"server rejects noncreative break "+block.kind+" "+mode);
            }
            p.changeGameMode(GameMode.CREATIVE);check(w.getBlockState(pos).getCollisionShape(w,pos,ShapeContext.of(p)).isEmpty()==(block.kind!=DwUtilityBlocks.Kind.BARRIER),"own player collision "+block.kind);
            check(w.getBlockState(pos).getCollisionShape(w,pos,ShapeContext.absent()).isEmpty(),"placement ignores own helper collision "+block.kind);
            check(p.interactionManager.tryBreakBlock(pos)&&w.getBlockState(pos).isAir(),"creative ordinary break "+block.kind);check(place(p,block,pos).isAccepted(),"restore helper fixture "+block.kind);
        }
        check(DwUtilityBlocks.LIGHT.getDefaultState().getLuminance()==14,"DW light emits level 14");check(DwUtilityBlocks.DARKNESS.getDefaultState().getOpacity(w,cell(2))==15,"darkness occludes lighting normally");
        var water=cell(3);check(w.getBlockState(water).getFluidState().isOf(Fluids.WATER),"faux terrain uses vanilla water state/textures");check(w.getFluidState(water).isOf(DwUtilityBlocks.WATER_FLUID)&&!w.getFluidState(water).isIn(FluidTags.WATER),"world exposes nonphysical faux fluid");
        p.setPosition(water.getX()+.5,water.getY()+.1,water.getZ()+.5);p.setVelocity(.1,-.2,.1);var velocity=p.getVelocity();check(!p.updateMovementInFluid(FluidTags.WATER,.014)&&p.getVelocity().equals(velocity),"player receives no water drag/current/swimming");
        var item=new ItemEntity(w,water.getX()+.5,water.getY()+.1,water.getZ()+.5,new ItemStack(Items.DIAMOND));item.setVelocity(0,-.2,0);check(!item.updateMovementInFluid(FluidTags.WATER,.014)&&item.getVelocity().y==-.2,"dropped item receives no buoyancy or water drag");
        var hosts=List.of(Blocks.STONE_SLAB,Blocks.STONE_STAIRS,Blocks.OAK_FENCE,Blocks.GLASS_PANE,Blocks.COBBLESTONE_WALL,Blocks.CHEST);
        for(int n=0;n<hosts.size();n++){
            var pos=host(n);w.setBlockState(pos,hosts.get(n).getDefaultState(),Block.NOTIFY_ALL);if(w.getBlockEntity(pos) instanceof ChestBlockEntity chest){chest.setCustomName(Text.literal("Faux host NBT preserved"));chest.setStack(0,new ItemStack(Items.DIAMOND,7));}
            var be=w.getBlockEntity(pos);var before=be==null?null:be.createNbt();check(FauxWaterlogging.fill(w,pos,p),"waterlog host "+hosts.get(n));check(w.getBlockState(pos).isOf(hosts.get(n))&&FauxWaterlogging.logged(w,pos)&&w.getFluidState(pos).isOf(DwUtilityBlocks.WATER_FLUID),"host identity and faux physics preserved "+n);
            if(before!=null)check(before.equals(w.getBlockEntity(pos).createNbt()),"waterlogging preserves full chest NBT");
            check(((Waterloggable)hosts.get(n)).tryDrainFluid(w,pos,w.getBlockState(pos)).isEmpty(),"cannot obtain real bucket from faux water "+n);
            check(p.interactionManager.tryBreakBlock(pos)&&w.getBlockState(pos).isOf(hosts.get(n))&&!FauxWaterlogging.logged(w,pos)&&!w.getBlockState(pos).get(Properties.WATERLOGGED),"creative break removes only water, preserving host "+n);
            check(FauxWaterlogging.fill(w,pos,p),"restore logged host "+n);
        }
        // Native placement into faux water must keep decoration in a waterloggable state.
        var pos=BASE.add(28,0,-8);check(place(p,DwUtilityBlocks.WATER,pos).isAccepted(),"place faux water before slab");var slab=new ItemStack(Blocks.OAK_SLAB);p.getInventory().setStack(p.getInventory().selectedSlot,slab);var hit=new BlockHitResult(Vec3d.ofBottomCenter(pos),Direction.UP,pos.down(),false);check(((BlockItem)slab.getItem()).place(new ItemPlacementContext(new ItemUsageContext(p,Hand.MAIN_HAND,hit))).isAccepted(),"normal slab placement into faux water");check(w.getBlockState(pos).isOf(Blocks.OAK_SLAB)&&FauxWaterlogging.logged(w,pos),"normal placed slab retains faux water");
        // Keep source ownership/data through the utility carrier in a colliding service cell.
        int k=0;for(var block:blocks()){
            var root=BASE.add(k++*8,0,8);var source=CompositeArchitecture.kindBlock("source_hold_bench").getDefaultState();var id=UUID.randomUUID();check(CompositeRuntime.place(w,root,source,id,p,ImportedTypeAliases.markPayload(null)).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"source fixture for "+block.kind);
            var before=((CompositeBlockEntity)w.getBlockEntity(root)).createNbt();var instance=CompositeRuntime.instance(w,root,source,id,ImportedTypeAliases.markPayload(null));var part=instance.cells().entrySet().stream().filter(e->e.getKey().y()==0&&!e.getKey().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)&&!e.getValue().collision().isEmpty()).findFirst().orElseThrow();var relative=part.getKey();var target=root.add(relative.x(),relative.y(),relative.z());
            check(place(p,block,target).isAccepted(),"helper ignores existing protruding collision "+block.kind);check(before.equals(w.getBlockEntity(root).createNbt())&&!CompositeRuntime.contributions(w,target).isEmpty(),"source root/UUID/data/guest retained "+block.kind);
        }
        k=0;for(var block:blocks()){
            var root=BASE.add(k++*8,0,16);var source=CompositeArchitecture.kindBlock("source_hold_bench").getDefaultState();var id=UUID.randomUUID();var instance=CompositeRuntime.instance(w,root,source,id,ImportedTypeAliases.markPayload(null));var relative=instance.cells().entrySet().stream().filter(e->e.getKey().y()==0&&!e.getKey().equals(dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell.ORIGIN)&&!e.getValue().collision().isEmpty()).findFirst().orElseThrow().getKey();var target=root.add(relative.x(),relative.y(),relative.z());
            check(place(p,block,target).isAccepted(),"helper before incoming geometry "+block.kind);check(CompositeRuntime.place(w,root,source,id,p,ImportedTypeAliases.markPayload(null)).outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED,"new source geometry may enter helper "+block.kind);check(w.getBlockState(target).isOf(block)&&!CompositeRuntime.contributions(w,target).isEmpty(),"helper and entering object coexist "+block.kind);
        }
        // Closed one-cell tunnel: visible black material blocks light without blocking a body.
        for(int x=39;x<=44;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)if(y!=0||z!=0||x==39||x==44)w.setBlockState(BASE.add(x,y,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        w.setBlockState(BASE.add(40,0,0),DwUtilityBlocks.LIGHT.getDefaultState(),Block.NOTIFY_ALL);w.setBlockState(BASE.add(41,0,0),DwUtilityBlocks.DARKNESS.getDefaultState(),Block.NOTIFY_ALL);
        save("WAITING_FOR_LIGHT",null);
    }
    private void delayed(ServerWorld w){check(w.getLightLevel(LightType.BLOCK,cell(1))==14,"actual propagated block light reaches 14");check(w.getLightLevel(LightType.BLOCK,BASE.add(42,0,0))==0,"dark block stops light across closed tunnel");for(var d:Direction.values())check(!w.getBlockState(cell(3).offset(d)).isOf(Blocks.WATER),"faux water never flows "+d);verify(w);}
    private void verify(ServerWorld w){
        for(int i=0;i<4;i++){w.getChunk(cell(i));check(w.getBlockState(cell(i)).isOf(blocks().get(i)),"saved helper state "+i);}
        for(int i=0;i<6;i++){w.getChunk(host(i));check(FauxWaterlogging.logged(w,host(i))&&w.getFluidState(host(i)).isOf(DwUtilityBlocks.WATER_FLUID),"saved host faux metadata/physics "+i);}
        var chest=(ChestBlockEntity)w.getBlockEntity(host(5));check(chest!=null&&chest.getStack(0).isOf(Items.DIAMOND)&&chest.getStack(0).getCount()==7&&chest.getCustomName().getString().equals("Faux host NBT preserved"),"saved chest data unchanged");
        check(FauxWaterlogging.logged(w,BASE.add(28,0,-8)),"saved slab placed into faux water");
    }
}
