package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

/** Actual saved city coordinates reported by the player, using production blocks. */
public final class PavementMapChecks implements ModInitializer {
 private final JsonArray checks=new JsonArray();
 private void check(boolean value,String name){var row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",value);checks.add(row);if(!value)throw new IllegalStateException(name);}
 @Override public void onInitialize(){ServerLifecycleEvents.SERVER_STARTED.register(server->server.execute(()->run(server)));}
 private void run(MinecraftServer server){
  var report=new JsonObject();
  try{
   var world=server.getOverworld();var out=Path.of(System.getProperty("dw.pavement.output"));var expected=NbtIo.readCompressed(out.resolve("expected.nbt").toFile());
   for(var tag:expected.getList("probes",10)){
    var row=(NbtCompound)tag;var xyz=row.getIntArray("pos");var pos=new BlockPos(xyz[0],xyz[1],xyz[2]);world.getChunk(pos);var state=world.getBlockState(pos);var stored=row.getCompound("state");
    check(Registries.BLOCK.getId(state.getBlock()).toString().equals(stored.getString("Name")),"saved city block ID "+pos.toShortString());
    var properties=stored.getCompound("Properties");var actual=NbtHelper.fromBlockState(state).getCompound("Properties");for(String k:properties.getKeys())check(actual.getString(k).equals(properties.getString(k)),"saved property "+k+" "+pos.toShortString());
    var entity=world.getBlockEntity(pos);var wanted=row.getCompound("entity");
    if(wanted.contains("resident",10)){check(entity!=null&&wanted.get("resident").equals(entity.createNbt().get("resident")),"non-pavement owner UUID preserved "+pos.toShortString());check(wanted.getCompound("payload").equals(entity.createNbt().getCompound("payload")),"non-pavement typed payload preserved "+pos.toShortString());}
    if(row.getBoolean("pavement")){
     check(state.getBlock() instanceof MaterialSlabs.Slab&&state.get(SlabBlock.TYPE)==SlabType.BOTTOM&&!state.get(SlabBlock.WATERLOGGED),"real dry lower slab at reported hole "+pos.toShortString());
     var actor=new ArmorStandEntity(world,pos.getX()+.5,pos.getY()+2,pos.getZ()+.5);world.spawnEntity(actor);actor.move(MovementType.SELF,new Vec3d(0,-3,0));check(Math.abs(actor.getY()-(pos.getY()+.5))<1e-6,"entity cannot fall through reported hole "+pos.toShortString());
     var hit=world.raycast(new RaycastContext(new Vec3d(pos.getX()+.5,pos.getY()+2,pos.getZ()+.5),new Vec3d(pos.getX()+.5,pos.getY()-1,pos.getZ()+.5),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,actor));check(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos),"reported pavement is selectable "+pos.toShortString());actor.discard();
    }
   }
   BlockPos start=new BlockPos(143,74,-987);world.getChunk(start);var walker=new ArmorStandEntity(world,143.5,74.5,-986.5);world.spawnEntity(walker);
   for(int step=0;step<4;step++){walker.move(MovementType.SELF,new Vec3d(0,-.05,1));check(Math.abs(walker.getY()-74.5)<1e-6,"walk across old gaps step "+step);}
   walker.discard();report.addProperty("status","PASS");
  }catch(Throwable e){report.addProperty("status","FAIL");report.addProperty("error",e.toString());e.printStackTrace();}
  report.add("checks",checks);try{var out=Path.of(System.getProperty("dw.pavement.output"));Files.createDirectories(out);Files.writeString(out.resolve("checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){e.printStackTrace();}server.stop(false);
 }
}
