package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.RpConfig;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Server-owned explicit lamp network. No light or respawn policy is changed here. */
public final class LampService {
 public static final Identifier LIST_PACKET=BloodborneRp.id("lamp_list"),TRAVEL_PACKET=BloodborneRp.id("lamp_travel"),CANCEL_PACKET=BloodborneRp.id("lamp_cancel");
 private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
 private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
 private static final Set<UUID> NETWORK_PENDING=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private static final Map<UUID,Long> CANCEL_PENDING=Collections.synchronizedMap(new HashMap<>());
 private static final Map<UUID,Pending> WAITING=new LinkedHashMap<>();
 private static final ChunkTicketType<UUID> TRAVEL_TICKET=ChunkTicketType.create("bloodborne_rp_lamp_travel",UUID::compareTo);
 private static final int MAX_WAITING=64,WAIT_TICKS=200,CONTEXT_TICKS=1200;
 private static boolean initialized;
 private LampService(){}
 public static void initialize(){
  if(initialized)return;initialized=true;
  ServerPlayNetworking.registerGlobalReceiver(TRAVEL_PACKET,(server,player,handler,buf,response)->{
   int bytes=buf.readableBytes();
   if(bytes!=24){protocolError(player,"lamp_travel_expected_24_bytes");return;}UUID destination=buf.readUuid();long token=buf.readLong();
   if(!NETWORK_PENDING.add(player.getUuid()))return;server.execute(()->{try{if(server.getPlayerManager().getPlayer(player.getUuid())==player)travel(player,destination,token);}finally{NETWORK_PENDING.remove(player.getUuid());}});
  });
  ServerPlayNetworking.registerGlobalReceiver(CANCEL_PACKET,(server,player,handler,buf,response)->{
   if(buf.readableBytes()!=8){protocolError(player,"lamp_cancel_expected_8_bytes");return;}long token=buf.readLong();boolean enqueue;
   synchronized(CANCEL_PENDING){if(CANCEL_PENDING.size()>=256&&!CANCEL_PENDING.containsKey(player.getUuid()))return;enqueue=!CANCEL_PENDING.containsKey(player.getUuid());CANCEL_PENDING.put(player.getUuid(),token);}
   if(enqueue)server.execute(()->{Long latest;synchronized(CANCEL_PENDING){latest=CANCEL_PENDING.remove(player.getUuid());}if(latest==null||server.getPlayerManager().getPlayer(player.getUuid())!=player)return;Context c=CONTEXTS.get(player.getUuid());if(c!=null&&c.token==latest){cancelWaiting(player.getUuid());CONTEXTS.remove(player.getUuid());}});
  });
  ServerTickEvents.END_SERVER_TICK.register(LampService::tick);
  ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof RpObjectEntity lamp&&lamp.assetId().equals("hunterlamp"))entityMoved(lamp);});
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clearPlayer(handler.player.getUuid()));
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(UUID id:List.copyOf(WAITING.keySet()))cancelWaiting(id);CONTEXTS.clear();COOLDOWNS.clear();NETWORK_PENDING.clear();CANCEL_PENDING.clear();LampEditor.clearAll();});
 }
 private static void tick(MinecraftServer server){
  long now=server.getTicks();CONTEXTS.entrySet().removeIf(e->e.getValue().expiresAt<now&&!WAITING.containsKey(e.getKey()));COOLDOWNS.entrySet().removeIf(e->e.getValue()<=now);LampEditor.expire(server);
  for(Map.Entry<UUID,Pending> row:List.copyOf(WAITING.entrySet())){
   ServerPlayerEntity player=server.getPlayerManager().getPlayer(row.getKey());Pending p=row.getValue();
   if(player==null){cancelWaiting(row.getKey());continue;}
   LampState graph=state(server);LampState.Node source=graph.nodes.get(p.source),target=graph.nodes.get(p.destination);
   if(source==null||target==null){cancelWaiting(row.getKey());refuse(player,source,"Фонарь был удалён; переход отменён.","node_removed");continue;}
   if(!usableSource(player,source)||!graph.connected(source.id,target.id)||!player.isAlive()||player.isSpectator()){cancelWaiting(row.getKey());refuse(player,source,"Источник или маршрут изменился; откройте меню нужного фонаря заново.","source_or_route_changed");continue;}
   if(p.world!=world(server,target.dimension)||!p.chunks.contains(new ChunkPos(target.pos))){
    cancelWaiting(row.getKey());Context context=CONTEXTS.get(row.getKey());
    if(now>=p.deadline||context==null){refuse(player,source,"Назначение переместилось; время ожидания истекло.","moving_target_timeout");continue;}
    travel(player,target.id,context.token);Pending replacement=WAITING.get(row.getKey());
    if(replacement!=null)WAITING.put(row.getKey(),new Pending(replacement.source,replacement.destination,replacement.world,replacement.lease,replacement.chunks,p.deadline));
    continue;
   }
   RpObjectEntity lamp=loadedLamp(p.world,target.lamp);
   if(lamp!=null){cancelWaiting(row.getKey());finishTravel(player,source,target,lamp);continue;}
   if(now>=p.deadline){boolean loaded=p.world.isChunkLoaded(target.pos);cancelWaiting(row.getKey());refuse(player,source,loaded?"Целевой чанк загружен, но сохранённый фонарь не найден. Он мог быть удалён.":"Не удалось загрузить целевой фонарь за 10 секунд. Можно повторить попытку.",loaded?"loaded_instance_missing":"target_load_timeout");}
  }
 }
 /** Compatibility command/test entrypoint; gameplay must pass the actually clicked lamp. */
 public static void open(ServerPlayerEntity player){RpObjectEntity source=dev.dreamwalker.bloodbornerp.object.ObjectRegistry.objectAt(player);if(source==null||!source.assetId().equals("hunterlamp")){LampState.Node nearest=nearbySource(player);source=nearest==null?null:loadedLamp(player.getServerWorld(),nearest.lamp);}if(source==null){tell(player,"Нажмите нужный охотничий фонарь.");return;}open(player,source);}
 public static void open(ServerPlayerEntity player,RpObjectEntity clicked){
  cancelWaiting(player.getUuid());CONTEXTS.remove(player.getUuid());
  if(!RpConfig.INSTANCE.playerTravelEnabled){tell(player,"Переходы между фонарями отключены на сервере.");return;}
  if(clicked==null||!clicked.assetId().equals("hunterlamp")||clicked.getWorld()!=player.getWorld()||clicked.isRemoved()||!near(player,clicked)){tell(player,"Этот фонарь недоступен или слишком далеко.");return;}
  entityMoved(clicked);LampState.Node source=state(player.getServer()).byEntity(clicked.getUuid());
  if(source==null){tell(player,"Этот фонарь ещё не настроен. Строитель может назвать его и добавить связь.");sendMenu(player,0,List.of(),"Охотничий фонарь","Нет настроенных назначений.",0,false);return;}
  cancelWaiting(player.getUuid());openNode(player,source,"");
 }
 private static void openNode(ServerPlayerEntity player,LampState.Node source,String message){
  long token;do{token=player.getRandom().nextLong();}while(token==0);
  CONTEXTS.put(player.getUuid(),new Context(token,source.id,player.getServer().getTicks()+CONTEXT_TICKS));
  List<LampState.Node> list=destinations(player.getServer(),source);int cooldown=cooldownTicks(player);
  String status=!message.isBlank()?message:cooldown>0?"Повторный переход будет доступен после задержки.":list.isEmpty()?"Нет настроенных исходящих назначений.":"Выберите фонарь назначения.";
  sendMenu(player,token,list,source.name,status,cooldown,false);

 }
 static List<LampState.Node> destinations(MinecraftServer server,LampState.Node source){List<LampState.Node> out=new ArrayList<>();Set<UUID> entities=new HashSet<>();for(UUID id:source.routes){LampState.Node n=state(server).nodes.get(id);if(n!=null&&!n.id.equals(source.id)&&entities.add(n.lamp)&&out.size()<RpConfig.INSTANCE.maxLampDestinations)out.add(n);}return List.copyOf(out);}
 private static boolean travel(ServerPlayerEntity player,UUID destination,long token){
  long tick=player.getServer().getTicks();Context c=CONTEXTS.get(player.getUuid());LampState graph=state(player.getServer());LampState.Node source=c==null?null:graph.nodes.get(c.source);
  if(c==null||!LampPolicy.validContext(c.token,token,c.expiresAt,tick)){refuse(player,source,"Меню устарело. Снова нажмите нужный фонарь.","expired_or_invalid_context");return false;}
  if(WAITING.containsKey(player.getUuid())){tell(player,"Целевой фонарь уже загружается. Esc отменяет ожидание.");return false;}
  if(!player.isAlive()||player.isSpectator()||!RpConfig.INSTANCE.playerTravelEnabled||source==null||!usableSource(player,source)){refuse(player,source,"Источник недоступен. Нажмите нужный фонарь заново.","source_unavailable");return false;}
  int cooldown=cooldownTicks(player);if(cooldown>0){refuse(player,source,"Подождите ещё "+String.format(java.util.Locale.ROOT,"%.1f",cooldown/20.0)+" сек. перед повторным переходом.","cooldown");return false;}
  LampState.Node target=graph.nodes.get(destination);if(target==null||!graph.connected(source.id,destination)){refuse(player,source,"Выбранное назначение удалено или связь закрыта.","route_unavailable");return false;}
  ServerWorld world=world(player.getServer(),target.dimension);if(world==null||!world.getWorldBorder().contains(target.pos)){refuse(player,source,"Назначение вне доступного мира.","target_world_unavailable");return false;}
  RpObjectEntity lamp=loadedLamp(world,target.lamp);if(lamp!=null)return finishTravel(player,source,target,lamp);
  if(WAITING.size()>=MAX_WAITING){refuse(player,source,"Слишком много ожидающих переходов. Повторите позже.","pending_limit");return false;}
  UUID lease=UUID.randomUUID();ChunkPos center=new ChunkPos(target.pos);List<ChunkPos> chunks=new ArrayList<>();
  try{for(int x=center.x-1;x<=center.x+1;x++)for(int z=center.z-1;z<=center.z+1;z++){ChunkPos pos=new ChunkPos(x,z);world.getChunkManager().addTicket(TRAVEL_TICKET,pos,2,lease);chunks.add(pos);}}
  catch(RuntimeException failure){for(ChunkPos pos:chunks)world.getChunkManager().removeTicket(TRAVEL_TICKET,pos,2,lease);DreamwalkerBb.LOG.error("Unable to acquire temporary lamp travel chunks for {}", player.getUuid(), failure);refuse(player,source,"Не удалось запросить загрузку назначения.","ticket_error");return false;}
  WAITING.put(player.getUuid(),new Pending(source.id,target.id,world,lease,List.copyOf(chunks),tick+WAIT_TICKS));
  sendMenu(player,token,destinations(player.getServer(),source),source.name,"Загружается целевой фонарь… Esc отменяет ожидание.",0,true);
  return true;
 }
 private static boolean finishTravel(ServerPlayerEntity player,LampState.Node source,LampState.Node target,RpObjectEntity lamp){
  entityMoved(lamp);ServerWorld world=(ServerWorld)lamp.getWorld();Vec3d landing=findLanding(world,player,lamp.getPos());if(landing==null){refuse(player,source,"Рядом с назначением нет безопасного места прибытия.","no_safe_landing");return false;}
  player.teleport(world,landing.x,landing.y,landing.z,player.getYaw(),player.getPitch());COOLDOWNS.put(player.getUuid(),(long)player.getServer().getTicks()+RpConfig.INSTANCE.lampTravelCooldownTicks);CONTEXTS.remove(player.getUuid());sendMenu(player,0,List.of(),"","",0,false);
  return true;
 }
 private static void refuse(ServerPlayerEntity player,LampState.Node source,String message,String reason){tell(player,message);if(source!=null&&usableSource(player,source))openNode(player,source,message);else{CONTEXTS.remove(player.getUuid());sendMenu(player,0,List.of(),"",message,0,false);}}
 private static boolean usableSource(ServerPlayerEntity player,LampState.Node n){return n.dimension.equals(player.getWorld().getRegistryKey().getValue().toString())&&near(player,loadedLamp(player.getServerWorld(),n.lamp));}
 static boolean near(ServerPlayerEntity player,RpObjectEntity object){return object!=null&&!object.isRemoved()&&object.getWorld()==player.getWorld()&&object.getBoundingBox().squaredMagnitude(player.getEyePos())<=36;}
 static RpObjectEntity loadedLamp(ServerWorld world,UUID id){Entity entity=world.getEntity(id);return entity instanceof RpObjectEntity lamp&&!lamp.isRemoved()&&lamp.assetId().equals("hunterlamp")?lamp:null;}
 static ServerWorld world(MinecraftServer server,String dimension){Identifier id=Identifier.tryParse(dimension);return id==null?null:server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));}
 static LampState state(MinecraftServer server){return LampState.get(server.getOverworld());}
 private static LampState.Node nearbySource(ServerPlayerEntity player){LampState.Node best=null;double distance=Double.MAX_VALUE;for(LampState.Node n:state(player.getServer()).nodes.values()){if(!n.dimension.equals(player.getWorld().getRegistryKey().getValue().toString()))continue;RpObjectEntity object=loadedLamp(player.getServerWorld(),n.lamp);if(near(player,object)&&object.squaredDistanceTo(player)<distance){best=n;distance=object.squaredDistanceTo(player);}}return best;}
 private static int cooldownTicks(ServerPlayerEntity player){return (int)Math.max(0,COOLDOWNS.getOrDefault(player.getUuid(),0L)-player.getServer().getTicks());}
 private static Vec3d findLanding(ServerWorld world,ServerPlayerEntity player,Vec3d lamp){
  double height=Math.max(1.8,player.getHeight());BlockPos anchor=BlockPos.ofFloored(lamp);
  for(int[] off:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){
   BlockPos p=anchor.add(off[0],0,off[1]);if(p.getY()<=world.getBottomY()||p.getY()+height>=world.getTopY()||!world.isChunkLoaded(p))continue;
   if(!world.getFluidState(p).isEmpty()||!world.getFluidState(p.up()).isEmpty()||hazard(world.getBlockState(p))||hazard(world.getBlockState(p.up())))continue;
   BlockPos floor=p.down();var support=world.getBlockState(floor);var shape=support.getCollisionShape(world,floor);
   if(shape.isEmpty()||!world.getFluidState(floor).isEmpty()||hazard(support)||support.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)||support.isOf(net.minecraft.block.Blocks.CAMPFIRE)||support.isOf(net.minecraft.block.Blocks.SOUL_CAMPFIRE)||support.isOf(net.minecraft.block.Blocks.CACTUS))continue;
   double y=floor.getY()+shape.getMax(Direction.Axis.Y);Box box=new Box(p.getX()+.5-player.getWidth()/2,y,p.getZ()+.5-player.getWidth()/2,p.getX()+.5+player.getWidth()/2,y+height,p.getZ()+.5+player.getWidth()/2);
   if(world.getWorldBorder().contains(box)&&world.isSpaceEmpty(player,box)&&world.getOtherEntities(player,box,e->e instanceof net.minecraft.entity.LivingEntity&&e.isAlive()).isEmpty())return new Vec3d(p.getX()+.5,y,p.getZ()+.5);
  }return null;
 }
 private static boolean hazard(net.minecraft.block.BlockState s){return s.isIn(net.minecraft.registry.tag.BlockTags.FIRE)||s.isOf(net.minecraft.block.Blocks.WITHER_ROSE)||s.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH)||s.isOf(net.minecraft.block.Blocks.POWDER_SNOW)||s.isOf(net.minecraft.block.Blocks.NETHER_PORTAL)||s.isOf(net.minecraft.block.Blocks.END_PORTAL)||s.isOf(net.minecraft.block.Blocks.END_GATEWAY);}
 private static void sendMenu(ServerPlayerEntity p,long token,List<LampState.Node> entries,String source,String message,int cooldown,boolean pending){PacketByteBuf out=new PacketByteBuf(Unpooled.buffer());out.writeLong(token);out.writeVarInt(entries.size());for(LampState.Node n:entries){out.writeUuid(n.id);out.writeString(n.name,64);}out.writeString(source,64);out.writeString(message,256);out.writeVarInt(cooldown);out.writeBoolean(pending);if(p.networkHandler!=null)ServerPlayNetworking.send(p,LIST_PACKET,out);}
 private static void tell(ServerPlayerEntity player,String message){player.sendMessage(Text.literal(message),false);}
 private static void protocolError(ServerPlayerEntity p,String reason){DreamwalkerBb.LOG.warn("Invalid lamp packet from {}: {}", p.getUuid(), reason);}
 private static void cancelWaiting(UUID player){Pending p=WAITING.remove(player);if(p!=null)for(ChunkPos chunk:p.chunks)p.world.getChunkManager().removeTicket(TRAVEL_TICKET,chunk,2,p.lease);}
 /** Cancels travel without resetting its cooldown. */
 public static void cancelTravel(ServerPlayerEntity player){cancelWaiting(player.getUuid());CONTEXTS.remove(player.getUuid());}
 private static void clearPlayer(UUID player){cancelWaiting(player);CONTEXTS.remove(player);COOLDOWNS.remove(player);NETWORK_PENDING.remove(player);CANCEL_PENDING.remove(player);LampEditor.clearPlayer(player);}
 static long contextTokenForTest(UUID player){Context c=CONTEXTS.get(player);return c==null?Long.MIN_VALUE:c.token;}
 static boolean travelForTest(ServerPlayerEntity player,UUID destination,long token){return travel(player,destination,token);}
 static void clearPlayerForTest(UUID player){clearPlayer(player);}
 public static void entityMoved(RpObjectEntity lamp){if(lamp==null||!(lamp.getWorld() instanceof ServerWorld world)||!lamp.assetId().equals("hunterlamp"))return;LampState graph=state(world.getServer());LampState.Node n=graph.byEntity(lamp.getUuid());if(n==null)return;Vec3d p=lamp.getPos();String dim=world.getRegistryKey().getValue().toString();if(n.origin.equals(p)&&n.dimension.equals(dim))return;n.origin=p;n.pos=lamp.getBlockPos();n.dimension=dim;graph.markDirty();}
 public static boolean register(ServerPlayerEntity player,String name){if(!player.hasPermissionLevel(2))return false;RpObjectEntity lamp=dev.dreamwalker.bloodbornerp.object.ObjectRegistry.objectAt(player);return register(player,lamp,name)!=null;}
 static LampState.Node register(ServerPlayerEntity player,RpObjectEntity lamp,String name){if(lamp==null||!LampPolicy.validName(name)||!near(player,lamp))return null;LampState graph=state(player.getServer());LampState.Node old=graph.byEntity(lamp.getUuid());if(old!=null)return old;if(graph.nodes.size()>=RpConfig.INSTANCE.maxLampDestinations)return null;LampState.Node n=new LampState.Node(UUID.randomUUID(),lamp.getUuid(),name,lamp.getWorld().getRegistryKey().getValue().toString(),lamp.getBlockPos());n.origin=lamp.getPos();graph.nodes.put(n.id,n);graph.markDirty();return n;}
 public static boolean rename(MinecraftServer server,UUID id,String name){LampState.Node n=state(server).nodes.get(id);if(n==null||!LampPolicy.validName(name))return false;n.name=name;state(server).markDirty();return true;}
 public static boolean remove(MinecraftServer server,UUID id){LampState graph=state(server);LampState.Node n=graph.nodes.get(id);if(n==null)return false;boolean removed=graph.removeNode(id);invalidate(server,n);return removed;}
 public static void removeByEntity(MinecraftServer server,UUID entityId){LampState graph=state(server);for(LampState.Node n:List.copyOf(graph.nodes.values()))if(n.lamp.equals(entityId)){graph.removeNode(n.id);invalidate(server,n);}}
 static void invalidate(MinecraftServer server,LampState.Node node){for(var e:List.copyOf(CONTEXTS.entrySet()))if(e.getValue().source.equals(node.id)){CONTEXTS.remove(e.getKey());cancelWaiting(e.getKey());}for(var e:List.copyOf(WAITING.entrySet()))if(e.getValue().destination.equals(node.id)){ServerPlayerEntity p=server.getPlayerManager().getPlayer(e.getKey());cancelWaiting(e.getKey());if(p!=null)tell(p,"Целевой фонарь удалён; переход отменён.");}LampEditor.removed(node.lamp);}
 public static boolean route(MinecraftServer server,UUID from,UUID to,boolean open){LampState graph=state(server);LampState.Node source=graph.nodes.get(from);if(source==null)return false;boolean changed;if(open){if(graph.connected(from,to))return false;changed=graph.setConnection(LampState.LEGACY_LINE,from,to,false);}else changed=graph.unlink(LampState.LEGACY_LINE,from,to,false);return changed;}
 public static List<LampInfo> list(MinecraftServer server){List<LampInfo> out=new ArrayList<>();for(LampState.Node n:state(server).nodes.values()){if(out.size()>=RpConfig.INSTANCE.maxLampDestinations)break;out.add(new LampInfo(n.id,n.name));}return List.copyOf(out);}
 public record LampInfo(UUID id,String name){}
 private record Context(long token,UUID source,long expiresAt){}
 private record Pending(UUID source,UUID destination,ServerWorld world,UUID lease,List<ChunkPos> chunks,long deadline){}
}
