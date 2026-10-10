package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.architecture.BuildPermissions;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** V12's one-target, at-most-six-lever ALL latch. Separate from legacy rule semantics. */
final class MechanismGroups extends PersistentState {
 private static final int LIMIT=4096,MAX_LEVERS=6;
 private static final class Group {
  final UUID id;final MechanismLinks.TargetRef target;final LinkedHashMap<UUID,MechanismLinks.TargetRef> sources=new LinkedHashMap<>();final Set<UUID> active=new LinkedHashSet<>();
  Boolean pending;ObjectPolicies.Initiator initiator;
  Group(UUID id,MechanismLinks.TargetRef target){this.id=id;this.target=target;}
 }
 private final Map<UUID,Group> groups=new LinkedHashMap<>();
 static MechanismGroups get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(MechanismGroups::fromNbt,MechanismGroups::new,"bloodborne_dw_lever_groups");}
 private Group source(UUID id){return groups.values().stream().filter(g->g.sources.containsKey(id)).findFirst().orElse(null);}
 static boolean contains(MinecraftServer server,UUID id){return get(server).source(id)!=null;}
 static boolean link(ServerPlayerEntity player,List<UUID> levers,MechanismLinks.TargetRef target){
  if(levers==null||target==null||levers.isEmpty()||levers.size()>MAX_LEVERS||new HashSet<>(levers).size()!=levers.size())return false;
  MinecraftServer server=player.getServer();var status=MechanismLinks.inspect(server,target);ServerWorld targetWorld=world(server,target.dimension());
  if(targetWorld==null||status.availability()!=MechanismLinks.Availability.LOADED||status.pulseOnly()||status.locked()||!BuildPermissions.canEdit(targetWorld,player,BlockPos.fromLong(target.root()))||!ObjectPolicies.canSetManual(player,target))return false;
  LinkedHashMap<UUID,RpObjectEntity> sources=new LinkedHashMap<>();for(UUID id:levers){RpObjectEntity lever=find(server,id);if(lever==null||!lever.isMechanism()||lever.isLocked()||id.equals(target.instance())||!BuildPermissions.canEdit((ServerWorld)lever.getWorld(),player,lever.getBlockPos()))return false;sources.put(id,lever);}
  MechanismGroups state=get(server);if(state.groups.size()>=LIMIT&&!sources.keySet().stream().anyMatch(id->state.source(id)!=null)&&state.groups.values().stream().noneMatch(g->g.target.instance().equals(target.instance())))return false;
  sources.values().forEach(MechanismLinks::loaded);for(UUID id:sources.keySet())MechanismLinks.removeWholeGroup(server,id);MechanismLinks.removeWholeGroup(server,target.instance());
  Group group=new Group(UUID.randomUUID(),target);sources.forEach((id,lever)->{group.sources.put(id,MechanismLinks.TargetRef.rp(lever));MechanismLinks.importBarrier(server,lever);});state.groups.put(group.id,group);state.markDirty();ObjectPolicies.setManual(player,target,true);return true;
 }
 static boolean use(RpObjectEntity lever,ServerPlayerEntity player){
  if(!(lever.getWorld() instanceof ServerWorld world))return false;MinecraftServer server=world.getServer();MechanismGroups state=get(server);Group group=state.source(lever.getUuid());if(group==null)return false;
  if(lever.isLocked()||group.pending!=null)return true;
  var status=MechanismLinks.inspect(server,group.target);if(status.availability()==MechanismLinks.Availability.STALE){MechanismLinks.removeWholeGroup(server,lever.getUuid());return true;}
  if(status.locked()||busy(server,group.target))return true;
  if(status.availability()==MechanismLinks.Availability.LOADED&&status.open())group.pending=false;
  else{
   if(status.availability()==MechanismLinks.Availability.LOADED&&group.active.size()==group.sources.size())group.active.clear(); // A permitted manual close starts a fresh cycle.
   if(!group.active.add(lever.getUuid()))return true;
   // Visual lever animation remains a short local action; the persistent latch is independent.
   lever.setOpen(true);
   if(group.active.size()<group.sources.size()){player.sendMessage(Text.literal("Механизм работает, но чего-то не хватает..."),false);state.markDirty();return true;}
   group.pending=true;
  }
  group.initiator=new ObjectPolicies.Initiator(player.getUuid(),player.getGameProfile().getName());
  state.markDirty();retry(server);return true;
 }
 static void retry(MinecraftServer server){MechanismGroups state=get(server);for(Group group:List.copyOf(state.groups.values())){
  if(group.pending==null)continue;var status=MechanismLinks.inspect(server,group.target);if(status.availability()==MechanismLinks.Availability.STALE){MechanismLinks.removeWholeGroup(server,group.target.instance());continue;}
  if(status.availability()!=MechanismLinks.Availability.LOADED||status.locked()||busy(server,group.target))continue;
  boolean desired=group.pending;ServerWorld world=world(server,group.target.dimension());ObjectPolicies.context(world,group.target,group.initiator);boolean accepted=false;
  try{accepted=status.open()==desired||MechanismLinks.applyEffect(server,group.target,desired,false);}catch(RuntimeException failure){DreamwalkerBb.LOG.error("Lever group action failed for {}; pending action retained",group.target.instance(),failure);}finally{ObjectPolicies.context(world,group.target,null);}
  if(accepted){if(desired&&!status.open()&&group.initiator!=null){ServerPlayerEntity player=server.getPlayerManager().getPlayer(group.initiator.uuid());if(player!=null)player.sendMessage(Text.literal("Что-то открылось..."),false);}if(!desired)group.active.clear();group.pending=null;group.initiator=null;state.markDirty();}
 }}
 private static boolean busy(MinecraftServer server,MechanismLinks.TargetRef target){ServerWorld world=world(server,target.dimension());return target.kind()==MechanismLinks.Kind.RP&&world!=null&&world.getEntity(target.instance()) instanceof RpObjectEntity rp&&rp.animationBusy();}
 static boolean pending(MinecraftServer server,MechanismLinks.TargetRef target){return get(server).groups.values().stream().anyMatch(g->g.target.key().equals(target.key())&&g.pending!=null);}
 static void expandMembers(MinecraftServer server,Set<UUID> members){for(Group group:get(server).groups.values())if(members.contains(group.target.instance())||group.sources.keySet().stream().anyMatch(members::contains)){members.add(group.target.instance());members.addAll(group.sources.keySet());}}
 static boolean removeMembers(MinecraftServer server,Set<UUID> members){MechanismGroups state=get(server);boolean changed=state.groups.values().removeIf(g->members.contains(g.target.instance())||g.sources.keySet().stream().anyMatch(members::contains));if(changed)state.markDirty();return changed;}
 static boolean active(MinecraftServer server,UUID source){Group group=get(server).source(source);return group!=null&&group.active.contains(source);}
 static List<MechanismLinks.TargetRef> targets(MinecraftServer server,UUID source){Group group=get(server).source(source);return group==null?List.of():List.of(group.target);}
 static List<MechanismLinks.TargetRef> sources(MinecraftServer server,MechanismLinks.TargetRef target){return get(server).groups.values().stream().filter(g->g.target.key().equals(target.key())).flatMap(g->g.sources.values().stream()).toList();}
 static MechanismGroups fromNbt(NbtCompound nbt){MechanismGroups state=new MechanismGroups();Set<UUID> assigned=new HashSet<>();for(NbtElement raw:nbt.getList("Groups",NbtElement.COMPOUND_TYPE)){
  if(state.groups.size()>=LIMIT)break;try{NbtCompound tag=(NbtCompound)raw;Group group=new Group(tag.getUuid("Id"),MechanismLinks.TargetRef.read(tag.getCompound("Target")));NbtList sources=tag.getList("Sources",NbtElement.COMPOUND_TYPE);if(sources.isEmpty()||sources.size()>MAX_LEVERS||state.groups.containsKey(group.id))continue;for(NbtElement item:sources){var ref=MechanismLinks.TargetRef.read((NbtCompound)item);if(ref.kind()!=MechanismLinks.Kind.RP||!ref.registryId().startsWith("lever_")||assigned.contains(ref.instance())||group.sources.put(ref.instance(),ref)!=null)throw new IllegalArgumentException("Invalid lever group source");}for(NbtElement item:tag.getList("Active",NbtElement.INT_ARRAY_TYPE)){UUID id=NbtHelper.toUuid(item);if(group.sources.containsKey(id))group.active.add(id);}if(tag.contains("Pending",NbtElement.BYTE_TYPE))group.pending=tag.getBoolean("Pending");group.initiator=ObjectPolicies.Initiator.read(tag.getCompound("Initiator"));assigned.addAll(group.sources.keySet());state.groups.put(group.id,group);}catch(RuntimeException failure){DreamwalkerBb.LOG.warn("Malformed saved lever group skipped",failure);}
 }return state;}
 @Override public NbtCompound writeNbt(NbtCompound nbt){nbt.putInt("Schema",1);NbtList out=new NbtList();for(Group group:groups.values()){NbtCompound tag=new NbtCompound();tag.putUuid("Id",group.id);tag.put("Target",group.target.write());NbtList sources=new NbtList(),active=new NbtList();group.sources.values().forEach(ref->sources.add(ref.write()));group.active.forEach(id->active.add(NbtHelper.fromUuid(id)));tag.put("Sources",sources);tag.put("Active",active);if(group.pending!=null)tag.putBoolean("Pending",group.pending);if(group.initiator!=null)tag.put("Initiator",group.initiator.write());out.add(tag);}nbt.put("Groups",out);return nbt;}
 private static ServerWorld world(MinecraftServer server,String dimension){return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(dimension)));}
 private static RpObjectEntity find(MinecraftServer server,UUID id){for(ServerWorld world:server.getWorlds())if(world.getEntity(id) instanceof RpObjectEntity rp&&!rp.isRemoved())return rp;return null;}
}
