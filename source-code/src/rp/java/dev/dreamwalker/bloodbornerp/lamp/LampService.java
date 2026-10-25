package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.RpConfig;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.architecture.BuildPermissions;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Direct travel to one partner; no menu, naming commands or client travel packets. */
public final class LampService {
 private static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
 private static final Map<UUID,Pending> WAITING=new LinkedHashMap<>();
 private static final ChunkTicketType<UUID> TRAVEL_TICKET=ChunkTicketType.create("bloodborne_rp_lamp_travel",UUID::compareTo);
 private static final int MAX_WAITING=64,WAIT_TICKS=200;
 private static boolean initialized;
 private LampService(){}
 public static void initialize(){
  if(initialized)return;initialized=true;
  ServerTickEvents.END_SERVER_TICK.register(LampService::tick);
  ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof RpObjectEntity lamp&&lamp.assetId().equals("hunterlamp"))entityMoved(lamp);});
  ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof RpObjectEntity lamp&&lamp.assetId().equals("hunterlamp")&&entity.getRemovalReason()!=null&&entity.getRemovalReason().shouldDestroy())removeWholeGroup(world.getServer(),lamp.getUuid());});
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clearPlayer(handler.player.getUuid()));
  ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(UUID id:List.copyOf(WAITING.keySet()))cancelWaiting(id);COOLDOWNS.clear();});
 }
 private static void tick(MinecraftServer server){
  long now=server.getTicks();COOLDOWNS.entrySet().removeIf(e->e.getValue()<=now);
  for(Map.Entry<UUID,Pending> row:List.copyOf(WAITING.entrySet())){
   ServerPlayerEntity player=server.getPlayerManager().getPlayer(row.getKey());Pending p=row.getValue();
   if(player==null){cancelWaiting(row.getKey());continue;}
   LampState graph=state(server);LampState.Node source=graph.nodes.get(p.source),target=graph.nodes.get(p.destination);
   if(source==null||target==null||graph.partner(source)!=target||!usableSource(player,source)||!player.isAlive()||player.isSpectator()||!RpConfig.INSTANCE.playerTravelEnabled){cancelWaiting(row.getKey());tell(player,"Источник или связка фонарей изменились; переход отменён.");continue;}
   if(p.world!=world(server,target.dimension)||!p.chunks.contains(new ChunkPos(target.pos))){cancelWaiting(row.getKey());if(now>=p.deadline){tell(player,"Назначение переместилось; время ожидания истекло.");continue;}requestTravel(player,source,target,p.deadline);continue;}
   RpObjectEntity lamp=loadedLamp(p.world,target.lamp);
   if(lamp!=null){cancelWaiting(row.getKey());finishTravel(player,target,lamp);continue;}
   if(now>=p.deadline){cancelWaiting(row.getKey());tell(player,"Не удалось загрузить целевой фонарь за 10 секунд. Можно повторить попытку.");}
  }
 }
 /** Uses exactly the clicked instance, even when another lamp is nearby. */
 public static void open(ServerPlayerEntity player,RpObjectEntity clicked){
  if(WAITING.containsKey(player.getUuid()))return;
  if(!RpConfig.INSTANCE.playerTravelEnabled){tell(player,"Переходы между фонарями отключены на сервере.");return;}
  if(!player.isAlive()||player.isSpectator()||clicked==null||!clicked.assetId().equals("hunterlamp")||!near(player,clicked)){tell(player,"Этот фонарь недоступен или слишком далеко.");return;}
  entityMoved(clicked);LampState graph=state(player.getServer());LampState.Node source=graph.byEntity(clicked.getUuid()),target=graph.partner(source);
  if(target==null){tell(player,"Этот фонарь не связан с другим фонарём.");return;}
  int cooldown=cooldownTicks(player);if(cooldown>0){tell(player,"Подождите ещё "+String.format(Locale.ROOT,"%.1f",cooldown/20.0)+" сек. перед повторным переходом.");return;}
  requestTravel(player,source,target,(long)player.getServer().getTicks()+WAIT_TICKS);
 }
 private static boolean requestTravel(ServerPlayerEntity player,LampState.Node source,LampState.Node target,long deadline){
  ServerWorld world=world(player.getServer(),target.dimension);if(world==null||!world.getWorldBorder().contains(target.pos)){tell(player,"Назначение вне доступного мира.");return false;}
  RpObjectEntity lamp=loadedLamp(world,target.lamp);if(lamp!=null)return finishTravel(player,target,lamp);
  if(WAITING.size()>=MAX_WAITING){tell(player,"Слишком много ожидающих переходов. Повторите позже.");return false;}
  UUID lease=UUID.randomUUID();ChunkPos center=new ChunkPos(target.pos);List<ChunkPos> chunks=new ArrayList<>();
  try{for(int x=center.x-1;x<=center.x+1;x++)for(int z=center.z-1;z<=center.z+1;z++){ChunkPos pos=new ChunkPos(x,z);world.getChunkManager().addTicket(TRAVEL_TICKET,pos,2,lease);chunks.add(pos);}}
  catch(RuntimeException failure){for(ChunkPos pos:chunks)world.getChunkManager().removeTicket(TRAVEL_TICKET,pos,2,lease);DreamwalkerBb.LOG.error("Unable to acquire temporary lamp travel chunks for {}",player.getUuid(),failure);tell(player,"Не удалось загрузить назначение.");return false;}
  WAITING.put(player.getUuid(),new Pending(source.id,target.id,world,lease,List.copyOf(chunks),deadline));tell(player,"Загружается целевой фонарь…");return true;
 }
 private static boolean finishTravel(ServerPlayerEntity player,LampState.Node target,RpObjectEntity lamp){
  entityMoved(lamp);ServerWorld world=(ServerWorld)lamp.getWorld();Vec3d landing=findLanding(world,player,lamp.getPos());if(landing==null){tell(player,"Рядом с назначением нет безопасного места прибытия.");return false;}
  player.teleport(world,landing.x,landing.y,landing.z,player.getYaw(),player.getPitch());COOLDOWNS.put(player.getUuid(),(long)player.getServer().getTicks()+RpConfig.INSTANCE.lampTravelCooldownTicks);return true;
 }
 public static boolean pairLamps(ServerPlayerEntity player,UUID first,UUID second){
  if(first==null||second==null||first.equals(second))return false;MinecraftServer server=player.getServer();RpObjectEntity a=findLoaded(server,first),b=findLoaded(server,second);
  if(a==null||b==null||a.isLocked()||b.isLocked()||!BuildPermissions.canEdit((ServerWorld)a.getWorld(),player,a.getBlockPos())||!BuildPermissions.canEdit((ServerWorld)b.getWorld(),player,b.getBlockPos()))return false;
  LampState graph=state(server);LampState.Node oldA=graph.byEntity(first),oldB=graph.byEntity(second);if(oldA!=null&&graph.partner(oldA)==oldB)return true;
  Set<UUID> replaced=new HashSet<>();for(UUID member:groupMembers(server,first)){LampState.Node n=graph.byEntity(member);if(n!=null)replaced.add(n.id);}for(UUID member:groupMembers(server,second)){LampState.Node n=graph.byEntity(member);if(n!=null)replaced.add(n.id);}
  if(graph.nodes.size()-replaced.size()+2>LampState.MAX_NODES)return false;
  removeWholeGroup(server,first);removeWholeGroup(server,second);
  LampState.Node na=node(a),nb=node(b);na.partner=nb.id;nb.partner=na.id;graph.nodes.put(na.id,na);graph.nodes.put(nb.id,nb);graph.markDirty();return true;
 }
 private static LampState.Node node(RpObjectEntity lamp){LampState.Node n=new LampState.Node(UUID.randomUUID(),lamp.getUuid(),lamp.getWorld().getRegistryKey().getValue().toString(),lamp.getBlockPos());n.origin=lamp.getPos();return n;}
 public static Set<UUID> groupMembers(MinecraftServer server,UUID member){LampState graph=state(server);LampState.Node n=graph.byEntity(member);if(n==null)return Set.of();LampState.Node other=graph.partner(n);return other==null?Set.of(n.lamp):Set.of(n.lamp,other.lamp);}
 public static boolean removeWholeGroup(MinecraftServer server,UUID member){Set<UUID> removed=state(server).removePair(member);if(removed.isEmpty())return false;for(var row:List.copyOf(WAITING.entrySet()))if(removed.contains(row.getValue().source)||removed.contains(row.getValue().destination)){cancelWaiting(row.getKey());ServerPlayerEntity player=server.getPlayerManager().getPlayer(row.getKey());if(player!=null)tell(player,"Связка фонарей удалена; переход отменён.");}return true;}
 public static void removeByEntity(MinecraftServer server,UUID member){removeWholeGroup(server,member);}
 private static RpObjectEntity findLoaded(MinecraftServer server,UUID id){for(ServerWorld world:server.getWorlds()){RpObjectEntity lamp=loadedLamp(world,id);if(lamp!=null)return lamp;}return null;}
 private static boolean usableSource(ServerPlayerEntity player,LampState.Node n){return n.dimension.equals(player.getWorld().getRegistryKey().getValue().toString())&&near(player,loadedLamp(player.getServerWorld(),n.lamp));}
 static boolean near(ServerPlayerEntity player,RpObjectEntity object){return object!=null&&!object.isRemoved()&&object.getWorld()==player.getWorld()&&object.getBoundingBox().squaredMagnitude(player.getEyePos())<=36;}
 static RpObjectEntity loadedLamp(ServerWorld world,UUID id){Entity entity=world.getEntity(id);return entity instanceof RpObjectEntity lamp&&!lamp.isRemoved()&&lamp.assetId().equals("hunterlamp")?lamp:null;}
 static ServerWorld world(MinecraftServer server,String dimension){Identifier id=Identifier.tryParse(dimension);return id==null?null:server.getWorld(RegistryKey.of(RegistryKeys.WORLD,id));}
 static LampState state(MinecraftServer server){return LampState.get(server.getOverworld());}
 static int cooldownTicks(ServerPlayerEntity player){return (int)Math.max(0,COOLDOWNS.getOrDefault(player.getUuid(),0L)-player.getServer().getTicks());}
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
 private static void tell(ServerPlayerEntity player,String message){player.sendMessage(Text.literal(message),false);}
 private static void cancelWaiting(UUID player){Pending p=WAITING.remove(player);if(p!=null)for(ChunkPos chunk:p.chunks)p.world.getChunkManager().removeTicket(TRAVEL_TICKET,chunk,2,p.lease);}
 /** Cancels a pending request without clearing cooldown. */
 public static void cancelTravel(ServerPlayerEntity player){cancelWaiting(player.getUuid());}
 private static void clearPlayer(UUID player){cancelWaiting(player);COOLDOWNS.remove(player);}
 static void clearPlayerForTest(UUID player){clearPlayer(player);}
 static boolean waitingForTest(UUID player){return WAITING.containsKey(player);}
 public static void entityMoved(RpObjectEntity lamp){if(lamp==null||!(lamp.getWorld() instanceof ServerWorld world)||!lamp.assetId().equals("hunterlamp"))return;LampState graph=state(world.getServer());LampState.Node n=graph.byEntity(lamp.getUuid());if(n==null)return;Vec3d p=lamp.getPos();String dim=world.getRegistryKey().getValue().toString();if(n.origin.equals(p)&&n.dimension.equals(dim))return;n.origin=p;n.pos=lamp.getBlockPos();n.dimension=dim;graph.markDirty();}
 private record Pending(UUID source,UUID destination,ServerWorld world,UUID lease,List<ChunkPos> chunks,long deadline){}
}
