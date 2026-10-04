package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.RpConfig;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Server-owned lamp graph and opaque-destination travel protocol. */
public final class LampService {
 public static final Identifier LIST_PACKET=BloodborneRp.id("lamp_list"),TRAVEL_PACKET=BloodborneRp.id("lamp_travel");
 private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
 private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
 private static final java.util.Set<UUID> PENDING=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private static boolean initialized;
 private LampService(){}
 public static void initialize(){if(initialized)return;initialized=true;ServerPlayNetworking.registerGlobalReceiver(TRAVEL_PACKET,(server,player,handler,buf,response)->{if(buf.readableBytes()!=24)return;UUID destination=buf.readUuid();long context=buf.readLong();if(!PENDING.add(player.getUuid()))return;server.execute(()->{try{if(server.getPlayerManager().getPlayer(player.getUuid())==player)travel(player,destination,context);}finally{PENDING.remove(player.getUuid());}});});ServerTickEvents.END_SERVER_TICK.register(server->{long tick=server.getTicks();CONTEXTS.entrySet().removeIf(entry->entry.getValue().expiresAt<tick);COOLDOWNS.entrySet().removeIf(entry->entry.getValue()<=tick);});ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clearPlayer(handler.player.getUuid()));ServerLifecycleEvents.SERVER_STOPPING.register(server->{CONTEXTS.clear();COOLDOWNS.clear();PENDING.clear();});}
 public static void open(ServerPlayerEntity player){if(!RpConfig.INSTANCE.playerTravelEnabled)return;LampState.Node source=nearbySource(player);if(source==null)return;long token=player.getRandom().nextLong();CONTEXTS.put(player.getUuid(),new Context(token,source.id,player.getServer().getTicks()+200));PacketByteBuf out=new PacketByteBuf(Unpooled.buffer());out.writeLong(token);List<LampState.Node> destinations=destinations(player.getServer(),source);out.writeVarInt(destinations.size());for(LampState.Node node:destinations){out.writeUuid(node.id);out.writeString(node.name,64);}ServerPlayNetworking.send(player,LIST_PACKET,out);}
 private static List<LampState.Node> destinations(MinecraftServer server,LampState.Node source){List<LampState.Node> out=new ArrayList<>();LampState state=state(server);for(UUID id:source.routes){LampState.Node node=state.nodes.get(id);if(node!=null&&out.size()<RpConfig.INSTANCE.maxLampDestinations)out.add(node);}return out;}
 private static boolean travel(ServerPlayerEntity player,UUID destination,long token){long tick=player.getServer().getTicks();Context context=CONTEXTS.remove(player.getUuid());if(context==null||!LampPolicy.validContext(context.token,token,context.expiresAt,tick)||!player.isAlive()||player.isSpectator()||!RpConfig.INSTANCE.playerTravelEnabled||COOLDOWNS.getOrDefault(player.getUuid(),0L)>tick)return fail(player);LampState.Node source=nearbySource(player);if(source==null||!source.id.equals(context.source))return fail(player);LampState state=state(player.getServer());if(!source.routes.contains(destination))return fail(player);LampState.Node target=state.nodes.get(destination);if(target==null||Identifier.tryParse(target.dimension)==null)return fail(player);ServerWorld targetWorld=player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(target.dimension)));if(targetWorld==null||!targetWorld.isChunkLoaded(target.pos)||!targetWorld.getWorldBorder().contains(target.pos))return fail(player);Entity targetEntity=targetWorld.getEntity(target.lamp);if(!(targetEntity instanceof RpObjectEntity lamp)||!lamp.assetId().equals("hunterlamp"))return fail(player);BlockPos landing=findLanding(targetWorld,player,lamp.getBlockPos());if(landing==null)return fail(player);player.teleport(targetWorld,landing.getX()+.5,landing.getY(),landing.getZ()+.5,player.getYaw(),player.getPitch());COOLDOWNS.put(player.getUuid(),tick+RpConfig.INSTANCE.lampTravelCooldownTicks);return true;}
 private static boolean fail(ServerPlayerEntity player){clear(player);return false;}
 static long contextTokenForTest(UUID player){Context context=CONTEXTS.get(player);return context==null?Long.MIN_VALUE:context.token;}
 static boolean travelForTest(ServerPlayerEntity player,UUID destination,long token){return travel(player,destination,token);}
 static void clearPlayerForTest(UUID player){clearPlayer(player);}
 private static void clearPlayer(UUID player){CONTEXTS.remove(player);COOLDOWNS.remove(player);}
 private static void clear(ServerPlayerEntity player){PacketByteBuf out=new PacketByteBuf(Unpooled.buffer(9));out.writeLong(0);out.writeVarInt(0);ServerPlayNetworking.send(player,LIST_PACKET,out);}
 private static LampState.Node nearbySource(ServerPlayerEntity player){LampState state=state(player.getServer());for(LampState.Node node:state.nodes.values()){if(!node.dimension.equals(player.getWorld().getRegistryKey().getValue().toString())||!player.getServerWorld().isChunkLoaded(node.pos))continue;Entity entity=player.getServerWorld().getEntity(node.lamp);if(entity instanceof RpObjectEntity object&&object.assetId().equals("hunterlamp")&&object.squaredDistanceTo(player)<=25)return node;}return null;}
 private static BlockPos findLanding(ServerWorld world,ServerPlayerEntity player,BlockPos lamp){
  float height=Math.max(1.8F,player.getHeight());
  for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){
   BlockPos pos=lamp.add(offset[0],0,offset[1]);
   if(pos.getY()<=world.getBottomY()||pos.getY()+height>=world.getTopY()||!world.isChunkLoaded(pos))continue;
   if(!world.getFluidState(pos).isEmpty()||!world.getFluidState(pos.up()).isEmpty())continue;
   if(hazard(world.getBlockState(pos))||hazard(world.getBlockState(pos.up())))continue;
   var support=world.getBlockState(pos.down());
   if(support.getCollisionShape(world,pos.down()).isEmpty()||!world.getFluidState(pos.down()).isEmpty()
       ||support.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)||support.isOf(net.minecraft.block.Blocks.CAMPFIRE)
       ||support.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE)||support.isOf(net.minecraft.block.Blocks.CACTUS))continue;
   var box=new net.minecraft.util.math.Box(pos.getX()+.5-player.getWidth()/2,pos.getY(),pos.getZ()+.5-player.getWidth()/2,
       pos.getX()+.5+player.getWidth()/2,pos.getY()+height,pos.getZ()+.5+player.getWidth()/2);
   if(world.getWorldBorder().contains(box)&&world.isSpaceEmpty(player,box)
       &&world.getOtherEntities(player,box,e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())return pos;
  }
  return null;
 }
 private static boolean hazard(net.minecraft.block.BlockState state){
  return state.isIn(net.minecraft.registry.tag.BlockTags.FIRE)||state.isOf(net.minecraft.block.Blocks.WITHER_ROSE)
      ||state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH)||state.isOf(net.minecraft.block.Blocks.POWDER_SNOW)
      ||state.isOf(net.minecraft.block.Blocks.NETHER_PORTAL)||state.isOf(net.minecraft.block.Blocks.END_PORTAL)
      ||state.isOf(net.minecraft.block.Blocks.END_GATEWAY);
 }
 private static LampState state(MinecraftServer server){return LampState.get(server.getOverworld());}
 public static boolean register(ServerPlayerEntity player,String name){if(!LampPolicy.validName(name))return false;RpObjectEntity object=dev.dreamwalker.bloodbornerp.object.ObjectRegistry.objectAt(player);if(object==null||!object.assetId().equals("hunterlamp"))return false;LampState state=state(player.getServer());if(state.nodes.size()>=RpConfig.INSTANCE.maxLampDestinations)return false;for(LampState.Node node:state.nodes.values())if(node.lamp.equals(object.getUuid()))return false;UUID id=UUID.randomUUID();state.nodes.put(id,new LampState.Node(id,object.getUuid(),name,player.getWorld().getRegistryKey().getValue().toString(),object.getBlockPos()));state.markDirty();return true;}
 public static boolean rename(MinecraftServer server,UUID id,String name){LampState.Node node=state(server).nodes.get(id);if(node==null||!LampPolicy.validName(name))return false;node.name=name;state(server).markDirty();return true;}
 public static boolean remove(MinecraftServer server,UUID id){LampState state=state(server);if(state.nodes.remove(id)==null)return false;for(LampState.Node node:state.nodes.values())node.routes.remove(id);state.markDirty();return true;}
 public static void removeByEntity(MinecraftServer server,UUID entityId){LampState state=state(server);List<UUID> removed=new ArrayList<>();for(LampState.Node node:state.nodes.values())if(node.lamp.equals(entityId))removed.add(node.id);if(removed.isEmpty())return;for(UUID id:removed)state.nodes.remove(id);for(LampState.Node node:state.nodes.values())node.routes.removeIf(removed::contains);state.markDirty();}
 public static boolean route(MinecraftServer server,UUID from,UUID to,boolean open){LampState state=state(server);LampState.Node source=state.nodes.get(from);if(source==null||state.nodes.get(to)==null||from.equals(to))return false;if(open){if(!LampPolicy.validRoute(source.routes.size(),RpConfig.INSTANCE.maxLampDestinations,source.routes.contains(to)))return false;source.routes.add(to);}else if(!source.routes.remove(to))return false;state.markDirty();return true;}
 public static List<LampInfo> list(MinecraftServer server){List<LampInfo> out=new ArrayList<>();for(LampState.Node node:state(server).nodes.values()){if(out.size()>=RpConfig.INSTANCE.maxLampDestinations)break;out.add(new LampInfo(node.id,node.name));}return List.copyOf(out);}
 public record LampInfo(UUID id,String name){}
 private record Context(long token,UUID source,long expiresAt){}
}
