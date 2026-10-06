package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import dev.dreamwalker.bloodbornerp.weapon.TrickWeaponItem;
import dev.dreamwalker.bloodbornerp.weapon.WeaponForm;
import dev.dreamwalker.bloodbornerp.weapon.WeaponRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

/** Standalone two-process dedicated-server persistence smoke test. */
public final class RpRestartSmoke implements DedicatedServerModInitializer {
 private static final UUID LAMP_ONE=UUID.fromString("00000000-0000-0000-0000-000000000101");
 private static final UUID LAMP_TWO=UUID.fromString("00000000-0000-0000-0000-000000000102");
 private static final UUID ITEM=UUID.fromString("00000000-0000-0000-0000-000000000103");
 private static final UUID NODE_ONE=UUID.fromString("00000000-0000-0000-0000-000000000201");
 private static final UUID NODE_TWO=UUID.fromString("00000000-0000-0000-0000-000000000202");
 private static final BlockPos FLOOR=new BlockPos(0,75,0), BB_POS=new BlockPos(5,75,5), LAMP_ONE_POS=new BlockPos(1,76,1), LAMP_TWO_POS=new BlockPos(4,76,1), MOB_POS=new BlockPos(2,76,4);
 private static long startedAt; private static boolean acted;

 @Override public void onInitializeServer(){
  ServerLifecycleEvents.SERVER_STARTED.register(server->{startedAt=server.getTicks();acted=false;ServerWorld world=server.getOverworld();world.setChunkForced(0,0,true);});
  ServerTickEvents.END_SERVER_TICK.register(server->{if(acted)return;long elapsed=server.getTicks()-startedAt;if(elapsed>3600)fail(server,"restart smoke exceeded 180 seconds");if(elapsed<40)return;acted=true;try{runPhase(server);}catch(Throwable error){BloodborneRp.LOGGER.error("BBRP_RESTART failed",error);server.stop(false);throw new IllegalStateException("Restart smoke failed",error);}});
 }

 private static void runPhase(MinecraftServer server) throws Exception {Path marker=server.getSavePath(WorldSavePath.ROOT).resolve("rp-restart-phase.txt");String phase=Files.exists(marker)?Files.readString(marker,StandardCharsets.UTF_8).trim():"";if(phase.isEmpty()){createPhaseOne(server);server.save(false,true,true);Files.writeString(marker,"phase1\n",StandardCharsets.UTF_8);BloodborneRp.LOGGER.info("BBRP_RESTART phase1 saved; stopping for restart");server.stop(false);return;}if(!phase.equals("phase1"))throw new IllegalStateException("unexpected restart marker: "+phase);verifyPhaseTwo(server);Files.writeString(marker,"complete\n",StandardCharsets.UTF_8);BloodborneRp.LOGGER.info("BBRP_RESTART complete");server.stop(false);}

