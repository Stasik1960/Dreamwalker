package dev.dreamwalker.bloodbornedw.utilityqa;
import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class UtilityQaClient implements ClientModInitializer {
    private final JsonObject report=new JsonObject();private final JsonArray checks=new JsonArray();private Path out;private int stage,age;private boolean done;private volatile boolean positioned;
    private static final BlockPos EXTRA=UtilityQa.BASE.add(32,0,-8);
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);save();}
    private void save(){report.add("checks",checks);try{Files.writeString(out.resolve("client-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){throw new RuntimeException(e);}}
    private void frame(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});checks.add("frame "+name);save();}
    @Override public void onInitializeClient(){String dir=System.getProperty("dw.utility.qa.output");if(dir==null)return;out=Path.of(dir);report.addProperty("status","STARTING");save();ClientTickEvents.END_CLIENT_TICK.register(c->{if(done)return;try{tick(c);}catch(Throwable t){report.addProperty("status","FAIL");report.addProperty("error",t.toString());t.printStackTrace();save();done=true;c.scheduleStop();}});}
    private void setup(MinecraftClient c,BlockPos target,boolean giveWater){positioned=false;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());p.changeGameMode(GameMode.CREATIVE);p.getInventory().clear();if(giveWater)p.getInventory().setStack(p.getInventory().selectedSlot,new ItemStack(DwUtilityBlocks.WATER_ITEM));p.playerScreenHandler.sendContentUpdates();p.getAbilities().flying=true;p.sendAbilitiesUpdate();p.teleport(p.getServerWorld(),target.getX()+.5,target.getY()+1.1,target.getZ()+3.2,180,20);positioned=true;});age=0;}
    private boolean ready(MinecraftClient c,BlockPos target){boolean ready=positioned&&c.player.squaredDistanceTo(Vec3d.ofCenter(target))<64&&c.world.isChunkLoaded(target);if(!ready&&age>1200)throw new AssertionError("Timed out awaiting client chunk/teleport "+target);return ready;}
    private void tick(MinecraftClient c)throws Exception{
        if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;age++;
        if(stage==0){var p=out.resolve("server-checks.json");if(!Files.exists(p))return;check(JsonParser.parseString(Files.readString(p)).getAsJsonObject().get("status").getAsString().equals("PASS"),"saved world reload/host NBT verified");c.getServer().execute(()->c.getServer().getOverworld().setBlockState(EXTRA,Blocks.STONE_STAIRS.getDefaultState(),Block.NOTIFY_ALL));setup(c,EXTRA,true);stage=1;return;}
        if(stage==1&&age>=100&&ready(c,EXTRA)&&c.player.getMainHandStack().isOf(DwUtilityBlocks.WATER_ITEM)&&c.world.getBlockState(EXTRA).isOf(Blocks.STONE_STAIRS)){
            var hit=new BlockHitResult(Vec3d.ofCenter(EXTRA).add(0,.3,0),Direction.UP,EXTRA,false);check(c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,hit).isAccepted(),"ordinary network right click decorative water onto stairs");stage=2;age=0;return;}
        if(stage==2&&age>=60){check(FauxWaterlogging.logged(c.world,EXTRA)&&c.world.getBlockState(EXTRA).get(Properties.WATERLOGGED),"server delta sync preserves original stairs and decorative water");check(c.world.getFluidState(EXTRA).isOf(DwUtilityBlocks.WATER_FLUID)&&!c.world.getFluidState(EXTRA).isIn(FluidTags.WATER),"client has nonphysical water after update");frame(c,"faux-waterlogged.png");check(c.interactionManager.attackBlock(EXTRA,Direction.UP),"ordinary creative attack sends removal");stage=3;age=0;return;}
        if(stage==3&&age>=60){check(c.world.getBlockState(EXTRA).isOf(Blocks.STONE_STAIRS)&&!FauxWaterlogging.logged(c.world,EXTRA)&&!c.world.getBlockState(EXTRA).get(Properties.WATERLOGGED),"creative network break removes only faux water");setup(c,UtilityQa.cell(2),false);stage=4;return;}
        if(stage==4&&age>=100&&ready(c,UtilityQa.cell(2))){
            for(int i=0;i<4;i++){var pos=UtilityQa.cell(i);if(!c.world.isChunkLoaded(pos))return;check(c.world.getBlockState(pos).isOf(UtilityQa.blocks().get(i)),"client initial chunk helper "+i);check(!c.world.getBlockState(pos).getOutlineShape(c.world,pos,ShapeContext.of(c.player)).isEmpty(),"creative client can target helper "+i);}
            frame(c,"utility-overview.png");var sprite=c.getBakedModelManager().getAtlas(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(new Identifier("minecraft","block/water_still"));check(!sprite.getContents().getId().getPath().equals("missingno"),"vanilla/resourcepack water sprite is used");
            positioned=false;c.getServer().execute(()->{var p=c.getServer().getPlayerManager().getPlayer(c.player.getUuid());var pos=UtilityQa.cell(3);p.getServerWorld().setBlockState(pos.up(),DwUtilityBlocks.WATER.getDefaultState(),Block.NOTIFY_ALL);p.teleport(p.getServerWorld(),pos.getX()+.5,pos.getY()+.05,pos.getZ()+.5,180,0);positioned=true;});stage=5;age=0;return;
        }
        if(stage==5&&age>=80&&positioned){check(!c.player.isTouchingWater()&&!c.player.isSubmergedInWater()&&!c.player.isSwimming(),"real player freely occupies two-block water volume without water state");frame(c,"faux-water-inside.png");report.addProperty("status","PASS");save();done=true;c.scheduleStop();}
        if(age>1800)throw new AssertionError("Client stage stalled: "+stage);
    }
}