 private static void createPhaseOne(MinecraftServer server) {
  ServerWorld world=server.getOverworld();for(int x=0;x<9;x++)for(int z=0;z<9;z++)world.setBlockState(new BlockPos(x,75,z),Blocks.STONE.getDefaultState());
  RpObjectEntity first=lamp(world,LAMP_ONE,LAMP_ONE_POS),second=lamp(world,LAMP_TWO,LAMP_TWO_POS);LampState state=LampState.get(world);state.nodes.remove(NODE_ONE);state.nodes.remove(NODE_TWO);LampState.Node firstNode=new LampState.Node(NODE_ONE,LAMP_ONE,"Restart Alpha",world.getRegistryKey().getValue().toString(),LAMP_ONE_POS),secondNode=new LampState.Node(NODE_TWO,LAMP_TWO,"Restart Beta",world.getRegistryKey().getValue().toString(),LAMP_TWO_POS);firstNode.routes.add(NODE_TWO);state.nodes.put(NODE_ONE,firstNode);state.nodes.put(NODE_TWO,secondNode);state.markDirty();
  EntityType<RpMobEntity> mobType=required(MobRegistry.TYPES.get("huntsman_a"),"huntsman_a");RpMobEntity mob=required(mobType.create(world),"huntsman_a factory");mob.refreshPositionAndAngles(MOB_POS.getX()+.5,MOB_POS.getY(),MOB_POS.getZ()+.5,0,0);mob.setFrozen(true);require(world.spawnEntity(mob),"spawn frozen mob");
  ItemStack stack=WeaponRegistry.SAW_CLEAVER.getDefaultStack();TrickWeaponItem.setForm(stack,WeaponForm.EXTENDED);stack.setCustomName(net.minecraft.text.Text.literal("restart weapon"));stack.setDamage(13);stack.getOrCreateNbt().putString("RpRestartTag","present");ItemEntity item=new ItemEntity(world,3.5,76,3.5,stack);item.setUuid(ITEM);require(world.spawnEntity(item),"spawn transformed weapon item");
  Identifier bbId=new Identifier("bloodborne_blocks","o_barrel");require(Registries.BLOCK.containsId(bbId),"Bloodborne Blocks root unavailable");world.setBlockState(BB_POS,Registries.BLOCK.get(bbId).getDefaultState());
 }

 private static void verifyPhaseTwo(MinecraftServer server) {
  ServerWorld world=server.getOverworld();LampState state=LampState.get(world);LampState.Node first=state.nodes.get(NODE_ONE),second=state.nodes.get(NODE_TWO);require(first!=null&&second!=null&&first.name.equals("Restart Alpha")&&second.name.equals("Restart Beta")&&first.routes.equals(java.util.List.of(NODE_TWO)),"lamp nodes, names, and route did not persist");require(isLamp(world.getEntity(LAMP_ONE))&&isLamp(world.getEntity(LAMP_TWO)),"lamp UUID entities did not persist");
  RpMobEntity mob=world.getEntitiesByType(MobRegistry.TYPES.get("huntsman_a"),new net.minecraft.util.math.Box(MOB_POS).expand(2),candidate->true).stream().findFirst().orElseThrow(()->new IllegalStateException("frozen mob missing"));require(mob.isFrozen()&&mob.getHealth()==mob.getMaxHealth(),"frozen mob or vanilla health did not persist");
  Entity entity=world.getEntity(ITEM);require(entity instanceof ItemEntity,"weapon item missing");ItemStack stack=((ItemEntity)entity).getStack();require(stack.getItem()==WeaponRegistry.SAW_CLEAVER&&TrickWeaponItem.form(stack)==WeaponForm.EXTENDED&&stack.getDamage()==13&&stack.hasCustomName()&&stack.getName().getString().equals("restart weapon")&&"present".equals(stack.getNbt().getString("RpRestartTag")),"weapon stack form/name/durability/tag did not persist");
  Identifier bbId=new Identifier("bloodborne_blocks","o_barrel");require(Registries.BLOCK.containsId(bbId)&&world.getBlockState(BB_POS).isOf(Registries.BLOCK.get(bbId)),"Bloodborne Blocks root did not persist");
 }

 private static RpObjectEntity lamp(ServerWorld world,UUID id,BlockPos pos){EntityType<RpObjectEntity> type=required(ObjectRegistry.TYPES.get("hunterlamp"),"hunterlamp");RpObjectEntity lamp=required(type.create(world),"hunterlamp factory");lamp.setUuid(id);lamp.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);require(world.spawnEntity(lamp),"spawn hunterlamp");return lamp;}
 private static boolean isLamp(Entity entity){return entity instanceof RpObjectEntity lamp&&lamp.assetId().equals("hunterlamp");}
 private static <T> T required(T value,String message){if(value==null)throw new IllegalStateException(message);return value;}
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static void fail(MinecraftServer server,String message){BloodborneRp.LOGGER.error("BBRP_RESTART failed: {}",message);server.stop(false);throw new IllegalStateException(message);}
}
